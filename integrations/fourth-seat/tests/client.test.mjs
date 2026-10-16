import test from 'node:test';
import assert from 'node:assert/strict';
import { deckForEngine, emptyEngineState, reduceEngineFrame, promptAnswer } from '../client/session.ts';
const game={players:[{playerId:'one',name:'One',controlled:true},{playerId:'two',name:'Two',controlled:false}],myHand:{land:{id:'land',name:'Mountain'}},canPlayObjects:{objects:{land:{}}},stack:{}};
const frame=(method,data,seq=1)=>({type:'event',method,objectId:'game',data,seq});
test('the designated commander is removed exactly once from the main deck',()=>{
  assert.deepEqual(deckForEngine({name:'Deck',commander:{name:'Krenko'},cards:[{name:'Krenko',quantity:1},{name:'Mountain',quantity:99}]}),{name:'Deck',cards:[{cardName:'Mountain',amount:99}],commanders:[{cardName:'Krenko',amount:1}]});
});
test('ordinary updates preserve an unanswered prompt and private hand',()=>{
  const before=reduceEngineFrame(emptyEngineState(),frame('GAME_ASK',{question:'Mulligan?',gameView:game}));
  const after=reduceEngineFrame(before,frame('GAME_UPDATE',{gameView:game},2));
  assert.equal(after.prompt.mode,'boolean');assert.equal(after.game.myHand.land.name,'Mountain');
});
test('priority exposes the server card hints and a pass button',()=>{
  const s=reduceEngineFrame(emptyEngineState(),frame('GAME_SELECT',{gameView:game,message:'Play spells'}));
  assert.equal(s.prompt.title,'Your priority');assert.deepEqual(s.selectable,['land']);
});
test('combat highlights the server attackers rather than the whole battlefield',()=>{
  const s=reduceEngineFrame(emptyEngineState(),frame('GAME_SELECT',{gameView:game,options:{possibleAttackers:['goblin']}}));
  assert.equal(s.prompt.title,'Declare attackers');assert.deepEqual(s.selectable,['goblin']);
});
test('target prompts use current player labels and server target IDs',()=>{
  let s=reduceEngineFrame(emptyEngineState(),frame('GAME_INIT',{gameView:game}));
  s=reduceEngineFrame(s,frame('GAME_TARGET',{message:'Choose a player',targets:['two']},2));
  assert.equal(s.prompt.options[0].label,'Two');assert.deepEqual(s.selectable,['two']);
});
test('mulligan and Commander return use the exact engine button meanings',()=>{
  const p=reduceEngineFrame(emptyEngineState(),frame('GAME_ASK',{question:'Move to command zone?',options:{'UI.left.btn.text':'Move to command zone','UI.right.btn.text':'Leave in graveyard'}})).prompt;
  assert.deepEqual(promptAnswer(p,p.options[0].value),{action:'sendPlayerBoolean',args:{gameId:'game',value:true}});
});
test('mana payment retains server-driven mana-source choices',()=>{
  const s=reduceEngineFrame(emptyEngineState(),frame('GAME_PLAY_MANA',{message:'Pay {R}',gameView:game}));
  assert.equal(s.prompt.mode,'mana');assert.deepEqual(s.selectable,['land']);
});
test('numbers, strings, piles, and constrained multi amounts map to typed prompts',()=>{
  for(const [method,data,mode] of [['GAME_GET_AMOUNT',{min:2,max:5},'integer'],['GAME_CHOOSE_CHOICE',{choice:{keyChoices:{one:'Option one'}}},'string'],['GAME_CHOOSE_PILE',{cardsView1:{a:{name:'A'}},cardsView2:{}},'boolean'],['GAME_GET_MULTI_AMOUNT',{min:3,max:3,messages:[{id:'a',message:'A',min:0,max:3}]},'multiString']]){
    assert.equal(reduceEngineFrame(emptyEngineState(),frame(method,data)).prompt.mode,mode);
  }
});
test('replayed and unrelated-game frames do not overwrite newer state',()=>{
  const s=reduceEngineFrame(emptyEngineState(),frame('GAME_INIT',{gameView:game},5));
  assert.equal(reduceEngineFrame(s,frame('GAME_ASK',{question:'Stale'},4)),s);
  assert.equal(reduceEngineFrame(s,{...frame('GAME_UPDATE',{gameView:{players:[]}},6),objectId:'other'}),s);
});
test('acknowledgements never optimistically move cards or change life',()=>{
  const s=reduceEngineFrame(emptyEngineState(),frame('GAME_INIT',{gameView:game}));
  assert.equal(reduceEngineFrame(s,{type:'result',ok:true,data:true}),s);
});
