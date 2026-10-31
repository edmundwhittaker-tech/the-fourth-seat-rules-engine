"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import { emptyEngineState, reduceEngineFrame } from '../../lib/rules/session';
import { shouldAutoPass, currentTurnPass, currentNextTurnPass, matchesTurnPass, canPassTurn, priorityCheckpoint, priorityStopKey, smartPassUnchanged, readPriorityStops, defaultPriorityStops, type PriorityStops, type TurnPass } from '../../lib/rules/interaction';
import type { GameView } from '../../lib/rules/views';
import type { EngineFrame, EngineRoom, EngineState } from '../../lib/rules/session';

type ApiReply = { error?: string; room?: EngineRoom; left?: boolean; ticket?: string; user?: string; websocketUrl?: string };
class EngineApiError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
}
const roomGone = (cause: unknown) => cause instanceof EngineApiError && (cause.status === 404 || cause.status === 410);
const passKey = (current: EngineState) => current.gameId+':'+current.promptKey;
type Session = { ticket: string; user: string; websocketUrl: string; room: EngineRoom | null };
export function browserKey() {
  let key = localStorage.getItem('fourth-seat-engine-key');
  if (!key || !/^[a-f0-9]{64}$/.test(key)) {
    key = [...crypto.getRandomValues(new Uint8Array(32))].map(b => b.toString(16).padStart(2,'0')).join('');
    localStorage.setItem('fourth-seat-engine-key',key);
  }
  return key;
}
export function useEngineSession(profileId: string, displayName: string, initialRoom: EngineRoom | null = null) {
  const [room,setRoom] = useState<EngineRoom | null>(initialRoom);
  const [state,setState] = useState(emptyEngineState);
  // Update transport state synchronously so a queued pass cannot answer a newer prompt.
  const stateRef = useRef(state);
  const updateState = useCallback((change: (previous: typeof state) => typeof state) => {
    const next = change(stateRef.current); stateRef.current = next; setState(next);
  },[]);
  const [available,setAvailable] = useState(false);
  const [availabilityMessage,setAvailabilityMessage] = useState('Checking Rules Engine availability…');
  const [connection,setConnection] = useState('connecting');
  const [error,setError] = useState('');
  const [busy,setBusy] = useState(false);
  const [user,setUser] = useState('');
  const [autoPass,setAutoPass] = useState(()=>typeof window==='undefined'||localStorage.getItem('fourth-seat-auto-pass')!=='off');
  const autoPassRef = useRef(autoPass);
  const [priorityStops,setPriorityStops] = useState<PriorityStops>(()=>typeof window==='undefined'?defaultPriorityStops:readPriorityStops(localStorage.getItem('fourth-seat-priority-stops')));
  const stopsRef = useRef(priorityStops);
  const releasedStops = useRef(new Set<string>());
  const [releasedStop,setReleasedStop] = useState<string|null>(null);
  const [autoPassPaused,setAutoPassPaused] = useState(false);
  const holdRef = useRef(false);
  const holdPriority = useCallback((hold: boolean) => { holdRef.current=hold; setAutoPassPaused(hold); },[]);
  const [actionPending,setActionPending] = useState(false);
  const [turnPass,setTurnPass] = useState<TurnPass | null>(null);
  const turnPassRef = useRef<TurnPass | null>(null);
  const cancelPassTurn = useCallback(() => { turnPassRef.current=null; setTurnPass(null); },[]);
  const autoPassed = useRef(new Set<string>());
  const takeControl = useCallback(() => { cancelPassTurn(); holdPriority(true); },[cancelPassTurn,holdPriority]);
  const toggleAutoPass = (enabled: boolean) => {
    autoPassRef.current=enabled; setAutoPass(enabled); cancelPassTurn(); holdPriority(!enabled);
    localStorage.setItem('fourth-seat-auto-pass', enabled ? 'on' : 'off');
  };
  const updatePriorityStops = (next: PriorityStops) => {
    stopsRef.current=next; setPriorityStops(next); releasedStops.current.clear(); setReleasedStop(null);
    localStorage.setItem('fourth-seat-priority-stops',JSON.stringify(next));
  };
  const socket = useRef<WebSocket | null>(null);
  const pending = useRef(new Map<string,{resolve:(value:boolean)=>void;timer:number;promptKey:string|null}>());
  const counter = useRef(0);
  const callPending = useRef(false);
  const roomRevision = useRef(0);
  const retiredGames = useRef(new Set<string>());
  const adoptRoom = useCallback((next: EngineRoom | null, endedGameId?: string) => {
    if(endedGameId)retiredGames.current.add(endedGameId);
    setRoom(next);
    if(!next||next.status==='lobby') {
      cancelPassTurn();
      updateState(previous=>{
        if(previous.gameId)retiredGames.current.add(previous.gameId);
        return emptyEngineState();
      });
      for(const entry of pending.current.values()){clearTimeout(entry.timer);entry.resolve(false);}
      pending.current.clear();setActionPending(false);
    }
  },[cancelPassTurn,updateState]);
  const api = useCallback(async (body: Record<string,unknown>) => {
    const response = await fetch('/api/engine',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...body,profileId,displayName,browserKey:browserKey()})});
    const data = await response.json() as ApiReply;
    if (!response.ok) throw new EngineApiError(data.error ?? 'The engine request failed.', response.status);
    return data;
  },[profileId,displayName]);
  useEffect(() => {
    let cancelled = false;
    const check = async () => {
      try {
        const data = await (await fetch('/api/engine',{cache:'no-store'})).json() as {available?:boolean;message?:string};
        if (!cancelled) { setAvailable(data.available === true); setAvailabilityMessage(data.message ?? ''); }
      } catch { if (!cancelled) { setAvailable(false); setAvailabilityMessage('The rules service could not be reached. Regular play remains available.'); } }
    };
    void check(); const timer = window.setInterval(check,20_000);
    return () => { cancelled=true; clearInterval(timer); };
  },[]);
  useEffect(() => {
    if (!available) return;
    let cancelled = false, retry = 0, reconnectTimer: number | undefined;
    const currentPending = pending.current;
    const connect = async () => {
      if (cancelled) return;
      setConnection('connecting');
      try {
        const revision = roomRevision.current;
        const session = await api({operation:'session'}) as Session;
        if (cancelled) return;
        setUser(session.user);
        if (!callPending.current && revision === roomRevision.current) {
          adoptRoom(session.room);
        }
        const ws = new WebSocket(session.websocketUrl); socket.current=ws;
        ws.onopen=()=>ws.send(JSON.stringify({ticket:session.ticket}));
        ws.onmessage=event=>{
          const frame: EngineFrame = JSON.parse(String(event.data));
          if(frame.type==='roomReset') {
            roomRevision.current++;
            const reset=frame as EngineFrame & {room:EngineRoom;endedGameId?:string};
            adoptRoom(reset.room,reset.endedGameId);setError('');return;
          }
          const data=frame.data as {gameId?:string;gameView?:GameView;players?:unknown[]}|undefined;
          const frameGameId=frame.method==='START_GAME'?data?.gameId??frame.objectId:frame.objectId;
          if(frame.type==='event'&&frameGameId&&retiredGames.current.has(frameGameId))return;
          const updatedGame=data?.gameView??(Array.isArray(data?.players)?data as unknown as GameView:null);
          if(frame.type==='event'&&updatedGame&&releasedStops.current.size){
            const nextStop=priorityStopKey({...emptyEngineState(),gameId:frameGameId??null,game:updatedGame},stopsRef.current);
            if(!nextStop||!releasedStops.current.has(nextStop)){releasedStops.current.clear();setReleasedStop(null);}
          }
          if(turnPassRef.current&&frame.type==='event'&&(
            ['START_GAME','GAME_OVER','END_GAME_INFO','GAME_ERROR'].includes(frame.method??'')
            ||updatedGame&&!matchesTurnPass({...emptyEngineState(),gameId:frameGameId??null,game:updatedGame},turnPassRef.current)
          ))cancelPassTurn();
          // Preserve the highest verified turn, not only the turn where yielding
          // began. A newer-sequence regressive snapshot must cancel before an
          // automatic priority response can be scheduled.
          if(turnPassRef.current?.untilPlayerId&&frame.type==='event'&&updatedGame
            &&Number.isInteger(updatedGame.turn)&&updatedGame.turn>turnPassRef.current.turn){
            const advanced={...turnPassRef.current,turn:updatedGame.turn};
            turnPassRef.current=advanced;setTurnPass(advanced);
          }
          if (frame.type==='ready') { cancelPassTurn(); retry=0; setConnection('live'); updateState(s=>({...s,seq:-1,prompt:null,promptKey:null,selectable:[],priorityWindowKnown:false,priorityWindowEmpty:false})); return; }
          if (frame.type==='result'&&frame.requestId) {
            const entry=currentPending.get(frame.requestId);
            if (entry) {
              clearTimeout(entry.timer); currentPending.delete(frame.requestId); setActionPending(false);
              if(frame.ok===true)updateState(s=>s.promptKey===entry.promptKey?{...s,prompt:null,promptKey:null,selectable:[],priorityWindowKnown:false,priorityWindowEmpty:false}:s);
              entry.resolve(frame.ok===true);
            }
          }
          if(frame.type==='error'||frame.type==='result'&&frame.ok===false)takeControl();
          if (frame.type==='serverLink') {takeControl();setConnection((frame.data as {state?:string})?.state==='restored'?'live':'connecting');}
          const previous=stateRef.current;
          const next=reduceEngineFrame(previous,frame);
          // Mandatory choices end consent to skip. Never answer them automatically,
          // and never silently revive the old yield after the human makes that choice.
          if(next!==previous&&next.promptKey!==previous.promptKey&&next.prompt&&!canPassTurn(next))takeControl();
          if(frame.method==='START_GAME'){releasedStops.current.clear();setReleasedStop(null);holdPriority(false);}
          updateState(()=>next);
        };
        ws.onclose=()=>{
          takeControl();
          for (const entry of currentPending.values()) { clearTimeout(entry.timer); entry.resolve(false); }
          currentPending.clear(); setActionPending(false); setConnection('reconnecting');
          if (!cancelled) reconnectTimer=window.setTimeout(connect,Math.min(10_000,1000*2**retry++));
        };
        ws.onerror=()=>{takeControl();setConnection('reconnecting');};
      } catch (cause) {
        if (cancelled) return;
        setError(cause instanceof Error?cause.message:'Could not reconnect.');
        reconnectTimer=window.setTimeout(connect,Math.min(10_000,1000*2**retry++));
      }
    };
    void connect();
    const heartbeat=window.setInterval(()=>{if(socket.current?.readyState===WebSocket.OPEN)socket.current.send(JSON.stringify({action:'ping'}));},20_000);
    return ()=>{
      cancelled=true;clearTimeout(reconnectTimer);clearInterval(heartbeat);
      socket.current?.close();socket.current=null;
      for(const entry of currentPending.values()){clearTimeout(entry.timer);entry.resolve(false);}currentPending.clear();
    };
  },[available,api,adoptRoom,cancelPassTurn,updateState,takeControl,holdPriority]);
  const roomCode=room?.code,roomStatus=room?.status;
  useEffect(()=>{
    if(!roomCode||!available)return;
    let cancelled=false;
    const code=roomCode;
    const timer=window.setInterval(()=>{
      if(callPending.current)return;
      const revision=roomRevision.current;
      void api({operation:'room',code}).then(data=>{if(!cancelled&&!callPending.current&&revision===roomRevision.current)adoptRoom(data.room??null);}).catch(cause=>{
        if(cancelled||callPending.current||revision!==roomRevision.current)return;
        if(roomGone(cause)){roomRevision.current++;adoptRoom(null);}
        setError(cause.message);
      });
    },roomStatus==='lobby'||roomStatus==='starting'?3000:20_000);
    return()=>{cancelled=true;clearInterval(timer);};
  },[roomCode,roomStatus,available,api,adoptRoom]);
  const call=async(body:Record<string,unknown>)=>{
    if(callPending.current)return null;
    cancelPassTurn();callPending.current=true;roomRevision.current++;setBusy(true);setError('');
    try {const data=await api(body);if(data.room)adoptRoom(data.room);if(data.left)adoptRoom(null);return data;}
    catch(cause){
      if(roomGone(cause)&&body.operation==='leave')adoptRoom(null);
      setError(cause instanceof Error?cause.message:'The request failed.');return null;
    }
    finally{callPending.current=false;setBusy(false);}
  };
  const sendRequest=useCallback(async(action:string,args:Record<string,unknown>):Promise<boolean>=>{
    if(pending.current.size)return false;
    if(connection!=='live'||socket.current?.readyState!==WebSocket.OPEN){setError('Reconnect before taking an action.');return false;}
    setActionPending(true);
    const ws=socket.current,requestId='play-'+ ++counter.current;
    return new Promise(resolve=>{
      const timer=window.setTimeout(()=>{takeControl();pending.current.delete(requestId);setActionPending(false);setError('No acknowledgement yet. Reconnect before trying again.');resolve(false);},30_000);
      pending.current.set(requestId,{resolve,timer,promptKey:stateRef.current.promptKey});
      ws.send(JSON.stringify({requestId,action,args}));
    });
  },[connection,takeControl]);
  const send=useCallback(async(action:string,args:Record<string,unknown>)=>{
    cancelPassTurn(); holdPriority(false);
    return sendRequest(action,args);
  },[cancelPassTurn,holdPriority,sendRequest]);
  const rememberPass = (key: string) => {
    autoPassed.current.add(key);
    if(autoPassed.current.size>256)autoPassed.current.delete(autoPassed.current.values().next().value!);
  };
  const beginTurnPass=async(intent:TurnPass|null,smart=true):Promise<boolean>=>{
    const current=stateRef.current;
    if(!intent||connection!=='live'||busy||error||pending.current.size||!canPassTurn(current))return false;
    holdPriority(false);
    const stop=priorityStopKey(current,stopsRef.current);
    if(stop){releasedStops.current.add(stop);setReleasedStop(stop);}
    // Missing or unfamiliar legal-action data permits this one explicit pass only.
    const next=smart&&current.priorityWindowKnown&&current.game?{...intent,checkpoint:priorityCheckpoint(current.game)}:null;
    turnPassRef.current=next;setTurnPass(next);
    rememberPass(passKey(current));
    const accepted=await sendRequest('sendPlayerBoolean',{gameId:current.gameId,value:false});
    if(!accepted)cancelPassTurn();
    return accepted;
  };
  const passTurn=()=>{
    const current=stateRef.current,intent=currentTurnPass(current);
    if(!intent||!current.game?.players?.some(player=>player.controlled&&!player.hasLeft&&player.playerId===intent.activePlayerId))return Promise.resolve(false);
    return beginTurnPass({...intent,skipResponses:true});
  };
  const passPriority=()=>{
    const current=stateRef.current;
    if(!autoPassRef.current)return beginTurnPass(currentTurnPass(current),false);
    return beginTurnPass(currentNextTurnPass(current)??currentTurnPass(current));
  };
  const passingTurn=matchesTurnPass(state,turnPass)&&connection==='live'&&!error&&!state.error;
  const stop=priorityStopKey(state,priorityStops);
  const atPriorityStop=!!stop&&stop!==releasedStop&&!(turnPass?.skipResponses&&matchesTurnPass(state,turnPass));
  const idlePassing=autoPass&&!autoPassPaused&&!atPriorityStop&&shouldAutoPass(state);
  const smartPassing=passingTurn&&!atPriorityStop&&(!state.prompt||smartPassUnchanged(state,turnPass));
  useEffect(()=>{
    const current=stateRef.current,intent=turnPassRef.current;
    const stopKey=priorityStopKey(current,stopsRef.current);
    const stopped=!!stopKey&&!releasedStops.current.has(stopKey)&&!(intent?.skipResponses&&matchesTurnPass(current,intent));
    if(stopped&&canPassTurn(current)){cancelPassTurn();return;}
    if(connection!=='live'||actionPending||busy||error||holdRef.current)return;
    if(intent&&canPassTurn(current)&&!smartPassUnchanged(current,intent)){
      // No available response: let the usual idle pass continue. Otherwise restore
      // the exact new prompt rather than granting a blanket skip through a spell.
      cancelPassTurn();
      if(!shouldAutoPass(current)){holdPriority(true);return;}
    }
    const yielding=smartPassUnchanged(current,turnPassRef.current);
    if(!yielding&&(!autoPassRef.current||!shouldAutoPass(current)))return;
    const key=passKey(current);
    if(autoPassed.current.has(key))return;
    const timer=window.setTimeout(()=>{
      const latest=stateRef.current,latestStop=priorityStopKey(latest,stopsRef.current);
      if(pending.current.size||callPending.current||holdRef.current||passKey(latest)!==key
        ||latestStop&&!releasedStops.current.has(latestStop)&&!(turnPassRef.current?.skipResponses&&matchesTurnPass(latest,turnPassRef.current)))return;
      if(!smartPassUnchanged(latest,turnPassRef.current)&&(!autoPassRef.current||!shouldAutoPass(latest)))return;
      rememberPass(key);
      void sendRequest('sendPlayerBoolean',{gameId:latest.gameId,value:false}).then(accepted=>{if(!accepted)cancelPassTurn();});
    },150);
    return()=>clearTimeout(timer);
  },[autoPass,autoPassPaused,priorityStops,connection,actionPending,busy,error,state,sendRequest,turnPass,cancelPassTurn,holdPriority]);
  return {passTurn,passPriority,passingTurn:smartPassing,autoPassing:idlePassing||smartPassing,cancelPassTurn:takeControl,autoPass,toggleAutoPass,priorityStops,updatePriorityStops,atPriorityStop,actionPending,room,state,available,availabilityMessage,connection,error,setError,busy,user,call,send};
}
