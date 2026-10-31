"use client";
import {useRef,useState} from 'react';
import CardArt from '../CardArt';
import type {CardData} from '../../lib/types';
import type {EngineCardChoice} from '../../lib/rules/choices';
import type {FeedbackPrompt} from '../../lib/rules/vendor/feedback/types';
import './choices.css';
export default function EngineCardChoices({choices,prompt,open,disabled,resolve,label,choose,onClose}:{choices:EngineCardChoice[];prompt:FeedbackPrompt;open:boolean;disabled:boolean;resolve:(card:EngineCardChoice['card'])=>CardData;label:(value:string)=>string;choose:(value:string)=>Promise<boolean>;onClose:()=>void}){
  const [query,setQuery]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const pending=useRef(false);
  if(!open||!choices.length)return null;
  const visible=choices.filter(choice=>(choice.card.displayName??choice.card.name).toLowerCase().includes(query.toLowerCase()));
  async function pick(value:string){
    if(disabled||pending.current)return;
    pending.current=true;setBusy(true);
    try{setError('');if(!await choose(value))setError('The engine could not accept that choice. Return to the battlefield to check the latest prompt.');}finally{pending.current=false;setBusy(false);}
  }
  return <div className="engine-card-browser-backdrop"><section className="engine-card-browser" role="dialog" aria-modal="true" aria-label={prompt.title} onKeyDown={event=>{if(event.key==='Escape')onClose();}}>
    <header><div><small>{label(prompt.sourceName??'RULES ENGINE')} · {choices.length} LEGAL CHOICES</small><h2>{label(prompt.title)}</h2><p>{label(prompt.message)}</p>{prompt.progress&&<p>Selected {prompt.progress.selected}{prompt.progress.max!==undefined?` of ${prompt.progress.max}`:''}</p>}</div><button type="button" onClick={onClose} aria-label="Return to the battlefield">×</button></header>
    {error&&<p role="alert" className="engine-card-browser-empty">{error}</p>}
    <div className="engine-card-browser-tools"><label>Find a card<input autoFocus aria-label="Find a legal card" value={query} onChange={event=>setQuery(event.target.value)} placeholder="Search these choices…"/></label><span>Click a card to choose it. The engine handles the effect.</span></div>
    <div className="engine-visual-card-grid">{visible.map(choice=>{const card=resolve(choice.card);return <button type="button" className="engine-visual-card" key={choice.value} disabled={disabled||busy} onClick={()=>void pick(choice.value)} aria-label={`Choose ${card.name} from ${choice.zone}`} data-engine-choice={choice.value}><CardArt source={card.image} alt={card.name}/><b>{card.name}</b><small>{choice.zone}</small>{card.power&&card.toughness&&<span>{card.power}/{card.toughness}</span>}</button>;})}</div>
    {!visible.length&&<p className="engine-card-browser-empty">No matching cards in these choices.</p>}
    <footer><span>Only cards supplied for this decision are shown.</span><button type="button" onClick={onClose}>Return to battlefield</button></footer>
  </section></div>;
}
