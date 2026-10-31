"use client";

import { memo, useContext, useEffect, useRef, useState } from "react";
import type React from "react";
import type { CardData } from "../../lib/types";
import type { BattlefieldRow, CardInstance } from "../../lib/game-types";
import { calculateHandLayout } from "../../lib/hand-layout";
import { calculateBattlefieldRowLayout } from "../../lib/battlefield-layout";
import { formatSignedPowerToughnessModifier, isPowerToughnessCounter, signedPowerToughnessModifier, SIGNED_PT_COUNTER } from "../../lib/card-counters";
import { activeCardData } from "../../lib/card-faces";
import CardArt from "../CardArt";
import { EngineControlsContext } from "../rules/engine-controls";
import type { CardStatus, CombatCardView, DragData } from "./room-types";
import { cardPreviewStatuses } from "./game-ui";

type GameCardProps = {
    item: CardInstance;
    own: boolean;
    pinged: boolean;
    selected?: boolean;
    compact?: boolean;
    overview?: boolean;
    combat?: CombatCardView;
    slotClassName?: string;
    slotStyle?: React.CSSProperties;
    attachmentCount?: number;
    onAttachDrop?: (event: React.DragEvent, item: CardInstance, data: DragData) => void;
    onAction: (type: string, payload: Record<string, unknown>) => void;
    onSelect?: (event: {
        shiftKey: boolean;
    }, item: CardInstance) => void;
    onHover: (card: CardData | null, statuses?: CardStatus[]) => void;
    onMenu?: (event: React.MouseEvent, item: CardInstance, own: boolean) => void;
};

// React's default comparison includes every prop, including event handlers and
// combat interaction changes. A serialized comparison silently discards functions.
export const GameCard = memo(function GameCard({ item, own:givenOwn, pinged, selected=false, compact = false, overview=false, combat, slotClassName="",slotStyle,attachmentCount=0,onAttachDrop,onAction, onSelect, onHover, onMenu }: GameCardProps) {
  const engine=useContext(EngineControlsContext);
  const own=givenOwn&&!engine;
  const [dropTarget,setDropTarget]=useState(false);
  const activeCard=activeCardData(item);
  const signedModifier=signedPowerToughnessModifier(item.counters);
  const signedLabel=formatSignedPowerToughnessModifier(signedModifier);
  const otherCounters=Object.entries(item.counters).filter(([name,value])=>!isPowerToughnessCounter(name)&&value>0) as CardStatus[];
  const previewStatuses=cardPreviewStatuses(item);
  const stopControlEvent=(event:React.SyntheticEvent)=>event.stopPropagation();
  const adjustCounter = (delta:number) => onAction("CHANGE_COUNTER",{instanceId:item.instanceId,name:SIGNED_PT_COUNTER,delta});
  return <div className={`battlefield-card-slot ${overview?"overview-slot":""} ${item.tapped?"is-tapped":""} ${combat?.attackLabel?"is-attacking":""} ${combat?.colourClass??""} ${slotClassName}`} style={slotStyle}><div className={`game-card ${item.tapped ? "tapped" : ""} ${compact ? "compact" : ""} ${overview ? "overview-card" : ""} ${pinged ? "pinged" : ""} ${selected ? "selected" : ""} ${combat?.attackLabel ? "attacking" : ""} ${dropTarget?"attachment-drop-target":""} ${engine?.selectable.has(item.instanceId)?"engine-legal":""}`} data-instance-id={item.instanceId} data-own={own?"true":"false"} data-quantity={item.quantity} data-signed-modifier={signedModifier} data-additional-statuses={otherCounters.length} data-attached-to={item.attachedTo??""} data-face-index={item.faceIndex??0} role="button" tabIndex={0} aria-label={`${activeCard.name}${item.tapped ? ", tapped" : ""}${item.attachedTo?", attached":""}${attachmentCount?`, ${attachmentCount} attachments`:""}${signedLabel?`, ${signedLabel}`:""}${selected?", selected":""}${combat?.attackLabel ? `, attacking ${combat.attackLabel}` : ""}`} aria-pressed={selected} draggable={own} onDragStart={(event) => event.dataTransfer.setData("application/json", JSON.stringify({ instanceId:item.instanceId, from:"battlefield" }))} onDragOver={own&&onAttachDrop?(event)=>{const raw=event.dataTransfer.types.includes("application/json");if(raw){event.preventDefault();event.stopPropagation();setDropTarget(true);}}:undefined} onDragLeave={()=>setDropTarget(false)} onDrop={own&&onAttachDrop?(event)=>{event.preventDefault();event.stopPropagation();setDropTarget(false);try{const data=JSON.parse(event.dataTransfer.getData("application/json")) as DragData;if(data.from==="battlefield"&&data.instanceId!==item.instanceId)onAttachDrop(event,item,data);}catch{}}:undefined} onMouseEnter={() => onHover(activeCard,previewStatuses)} onMouseLeave={() => onHover(null)} onFocus={(event)=>{event.currentTarget.scrollIntoView({block:"nearest",inline:"nearest"});onHover(activeCard,previewStatuses);}} onBlur={()=>onHover(null)} onKeyDown={(event)=>{if((event.key==="Enter"||event.key===" ")&&(own||engine)){event.preventDefault();if(engine)engine.select(item.instanceId);else onSelect?.({shiftKey:event.shiftKey},item);}}} onContextMenu={engine?(event)=>{event.preventDefault();onHover(activeCard,previewStatuses);}:onMenu?(event) => onMenu(event,item,own):undefined} onClick={(event) => { if(engine){engine.select(item.instanceId);return;} if (event.altKey&&own) onAction("PING", { instanceId:item.instanceId }); else onSelect?.(event,item); }}>
    <CardArt source={activeCard.image} alt={activeCard.name} publicObjectId={item.instanceId} fallback={engine&&item.token?<span className="engine-token-fallback"><small>ENGINE TOKEN</small><b>{activeCard.name}</b><em>{activeCard.typeLine}</em><span>{activeCard.oracleText}</span>{activeCard.power!==undefined&&activeCard.toughness!==undefined&&<strong>{activeCard.power}/{activeCard.toughness}</strong>}</span>:undefined}/>
    {item.copy && <span className="copy-stamp">COPY</span>}
    {(own || item.quantity > 1) && <div className={`battlefield-status quantity-status ${item.quantity <= 1 ? "inactive-status" : ""}`} onClick={stopControlEvent} onDoubleClick={stopControlEvent}>{own&&<button aria-label={`Decrease ${activeCard.name} quantity`} onClick={(e) => {e.stopPropagation();onAction("CHANGE_QUANTITY",{instanceId:item.instanceId,delta:-1});}}>−</button>}<b>×{item.quantity}</b>{own&&<button aria-label={`Increase ${activeCard.name} quantity`} onClick={(e) => {e.stopPropagation();onAction("CHANGE_QUANTITY",{instanceId:item.instanceId,delta:1});}}>+</button>}</div>}
    {(own||signedModifier!==0)&&<div className={`battlefield-status signed-modifier-status ${signedModifier<0?"negative":"positive"} ${signedModifier===0?"inactive-status":""}`} onClick={stopControlEvent} onDoubleClick={stopControlEvent}>{own&&<button aria-label={`Decrease power and toughness modifier on ${activeCard.name}`} onClick={(e)=>{e.stopPropagation();adjustCounter(-1);}}>−</button>}<b>{signedLabel||"0/0"}</b>{own&&<button aria-label={`Increase power and toughness modifier on ${activeCard.name}`} onClick={(e)=>{e.stopPropagation();adjustCounter(1);}}>+</button>}</div>}
    {otherCounters.length>0&&<span className="additional-status-indicator" aria-label={`${otherCounters.length} additional status ${otherCounters.length===1?"type":"types"}`}>◆<b>{otherCounters.length}</b></span>}
  </div>{combat?.attackLabel&&(combat.onBadgeClick?<button className="attack-target-label" onClick={(event)=>{event.stopPropagation();combat.onBadgeClick?.();}} onDoubleClick={stopControlEvent} aria-label={`Change attack target from ${combat.attackLabel}`}><span aria-hidden="true">⚔</span>{combat.attackLabel}</button>:<span className="attack-target-label"><span aria-hidden="true">⚔</span>{combat.attackLabel}</span>)}</div>;
});

