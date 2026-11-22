import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import path from 'node:path';
import WebSocket from '../gateway/node_modules/ws/wrapper.mjs';
import { startGateway } from '../gateway/server.mjs';
import { aiDeckPool } from '../gateway/ai-decks.mjs';
import { validateCommanderDeck } from '../gateway/deck-validation.mjs';
import { emptyEngineState, reduceEngineFrame, records } from '../client/session.ts';

const out=path.resolve('build/nexus/fourth-seat');await mkdir(out,{recursive:true});
const secret=randomBytes(32).toString('hex'),origin='https://thefourthseat.net';
const gateway=await startGateway({secret,origin,port:8080,dataDir:path.join(out,'data')});
const checks=[],clients=[];
const check=(name,condition)=>{assert.ok(condition,name);checks.push({name,passed:true});console.log('PASS '+name);};
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function wait(fn,label){const deadline=Date.now()+90_000;while(Date.now()<deadline){const result=fn();if(result)return result;await delay(100);}throw new Error('Timed out: '+label);}
async function api(key,operation,args={}){
  const response=await fetch('http://127.0.0.1:8080/api',{method:'POST',headers:{Authorization:'Bearer '+secret,'Content-Type':'application/json'},body:JSON.stringify({browserKey:key,operation,...args})});
  const data=await response.json();if(!response.ok)throw new Error(data.error);return data;
}
async function attach(key){
  const session=await api(key,'session'),socket=new WebSocket('ws://127.0.0.1:8080/play',{origin});
  const client={key,socket,state:emptyEngineState(),pending:new Map(),counter:0};clients.push(client);
  const ready=new Promise((resolve,reject)=>{socket.on('error',reject);client.resolveReady=resolve;});
  socket.on('open',()=>socket.send(JSON.stringify({ticket:session.ticket})));
  socket.on('message',raw=>{
    const frame=JSON.parse(String(raw));if(frame.type==='ready')client.resolveReady();
    if(frame.type==='roomReset'){client.reset=frame;client.state=emptyEngineState();return;}
    if(frame.type==='result'&&client.pending.has(frame.requestId)){client.pending.get(frame.requestId)(frame);client.pending.delete(frame.requestId);}
    client.state=reduceEngineFrame(client.state,frame);
  });
  client.send=(action,args)=>new Promise((resolve,reject)=>{
    const requestId='test-'+ ++client.counter,timer=setTimeout(()=>{client.pending.delete(requestId);reject(new Error('No action acknowledgement'));},30_000);
    client.pending.set(requestId,frame=>{clearTimeout(timer);frame.ok?resolve(frame):reject(new Error(frame.error));});
    socket.send(JSON.stringify({action,args,requestId}));
  });
  await ready;return client;
}
// This validator uses the private loopback bridge in CI, never a public browser privilege.
async function verifyPool() {
  const socket = new WebSocket('ws://127.0.0.1:8787');
  const pending = new Map(); let counter = 0;
  socket.on('message', raw => {
    const frame = JSON.parse(String(raw)); const receive = pending.get(frame.requestId);
    if (receive) { pending.delete(frame.requestId); receive(frame); }
  });
  const call = (action, args = {}) => new Promise((resolve, reject) => {
    const requestId = 'pool-' + ++counter;
    const timer = setTimeout(() => { pending.delete(requestId); reject(new Error('Pool validation timed out')); }, 35_000);
    pending.set(requestId, frame => { clearTimeout(timer); frame.ok ? resolve(frame.data) : reject(new Error(frame.error)); });
    socket.send(JSON.stringify({ requestId, action, args }));
  });
  const results = [];
  try {
    await new Promise((resolve, reject) => { socket.once('open', resolve); socket.once('error', reject); });
    await call('connect', { host: '127.0.0.1', port: 17171, username: 'Pool' + randomBytes(4).toString('hex'), password: randomBytes(24).toString('hex') });
    for (const entry of aiDeckPool) {
      try {
        const resolved = await validateCommanderDeck({ call }, entry.deck);
        results.push({ id: entry.id, source: entry.source.url, commander: resolved.commanders[0].cardName,
          contentSha256: entry.checks.contentSha256, valid: true });
        console.log('VALID ' + entry.id + ': ' + resolved.commanders[0].cardName);
      } catch (error) {
        results.push({ id: entry.id, valid: false, error: error.message });
        console.error('INVALID ' + entry.id + ': ' + error.message);
      }
    }
  } finally {
    socket.terminate();
    await writeFile(path.join(out, 'ai-decks-result.json'), JSON.stringify({ integrationCommit: process.env.GITHUB_SHA, results }, null, 2));
  }
  check('all ten unchanged published decks pass the real pinned XMage Commander validator', results.length === 10 && results.every(r => r.valid));
}
let failure;
try{
  await verifyPool();
  const soloKey=randomBytes(32).toString('hex');
  const soloDeck={name:'Solo pool check',cards:[{cardName:'Mountain',amount:99}],commanders:[{cardName:'Krenko, Mob Boss',amount:1}]};
  const solo=(await api(soloKey,'create',{humanSeats:1,deck:soloDeck})).room;
  const soloAI=solo.players.filter(p=>p.kind==='ai');
  check('one human receives three distinct published AI decks, avoiding the host commander',soloAI.length===3&&new Set(soloAI.map(p=>p.commander)).size===3&&soloAI.every(p=>p.commander!=='Krenko, Mob Boss'&&aiDeckPool.some(d=>d.deck.commanders[0].cardName===p.commander)));
  await api(soloKey,'leave',{code:solo.code});
  const key1=randomBytes(32).toString('hex'),key2=randomBytes(32).toString('hex');
  const host=await attach(key1),friend=await attach(key2);
  const deck={name:'Cloud Commander verification',cards:[{cardName:'Mountain',amount:99}],commanders:[{cardName:'Krenko, Mob Boss',amount:1}]};
  const created=await api(key1,'create',{displayName:'HostPilot',humanSeats:2,deck});
  const code=created.room.code;
  await api(key2,'join',{displayName:'FriendPilot',code,deck});
  const initial=(await api(key1,'room',{code})).room;
  check('editable lobby exposes ten sourced bot decks and stable seat indexes',initial.lobbyEditable===true&&initial.aiDecks.length===10&&initial.players.every(p=>Number.isInteger(p.seat)));
  const bot=initial.players.find(p=>p.kind==='ai');
  await api(key1,'configure',{code,seat:bot.seat,kind:'human'});
  const selected=initial.aiDecks.find(d=>!initial.players.some(p=>p.commander===d.commander));
  await api(key1,'configure',{code,seat:bot.seat,kind:'ai',deckId:selected.id});
  await api(key2,'changeDeck',{code,deck});
  const room=(await api(key1,'room',{code})).room;
  check('host can remove and replace a bot with its selected published deck',room.players.find(p=>p.seat===bot.seat).deckId===selected.id);

  check('two human accounts and two native AI fill an engine room',room.players.filter(p=>p.kind==='human').length===2&&room.players.filter(p=>p.kind==='ai').length===2);
  check('mixed games use separate randomly assigned AI decks',new Set(room.players.filter(p=>p.kind==='ai').map(p=>p.commander)).size===2&&room.players.filter(p=>p.kind==='ai').every(p=>p.commander!=='Krenko, Mob Boss'));
  check('room responses omit private table passwords',room.password===undefined&&room.tableId===undefined);
  await api(key1,'start',{code});
  await wait(()=>host.state.game?.players?.length===4&&friend.state.game?.players?.length===4,'both private game views');
  check('the real server starts four Commander seats at 40 life',host.state.game.players.every(p=>p.life===40));
  check('server player types identify two native AI',host.state.game.players.filter(p=>!p.isHuman).length===2);
  check('human accounts control distinct server players',host.state.game.players.find(p=>p.controlled).playerId!==friend.state.game.players.find(p=>p.controlled).playerId);
  const handled=new Map(),mulligans=new Map(),bottomed=new Map();let landPlayer;
  // Instantiation alone does not exercise the lazily loaded strategy. Require
  // both bots to take a real turn before ending the game for lifecycle checks.
  const aiPlayed=()=>{
    const bots=host.state.game?.players?.filter(p=>!p.isHuman)??[];
    return bots.length===2&&bots.every(p=>records(p.battlefield).length>0);
  };
  const humansPlayed=()=>[host,friend].every(c=>c.state.game?.players?.some(p=>p.controlled&&records(p.battlefield).some(card=>card.name==='Mountain')));
  const deadline=Date.now()+180_000;
  while(Date.now()<deadline&&(!landPlayer||!aiPlayed()||!humansPlayed())){
    for(const client of [host,friend]){
      const state=client.state,prompt=state.prompt;if(!prompt||handled.get(client)===state.promptKey)continue;handled.set(client,state.promptKey);
      const gameId=state.gameId;
      if(prompt.isStartingPlayer){const target=host.state.game.players.find(p=>p.controlled).playerId;await client.send('sendPlayerUUID',{gameId,value:prompt.options.find(p=>p.value===target)?.value??prompt.options[0].value});}
      else if(prompt.isMulligan){
        const count=mulligans.get(client)??0;
        if(count===1)check('first mulligan redraws seven for '+(client===host?'host':'friend'),records(state.game.myHand).length===7);
        const again=count<(client===host?2:1);
        if(again)mulligans.set(client,count+1);
        else if(client===host)check('second mulligan keeps six after one card goes to the bottom',records(state.game.myHand).length===6&&bottomed.get(client)===1);
        await client.send('sendPlayerBoolean',{gameId,value:again});
      }
      else if(prompt.isMulliganLondon){
        bottomed.set(client,(bottomed.get(client)??0)+1);
        await client.send('sendPlayerUUID',{gameId,value:prompt.options[0].value});
      }
      else if(prompt.method==='GAME_SELECT'){
        const me=state.game.players.find(p=>p.controlled),land=records(state.game.myHand).find(c=>c.name==='Mountain'&&state.selectable.includes(c.id));
        if(land&&state.game.activePlayerId===me.playerId){await client.send('sendPlayerUUID',{gameId,value:land.id});landPlayer??=client;}
        else await client.send('sendPlayerBoolean',{gameId,value:false});
      }else if(prompt.method==='GAME_TARGET'&&/discard/i.test(prompt.message)&&prompt.options.length){
        // The fixture has only identical Mountains; an end-step discard is a
        // real server choice if another turn starts before both bots have acted.
        await client.send('sendPlayerUUID',{gameId,value:prompt.options[0].value});
      }else throw new Error('Unexpected opening prompt: '+prompt.method+' '+prompt.message);
    }
    await delay(100);
  }
  check('a human can submit a legal land play through the public gateway',!!landPlayer);
  check('both native AI make real battlefield plays before lifecycle cleanup',aiPlayed());
  check('both human accounts make authoritative land plays before lifecycle cleanup',humansPlayed());
  await wait(()=>[host,friend].every(c=>c.state.game.players.some(p=>records(p.battlefield).some(card=>card.name==='Mountain'))),'authoritative land update in both sessions');
  check('both sessions receive the accepted rules state',true);
  check('opponent hand names remain private',[host,friend].every(c=>records(c.state.game.opponentHands).every(h=>records(h).every(card=>!card.name))));
  landPlayer.socket.close();await delay(100);
  const restored=await attach(landPlayer.key);
  const other=landPlayer===host?friend:host;
  await wait(()=>restored.state.game?.players?.some(p=>p.controlled&&records(p.battlefield).some(c=>c.name==='Mountain')),'private reconnect cache');
  check('reconnect restores the same game without replaying an action',restored.state.gameId===landPlayer.state.gameId);
  await assert.rejects(api(key2,'end',{code}),/Only the host/);
  const previousGame=restored.state.gameId;
  const reset=(await api(key1,'end',{code})).room;
  await wait(()=>other.reset&&restored.reset,'reset broadcast to both humans');
  check('host ends the native game and returns every human to the same editable lobby',reset.status==='lobby'&&!reset.gameId&&reset.code===code&&reset.players.length===4&&other.reset.room.status==='lobby'&&restored.reset.room.status==='lobby');
  check('seats and decks survive ending the game',JSON.stringify(reset.players)===JSON.stringify(room.players));
  check('reconnect resumes the lobby after game end',(await api(key2,'session')).room.status==='lobby');
  await api(key1,'end',{code}); // idempotent duplicate
  await api(key1,'start',{code});
  await wait(()=>other.state.gameId&&other.state.gameId!==previousGame,'fresh rematch');
  check('the retained room starts a different native game',other.state.gameId!==previousGame);
  await api(key1,'end',{code});
  await api(key2,'leave',{code});await api(key1,'leave',{code});
  const unauthorized=await fetch('http://127.0.0.1:8080/api',{method:'POST',body:'{}'});
  check('the public gateway rejects unauthenticated API requests',unauthorized.status===401);
}catch(error){failure=error;throw error;}
finally{
  await writeFile(path.join(out,'result.json'),JSON.stringify({passed:!failure,checks,failure:failure?.message,publicServerUsed:false,simEnabled:false,serverTestMode:false,interfaceSourceIncluded:false,integrationCommit:process.env.GITHUB_SHA},null,2));
  for(const client of clients)client.socket.terminate();await gateway.close();
}
