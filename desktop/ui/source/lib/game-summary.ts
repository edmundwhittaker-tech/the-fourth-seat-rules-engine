import type { GameStats, PlayerGameStats, RoomPlayer, RoomState } from "./game-types";

export type FinishedGameSummary = {
  code: string;
  finishedAt: number;
  expiresAt: number;
  winnerSeatId: string | null;
  turnNumber: number;
  players: { seatId: string; seat: number; displayName: string; commanderName: string }[];
  stats: GameStats;
};

/** An explicit allowlist: no decks, zones, card data, profile IDs, log or undo state. */
export function finishedGameSummary(room: RoomState, expiresAt: number): FinishedGameSummary {
  if (room.status !== "finished" || !room.finishedAt || !room.game?.stats) throw new Error("The game must finish before its result can be saved.");
  return {
    code: room.code, finishedAt: room.finishedAt, expiresAt,
    winnerSeatId: room.winnerSeatId ?? null, turnNumber: room.game.turnNumber,
    players: room.players.map(({seatId,seat,displayName,deck}) => ({seatId,seat,displayName,commanderName:deck.commander.name})),
    stats: {startedAt:room.game.stats.startedAt,finishedAt:room.finishedAt,activeTurnStartedAt:null,
      players:Object.fromEntries(room.players.map(({seatId})=>{
        const s=room.game!.stats.players[seatId];
        return [seatId,{turnsTaken:s?.turnsTaken??0,activeTurnMilliseconds:s?.activeTurnMilliseconds??0,cardsDrawn:s?.cardsDrawn??0,cardsPlayed:s?.cardsPlayed??0,creaturesPlayed:s?.creaturesPlayed??0,tokensCreated:s?.tokensCreated??0,commanderCasts:s?.commanderCasts??0}];
      }))},
  };
}

export type GameHighlight = { label:string; seatId:string; value:string };

export function formatSummaryDuration(milliseconds:number){
  const totalSeconds=Math.max(0,Math.floor(milliseconds/1000));
  const seconds=totalSeconds%60;
  const totalMinutes=Math.floor(totalSeconds/60);
  const minutes=totalMinutes%60;
  const hours=Math.floor(totalMinutes/60);
  if(hours)return `${hours}h ${minutes}m`;
  if(totalMinutes)return `${totalMinutes}m ${seconds}s`;
  return `${seconds}s`;
}

export function averageTurnMilliseconds(stats:PlayerGameStats){return stats.turnsTaken>0?Math.round(stats.activeTurnMilliseconds/stats.turnsTaken):0;}

export function gameHighlights(players:Pick<RoomPlayer,"seatId">[],stats:GameStats):GameHighlight[]{
  const entries=players.map((player)=>({player,stats:stats.players[player.seatId]})).filter((entry)=>entry.stats);
  const highest=(key:"cardsDrawn"|"creaturesPlayed"|"tokensCreated",label:string,suffix="")=>{
    const winner=[...entries].sort((a,b)=>b.stats[key]-a.stats[key])[0];
    return winner&&winner.stats[key]>0?{label,seatId:winner.player.seatId,value:`${winner.stats[key]}${suffix}`} : null;
  };
  const timed=entries.filter((entry)=>entry.stats.turnsTaken>0&&entry.stats.activeTurnMilliseconds>0).sort((a,b)=>averageTurnMilliseconds(a.stats)-averageTurnMilliseconds(b.stats));
  return [
    highest("cardsDrawn","MOST CARDS DRAWN"),
    highest("creaturesPlayed","MOST CREATURES PLAYED"),
    highest("tokensCreated","BIGGEST TOKEN MAKER"," tokens"),
    timed[0]?{label:"FASTEST AVERAGE TURN",seatId:timed[0].player.seatId,value:formatSummaryDuration(averageTurnMilliseconds(timed[0].stats))}:null,
  ].filter(Boolean) as GameHighlight[];
}
