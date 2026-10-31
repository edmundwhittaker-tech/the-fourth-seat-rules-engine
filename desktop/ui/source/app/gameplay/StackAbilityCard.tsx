"use client";

import CardArt from '../CardArt';
import type {StackObject} from '../../lib/game-types';

const abilityLabel=(item:StackObject)=>/TRIGGER/i.test(item.abilityType??'')?'Triggered ability':/ACTIVAT/i.test(item.abilityType??'')?'Activated ability':'Ability';

export function StackAbilityCard({item,onInspect}:{item:StackObject;onInspect:()=>void}){
  const source=item.source;
  return <div className="engine-stack-ability">
    <CardArt source={source?.image} alt={source?.name??'Ability source'} fallback={<><b>{source?.name??'Ability source'}</b><small>{item.note}</small></>}/>
    <span className="engine-stack-ability-label">{abilityLabel(item)}</span>
    <button type="button" aria-label={`Read ${abilityLabel(item).toLowerCase()} from ${source?.name??'unknown source'}`} onClick={event=>{event.stopPropagation();onInspect();}}>Read ability</button>
  </div>;
}

export function StackAbilityDetails({item,onClose}:{item:StackObject;onClose:()=>void}){
  const source=item.source;
  return <div className="engine-ability-backdrop" onClick={onClose}>
    <section className="engine-ability-details" role="dialog" aria-modal="true" aria-label={`${source?.name??'Source card'} ability`} onClick={event=>event.stopPropagation()} onKeyDown={event=>{if(event.key==='Escape')onClose();
        if(event.key==='Tab'){
          const controls=event.currentTarget.querySelectorAll<HTMLElement>('button, summary');
          const first=controls[0],last=controls[controls.length-1];
          if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
          else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
        }}}>
      <button type="button" autoFocus className="engine-ability-close" aria-label="Close ability details" onClick={onClose}>×</button>
      <div className="engine-ability-art"><CardArt source={source?.image} alt={source?.name??'Ability source'}/></div>
      <div className="engine-ability-copy"><small>{abilityLabel(item)} · ON THE STACK</small><h2>{source?.name??'Ability source'}</h2><p>{item.note||'Ability text is unavailable from the engine.'}</p>{source?.oracleText&&<details><summary>Full card text</summary><p>{source.oracleText}</p></details>}</div>
    </section>
  </div>;
}
