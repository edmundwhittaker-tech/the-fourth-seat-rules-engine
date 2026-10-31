import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';
const compiled=await build({entryPoints:[fileURLToPath(new URL('../ui/source/lib/rules/art.ts',import.meta.url))],bundle:true,write:false,platform:'node',format:'esm'});
const {createEngineCardResolver,knownEngineArtKeys}=await import('data:text/javascript;base64,'+Buffer.from(compiled.outputFiles[0].text).toString('base64'));
const pool=JSON.parse(await readFile(new URL('../../integrations/fourth-seat/gateway/ai-decks.json',import.meta.url),'utf8')).decks;
const record=(name,image='')=>({id:name,name,image,quantity:1,faces:[],typeLine:'',oracleText:'',manaCost:'',colors:[],colorIdentity:[]});
const native=name=>({id:'native-'+name,name,manaCostLeftStr:['{1}'],rules:['Native rules remain authoritative.'],cardTypes:['Artifact']});
test('all ten bundled name-only decks fetch artwork and display fetched images',()=>{
 assert.equal(pool.length,10);
 for(const entry of pool){
  const names=[entry.deck.commanders[0].cardName,...entry.deck.cards.map(c=>c.cardName)];
  const deck={cards:names.map(name=>record(name))};
  assert.equal(knownEngineArtKeys(deck).size,0,entry.id+' must request missing images');
  const artwork=names.map((name,i)=>record(name,'https://cards.scryfall.io/normal/front/test-'+i+'.jpg'));
  const resolve=createEngineCardResolver(deck,artwork);
  assert.equal(knownEngineArtKeys(deck,artwork).size,new Set(names).size);
  for(const card of artwork){const view=resolve(native(card.name));assert.equal(view.image,card.image);assert.equal(view.oracleText,'Native rules remain authoritative.');}
 }
});
test('saved custom printing wins when it has art; blank/whitespace art cannot overwrite fetched art',()=>{
 const fetched=record('Sol Ring','https://cards.scryfall.io/fetched.jpg');
 for(const image of ['', '   '])assert.equal(createEngineCardResolver({cards:[record('Sol Ring',image)]},[fetched])(native('Sol Ring')).image,fetched.image);
 const custom=record('Sol Ring','https://cards.scryfall.io/custom.jpg');assert.equal(createEngineCardResolver({cards:[custom]},[fetched])(native('Sol Ring')).image,custom.image);
});
test('face images and hidden-card privacy survive artwork hydration',()=>{
 const card={...record('Front // Back','https://cards.scryfall.io/front.jpg'),faces:[{name:'Back',image:'https://cards.scryfall.io/back.jpg'}]};
 const resolve=createEngineCardResolver({cards:[record(card.name)]},[card]);assert.equal(resolve(native('Back')).image,card.faces[0].image);
 for(const flag of ['faceDown','hideInfo'])assert.equal(resolve({...native(card.name),[flag]:true}).image,'');
});
test('tokens still require their exact engine profile key',()=>{
 const token={...record('Soldier','https://cards.scryfall.io/token.jpg'),engineArtKey:'token:exact-profile'};
 assert.deepEqual([...knownEngineArtKeys(undefined,[token])],['token:exact-profile']);
});
