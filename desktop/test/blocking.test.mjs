import assert from 'node:assert/strict';
import test from 'node:test';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';

const {outputFiles:[bundle]}=await build({
  stdin:{resolveDir:fileURLToPath(new URL('..',import.meta.url)),contents:`
    import React from 'react';
    import {renderToString} from 'react-dom/server';
    import {emptyEngineState,reduceEngineFrame,promptAnswer} from './ui/source/lib/rules/session.ts';
    import {cardPromptChoices} from './ui/source/lib/rules/choices.ts';
    import EnginePrompt from './ui/source/app/rules/EnginePrompt.tsx';
    export {emptyEngineState,reduceEngineFrame,promptAnswer,cardPromptChoices};
    export function render(prompt,visualValues) {
      return renderToString(React.createElement(EnginePrompt,{prompt,visualValues,passingTurn:false,disabled:false,label:value=>value}));
    }
  `},bundle:true,platform:'node',format:'cjs',jsx:'automatic',write:false
});
const loaded={exports:{}};
new Function('module','exports','require',bundle.text)(loaded,loaded.exports,(await import('node:module')).createRequire(import.meta.url));
const {render,emptyEngineState,reduceEngineFrame,promptAnswer,cardPromptChoices}=loaded.exports;

const feeder={id:'feeder',name:'Carrion Feeder',manaValue:1};
const araumi={id:'araumi',name:'Araumi of the Dead Tide',manaValue:3};
const adeline={id:'adeline',name:'Adeline, Resplendent Cathar',manaValue:3};
const human={id:'human-token',name:'Human',manaValue:0};
const other={id:'other-token',name:'Human',manaValue:0};
const gameView={players:[
  {playerId:'me',controlled:true,battlefield:{feeder,araumi}},
  {playerId:'opponent',controlled:false,battlefield:{adeline,[human.id]:human,[other.id]:other}},
],priorityTime:60,step:'DECLARE_BLOCKERS'};
const frame=(method,data,seq=1)=>({type:'event',method,objectId:'test-game',seq,data:{gameView,...data}});
const selectBlockers=(seq=1)=>frame('GAME_SELECT',{message:'Select blockers',options:{possibleBlockers:['feeder','araumi']}},seq);
const chooseAttacker=(targets,source)=>frame('GAME_TARGET',{message:'Select attacker to block',targets,flag:false,options:{secondMessage:source}},2);

test('blocker selection offers the defender’s creatures and a distinct confirmation control',()=>{
  const state=reduceEngineFrame(emptyEngineState(),selectBlockers());
  assert.deepEqual(state.selectable,['feeder','araumi']);
  assert.match(render(state.prompt),/Click one of your highlighted creatures to block/);
  assert.match(render(state.prompt),/Confirm blockers/);
});

test('the Carrion Feeder zero-target response does not imply there is a target to choose',()=>{
  let state=reduceEngineFrame(emptyEngineState(),selectBlockers());
  state=reduceEngineFrame(state,chooseAttacker([],feeder.name));
  assert.deepEqual(state.selectable,[]);
  const html=render(state.prompt);
  assert.match(html,/No legal blocks/);
  assert.match(html,/Back to blockers/);
  assert.doesNotMatch(html,/Choose a target|Confirm blockers/);
  state=reduceEngineFrame(state,selectBlockers(3));
  assert.ok(state.selectable.includes(araumi.id),'can select Araumi after backing out');
});

test('only the specific attackers offered by XMage can be chosen and their IDs are the UUID answers',()=>{
  const state=reduceEngineFrame(emptyEngineState(),chooseAttacker([adeline.id,human.id],araumi.name));
  assert.deepEqual(state.selectable,[adeline.id,human.id]);
  const choices=cardPromptChoices(state);
  assert.deepEqual(choices.map(c=>[c.card.name,c.zone,c.value]),[[adeline.name,'Battlefield',adeline.id],[human.name,'Battlefield',human.id]]);
  assert.deepEqual(promptAnswer(state.prompt,choices[1].value),{action:'sendPlayerUUID',args:{gameId:'test-game',value:human.id}});
  assert.match(render(state.prompt,new Set(state.selectable)),/Click a highlighted attacker/);
});