export function ResponsiveBattlefieldCards({ children, attachments, row, overview=false }: { children:React.ReactNode;attachments:number[];row:BattlefieldRow;overview?:boolean }) {
  const containerRef=useRef<HTMLDivElement>(null);
  const attachmentKey=attachments.join(",");
  useEffect(()=>{
    const attachmentCounts=attachmentKey ? attachmentKey.split(",").map(Number) : [];
    const container=containerRef.current;if(!container)return;
    let frame=0;
    const update=()=>{
      const layout=calculateBattlefieldRowLayout(container.clientWidth,container.clientHeight,attachmentCounts.length,row,{
        attachments:attachmentCounts,overviewWidth:overview?Math.min(51.6,Math.max(38.4,window.innerWidth*.027)):undefined,
        scrollReserve:12,attackLabelHeight:row==="creatures"?26:0,
      });
      for(const [name,value] of Object.entries({
        "--battlefield-card-width":layout.cardWidth,"--battlefield-card-height":layout.cardHeight,
        "--battlefield-card-spacing":layout.spacing,"--battlefield-rotation-inset":layout.rotationInset,
        "--battlefield-label-height":row==="creatures"?26:0,"--battlefield-content-width":layout.contentWidth,"--battlefield-content-height":layout.contentHeight,
        "--battlefield-card-top":layout.cardTop,"--attack-label-top":layout.attackLabelTop,
        "--attack-label-tapped-top":layout.attackLabelTappedTop,"--attack-label-hovered-top":layout.attackLabelHoveredTop,
        "--attack-label-tapped-hovered-top":layout.attackLabelTappedHoveredTop,
      }))container.style.setProperty(name,`${value}px`);
    };
    const schedule=()=>{cancelAnimationFrame(frame);frame=requestAnimationFrame(update);};
    const observer=new ResizeObserver(schedule);observer.observe(container);schedule();
    return()=>{observer.disconnect();cancelAnimationFrame(frame);};
  },[attachmentKey,row,overview]);
  return <div className="battlefield-card-viewport" ref={containerRef} role="region" aria-label={`${row} cards; scroll to see additional cards`} tabIndex={0}><div className="row-cards responsive-battlefield-row" data-battlefield-row={row} data-card-count={attachments.length}>{children}</div></div>;
}

