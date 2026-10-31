"use client";

import { useRef, useState } from 'react';
import './table-layout.css';
import { GameTable } from '../gameplay/GameTable';
import { EngineControlsContext } from './engine-controls';
import type { EngineRoom } from '../../lib/rules/session';
import { promptAnswer, records } from '../../lib/rules/session';
import { projectEngineRoom } from '../../lib/rules/projection';
import type { DeckData } from '../../lib/types';
import { useEngineArt } from './use-engine-art';
import { useEngineSession } from './use-engine-session';
import EnginePrompt from './EnginePrompt';
import EngineCardChoices from './EngineCardChoices';
import {cardPromptChoices,opensCardBrowser} from '../../lib/rules/choices';
import {createEngineCardResolver} from '../../lib/rules/art';
import EngineManaPool from './EngineManaPool';
import EngineLobby from './EngineLobby';
import { engineText } from '../../lib/rules/player-labels';
import EngineTurnBar from './EngineTurnBar';
import { playerPromptAnswers } from '../../lib/rules/interaction';

type Props={localMode?:boolean;onStopLocal?:()=>void;initialRoom?:EngineRoom|null;profileId:string;displayName:string;selectedDeck?:DeckData;onChooseDeck:()=>void;onBack:()=>void};

export default function RulesExperience({localMode=false,onStopLocal,initialRoom,profileId,displayName,selectedDeck,onChooseDeck,onBack}:Props){
  const engine=useEngineSession(profileId,displayName,initialRoom);
  const {room,state,available,availabilityMessage,connection,error,busy,user,call,send}=engine;
  const [code,setCode]=useState(()=>typeof window==='undefined'?'':new URLSearchParams(window.location.search).get('rules')??'');
  const [manualActionBusy,setActionBusy]=useState(false);
  const actionInFlight=useRef(false);
  const actionBusy=manualActionBusy||engine.actionPending===true;
  const [cardBrowser,setCardBrowser]=useState<{key:string|null;open:boolean}|null>(null);
  const game=state.game;
  const viewerOut=game?.players?.some(player=>player.controlled&&player.hasLeft)===true;
  const artwork=useEngineArt(game,selectedDeck,state.prompt);
  const live=connection==='live';
  const label=(value:string)=>engineText(value,room);
  async function act(action:string,args:Record<string,unknown>){
    if(actionInFlight.current||actionBusy||!live||viewerOut||state.ended)return false;
    actionInFlight.current=true;setActionBusy(true);try{return await send(action,args);}finally{actionInFlight.current=false;setActionBusy(false);}
  }
  async function passTurn(endTurn=true){
    if(actionInFlight.current||actionBusy||!live||viewerOut||state.ended)return false;
    actionInFlight.current=true;setActionBusy(true);
    try{return await (endTurn?engine.passTurn():engine.passPriority());}finally{actionInFlight.current=false;setActionBusy(false);}
  }
  const endGame=async()=>{
    if(busy||!room?.isHost||!room.canEndGame)return;
    if(!window.confirm('End this game for everyone and return to the engine lobby? Your seats and decks will be kept.'))return;
    await call({operation:'end',code:room.code});
  };
  const leaveFinished=async()=>{const result=await call({operation:'leave',code:room?.code});if(result?.left)onBack();};
  const playerAnswers=playerPromptAnswers(state);
  const playerChoiceActive=playerAnswers.size>0;
  const cardChoices=cardPromptChoices(state);
  const choiceValues=new Set([...cardChoices.map(choice=>choice.value),...[...playerAnswers.values()].map(answer=>String(answer.args.value))]);
  const resolveCard=createEngineCardResolver(selectedDeck,artwork);
  const showCards=cardBrowser?.key===state.promptKey?cardBrowser?.open:opensCardBrowser(cardChoices,playerChoiceActive);
  const openCards=()=>setCardBrowser({key:state.promptKey,open:true});
  const closeCards=()=>setCardBrowser({key:state.promptKey,open:false});
  const chooseCard=async(value:string)=>{
    if(!live||actionBusy||viewerOut||state.ended||!state.prompt||!cardChoices.some(choice=>choice.value===value))return false;
    const command=promptAnswer(state.prompt,value);return act(command.action,command.args);
  };
  const attackChoice=playerChoiceActive&&/attack|defend/i.test(state.prompt?.message??'');
  const choosePlayer=(id:string)=>{const command=playerAnswers.get(id);if(command)void act(command.action,command.args);};
  const pick=(id:string)=>{const choice=cardChoices.find(choice=>choice.card.id===id);if(choice){void chooseCard(choice.value);return;}if(!state.selectable.includes(id)||!state.prompt||!live||actionBusy||viewerOut||state.ended)return;void act('sendPlayerUUID',{gameId:state.gameId,value:id});};
  const header=<header className="engine-header"><button className="table-brand" onClick={onBack}>THE <b>FOURTH SEAT</b></button><span className="engine-mode-badge">RULES ENGINE · EXPERIMENTAL</span><span className={`engine-connection ${live?'live':''}`} role="status">{available?connection:'Not active yet'}</span><button onClick={onBack}>{localMode?'Deck selection':'Regular play'}</button></header>;
  if(localMode&&!room&&!game)return <div className="room-overlay engine-screen">{header}<section className="engine-setup"><span className="eyebrow">ON YOUR COMPUTER</span><h1>Your next Commander game.</h1><p>{selectedDeck?`${selectedDeck.name} · ${selectedDeck.commander.name}`:'Choose a deck to play against three AI opponents.'}</p>{!available&&<p role="status">{availabilityMessage}</p>}<div className="desktop-actions"><button className="primary" disabled={!available||!live||busy||!selectedDeck} onClick={()=>void call({operation:'create',deckId:selectedDeck?.id,humanSeats:1})}>Create AI table</button><button onClick={onChooseDeck}>Choose a deck</button>{onStopLocal&&<button onClick={onStopLocal}>Stop local engine</button>}</div>{error&&<div role="alert">{error}</div>}</section></div>;
  if(!room&&!game)return <div className="room-overlay engine-screen">{header}<section className="engine-setup"><span className="eyebrow">CHOOSE HOW THE TABLE PLAYS</span><h1>Magic, with the rules handled.</h1><p>Invite friends, fill seats with AI, and let the engine manage the game. Your regular rooms and solo test tables are always available.</p>{!available&&<div className="engine-service-note" role="status"><strong>Engine hosting is not active yet</strong><p>{availabilityMessage}</p><button onClick={onBack}>Back to regular play</button></div>}<div className="engine-setup-grid"><section className="engine-setup-panel"><small>HOST A RULES ENGINE GAME</small><h2>Gather four seats</h2><p>{selectedDeck?`${selectedDeck.name} · ${selectedDeck.commander.name}`:'Select a Commander deck to create or join a game.'}</p><button onClick={onChooseDeck}>{selectedDeck?'Change deck':'Choose a deck'}</button><p className="engine-fine-print">Your lobby opens with three empty seats. Invite friends, or add bots and choose their decks in the lobby.</p><button className="primary" disabled={!available||!live||busy||!selectedDeck} onClick={()=>void call({operation:'create',deckId:selectedDeck?.id,humanSeats:4})}>Create engine room</button></section><section className="engine-setup-panel"><small>JOIN FRIENDS</small><h2>A seat is waiting</h2><p>Use the code from a Rules Engine room. Regular room codes still work from regular play.</p><form onSubmit={e=>{e.preventDefault();void call({operation:'join',code,deckId:selectedDeck?.id});}}><label htmlFor="engine-code">Engine room code</label><input id="engine-code" value={code} maxLength={5} onChange={e=>setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g,''))} placeholder="A3F72"/><button className="primary" disabled={!available||!live||busy||code.length!==5||!selectedDeck}>Join engine room</button></form><div className="engine-mode-note"><b>Your choice, per game</b><p>Regular play gives your table manual controls. Rules Engine play follows the game’s prompts for actions, targets, combat, and mana.</p></div></section></div>{error&&<div className="inline-error" role="alert">{error}</div>}</section></div>;
  if(!game&&room)return <div className="room-overlay lobby-screen engine-screen">{header}<EngineLobby room={room} user={user} live={live} busy={busy} selectedDeck={selectedDeck} onChooseDeck={onChooseDeck} call={call}/>{error&&<div className="inline-error engine-lobby-error" role="alert">{error}</div>}</div>;
  const projected=room?projectEngineRoom(state,room,selectedDeck,artwork):null;
  if(!projected||!game)return <div className="room-overlay engine-screen">{header}<section className="engine-setup"><p>Restoring the engine table…</p></section></div>;
  const ownTurn=game.activePlayerId===projected.viewerSeatId;
  const canEndTurn=live&&!actionBusy&&!state.ended&&!viewerOut&&!engine.passingTurn&&state.prompt?.method==='GAME_SELECT'&&state.prompt.title==='Your priority'&&ownTurn;
  const endTurn=()=>{if(canEndTurn)void passTurn();};
  const sendTable=(type:string,payload?:Record<string,unknown>)=>{
    if(type==='CONCEDE'){void act('sendPlayerAction',{gameId:state.gameId,action:'CONCEDE'});return;}
    if(type==='ADVANCE_TURN'){endTurn();return;}
    if(type==='MOVE_CARD'||type==='TAP_CARD'){if(typeof payload?.instanceId==='string')pick(payload.instanceId);return;}
    engine.setError('Use the current rules prompt to make this decision.');
  };
  const idlePassing=engine.autoPassing===true&&live&&!error&&!state.error;
  const priorityName=label(game.priorityPlayerName||'the table');
  const waitingText=actionBusy?'Playing…':idlePassing?'Auto-passing':`Waiting on ${priorityName}`;
  const menuContent=<>{localMode&&onStopLocal&&<button onClick={onStopLocal}>Stop local engine</button>}<label className="engine-auto-pass-setting"><input type="checkbox" checked={engine.autoPass!==false} onChange={event=>engine.toggleAutoPass(event.target.checked)}/>Smart passing</label><details className="rules-extra-choices"><summary>Priority stops</summary><p>Pass continues through quiet windows. New spells, abilities and required choices return control to you.</p><label className="engine-auto-pass-setting"><input type="checkbox" checked={engine.priorityStops.beforeCombat} onChange={event=>engine.updatePriorityStops({...engine.priorityStops,beforeCombat:event.target.checked})}/>Before combat</label><label className="engine-auto-pass-setting"><input type="checkbox" checked={engine.priorityStops.opponentEndStep} onChange={event=>engine.updatePriorityStops({...engine.priorityStops,opponentEndStep:event.target.checked})}/>Opponents’ end steps</label></details><details className="rules-extra-choices"><summary>Game details &amp; special actions</summary><p>Room {room?.code} · {game.step?.replaceAll('_',' ')} · {label(game.priorityPlayerName)} has priority</p>{game.players?.map(p=><p key={p.playerId}>{label(p.name)}: {Object.entries(p.manaPool??{}).filter(([,n])=>n>0).map(([name,n])=>name+' '+n).join(', ')||'No floating mana'}</p>)}{[...records(game.lookedAt),...records(game.revealed)].map((group,index)=><div key={index}><b>{group.name}</b><p>{records(group.cards).map(card=>card.displayName??card.name).join(', ')}</p></div>)}</details></>;
  const prompt=<div className="engine-decision-toolbar">{idlePassing&&!viewerOut&&!state.ended&&<div className="engine-turn-pass" role="status"><span>Auto-passing</span><button onClick={engine.cancelPassTurn}>Take control</button></div>}<EngineManaPool game={game} prompt={state.prompt} disabled={!live||actionBusy||viewerOut||!!state.ended} send={act}/>{(error||state.error)&&<div className="rules-inline-error" role="alert">{label(error||state.error)}</div>}{!live&&<p className="rules-connection-note" role="status">Restoring your game connection. Controls will return when the service reconnects.</p>}{state.ended?<section className="engine-prompt"><h2>{label(state.ended)}</h2><button onClick={()=>void leaveFinished()}>Leave finished game</button></section>:viewerOut?<section className="engine-prompt" role="status"><h2>You are out</h2><p>This table is read-only. The remaining players can continue their game.</p><button onClick={onBack}>Return to lobby</button></section>:<EnginePrompt key={state.promptKey} prompt={idlePassing?null:state.prompt} waitingText={waitingText} onPass={()=>passTurn(false)} passingTurn={engine.passingTurn} visualValues={choiceValues} cardChoices={cardChoices.length>0?<button className="engine-browse-cards" disabled={!live||actionBusy} onClick={openCards}>Choose a card · {cardChoices.length} legal options</button>:undefined} playerTargetHint={playerChoiceActive?(attackChoice?'Click a highlighted profile above to choose who this creature attacks.':'Click a highlighted profile above to choose that player.'):undefined} disabled={!live||actionBusy} label={label} send={act}/>}</div>;
  return <EngineControlsContext.Provider value={{openCardChoices:cardChoices.length>0?openCards:undefined,cardChoiceZones:new Set(cardChoices.map(choice=>choice.zone.toLowerCase())),endGame:room?.isHost&&room.canEndGame?()=>void endGame():undefined,endTurn,endingTurn:engine.passingTurn,lifecycleBusy:busy,menuContent,statusBar:<EngineTurnBar game={game}/>,playerChoiceActive,playerChoiceLabel:attackChoice?'Attack':'Choose',playerTargetIds:new Set(live&&!actionBusy&&!state.ended&&!viewerOut?[...playerAnswers.keys()]:[]),selectPlayer:choosePlayer,selectable:new Set(live&&!actionBusy&&!state.ended&&!viewerOut?[...state.selectable,...cardChoices.flatMap(choice=>choice.card.id?[choice.card.id]:[])]:[]),select:pick,prompt,canEndTurn}}><GameTable room={projected} profileId={profileId} send={sendTable} leave={onBack} finish={()=>{}} syncStatus={live?'live':'reconnecting'} pendingCount={actionBusy?1:0} syncMessage="" dismissSyncMessage={()=>engine.setError('')} playedCards={[]} announcement={null}/>{state.prompt&&!viewerOut&&!state.ended&&<EngineCardChoices key={state.promptKey} choices={cardChoices} prompt={state.prompt} open={!!showCards} disabled={!live||actionBusy} resolve={resolveCard} label={label} choose={chooseCard} onClose={closeCards}/>}</EngineControlsContext.Provider>;
}
