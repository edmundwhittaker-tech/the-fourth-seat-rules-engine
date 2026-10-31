import {spawn} from 'node:child_process';
import {readFile,mkdir,writeFile,unlink,open,access} from 'node:fs/promises';
import {createWriteStream} from 'node:fs';
import {createHash,randomBytes} from 'node:crypto';
import path from 'node:path';
import os from 'node:os';
import net from 'node:net';
import {fileURLToPath} from 'node:url';
import {startDesktopHttp} from './http.mjs';
import {startGateway} from '../gateway/server.mjs';
import {aiDeckPool} from '../gateway/ai-decks.mjs';
import * as cardApi from '../dist/card-api.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const dataDir=process.env.FOURTH_SEAT_DATA_DIR??path.join(process.env.LOCALAPPDATA??path.join(os.homedir(),'.local','share'),'TheFourthSeat');
const webPort=17890,serverPort=17771,wsPort=18787,httpPort=18788,gatewayPort=18789,origin=`http://127.0.0.1:${webPort}`;
const children=[];let stopping=false,ready=false,message='Starting your local rules engine…',gateway,web,lock;
await mkdir(dataDir,{recursive:true});await mkdir(path.join(dataDir,'logs'),{recursive:true});
function browser(){if(process.env.FOURTH_SEAT_NO_BROWSER==='1')return;if(process.platform==='win32')spawn('rundll32.exe',['url.dll,FileProtocolHandler',origin],{stdio:'ignore'}).unref();else console.log('Open '+origin);}
try{const response=await fetch(origin+'/desktop/status',{signal:AbortSignal.timeout(1000)});const data=await response.json();if(data.application==='fourth-seat-desktop'){console.log('The Fourth Seat is already running.');browser();process.exit(0);}throw Error('Port 17890 is used by another program. Close that program and try again.');}catch(e){if(e.message.startsWith('Port'))throw e;}
const lockPath=path.join(dataDir,'running.lock');
try{lock=await open(lockPath,'wx');}catch(e){if(e.code!=='EEXIST')throw e;let pid;try{pid=Number(await readFile(lockPath,'utf8'));if(!Number.isInteger(pid)||pid<1)throw Error('Invalid stale lock');process.kill(pid,0);}catch{await unlink(lockPath);lock=await open(lockPath,'wx');}if(!lock)throw Error('The engine is already starting. Please wait for its launcher window.');}
await lock.writeFile(String(process.pid));
async function stop(code=0){if(stopping)return;stopping=true;ready=false;console.log('Stopping the engine…');await gateway?.close().catch(()=>{});await web?.close().catch(()=>{});for(const child of children.toReversed()){if(child.exitCode===null){child.kill();await Promise.race([new Promise(r=>child.once('exit',r)),new Promise(r=>setTimeout(r,3000))]);if(child.exitCode===null)child.kill('SIGKILL');}}await lock?.close();await unlink(lockPath).catch(()=>{});process.exit(code);}
for(const sig of ['SIGINT','SIGTERM'])process.on(sig,()=>void stop());
function launch(name,args,cwd){const log=createWriteStream(path.join(dataDir,'logs',name+'.log'));const child=spawn(java,args,{cwd,windowsHide:true,stdio:['ignore','pipe','pipe']});child.stdout.pipe(log);child.stderr.pipe(log);children.push(child);child.on('error',e=>{console.error(name+': '+e.message);void stop(1);});child.on('exit',code=>{if(!stopping&&name!=='prepare'){console.error(name+' stopped ('+code+'). See '+path.join(dataDir,'logs',name+'.log'));void stop(1);}});return child;}
async function waitFor(check,label,timeout=480000){const deadline=Date.now()+timeout;while(Date.now()<deadline&&!stopping){try{if(await check())return;}catch{}await new Promise(r=>setTimeout(r,1000));}throw Error(label+' did not become ready. See '+path.join(dataDir,'logs'));}
const java=process.env.FOURTH_SEAT_JAVA??path.join(root,'jre','bin',process.platform==='win32'?'java.exe':'java');
try{
  for(const port of [serverPort,wsPort,httpPort,gatewayPort])await new Promise((resolve,reject)=>{const s=net.createServer();s.once('error',()=>reject(Error('Port '+port+' is already in use. Close the other engine first.')));s.listen(port,'127.0.0.1',()=>s.close(resolve));});
  const engine=path.join(root,'engine'),manifest=JSON.parse(await readFile(path.join(engine,'commander-ai-manifest.json'),'utf8'));
  for(const [file,hash] of Object.entries(manifest.files)){if(createHash('sha256').update(await readFile(path.join(engine,file))).digest('hex')!==hash)throw Error('Engine checksum failed: '+file+'. Extract a fresh download.');}
  const secret=randomBytes(32).toString('hex');
  web=await startDesktopHttp({port:webPort,dist:path.join(root,'dist'),dataDir,gatewayPort,secret,aiDeckPool,cardApi,status:()=>({ready,message}),shutdown:()=>void stop()});browser();
  const runtime=path.join(dataDir,'engine-'+manifest.files['mage-server.zip'].slice(0,16));
  try{await access(path.join(runtime,'.prepared'));}catch{console.log('Preparing the engine (first launch only)…');const child=launch('prepare',['-cp',path.join(root,'runtime'),'PrepareEngine',path.join(engine,'mage-server.zip'),runtime,String(serverPort)],dataDir);await new Promise((resolve,reject)=>child.once('exit',code=>code===0?resolve():reject(Error('Engine preparation failed. See prepare.log.'))));}
  const opens=['java.io','java.util','java.lang','java.lang.reflect','java.text'].map(n=>'--add-opens=java.base/'+n+'=ALL-UNNAMED');
  launch('server',['-Xmx1800m','-Djava.awt.headless=true','-Dh2.bindAddress=127.0.0.1',...opens,'-jar',path.join(runtime,'lib/mage-server-1.4.62.jar')],runtime);
  await waitFor(()=>new Promise(resolve=>{const socket=net.connect(serverPort,'127.0.0.1');socket.on('connect',()=>{socket.destroy();resolve(true);});socket.on('error',()=>resolve(false));socket.setTimeout(1000,()=>{socket.destroy();resolve(false);});}),'XMage');
  const proxy=path.join(dataDir,'proxy');await mkdir(proxy,{recursive:true});
  launch('bridge',['-Xmx1200m','-Djava.awt.headless=true','-Dh2.bindAddress=127.0.0.1',...opens,'-jar',path.join(engine,'mage-proxy-1.4.62.jar'),'--host','127.0.0.1','--port',String(serverPort),'--bind','127.0.0.1','--wsPort',String(wsPort),'--httpPort',String(httpPort),'--sim','off','--restoreIds',path.join(dataDir,'restore-ids.json'),'--allowedOrigins','https://desktop-internal.invalid','--adminToken',randomBytes(32).toString('hex')],proxy);
  await waitFor(async()=>{const response=await fetch(`http://127.0.0.1:${httpPort}/ready`,{signal:AbortSignal.timeout(3000)});return response.ok&&(await response.json()).ready===true;},'Card database');
  gateway=await startGateway({secret,host:'127.0.0.1',port:gatewayPort,serverPort,origin,nexusUrl:`ws://127.0.0.1:${wsPort}`,readinessUrl:`http://127.0.0.1:${httpPort}/ready`,dataDir});
  ready=true;message='Local engine ready.';console.log('\nReady: '+origin+'\nKeep this window open while playing. Close it to stop all local services.\nLogs: '+path.join(dataDir,'logs'));
}catch(error){console.error(error.message);await stop(1);}
