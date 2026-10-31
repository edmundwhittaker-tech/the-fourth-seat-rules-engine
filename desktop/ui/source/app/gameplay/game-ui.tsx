"use client";

import { memo, useEffect, useState } from "react";
import type { CardData } from "../../lib/types";
import type { CardInstance, RoomPlayer } from "../../lib/game-types";
import { activeTurnTime, formatActiveTurnTime } from "../../lib/turn-timer";
import { formatSignedPowerToughnessModifier, isPowerToughnessCounter, signedPowerToughnessModifier } from "../../lib/card-counters";
import { deckBackImage } from "../../lib/deck-backs";
import CardArt from "../CardArt";
import type { SyncStatus, PlayedCardNotice, CardStatus } from "./room-types";

export function miniIcon(name: "copy" | "undo" | "token" | "log" | "close" | "search") {
  const paths = {
    copy: <><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/></>,
    undo: <><path d="M9 8 4 12l5 4"/><path d="M5 12h8a6 6 0 0 1 6 6"/></>,
    token: <><circle cx="12" cy="12" r="8"/><path d="M12 8v8M8 12h8"/></>,
    log: <><path d="M5 5h14M5 10h14M5 15h9M5 20h7"/></>,
    close: <path d="m6 6 12 12M18 6 6 18"/>,
    search: <><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></>,
  };
  return <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>;
}

export function SyncIndicator({status,pendingCount}:{status:SyncStatus;pendingCount:number}){
  const effective=pendingCount>0&&status==="live"?"syncing":status;
  const label=effective==="live"?"Live":effective==="syncing"?`Syncing${pendingCount?` · ${pendingCount}`:""}`:effective==="offline"?`Offline${pendingCount?` · ${pendingCount} waiting`:""}`:`Reconnecting${pendingCount?` · ${pendingCount} waiting`:""}`;
  const detail=effective==="live"?"Table is synchronized.":effective==="syncing"?`${pendingCount} action${pendingCount===1?"":"s"} being synchronized.`:effective==="offline"?`${pendingCount} action${pendingCount===1?" is":"s are"} safely waiting for your connection.`:`Restoring the live table${pendingCount?`; ${pendingCount} action${pendingCount===1?" is":"s are"} waiting`:""}.`;
  return <span className={`sync-indicator sync-${effective}`} role="status" aria-live="polite" aria-label={detail} title={detail}><i aria-hidden="true"/>{label}</span>;
}

export function DeckBack({deck}:{deck:RoomPlayer["deck"]}){
  return <div className="library-deck"><div className="card-back"><CardArt source={deckBackImage(deck.cardBack)} alt={`${deck.name} card back`}/></div></div>;
}

export const PlayerTurnTime=memo(function PlayerTurnTime({seatId,activePlayerId,enabled,startedAt,totalMs}:{seatId:string;activePlayerId:string;enabled:boolean;startedAt:number|null;totalMs:number}){
  const [,setTick]=useState(0);
  const running=enabled&&seatId===activePlayerId&&!!startedAt;
  useEffect(()=>{if(!running)return;const interval=window.setInterval(()=>setTick((value)=>value+1),1000);return()=>window.clearInterval(interval);},[running,startedAt]);
  if(!enabled)return null;
  const milliseconds=activeTurnTime(seatId,activePlayerId,enabled,startedAt,{[seatId]:totalMs});
  const formatted=formatActiveTurnTime(milliseconds);
  return <span className={`turn-time ${running?"running":""}`} aria-label={`${formatted} cumulative active turn time`}>{formatted}</span>;
});

export function cardPreviewStatuses(item:CardInstance):CardStatus[]{
  const signedLabel=formatSignedPowerToughnessModifier(signedPowerToughnessModifier(item.counters));
  const other=Object.entries(item.counters).filter(([name,value])=>!isPowerToughnessCounter(name)&&value>0) as CardStatus[];
  return signedLabel?[[signedLabel],...other]:other;
}

export function CardPreview({ card, statuses=[], onClose }: { card: CardData; statuses?:CardStatus[]; onClose?: () => void }) {
  return <aside className={`card-preview-panel ${statuses.length?"has-status":""}`} aria-label={`${card.name} enlarged card preview`}>{onClose && <button onClick={onClose} aria-label="Close preview">×</button>}<div className="card-preview-art"><CardArt source={card.image} alt={card.name}/></div>{statuses.length>0&&<div className="card-preview-status"><span>STATUS</span>{statuses.map(([name,amount])=><div key={name}><b>{name}</b>{amount!==undefined&&<strong>×{amount}</strong>}</div>)}</div>}</aside>;
}

export function PlayedCardPopups({notices}:{notices:PlayedCardNotice[]}){
  if(!notices.length)return null;
  return <aside className="played-card-popups" aria-live="polite" aria-label="Recently played cards">{notices.map((notice,index)=><article className={index===0?"primary":"compact"} key={notice.id}><span>{notice.playerName.toUpperCase()} {notice.verb}</span><div><CardArt source={notice.card.image} alt={notice.card.name}/></div><b>{notice.card.name}</b></article>)}</aside>;
}
