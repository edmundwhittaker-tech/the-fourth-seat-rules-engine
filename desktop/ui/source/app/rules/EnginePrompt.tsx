"use client";

import { useState, type ReactNode } from 'react';
import type { FeedbackPrompt } from '../../lib/rules/vendor/feedback/types';
import { promptAnswer } from '../../lib/rules/session';

const manaNames:Record<string,string>={W:'White',U:'Blue',B:'Black',R:'Red',G:'Green',C:'Colorless',S:'Snow',P:'Phyrexian',X:'X',Y:'Y',Z:'Z'};
const manaColours:Record<string,string>={W:'#e6dfbb',U:'#81afc6',B:'#a39c9a',R:'#d7977c',G:'#8cab87',C:'#c5c0b7',P:'#c4b2a0'};
export function manaText(text:string):ReactNode{
  const parts=text.split(/(\{(?:\d+|[WUBRGCXYZS]|(?:2|[WUBRG])\/(?:[WUBRG]|P)(?:\/P)?)\})/gi);
  if(parts.length===1)return text;
  return parts.map((part,index)=>{
    if(index%2===0)return part;
    const symbol=part.slice(1,-1).toUpperCase(),halves=symbol.split('/');
    const name=halves.map(value=>manaNames[value]??value).join(' or ')+( /^\d+$/.test(symbol)?' generic mana':' mana');
    return <span key={index} className={`engine-inline-mana ${halves.length>1?'is-hybrid':''}`} role="img" aria-label={name} title={name} style={halves.length>1?{background:`linear-gradient(135deg,${manaColours[halves[0]]??'#c5c0b7'} 49%,${manaColours[halves[1]]??'#c5c0b7'} 51%)`}:undefined}>{halves.map((value,i)=><span key={i} className="engine-inline-mana-face">{/[WUBRGCS]/.test(value)?<span className="engine-inline-mana-icon" style={{backgroundImage:`url("/mana/${value}.svg")`}} aria-hidden="true"/>:value==='P'?'Φ':value}</span>)}</span>;
  });
}