type ResponsiveHandProps = {
    hand: CardInstance[];
    onHover: (card: CardData | null) => void;
    onMenu: (event: React.MouseEvent, item: CardInstance) => void;
    send: (type: string, payload?: Record<string, unknown>) => void;
};

export const ResponsiveHand = memo(function ResponsiveHand({ hand, onHover, onMenu, send }: ResponsiveHandProps) {
  const engine=useContext(EngineControlsContext);
  const containerRef = useRef<HTMLDivElement>(null);
  const [selectedId,setSelectedId] = useState<string|null>(null);
  const visibleSelectedId = hand.some((item)=>item.instanceId===selectedId) ? selectedId : null;

  useEffect(()=>{
    const container=containerRef.current;
    if(!container)return;
    let animationFrame=0;
    const update=()=>{
      const padding=getComputedStyle(container);
      const layout=calculateHandLayout(container.clientWidth,container.clientHeight,hand.length,{
        width:parseFloat(padding.paddingLeft)+parseFloat(padding.paddingRight),
        height:parseFloat(padding.paddingTop)+parseFloat(padding.paddingBottom),
      });
      container.style.setProperty("--hand-card-width",`${layout.cardWidth}px`);
      container.style.setProperty("--hand-card-height",`${layout.cardHeight}px`);
      container.style.setProperty("--hand-card-spacing",`${layout.spacing}px`);
      container.style.setProperty("--hand-content-width",`${layout.contentWidth}px`);
      container.style.setProperty("--hand-content-height",`${layout.contentHeight}px`);
      container.style.setProperty("--hand-inset",`${layout.inset}px`);
      container.style.setProperty("--hand-bottom-inset",`${layout.bottomInset}px`);
    };
    const schedule=()=>{cancelAnimationFrame(animationFrame);animationFrame=requestAnimationFrame(update);};
    const observer=new ResizeObserver(schedule);
    observer.observe(container);
    schedule();
    return()=>{observer.disconnect();cancelAnimationFrame(animationFrame);};
  },[hand.length]);

  return <div className="hand-cards" ref={containerRef} role="region" aria-label="Your hand; scroll to see additional cards" tabIndex={0}><div className="hand-card-track">{hand.map((item)=>{
    const pendingDraw=item.instanceId.startsWith("pending-draw-");
    if(pendingDraw)return <div className="hand-card pending-draw" key={item.instanceId} role="status" aria-label="Drawing a private card"><span className="pending-card-back" aria-hidden="true"><i/><b>DRAWING</b><small>PRIVATE CARD</small></span></div>;
    const selected=item.instanceId===visibleSelectedId;
    return <div className={`hand-card ${selected?"selected":""} ${engine?.selectable.has(item.instanceId)?"engine-legal":""}`} data-instance-id={item.instanceId} key={item.instanceId} role="button" tabIndex={0} aria-label={`${item.card.name}${selected?", selected":""}`} aria-pressed={selected} draggable={!engine} onClick={()=>engine?engine.select(item.instanceId):setSelectedId((current)=>current===item.instanceId?null:item.instanceId)} onKeyDown={(event)=>{if(event.key==="Enter"||event.key===" "){event.preventDefault();if(engine)engine.select(item.instanceId);else setSelectedId((current)=>current===item.instanceId?null:item.instanceId);}}} onDragStart={(event)=>event.dataTransfer.setData("application/json",JSON.stringify({instanceId:item.instanceId,from:"hand"}))} onFocus={(event)=>{event.currentTarget.scrollIntoView({block:"nearest",inline:"nearest"});onHover(item.card);}} onBlur={()=>onHover(null)} onMouseEnter={()=>onHover(item.card)} onMouseLeave={()=>onHover(null)} onContextMenu={(event)=>{if(engine){event.preventDefault();onHover(item.card);}else onMenu(event,item);}}><CardArt source={item.card.image} alt={item.card.name}/>{!engine&&<button onClick={(event)=>{event.stopPropagation();send("REVEAL_CARD",{instanceId:item.instanceId});}}>Reveal</button>}</div>;
  })}</div></div>;
});

export function DropZone({ className, label, onDrop, children }: { className?: string; label?: string; onDrop: (data: DragData) => void; children?: React.ReactNode }) {
  const [over,setOver] = useState(false);
  return <div className={`${className ?? ""} ${over ? "drop-active" : ""}`} onDragOver={(event) => {event.preventDefault();setOver(true);}} onDragLeave={() => setOver(false)} onDrop={(event) => {event.preventDefault();event.stopPropagation();setOver(false);try{onDrop(JSON.parse(event.dataTransfer.getData("application/json")));}catch{}}}>{label && <span className="drop-label">{label}</span>}{children}</div>;
}
