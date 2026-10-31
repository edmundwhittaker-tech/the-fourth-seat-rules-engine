import type {CardView} from './views';
import type {EngineState} from './session';
import {namedCards,records} from './session';
export type EngineCardChoice={value:string;card:CardView;zone:string};
export function cardPromptChoices(state:EngineState):EngineCardChoice[]{
  const prompt=state.prompt,game=state.game;
  if(!prompt||!game||state.ended||!['uuid','string'].includes(prompt.mode)||prompt.method==='GAME_CHOOSE_ABILITY')return [];
  const zones=new Map<string,string>();
  const add=(cards:CardView[],zone:string)=>{for(const card of cards)if(card.id)zones.set(card.id,zone);};
  add(records(game.myHand),'Hand');add(records(game.stack),'Stack');
  for(const player of game.players??[]){add(records(player.battlefield),'Battlefield');add(records(player.graveyard),'Graveyard');add(records(player.exile),'Exile');add(records(player.commandList as CardView[]),'Command zone');}
  const available=new Map(namedCards(game).filter(c=>c.id).map(c=>[c.id!,c]));
  for(const card of prompt.cards??[]){
    const previous=available.get(card.id);
    available.set(card.id,{...previous,...card,manaValue:previous?.manaValue??0});
  }
  return prompt.options.flatMap(option=>{
    if(prompt.mode==='string'&&(game.players??[]).some(p=>p.name===option.value||p.playerId===option.value))return [];
    const card=available.get(option.value)??(prompt.mode==='string'?[...available.values()].find(card=>card.name===option.value):undefined);if(!card||card.faceDown||card.hideInfo)return [];
    const zone=zones.get(option.value)??prompt.targetZone?.toLowerCase().replaceAll('_',' ')??(/library|search|scry/i.test(prompt.message)?'Library':'Revealed cards');
    return [{value:option.value,card,zone}];
  });
}
export function opensCardBrowser(choices:EngineCardChoice[],hasPlayers:boolean){
  return !hasPlayers&&choices.some(choice=>!['Hand','Battlefield','Stack','Command zone'].includes(choice.zone));
}
