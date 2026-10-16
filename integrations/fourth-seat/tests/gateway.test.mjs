import test from 'node:test';
import assert from 'node:assert/strict';
import { identity, signTicket, readTicket, authorizeAction, publicRoom } from '../gateway/policy.mjs';
const secret='test-secret-'.repeat(4), key='ab'.repeat(32);
test('browser identities are stable, private and fit XMage username limits',()=>{
  const user=identity(secret,key);assert.equal(user.length,13);assert.equal(identity(secret,key),user);
  assert.notEqual(identity(secret,'cd'.repeat(32)),user);assert.notEqual(identity(secret+'x',key),user);
});
test('missing or low-entropy browser keys are rejected',()=>{
  for(const input of ['', 'name','f'.repeat(63),null])assert.throws(()=>identity(secret,input));
});
test('websocket tickets expire and cannot be forged or used with another service secret',()=>{
  const user=identity(secret,key),ticket=signTicket(secret,user);
  assert.equal(readTicket(secret,ticket),user);assert.throws(()=>readTicket('other',ticket));
  assert.throws(()=>readTicket(secret,ticket+'x'));assert.throws(()=>readTicket(secret,signTicket(secret,user,Date.now()-1)));
});
test('gameplay is restricted to the seated game',()=>{
  assert.equal(authorizeAction({action:'sendPlayerBoolean',args:{gameId:'mine',value:false}},'mine').args.value,false);
  assert.throws(()=>authorizeAction({action:'sendPlayerUUID',args:{gameId:'other',value:'card'}},'mine'));
  assert.throws(()=>authorizeAction({action:'sendPlayerBoolean',args:{gameId:'mine'}},null));
});
test('public clients cannot select arbitrary servers, join arbitrary games, create SIM tables or cheat',()=>{
  for(const action of ['connect','joinGame','watchGame','createTable','joinTable','cheatSetup','updatePreferences','removeTable'])assert.throws(()=>authorizeAction({action,args:{gameId:'mine'}},'mine'));
  assert.throws(()=>authorizeAction({action:'sendPlayerAction',args:{gameId:'mine',action:'CHEAT'}},'mine'));
});
test('room views require membership and omit shared passwords and deck lists',()=>{
  const room={code:'ABCDE',host:'one',status:'lobby',humanSeats:2,password:'hidden',tableId:'private',players:[{user:'one',name:'One',kind:'human',commander:'Krenko',deckName:'Goblins',deck:{cards:['secret']}}]};
  const view=publicRoom(room,'one');assert.equal(view.isHost,true);assert.equal(view.players[0].name,'One');
  assert.equal(view.password,undefined);assert.equal(view.tableId,undefined);assert.equal(view.players[0].deck,undefined);
  assert.throws(()=>publicRoom(room,'outsider'));
});
