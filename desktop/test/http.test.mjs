import test from 'node:test';import assert from 'node:assert/strict';import {mkdtemp,writeFile,rm} from 'node:fs/promises';import os from 'node:os';import path from 'node:path';import {startDesktopHttp} from '../runtime/http.mjs';
test('local HTTP rejects foreign origins, missing sessions, Host rebinding and traversal; shutdown is explicit',async()=>{
 const dir=await mkdtemp(path.join(os.tmpdir(),'fourth-seat-http-'));await writeFile(path.join(dir,'index.html'),'local');let stopped=false;
 const service=await startDesktopHttp({port:17891,dist:dir,dataDir:dir,secret:'test',aiDeckPool:[],cardApi:{},status:()=>({ready:false}),shutdown:()=>{stopped=true;}});
 try{const base='http://127.0.0.1:17891';assert.equal((await fetch(base+'/api/desktop/decks')).status,403);assert.equal((await fetch(base,{headers:{Origin:'https://evil.invalid'}})).status,403);
 const page=await fetch(base);const cookie=page.headers.get('set-cookie').split(';')[0];assert.match(cookie,/fourthseat_local=/);assert.equal((await fetch(base+'/api/desktop/decks',{headers:{Cookie:cookie}})).status,200);
 assert.equal((await fetch(base+'/api/desktop/shutdown',{headers:{Cookie:cookie}})).status,404);assert.equal(stopped,false);
 assert.equal((await fetch(base+'/api/desktop/shutdown',{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json',Origin:'https://evil.invalid'},body:'{}'})).status,403);
 assert.equal((await fetch(base+'/api/desktop/shutdown',{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json'},body:'{}'})).status,200);await new Promise(r=>setTimeout(r,200));assert.equal(stopped,true);
 const http=await import('node:http');const hostStatus=await new Promise(resolve=>http.get(base,{headers:{Host:'evil.invalid'}},r=>{r.resume();resolve(r.statusCode);}));assert.equal(hostStatus,403);
 assert.equal((await fetch(base+'/%2e%2e%2fsecret')).status,403);
 }finally{await service.close();await rm(dir,{recursive:true,force:true});}
});
