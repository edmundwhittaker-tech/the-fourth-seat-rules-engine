import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { WebSocketServer } from '../gateway/node_modules/ws/wrapper.mjs';
import { startGateway } from '../gateway/server.mjs';
import { aiDeckPool } from '../gateway/ai-decks.mjs';

test('editable lobby enforces ownership, distinct decks and materializes exact selections only at start', async () => {
  const upstream = new WebSocketServer({ port: 0 });
  await new Promise(resolve => upstream.once('listening', resolve));
  const calls = [];
  upstream.on('connection', socket => socket.on('message', raw => {
    const m = JSON.parse(String(raw)); calls.push(m);
    let data = true;
    if (m.action === 'resolvePrintings') data = { ready:true, results:m.args.names.map(name => ({name,found:true,cardName:name,setCode:'TEST',cardNumber:'1'})) };
    if (m.action === 'validateDeckFormat') data = { supported:true, valid:true };
    if (m.action === 'createTable') data = { tableId:'table-1' };
    socket.send(JSON.stringify({requestId:m.requestId,ok:true,data}));
  }));
  const dir = await mkdtemp(join(tmpdir(),'fourth-seat-lobby-'));
  const secret = 'test-secret-'.repeat(4);
  const gateway = await startGateway({secret,port:0,dataDir:dir,nexusUrl:`ws://127.0.0.1:${upstream.address().port}`});
  const request = async (operation, args={}, browserKey='ab'.repeat(32)) => {
    const response = await fetch(`http://127.0.0.1:${gateway.port}/api`, {method:'POST',headers:{Authorization:'Bearer '+secret},body:JSON.stringify({operation,browserKey,...args})});
    return {status:response.status,...await response.json()};
  };
  try {
    let result = await request('create',{humanSeats:2,deck:aiDeckPool[0].deck});
    assert.equal(result.status,200); let room=result.room; const code=room.code;
    assert.equal(room.lobbyEditable,true); assert.equal(room.aiDecks.length,10);
    assert.equal(calls.some(c=>c.action==='createTable'),false);
    assert.deepEqual(room.players.map(p=>p.seat),[0,2,3]);
    assert.equal(new Set(room.players.map(p=>p.commander)).size,3);
    assert.equal(room.players[0].deck,undefined);
    assert.equal((await request('configure',{code,seat:0,kind:'ai'})).status,409);
    const guest='cd'.repeat(32);
    result=await request('join',{code,deck:aiDeckPool[1].deck},guest); assert.equal(result.status,200);
    assert.equal((await request('configure',{code,seat:2,kind:'human'},guest)).status,403);
    assert.equal((await request('configure',{code,seat:1,kind:'human'})).status,409);
    assert.equal((await request('configure',{code,seat:2,kind:'ai',deckId:aiDeckPool[0].id})).status,409);
    result=await request('configure',{code,seat:2,kind:'human'}); assert.equal(result.room.humanSeats,3);
    assert.equal((await request('start',{code})).status,409);
    result=await request('configure',{code,seat:2,kind:'ai',deckId:aiDeckPool.find(d=>d.id!==result.room.players.find(p=>p.kind==='ai').deckId && ![aiDeckPool[0].id,aiDeckPool[1].id].includes(d.id)).id});
    assert.equal(result.status,200); room=result.room;
    const oldId=room.players.find(p=>p.seat===2).deckId;
    result=await request('configure',{code,seat:2,kind:'ai',deckId:'random'}); assert.notEqual(result.room.players.find(p=>p.seat===2).deckId,oldId);
    const replacement=aiDeckPool.find(d=>d.id===result.room.players.find(p=>p.seat===2).deckId);
    result=await request('changeDeck',{code,deck:replacement.deck},guest); assert.equal(result.status,200);
    room=result.room; assert.equal(new Set(room.players.map(p=>p.commander)).size,4);
    result=await request('start',{code}); assert.equal(result.status,200);
    assert.equal(calls.filter(c=>c.action==='createTable').length,1);
    const joined=calls.filter(c=>c.action==='joinTable'); assert.equal(joined.length,4);
    for(let seat=0;seat<4;seat++) assert.equal(joined[seat].args.deck.commanders[0].cardName,room.players.find(p=>p.seat===seat).commander);
    assert.equal((await request('configure',{code,seat:2,kind:'human'})).status,409);
    assert.equal((await request('changeDeck',{code,deck:aiDeckPool[0].deck})).status,409);
    assert.equal((await request('start',{code})).status,409);
  } finally { await gateway.close(); await new Promise(resolve=>upstream.close(resolve)); await rm(dir,{recursive:true,force:true}); }
});
