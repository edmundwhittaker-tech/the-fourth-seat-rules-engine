import assert from 'node:assert/strict';import {spawn} from 'node:child_process';import {mkdtemp,readFile,writeFile} from 'node:fs/promises';import path from 'node:path';import os from 'node:os';import {randomBytes} from 'node:crypto';import WebSocket from 'ws';
import {emptyEngineState,reduceEngineFrame,records} from '../../integrations/fourth-seat/client/session.ts';
const root=path.resolve(process.argv[2]??'build/The Fourth Seat'),base='http://127.0.0.1:17890',data=await mkdtemp(path.join(os.tmpdir(),'Fourth Seat test ')),checks=[];let processHandle,socket,cookie,state=emptyEngineState(),count=0,pending=new Map();
const env={...process.env,FOURTH_SEAT_DATA_DIR:data,FOURTH_SEAT_NO_BROWSER:'1',FOURTH_SEAT_TEST:'1'};
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function wait(fn,label,ms=180000){const deadline=Date.now()+ms;while(Date.now()<deadline){try{if(await fn())return;}catch{}await delay(100);}throw Error('Timeout: '+label);}
const check=(name,value)=>{assert.ok(value,name);checks.push(name);console.log('PASS '+name);};
function launch(){return spawn(process.platform==='win32'?path.join(root,'The Fourth Seat.exe'):process.execPath,process.platform==='win32'?[]:[path.join(root,'runtime/main.mjs')],{cwd:root,env,stdio:'inherit'});}
const browserKey=randomBytes(32).toString('hex');
async function post(url,body){const response=await fetch(base+url,{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json',Origin:base},body:JSON.stringify(body)});const result=await response.json();if(!response.ok)throw Error(result.error);return result;}
const api=(operation,args={})=>post('/api/engine',{operation,browserKey,displayName:'Desktop smoke',...args});
function send(action,args){return new Promise((resolve,reject)=>{const requestId='test-'+ ++count;const timer=setTimeout(()=>{pending.delete(requestId);reject(Error('Action timeout'));},30000);pending.set(requestId,frame=>{clearTimeout(timer);frame.ok?resolve():reject(Error(frame.error));});socket.send(JSON.stringify({action,args,requestId}));});}
try{
 processHandle=launch();await wait(async()=>{const r=await fetch(base+'/desktop/status');return(await r.json()).ready;},'native packaged startup',600000);
 cookie=(await fetch(base)).headers.get('set-cookie').split(';')[0];
 check('all ten bundled decks are available offline',(await(await fetch(base+'/api/desktop/decks',{headers:{Cookie:cookie}})).json()).decks.length===10);
 check('foreign browser cannot use local API',(await fetch(base+'/api/engine',{headers:{Origin:'https://evil.invalid',Cookie:cookie}})).status===403);
 await new Promise((resolve,reject)=>{const ws=new WebSocket('ws://127.0.0.1:18787',{origin:base});ws.once('open',()=>{ws.terminate();reject(Error('Raw bridge accepted browser'));});ws.once('unexpected-response',(_req,r)=>{check('raw bridge refuses browser origin',r.statusCode===403);r.resume();ws.terminate();resolve();});ws.on('error',()=>{});});
 const card=(name,quantity)=>({id:name,name,quantity,typeLine:'Creature',oracleText:'',manaCost:'',image:'',colors:[],colorIdentity:[]});
 const deck=(await post('/api/desktop/decks',{name:'Desktop smoke fixture',commander:'Krenko, Mob Boss',cards:[card('Krenko, Mob Boss',1),card('Mountain',99)]})).deck;
 const session=await api('session');socket=new WebSocket(session.websocketUrl,{origin:base});let connected=false;
 socket.on('open',()=>socket.send(JSON.stringify({ticket:session.ticket})));socket.on('message',raw=>{const frame=JSON.parse(String(raw));if(frame.type==='ready')connected=true;if(frame.type==='roomReset'){state=emptyEngineState();return;}if(frame.type==='result'&&pending.has(frame.requestId)){pending.get(frame.requestId)(frame);pending.delete(frame.requestId);}state=reduceEngineFrame(state,frame);});await wait(()=>connected,'gateway connection');
 const room=(await api('create',{deckId:deck.id,humanSeats:1})).room;check('one human and three distinct AI seats',room.players.filter(p=>p.kind==='ai').length===3&&new Set(room.players.map(p=>p.commander)).size===4);
 await api('start',{code:room.code});await wait(()=>state.game?.players?.length===4,'four native players');check('all four seats start at 40 life',state.game.players.every(p=>p.life===40));
 let last,mulligan=0,played=false;const developed=()=>state.game?.players?.filter(p=>!p.isHuman).every(p=>records(p.battlefield).length>0);const deadline=Date.now()+240000;
 while(Date.now()<deadline&&(!developed()||!played)){
  if(state.prompt&&state.promptKey!==last){last=state.promptKey;const prompt=state.prompt,gameId=state.gameId;
   if(prompt.isStartingPlayer)await send('sendPlayerUUID',{gameId,value:prompt.options[0].value});
   else if(prompt.isMulligan){if(mulligan===1)check('free mulligan redraws seven',records(state.game.myHand).length===7);await send('sendPlayerBoolean',{gameId,value:mulligan++===0});}
   else if(prompt.isMulliganLondon)await send('sendPlayerUUID',{gameId,value:prompt.options[0].value});
   else if(prompt.method==='GAME_SELECT'){const land=records(state.game.myHand).find(c=>c.name==='Mountain'&&state.selectable.includes(c.id));if(land){await send('sendPlayerUUID',{gameId,value:land.id});played=true;}else await send('sendPlayerBoolean',{gameId,value:false});}
   else if(prompt.method==='GAME_TARGET'&&/discard/i.test(prompt.message))await send('sendPlayerUUID',{gameId,value:prompt.options[0].value});
   else throw Error('Unexpected prompt '+prompt.method+' '+prompt.message);
  }await delay(80);
 }
 check('all three native AI make battlefield plays',developed());check('human can play an authoritative land',played);const prior=state.gameId;
 check('end game returns to same lobby',(await api('end',{code:room.code})).room.status==='lobby');await api('start',{code:room.code});await wait(()=>state.gameId&&state.gameId!==prior,'fresh rematch');check('rematch starts a different native game',state.gameId!==prior);await api('end',{code:room.code});await api('leave',{code:room.code});socket.terminate();socket=null;
 await post('/api/desktop/shutdown',{});await wait(()=>processHandle.exitCode!==null,'clean launcher exit',30000);check('shutdown exits successfully',processHandle.exitCode===0);
 processHandle=launch();await wait(async()=>{const r=await fetch(base+'/desktop/status');return(await r.json()).ready;},'second launch',600000);cookie=(await fetch(base)).headers.get('set-cookie').split(';')[0];check('imported deck survives restart',(await(await fetch(base+'/api/desktop/decks',{headers:{Cookie:cookie}})).json()).decks.some(d=>d.id===deck.id));
 if(process.platform==='win32'){processHandle.kill();await wait(async()=>{try{await fetch(base+'/desktop/status');return false;}catch{return true;}},'Job Object cleanup',30000);for(const port of [17771,18787,18788,18789]){const net=await import('node:net');await wait(()=>new Promise(r=>{const s=net.connect(port,'127.0.0.1');s.on('connect',()=>{s.destroy();r(false);});s.on('error',()=>r(true));}),'child port '+port,30000);}check('closing launcher kills all local engine services',true);}
 else{await post('/api/desktop/shutdown',{});await wait(()=>processHandle.exitCode!==null,'second shutdown',30000);}
 await writeFile(path.join(root,'smoke-result.json'),JSON.stringify({passed:true,platform:process.platform,checks},null,2));
}finally{socket?.terminate();if(processHandle?.exitCode===null)processHandle.kill();console.log('Smoke logs: '+path.join(data,'logs'));}