export default function EnginePrompt({prompt,disabled,label,send,playerTargetHint,visualValues,cardChoices,passingTurn,onPass,waitingText}:{passingTurn:boolean;onPass?:()=>Promise<boolean>;waitingText?:string;prompt:FeedbackPrompt|null;disabled:boolean;label:(value:string)=>string;send:(action:string,args:Record<string,unknown>)=>Promise<boolean>;playerTargetHint?:string;visualValues?:Set<string>;cardChoices?:ReactNode}) {
  const [busy,setBusy]=useState(false);
  const [expanded,setExpanded]=useState(!cardChoices);
  const [amount,setAmount]=useState(prompt?.min??0);
  const [text,setText]=useState('');
  const [amounts,setAmounts]=useState<Record<string,number>>({});
  const [ordered,setOrdered]=useState<string[]>([]);
  if(!prompt)return <section className="engine-prompt is-waiting" aria-live="polite"><strong>{waitingText??'Waiting for the table'}</strong></section>;
  const blocked=disabled||busy;
  async function act(action:string,args:Record<string,unknown>){setBusy(true);try{await send(action,args);}finally{setBusy(false);}}
  function answer(value:string|number|boolean){const command=promptAnswer(prompt!,value);void act(command.action,command.args);}
  const pass=()=>void act('sendPlayerBoolean',{gameId:prompt.gameId,value:false});
  const priority=prompt.method==='GAME_SELECT'&&prompt.title==='Your priority';
  const offered=prompt.options.filter(o=>!visualValues?.has(o.value));
  const choices=offered.filter(o=>!text||label(o.label).toLowerCase().includes(text.toLowerCase()));
  const total=(prompt.items??[]).reduce((sum,i)=>sum+(amounts[i.id]??i.defaultValue??i.min),0);
  const complex=['integer','multiString','string','order'].includes(prompt.mode)||offered.length>4||offered.some(o=>label(o.label).length>55)||prompt.message.length>180;
  const controls=<>{cardChoices}
    {(priority||prompt.mode==='combat')&&<><button className="primary engine-brass-button" title={priority?'Pass priority. Smart passing continues through quiet windows and stops for new responses.':undefined} disabled={blocked||priority&&passingTurn} onClick={priority&&onPass?()=>void onPass():pass}>{priority?'Pass':'Confirm '+(prompt.title.toLowerCase().includes('block')?'blockers':'attackers')}</button></>}
    {prompt.mode==='mana'&&<><button disabled={blocked} onClick={pass}>Cancel payment</button></>}
    {prompt.mode==='integer'&&<form onSubmit={e=>{e.preventDefault();answer(amount);}}><label>Amount <input aria-label="Engine amount" type="number" min={prompt.min} max={prompt.max} value={amount} onChange={e=>setAmount(Number(e.target.value))}/></label><small>{prompt.min}–{prompt.max}</small><button disabled={blocked||!Number.isInteger(amount)||amount<prompt.min||amount>prompt.max}>Confirm amount</button></form>}
    {prompt.mode==='multiString'&&<form onSubmit={e=>{e.preventDefault();answer((prompt.items??[]).map(i=>amounts[i.id]??i.defaultValue??i.min).join(' '));}}>{prompt.items?.map(i=><label key={i.id}>{manaText(i.label)}<input type="number" aria-label={i.label} min={i.min} max={i.max} value={amounts[i.id]??i.defaultValue??i.min} onChange={e=>setAmounts(v=>({...v,[i.id]:Number(e.target.value)}))}/></label>)}<small>Total {total} · required {prompt.min}–{prompt.max}</small><button disabled={blocked||total<prompt.min||total>prompt.max||(prompt.items??[]).some(i=>!Number.isInteger(amounts[i.id]??i.defaultValue??i.min)||(amounts[i.id]??i.defaultValue??i.min)<i.min||(amounts[i.id]??i.defaultValue??i.min)>i.max)}>Confirm amounts</button></form>}
    {(prompt.mode==='string'||choices.length>7)&&<input aria-label="Find an engine option" value={text} onChange={e=>setText(e.target.value)} placeholder={prompt.mode==='string'?'Choose or enter a name…':'Find an option…'}/>}
    {prompt.mode==='order'?<><p>Choose cards in the requested order.</p>{prompt.options.map(o=><button key={o.id} disabled={blocked||ordered.includes(o.value)} onClick={()=>setOrdered(v=>[...v,o.value])}>{ordered.includes(o.value)?`${ordered.indexOf(o.value)+1}. `:''}{manaText(label(o.label))}</button>)}<button disabled={blocked||ordered.length!==prompt.options.length} onClick={()=>void act('sendPlayerString',{gameId:prompt.gameId,value:ordered.join(' ')})}>Confirm order</button><button onClick={()=>setOrdered([])}>Reset order</button></>:<div className="engine-choice-list">{choices.map(o=><button key={o.id} className={prompt.mode==='boolean'?'engine-brass-button':undefined} disabled={blocked} title={prompt.choiceHints?.[o.id]} onClick={()=>answer(o.value)}>{manaText(label(o.label))}</button>)}</div>}
    {prompt.mode==='string'&&<button disabled={blocked||!text.trim()} onClick={()=>answer(text.trim())}>Use “{text.trim()||'name'}”</button>}
    {prompt.required===false&&prompt.mode!=='mana'&&<button disabled={blocked} onClick={pass}>Done / decline</button>}
</>;
  return <section className={`engine-prompt ${priority?'is-priority':''}`} aria-label="Engine decision" aria-live="polite">
    <div className="engine-prompt-copy">{prompt.sourceName&&<small>{manaText(label(prompt.sourceName))}</small>}<h2>{priority?'Your move':manaText(label(prompt.title))}</h2>{!priority&&<p>{manaText(label(playerTargetHint??prompt.message))}</p>}{prompt.progress&&<small>Selected {prompt.progress.selected}{prompt.progress.max!==undefined?` of ${prompt.progress.max}`:''}</small>}</div>
    {complex?<><div className="engine-prompt-controls">{cardChoices}<button className="primary engine-brass-button" disabled={blocked} aria-expanded={expanded} onClick={()=>setExpanded(v=>!v)}>Make choice</button></div>{expanded&&<section className="engine-decision-popover" role="dialog" aria-label={prompt.title} onKeyDown={event=>{if(event.key==='Escape'){event.stopPropagation();setExpanded(false);}}}><header><h2>{manaText(label(prompt.title))}</h2><button aria-label="Close decision panel" onClick={()=>setExpanded(false)}>×</button></header><p>{manaText(label(prompt.message))}</p><div className="engine-prompt-controls">{controls}</div></section>}</>:<div className="engine-prompt-controls">{controls}</div>}
  </section>;
}
