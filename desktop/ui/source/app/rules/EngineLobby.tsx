"use client";

import { useEffect, useState } from 'react';
import { enginePlayerName } from '../../lib/rules/player-labels';
import CardArt from '../CardArt';
import type { CardData, DeckData } from '../../lib/types';
import type { EngineRoom } from '../../lib/rules/session';

type Props = { room: EngineRoom; user: string; live: boolean; busy: boolean; selectedDeck?: DeckData; onChooseDeck: () => void; call: (body: Record<string, unknown>) => Promise<unknown> };
export default function EngineLobby({room,user,live,busy,selectedDeck,onChooseDeck,call}:Props) {
  const [copied,setCopied]=useState('');
  const [art,setArt]=useState<CardData[]>([]);
  const names=[...new Set(room.players.map(player=>player.commander).filter(Boolean))].sort().join('\n');
  useEffect(()=>{
    if(!names)return;
    let cancelled=false;
    void fetch('/api/import',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({decklist:names.split('\n').map(name=>'1 '+name).join('\n')})}).then(async response=>{
      if(!response.ok)return;
      const data=await response.json() as {cards?:CardData[]};
      if(!cancelled)setArt(data.cards??[]);
    }).catch(()=>{});
    return()=>{cancelled=true;};
  },[names]);
  const editable=room.lobbyEditable===true&&room.status==='lobby';
  const disabled=!live||busy||!editable;
  const configure=(seat:number,kind:'human'|'ai',deckId?:string)=>void call({operation:'configure',code:room.code,seat,kind,deckId});
  const copy=async(invite:boolean)=>{
    try{await navigator.clipboard.writeText(invite?`${window.location.origin}?rules=${room.code}`:room.code);setCopied(invite?'Invite copied':'Code copied');}
    catch{setCopied('Could not copy. Share the room code shown above.');}
  };
  const humans=room.players.filter(player=>player.kind==='human');
  const bots=room.players.filter(player=>player.kind==='ai');
  const canStart=live&&!busy&&room.status==='lobby'&&room.players.length===4;
  return <section className="lobby-content engine-room-lobby">
    <span className="eyebrow">COMMANDER · RULES ENGINE ROOM</span>
    <h1>Gather your table</h1>
    <p>Invite friends or fill open seats with bots. Review every deck before you begin.</p>
    <div className="room-code-card"><div><small>ENGINE ROOM CODE</small><strong>{room.code}</strong></div><button onClick={()=>void copy(false)}>Copy code</button><button onClick={()=>void copy(true)}>Copy invite</button></div>
    <p className="engine-copy-status" role="status">{copied}</p>
    {!room.lobbyEditable&&<div className="engine-service-note" role="status">This engine version does not support editing seats yet. Update the engine service to enable bot and deck controls.</div>}
    <div className="seat-grid">{[0,1,2,3].map(seat=>{
      const player=room.players.some(p=>p.seat!==undefined)?room.players.find(p=>p.seat===seat):(seat<room.humanSeats?humans[seat]:bots[seat-room.humanSeats]);
      if(!player)return <article className="seat-card empty-seat" key={seat}><div className="waiting-ring">{seat+1}</div><h2>Open for a friend</h2><p>Share code {room.code}</p>{room.isHost&&<button className="secondary" disabled={disabled} onClick={()=>configure(seat,'ai')}>+ Add bot</button>}</article>;
      const mine=player.id===user;
      const card=selectedDeck?.commander.name===player.commander?selectedDeck.commander:art.find(c=>c.name===player.commander);
      const used=new Set(room.players.filter(p=>p.id!==player.id).map(p=>p.commander));
      return <article className="seat-card ready" key={player.id}>
        <div className="seat-art"><CardArt source={card?.artCrop||card?.image} alt={player.commander}/><span>{player.kind==='ai'?'XMAGE BOT':'HUMAN'}{mine?' · YOU':''}</span></div>
        <div className="seat-info"><small>SEAT {seat+1}{seat===0?' · HOST':''}</small><h2>{enginePlayerName(player)}</h2><b title={player.commander}>{player.commander}</b><p>{player.deckName}</p>
          <span className="online"><i/>{player.kind==='ai'?'Engine-controlled opponent':'Deck validated'}</span>
          {player.kind==='ai'&&room.isHost&&<div className="engine-seat-controls"><label htmlFor={`bot-deck-${seat}`}>Bot deck</label><select id={`bot-deck-${seat}`} value={player.deckId??''} disabled={disabled} onChange={event=>configure(seat,'ai',event.target.value)}>{!player.deckId&&<option value="">Current deck</option>}{(room.aiDecks??[]).map(deck=><option key={deck.id} value={deck.id} disabled={used.has(deck.commander)}>{deck.commander} · {deck.name}</option>)}</select><div><button disabled={disabled} onClick={()=>configure(seat,'ai','random')}>Randomise</button><button disabled={disabled} onClick={()=>configure(seat,'human')}>Open for friend</button></div></div>}
          {mine&&<div className="engine-seat-controls"><button disabled={disabled} onClick={onChooseDeck}>Choose another deck</button>{selectedDeck&&<button className="primary" disabled={disabled} onClick={()=>void call({operation:'changeDeck',code:room.code,deckId:selectedDeck.id})}>Use {selectedDeck.name}</button>}</div>}
        </div>
      </article>;
    })}</div>
    <details className="engine-room-options"><summary>Table options &amp; deck pool</summary><p>Commander · 4 seats · 40 starting life · XMage rules and native AI</p><p>{room.freeMulligans===1?'One free mulligan per player: redraw seven, then keep six after your next mulligan (London mulligan).':'Mulligans follow the engine’s current table settings.'}</p><p>Bots use the ten published deck lists, never copies of your deck. Random choices avoid other commanders already at this table. Pick a specific deck above or open a bot seat for a friend.</p><p>Seat and deck changes close when the game starts. {room.isHost?'Only you, as host, can manage bot seats.':'Your host manages bot seats and starts the game.'}</p></details>
    <div className="lobby-actions"><span>{room.players.length}/4 seats filled{room.players.length<4?' · Waiting for friends or bots':''}</span><button className="secondary" disabled={busy} onClick={()=>void call({operation:'leave',code:room.code})}>Leave lobby</button>{room.isHost?<button className="primary" disabled={!canStart} onClick={()=>void call({operation:'start',code:room.code})}>{room.status==='starting'?'Starting game…':'Start engine game'}</button>:<span>Waiting for host to start</span>}</div>
  </section>;
}
