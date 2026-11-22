import type { BattlefieldRow, CombatState, RoomPlayer } from "../../lib/game-types";
import type { ClientPlayerState } from "./room-types";

export function shortCombatName(name:string,max=18){return name.length<=max?name:`${name.slice(0,max-1).trim()}…`;}

export function fallbackCombat(activePlayerId:string,active=false):CombatState{return {active,attackingPlayerId:activePlayerId,attackers:[],confirmedAttackers:false,blockers:[]};}

export function handCount(state:ClientPlayerState){return state.handCount??state.hand.length;}

export function deckCount(state:ClientPlayerState){return state.libraryCount??state.library.length;}

export function battlefieldCount(state:ClientPlayerState,row:BattlefieldRow){return state.battlefield.filter((item)=>item.row===row).reduce((total,item)=>total+item.quantity,0);}

export function commanderLocation(state:ClientPlayerState){if(state.commandZone)return "COMMAND ZONE";if(state.battlefield.some((item)=>item.isCommander))return "BATTLEFIELD";if(state.graveyard.some((item)=>item.isCommander))return "GRAVEYARD";if(state.exile.some((item)=>item.isCommander))return "EXILE";return "OTHER ZONE";}

export function seatColour(player:Pick<RoomPlayer,"seat">|undefined){return `seat-colour-${Math.max(1,Math.min(4,(player?.seat??0)+1))}`;}
