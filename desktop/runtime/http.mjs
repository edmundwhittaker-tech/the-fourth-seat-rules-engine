import http from 'node:http';
import {readFile,writeFile,rename,mkdir} from 'node:fs/promises';
import {randomBytes,randomUUID} from 'node:crypto';
import path from 'node:path';
export async function startDesktopHttp({port=17890,dist,dataDir,gatewayPort=18789,secret,aiDeckPool,cardApi,status,shutdown}){
  const origin=`http://127.0.0.1:${port}`,cookie=randomBytes(32).toString('hex'),file=path.join(dataDir,'decks.json');
  await mkdir(dataDir,{recursive:true});let saved=[];
  try{saved=JSON.parse(await readFile(file,'utf8'));if(!Array.isArray(saved))throw Error('Invalid deck library');}catch(e){if(e.code!=='ENOENT')throw e;}
  const card=(name,quantity=1)=>({id:name,name,quantity,manaCost:'',oracleText:'',typeLine:'Card',colors:[],colorIdentity:[],image:''});
  const builtins=aiDeckPool.map(entry=>{const commander=card(entry.deck.commanders[0].cardName);const cards=[commander,...entry.deck.cards.map(c=>card(c.cardName,c.amount))];return{id:entry.id,name:entry.deck.name,commander,cards,ownerId:'desktop',createdAt:0,updatedAt:0,cardCount:cards.reduce((s,c)=>s+c.quantity,0)};});
  let saving=Promise.resolve();
  const server=http.createServer(async(req,res)=>{
    const json=(data,status=200)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(data));};
    try{
      if(req.headers.host!==`127.0.0.1:${port}`)return json({error:'Use the launcher address.'},403);
      if(req.headers.origin&&req.headers.origin!==origin)return json({error:'Foreign browser origin rejected.'},403);
      const url=new URL(req.url,origin);
      if(url.pathname==='/desktop/status'&&req.method==='GET')return json({application:'fourth-seat-desktop',...status()});
      if(url.pathname.startsWith('/api/')){
        if(!String(req.headers.cookie??'').split(';').some(c=>c.trim()===`fourthseat_local=${cookie}`))return json({error:'Open the game from the launcher first.'},403);
        if(!['GET','POST'].includes(req.method))return json({error:'Method not allowed'},405);
        let text='';if(req.method==='POST'){if(!req.headers['content-type']?.startsWith('application/json'))return json({error:'JSON required'},415);let size=0;for await(const chunk of req){size+=chunk.length;if(size>2_000_000){json({error:'Request too large'},413);req.destroy();return;}text+=chunk.toString();}}
        let body={};if(text){try{body=JSON.parse(text);}catch{return json({error:'Invalid JSON'},400);}}
        if(url.pathname==='/api/desktop/shutdown'&&req.method==='POST'){json({stopped:true});setTimeout(shutdown,150);return;}
        if(url.pathname==='/api/desktop/decks'){
          if(req.method==='GET')return json({decks:[...builtins,...saved]});
          if(saved.length>=100)return json({error:'This preview supports up to 100 imported decks.'},400);
          const commander=body.cards?.find(c=>c.name===body.commander);
          try{cardApi.validateDeckCards(commander,body.cards);}catch(e){return json({error:e.message},400);}
          const total=body.cards.reduce((s,c)=>s+c.quantity,0);if(total!==100)return json({error:'Include exactly 100 cards, including one commander.'},400);
          const deck={id:randomUUID(),ownerId:'desktop',name:String(body.name??commander.name).slice(0,120),commander,cards:body.cards,cardCount:total,createdAt:Date.now(),updatedAt:Date.now()};
          saved.push(deck);saving=saving.then(async()=>{await writeFile(file+'.tmp',JSON.stringify(saved));await rename(file+'.tmp',file);});await saving;return json({deck});
        }
        if(url.pathname==='/api/engine'){
          if(req.method==='GET')return json({available:status().ready,message:status().message});
          if(!status().ready)return json({error:status().message},503);
          if(['create','join','changeDeck'].includes(body.operation)){const deck=[...builtins,...saved].find(d=>d.id===body.deckId);if(!deck)return json({error:'Choose a saved deck.'},400);body.deck=cardApi.deckForEngine(deck);}
          if(body.operation==='create')body.humanSeats=1;
          if(body.operation==='configure'&&body.kind==='human')return json({error:'The desktop preview supports one human and three AI opponents.'},400);
          const response=await fetch(`http://127.0.0.1:${gatewayPort}/api`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+secret},body:JSON.stringify(body),signal:AbortSignal.timeout(90_000)});
          const result=await response.json();if(body.operation==='session'&&response.ok)result.websocketUrl=`ws://127.0.0.1:${gatewayPort}/play`;return json(result,response.status);
        }
        const handler=({'/api/import':req.method==='POST'&&cardApi.importCards,'/api/token-search':req.method==='GET'&&cardApi.tokens,'/api/card-image':req.method==='GET'&&cardApi.image})[url.pathname];
        if(handler){const response=await handler(new Request(url,{method:req.method,headers:{'Content-Type':'application/json'},...(req.method==='POST'?{body:text}:{})}));res.writeHead(response.status,Object.fromEntries(response.headers));res.end(Buffer.from(await response.arrayBuffer()));return;}
        return json({error:'Unknown local API'},404);
      }
      if(req.method!=='GET'&&req.method!=='HEAD')return json({error:'Method not allowed'},405);
      const name=decodeURIComponent(url.pathname==='/'?'/index.html':url.pathname),target=path.resolve(dist,'.'+name);
      if(!target.startsWith(path.resolve(dist)+path.sep))return json({error:'Invalid path'},403);
      let data;try{data=await readFile(target);}catch{return json({error:'Not found'},404);}
      const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.webp':'image/webp','.jpg':'image/jpeg','.png':'image/png','.woff2':'font/woff2'};
      res.writeHead(200,{'Content-Type':mime[path.extname(target)]??'application/octet-stream','X-Content-Type-Options':'nosniff','Cache-Control':'no-cache','Cross-Origin-Resource-Policy':'same-origin',...(url.pathname==='/'?{'Set-Cookie':`fourthseat_local=${cookie}; HttpOnly; SameSite=Strict; Path=/`}:{})});res.end(req.method==='HEAD'?undefined:data);
    }catch(error){console.error('Local request:',error.message);if(!res.headersSent)json({error:'Local request failed. Check the launcher log.'},500);else res.end();}
  });
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(port,'127.0.0.1',resolve);});
  return{server,close:async()=>{await saving;server.closeIdleConnections();await new Promise(resolve=>server.close(resolve));}};
}
