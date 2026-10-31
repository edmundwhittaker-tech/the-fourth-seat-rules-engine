import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import WebSocket, { WebSocketServer } from '../gateway/node_modules/ws/wrapper.mjs';
import { startGateway } from '../gateway/server.mjs';

// Real gateway/HTTP/WebSocket transports; a deterministic native bridge double
// exercises authorization, failure recovery and late native callbacks.
test('host end resets all humans, preserves decks and rejects retired game callbacks',async()=>{
  const native=new WebSocketServer({port:0});await once(native,'listening');
  const dir=await mkdtemp(path.join(tmpdir(),'engine-lifecycle-'));
  const nativeClients=[],browsers=[],created=[],removed=[];
  let table=0,game=0,failRemoval=false;
  const event=(socket,method,id)=>socket.send(JSON.stringify({type:'event',method,objectId:id,data:{gameId:id}}));
  native.on('connection',socket=>{
    nativeClients.push(socket);
    socket.on('message',raw=>{
      const {action,args,requestId}=JSON.parse(String(raw));let data=null,ok=true;
      if(action==='resolvePrintings')data={ready:true,results:args.names.map(name=>({name,found:true,cardName:name,setCode:'TST',cardNumber:'1'}))};
      if(action==='validateDeckFormat')data={valid:true,supported:true};
      if(action==='createTable'){created.push(args);data={tableId:'table-'+ ++table};}
      if(action==='startMatch')for(const peer of nativeClients)event(peer,'START_GAME','game-'+ ++game);
      if(action==='removeTable'){removed.push(args.tableId);ok=!failRemoval;if(ok)for(const peer of nativeClients)event(peer,'GAME_OVER','game-'+game);}
      socket.send(JSON.stringify({requestId,type:'result',ok,data,error:ok?null:'Removal failed'}));
    });
  });
  const gateway=await startGateway({secret:'s'.repeat(64),port:0,nexusUrl:'ws://127.0.0.1:'+native.address().port,dataDir:dir});
  const api=async(key,operation,args={})=>{
    const response=await fetch('http://127.0.0.1:'+gateway.port+'/api',{method:'POST',headers:{Authorization:'Bearer '+'s'.repeat(64)},body:JSON.stringify({browserKey:key,operation,...args})});
    return {status:response.status,...await response.json()};
  };
  const wait=async fn=>{for(let i=0;i<100;i++){if(fn())return;await new Promise(r=>setTimeout(r,10));}assert.fail('Timed out');};
  try{
    const keys=['aa','bb','cc','dd'].map(k=>k.repeat(32));
    const deck={name:'Fixture',cards:[{cardName:'Mountain',amount:99}],commanders:[{cardName:'Krenko, Mob Boss',amount:1}]};
    const {room}=await api(keys[0],'create',{humanSeats:4,deck});
    for(const key of keys.slice(1))await api(key,'join',{code:room.code,deck});
    for(const key of keys){
      const session=await api(key,'session');const ws=new WebSocket('ws://127.0.0.1:'+gateway.port+'/play',{origin:'https://thefourthseat.net'});ws.frames=[];
      ws.on('message',raw=>ws.frames.push(JSON.parse(String(raw))));await once(ws,'open');ws.send(JSON.stringify({ticket:session.ticket}));browsers.push(ws);
      await wait(()=>ws.frames.some(f=>f.type==='ready'));
    }
    const before=(await api(keys[0],'room',{code:room.code})).room;
    await api(keys[0],'start',{code:room.code});
    assert.equal(created[0].freeMulligans,1);assert.equal(created[0].mulliganType,'LONDON');
    assert.equal((await api(keys[1],'end',{code:room.code})).status,403);
    failRemoval=true;assert.equal((await api(keys[0],'end',{code:room.code})).status,422);
    assert.equal((await api(keys[0],'room',{code:room.code})).room.status,'game');failRemoval=false;
    const reset=await api(keys[0],'end',{code:room.code});
    assert.equal(reset.room.status,'lobby');assert.equal(reset.room.gameId,null);assert.deepEqual(reset.room.players,before.players);
    await wait(()=>browsers.every(ws=>ws.frames.some(f=>f.type==='roomReset')));
    for(const peer of nativeClients){event(peer,'START_GAME','game-'+game);event(peer,'GAME_OVER','game-'+game);}
    await new Promise(r=>setTimeout(r,30));
    assert.equal((await api(keys[0],'room',{code:room.code})).room.status,'lobby');
    const count=removed.length;await api(keys[0],'end',{code:room.code});assert.equal(removed.length,count);
    await api(keys[0],'start',{code:room.code});assert.equal(created.length,2);
    await api(keys[0],'end',{code:room.code});
  }finally{
    browsers.forEach(ws=>ws.terminate());await gateway.close();nativeClients.forEach(ws=>ws.terminate());await new Promise(r=>native.close(r));await rm(dir,{recursive:true,force:true});
  }
});
