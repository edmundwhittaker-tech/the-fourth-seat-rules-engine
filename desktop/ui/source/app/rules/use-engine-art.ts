"use client";
import {useEffect,useRef,useState} from 'react';
import type {CardData,DeckData} from '../../lib/types';
import type {GameView,CardView} from '../../lib/rules/views';
import type {FeedbackPrompt} from '../../lib/rules/vendor/feedback/types';
import {namedCards} from '../../lib/rules/session';
import {engineArtKey,isTokenArt,matchTokenArt,tokenProfile,type EngineArt} from '../../lib/rules/art';
export function useEngineArt(game:GameView|null,deck:DeckData|undefined,prompt?:FeedbackPrompt|null){
  const [cards,setCards]=useState<EngineArt[]>([]);
  const attempted=useRef(new Set<string>());
  const tokenCache=useRef(new Map<string,Promise<CardData[]>>());
  useEffect(()=>{
    if(!game)return;
    const visible=[...namedCards(game),...(prompt?.cards??[])] as CardView[];
    const known=new Set([...(deck?.cards??[]).map(c=>'card:'+c.name),...cards.map(c=>c.engineArtKey??'card:'+c.name)]);
    const pending=[...new Map(visible.filter(c=>!c.faceDown&&!c.hideInfo&&!c.isAbility&&c.name).map(c=>[engineArtKey(c),c])).entries()].filter(([key])=>!known.has(key)&&!attempted.current.has(key));
    if(!pending.length)return;
    const timer=window.setTimeout(()=>{
      for(const [key]of pending)attempted.current.add(key);
      const regular=pending.filter(([,card])=>!isTokenArt(card)).slice(0,75);
      if(regular.length)void fetch('/api/import',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({decklist:regular.map(([,card])=>'1 '+card.name).join('\n')})}).then(async response=>{
        if(!response.ok)throw Error('Artwork unavailable');
        const data=await response.json() as {cards?:CardData[]};
        if(data.cards)setCards(current=>[...current,...data.cards!]);
      }).catch(()=>{for(const [key]of regular)attempted.current.delete(key);});
      void (async()=>{
        for(const [key,card]of pending.filter(([,card])=>isTokenArt(card))){
          const name=tokenProfile(card).name;
          try{
            if(!tokenCache.current.has(name))tokenCache.current.set(name,fetch('/api/token-search?exact=1&q='+encodeURIComponent(name)).then(async response=>{
              if(!response.ok)throw Error('Token artwork unavailable');
              return ((await response.json()) as {cards?:CardData[]}).cards??[];
            }));
            const match=matchTokenArt(card,await tokenCache.current.get(name)!);
            if(match)setCards(current=>[...current,{...match,engineArtKey:key}]);
          }catch{tokenCache.current.delete(name);attempted.current.delete(key);}
        }
      })();
      for(const [key]of pending.filter(([,card])=>!isTokenArt(card)).slice(75))attempted.current.delete(key);
    },200);
    return()=>window.clearTimeout(timer);
  },[game,deck,cards,prompt]);
  return cards;
}
