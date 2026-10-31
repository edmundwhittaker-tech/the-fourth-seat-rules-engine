import type { ClientRoom } from '../../app/gameplay/room-types';
import type { DeckData } from '../types';
import type { CardInstance, ClientGameState, CombatState } from '../game-types';
import type { CardView, PermanentView } from './views';
import type { EngineRoom, EngineState } from './session';
import { records } from './session';
import { createEngineCardResolver, type EngineArt } from './art';
import { enginePlayerName, engineText } from './player-labels';

export const isEngineAbility=(card:CardView)=>card.isAbility===true||!!card.sourceCard||/ABILITY/i.test(card.mageObjectType??'')||/TRIGGER|ACTIVAT/i.test(card.abilityType??'');
const plain=(value:string)=>value.replace(/<[^>]*>/g,'');
export function projectEngineRoom(state:EngineState,room:EngineRoom,deck:DeckData|undefined,artwork:EngineArt[]=[]):ClientRoom|null {
  const game=state.game,me=game?.players?.find(p=>p.controlled);
  if(!game||!me)return null;
  const cardData=createEngineCardResolver(deck,artwork);
  const players=(game.players??[]).map((player,seat)=>{
    const metadata=room.players.find(p=>p.id===player.name),commander=records(player.commandList as CardView[]).find(c=>c.name===metadata?.commander);
    const template=deck&&deck.commander.name===metadata?.commander?deck.commander:undefined;
    return {seatId:player.playerId,profileId:player.name,displayName:metadata?enginePlayerName(metadata):player.name,seat,ready:true,lastSeen:0,connected:!player.hasLeft,
      deck:{id:player.playerId,name:metadata?.deckName??'Commander',cardCount:100,cardBack:deck?.cardBack,commander:commander?cardData(commander):template??cardData({name:metadata?.commander??'Commander',manaValue:0})}};
  });
  function instance(card:CardView,owner:string):CardInstance {
    const permanent=card as PermanentView;
    const data=cardData(card),commander=players.find(p=>p.deck.commander.name===card.name)?.seatId;
    return {instanceId:card.id??card.parentId??card.name,card:data,ownerSeatId:owner,controllerSeatId:card.controllerId??owner,
      tapped:permanent.tapped===true,quantity:1,counters:Object.fromEntries((card.counters??[]).map(c=>[c.name,c.count])),copy:permanent.copy===true,token:card.isToken===true,
      isCommander:!!commander,row:data.typeLine.includes('LAND')?'lands':data.typeLine.includes('CREATURE')?'creatures':'other',attachedTo:permanent.attachedTo??undefined};
  }
  const states=Object.fromEntries((game.players??[]).map(player=>{
    const commanderName=players.find(p=>p.seatId===player.playerId)!.deck.commander.name;
    const battlefield=records(player.battlefield),graveyard=records(player.graveyard),exile=records(player.exile),command=records(player.commandList as CardView[]);
    const ownCards=[...battlefield,...graveyard,...exile,...command,...(player.controlled?records(game.myHand):[])];
    const commander=ownCards.find(c=>c.name===commanderName);
    const casts=Number(plain(commander?.rules?.join(' ')??'').match(/Commander\s+(\d+) times? played from the command zone/i)?.[1]??0);
    const commanderDamage:Record<string,number>={};
    for(const other of game.players??[]){
      for(const card of [...records(other.battlefield),...records(other.graveyard),...records(other.exile),...records(other.commandList as CardView[])]){
        for(const rule of card.rules??[]){const match=plain(rule).match(/Commander did (\d+) combat damage to player (.+)\./i);if(match?.[2]===player.name)commanderDamage[other.playerId]=Number(match[1]);}
      }
    }
    return [player.playerId,{life:player.life,eliminated:player.hasLeft,eliminationReason:player.hasLeft?'conceded' as const:undefined,hand:player.controlled?records(game.myHand).map(c=>instance(c,player.playerId)):[],handCount:player.handCount,library:[],libraryCount:player.libraryCount,
      battlefield:battlefield.map(c=>instance(c,player.playerId)),graveyard:graveyard.map(c=>instance(c,player.playerId)),exile:exile.map(c=>instance(c,player.playerId)),commandZone:command.find(c=>c.name===commanderName)?instance(command.find(c=>c.name===commanderName)!,player.playerId):null,
      commanderReference:players.find(p=>p.seatId===player.playerId)!.deck.commander,commanderCastCount:casts,commanderWasCast:casts>0,commanderDamage,playerCounters:Object.fromEntries((player.counters??[]).map(c=>[c.name,c.count])),openingHandKept:true}];
  }));
  const combat:CombatState={active:/COMBAT|ATTACK|BLOCK|DAMAGE/.test(game.phase??'')||/ATTACK|BLOCK|DAMAGE/.test(game.step??''),attackingPlayerId:game.activePlayerId??'',attackers:[],blockers:[],confirmedAttackers:true};
  for(const raw of game.combat??[]){
    const group=raw as unknown as {attackers?:Record<string,CardView>;blockers?:Record<string,CardView>;defenderId?:string};
    for(const attacker of records(group.attackers)){combat.attackers.push({battlefieldObjectId:attacker.id??'',defenderType:players.some(p=>p.seatId===group.defenderId)?'player':'permanent',defendingPlayerId:group.defenderId??'',defendingPermanentId:players.some(p=>p.seatId===group.defenderId)?undefined:group.defenderId,quantity:1,isCommander:attacker.cardIcons?.some(i=>i.cardIconType==='COMMANDER')??false});
      for(const blocker of records(group.blockers))combat.blockers.push({blockerObjectId:blocker.id??'',attackerObjectId:attacker.id??''});}
  }
  const projected:ClientGameState={startingPlayerId:players[0]?.seatId??'',turnOrder:players.map(p=>p.seatId),activePlayerId:game.activePlayerId??'',turnNumber:game.turn,phase:combat.active?'Combat':game.step??game.phase??'',combat,playerStates:states,
    stack:records(game.stack).map(c=>{
      const ability=isEngineAbility(c);
      return {id:c.id??c.name,kind:ability?'ability' as const:'card' as const,controllerSeatId:c.controllerId??'',card:ability?undefined:instance(c,c.controllerId??''),source:ability?cardData(c.sourceCard??c):undefined,note:ability?c.rules?.map(plain).join('\n'):undefined,abilityType:ability?c.abilityType:undefined};
    }),
    reveal:null,ping:null,log:state.log.map((text,i)=>({id:String(i),text:engineText(text,room),at:0})),history:[],turnTimerEnabled:false,activeTurnStartedAt:null,activeTurnTimeMs:{},stats:{startedAt:0,activeTurnStartedAt:null,players:{}}};
  return {code:room.code,mode:'multiplayer',hostProfileId:room.players[0]?.id??'',hostSeatId:players[0]?.seatId??'',viewerSeatId:me.playerId,isHost:room.isHost,status:'game',players,game:projected,recentActionIds:[],createdAt:0,lastActivity:0};
}
