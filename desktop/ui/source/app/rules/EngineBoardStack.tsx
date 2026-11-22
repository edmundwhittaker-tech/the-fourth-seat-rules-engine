"use client";

import type {CSSProperties} from 'react';
import type {StackObject} from '../../lib/game-types';
import type {CardData} from '../../lib/types';
import CardArt from '../CardArt';
import {StackAbilityCard} from '../gameplay/StackAbilityCard';

type Props={items:StackObject[];selectable:Set<string>;select:(id:string)=>void;hover:(card:CardData|null)=>void;inspect:(id:string)=>void;open:()=>void};
export function EngineBoardStack({items,selectable,hover,open}:Props){
  const depth=Math.min(Math.max(items.length-1,0),3);
  return <section className="engine-board-stack" style={{'--stack-visible-depth':depth} as CSSProperties} aria-label={`Shared stack: ${items.length} objects`}>
    <button className="engine-stack-count" disabled={!items.length} onClick={open} aria-label="Inspect all objects on the stack" aria-haspopup="dialog">Stack{items.length?` · ${items.length}`:''}</button>
    <div className="engine-stack-pile">
      {!items.length&&<span className="engine-stack-empty">Empty</span>}
      {items.map((item,index)=>{const card=item.card?.card??item.source,legal=selectable.has(item.id);return <div key={item.id} className={`stack-object ${legal?'engine-legal':''}`} data-stack-id={item.id} role="button" tabIndex={index<4?0:-1} aria-haspopup="dialog" aria-label={`${index===0?'Resolves next: ':''}${card?.name??'Ability'}${legal?', legal target':''}, inspect stack`} style={{zIndex:items.length-index,'--stack-depth':Math.min(index,3)} as CSSProperties} onMouseEnter={()=>hover(card??null)} onMouseLeave={()=>hover(null)} onFocus={()=>hover(card??null)} onBlur={()=>hover(null)} onClick={open} onKeyDown={event=>{if(event.target!==event.currentTarget)return;if(event.key==='Enter'||event.key===' '){event.preventDefault();event.stopPropagation();open();}}}>
        {item.kind==='ability'?<StackAbilityCard item={item} onInspect={open}/>:<CardArt source={card?.image} alt={card?.name??'Spell'} publicObjectId={item.id}/>}
        {index===0&&<small>Resolves next</small>}
      </div>;})}
    </div>
    <span className="engine-stack-hint">{items.length?'Click to expand':'Spells & abilities'}</span>
  </section>;
}

export function EngineStackBrowser({items,selectable,select,inspect,close,playerName}:{items:StackObject[];selectable:Set<string>;select:(id:string)=>void;inspect:(id:string)=>void;close:()=>void;playerName?:(id:string)=>string}){
  return <div className="modal-backdrop game-modal-backdrop engine-stack-backdrop" onClick={close}><section className="modal zone-modal engine-stack-browser" role="dialog" aria-modal="true" aria-label="Stack in resolution order" onClick={event=>event.stopPropagation()} onKeyDown={event=>{if(event.key==='Escape'){event.stopPropagation();close();}
      if(event.key==='Tab'){const controls=event.currentTarget.querySelectorAll<HTMLElement>('button, summary');const first=controls[0],last=controls[controls.length-1];if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}}}}>
    <header className="modal-head"><div><small>FIRST CARD RESOLVES NEXT</small><h2>Stack · {items.length}</h2></div><button className="icon-button" autoFocus aria-label="Close stack" onClick={close}>×</button></header>
    {items.length===0?<p className="zone-empty">The stack is empty.</p>:<ol className="zone-card-grid engine-stack-grid">{items.map((item,index)=>{const card=item.card?.card??item.source;return <li className={`zone-card engine-stack-tile ${selectable.has(item.id)?'engine-legal':''}`} key={item.id}>
      <small className="engine-stack-order">{index===0?'Resolves next':`Then ${index+1}`}</small>
      <CardArt source={card?.image} alt={card?.name??'Ability'}/>
      <h3>{card?.name??'Ability'}</h3>
      {playerName&&<small>{playerName(item.controllerSeatId)}</small>}
      {item.kind==='ability'&&<><small>{/TRIGGER/i.test(item.abilityType??'')?'Triggered ability':/ACTIVAT/i.test(item.abilityType??'')?'Activated ability':'Ability'}</small>{item.note&&<p>{item.note}</p>}<button onClick={()=>inspect(item.id)}>Read ability</button></>}
      {card?.oracleText&&<details><summary>Card text</summary><p>{card.oracleText}</p></details>}
      {selectable.has(item.id)&&<button className="primary" onClick={()=>{select(item.id);close();}}>Choose this target</button>}
    </li>;})}</ol>}
  </section></div>;
}
