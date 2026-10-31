import type {CardData,DeckData} from '../types';
import type {CardView,PermanentView} from './views';
export type EngineArt=CardData & {engineArtKey?:string};
// Bundled/local decks may contain only names. A card record is not evidence that its image loaded.
export const hasCardArtwork=(card:CardData)=>typeof card.image==='string'&&card.image.trim().length>0;
export function knownEngineArtKeys(deck:DeckData|undefined,artwork:EngineArt[]=[]){
  return new Set([...(deck?.cards??[]).filter(hasCardArtwork).map(c=>'card:'+c.name),...artwork.filter(hasCardArtwork).map(c=>c.engineArtKey??'card:'+c.name)]);
}
const plain=(value:string)=>value.replace(/<[^>]*>/g,'').trim();
const colors=['white','blue','black','red','green'] as const;
export const engineColors=(card:CardView)=>colors.flatMap((name,i)=>card.color?.[name]?[['W','U','B','R','G'][i]]:[]);
export function isTokenArt(card:CardView){return !!card.isToken&&!(card as PermanentView).copy&&!card.originalIsCopy;}
export const tokenName=(name:string)=>name.trim().replace(/\s+token$/i,'').trim();
function tokenColors(card:CardView){
  const identity=card.originalColorIdentity?.trim();
  if(!identity)return engineColors(card);
  const symbols=identity.match(/\{[WUBRGC](?:\/[WUBRGC2P])*\}/gi);
  const letters=symbols?symbols.join('').replace(/[^WUBRG]/gi,''):/^[WUBRGC]+$/i.test(identity)?identity:'';
  if(symbols||letters)return ['W','U','B','R','G'].filter(symbol=>letters.toUpperCase().includes(symbol));
  if(/\bcolorless\b/i.test(identity))return [];
  const named=colors.flatMap((name,i)=>new RegExp('\\b'+name+'\\b','i').test(identity)?[['W','U','B','R','G'][i]]:[]);
  return named.length?named:engineColors(card);
}
// XMage sends original P/T as MageInt objects as well as strings in bridge views.
function tokenStat(original:unknown,current:string|undefined):string|undefined{
  if(typeof original==='string')return original||current;
  if(typeof original==='number'&&Number.isFinite(original))return String(original);
  if(original&&typeof original==='object'){
    const stat=original as {cardValue?:unknown;baseValue?:unknown};
    if(typeof stat.cardValue==='string'&&stat.cardValue&&!/^-?\d+$/.test(stat.cardValue))return stat.cardValue;
    if(typeof stat.baseValue==='number'&&Number.isFinite(stat.baseValue)&&stat.baseValue!==-2147483648)return String(stat.baseValue);
    if(typeof stat.cardValue==='string'&&stat.cardValue)return stat.cardValue;
  }
  return current;
}
export function tokenProfile(card:CardView){
  const subtypes=Array.isArray(card.subTypes)?card.subTypes.map(String):typeof card.subTypes==='string'?card.subTypes.split(/\s+/):[];
  return {name:tokenName(card.name),subtypes,colors:tokenColors(card),power:tokenStat(card.originalPower,card.power),toughness:tokenStat(card.originalToughness,card.toughness),rules:(card.rules??[]).map(plain).join(' ')};
}
export function engineArtKey(card:CardView){return isTokenArt(card)?'token:'+JSON.stringify(tokenProfile(card)):'card:'+card.name;}
const keywords=['flying','vigilance','deathtouch','lifelink','haste','trample','reach','defender','first strike','double strike','menace','decayed'];
export function matchTokenArt(card:CardView,candidates:CardData[]):CardData|undefined{
  const profile=tokenProfile(card),names=profile.name.toLowerCase();
  const eligible=candidates.filter(c=>c.image&&c.name.toLowerCase()===names&&/token/i.test(c.typeLine)&&((card.color==null&&card.originalColorIdentity==null)||[...c.colors].sort().join('')===[...profile.colors].sort().join('')));
  const score=(candidate:CardData)=>{
    let value=0;
    if([...candidate.colors].sort().join('')===[...profile.colors].sort().join(''))value+=30;else value-=30;
    for(const field of ['power','toughness'] as const)if(profile[field]!==undefined)value+=candidate[field]===profile[field]?16:-8;
    for(const subtype of profile.subtypes)if(candidate.typeLine.toLowerCase().includes(subtype.toLowerCase()))value+=8;
    for(const keyword of keywords){const engine=new RegExp('\\b'+keyword+'\\b','i').test(profile.rules),printed=new RegExp('\\b'+keyword+'\\b','i').test(candidate.oracleText);if(engine===printed)value+=2;else value-=5;}
    return value;
  };
  return eligible.sort((a,b)=>score(b)-score(a))[0];
}
export function createEngineCardResolver(deck:DeckData|undefined,artwork:EngineArt[]=[]){
  const known=new Map([...artwork.filter(c=>!c.engineArtKey?.startsWith('token:')&&hasCardArtwork(c)),...(deck?.cards??[]).filter(hasCardArtwork)].map(c=>[c.name,c]));
  const tokenArt=new Map(artwork.filter(c=>c.engineArtKey).map(c=>[c.engineArtKey,c]));
  for(const card of [...known.values()])for(const face of card.faces??[])known.set(face.name,{...card,...face});
  return (card:CardView):CardData=>{
    const hidden=card.faceDown||card.hideInfo;
    const stored=isTokenArt(card)?tokenArt.get(engineArtKey(card)):known.get(card.name);
    const name=hidden?'Face-down card':card.displayName??card.name,color=engineColors(card);
    const subtypes=Array.isArray(card.subTypes)?card.subTypes.map(String).join(' '):typeof card.subTypes==='string'?card.subTypes:'';
    return {id:card.id??name,name,manaCost:hidden?'':card.manaCostLeftStr?.join('')??'',oracleText:hidden?'':card.rules?.map(plain).join('\n')??'',
      typeLine:hidden?'':[...(card.isToken?['Token']:[]),...(card.superTypes??[]),...(card.cardTypes??[]),...(subtypes?['—',subtypes]:[])].join(' '),colors:hidden?[]:color,colorIdentity:hidden?[]:color,
      image:hidden?'':stored?.image??'',artCrop:hidden?undefined:stored?.artCrop,power:hidden?undefined:card.power,toughness:hidden?undefined:card.toughness,loyalty:hidden?undefined:card.loyalty,quantity:1};
  };
}
