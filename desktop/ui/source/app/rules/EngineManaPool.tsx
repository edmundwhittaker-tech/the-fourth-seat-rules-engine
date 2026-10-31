"use client";
import {useRef,useState} from 'react';
import type { GameView } from '../../lib/rules/views';
import type { FeedbackPrompt } from '../../lib/rules/vendor/feedback/types';
import { controlledMana, manaColors, manaPayment, type ManaColor } from '../../lib/rules/mana';
import './mana.css';

export default function EngineManaPool({game,prompt,disabled,send}:{game:GameView;prompt:FeedbackPrompt|null;disabled:boolean;send:(action:string,args:Record<string,unknown>)=>Promise<boolean>}) {
  const [pending,setPending]=useState(false);
  const inFlight=useRef(false);
  const {player,pool,total}=controlledMana(game,prompt);
  if(!player)return null;
  const paying=prompt?.mode==='mana';
  async function pay(color:ManaColor){
    const command=manaPayment(game,prompt,color);
    if(!command||disabled||inFlight.current)return;
    inFlight.current=true;setPending(true);
    try{await send(command.action,command.args);}finally{inFlight.current=false;setPending(false);}
  }
  return <section className="engine-mana-pool" aria-label="Your floating mana">
    <div className="engine-mana-heading"><strong>Mana</strong><span aria-label={`Total floating mana: ${total}`}>{total>0?`Total ${total}`:''}</span></div>
    <div className="engine-mana-colors">{total===0&&!paying&&<span className="engine-mana-empty">No floating mana</span>}{manaColors.filter(({key})=>paying||pool[key]>0).map(({key,symbol,name})=><button type="button" key={key} className={`engine-mana-unit mana-${key} ${pool[key]>0?"has-mana":"is-empty"}`} disabled={disabled||pending||!manaPayment(game,prompt,key)} onClick={()=>void pay(key)} aria-label={`${name} mana: ${pool[key]}${paying?', spend one':''}`} title={paying?`Spend one ${name.toLowerCase()} mana toward this cost. The engine checks restrictions.`:`${name}: ${pool[key]}. Choose a spell or ability to spend floating mana.`}><span className="engine-mana-symbol" style={{backgroundImage:`url("/mana/${symbol}.svg")`}} aria-hidden="true"/><b>{pool[key]}</b></button>)}</div>

  </section>;
}
