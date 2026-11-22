import {spawn} from 'node:child_process';
import {readFile,mkdir,unlink,open,access} from 'node:fs/promises';
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
const children=[],startup=new AbortController();let stopping=false,phase='checking',message='Checking the local engine files…',gateway,web,lock,engineCleanup;
const status=()=>({ready:phase==='ready',phase,message});
function show(next,detail){phase=next;message=detail;console.log(detail);}
await mkdir(dataDir,{recursive:true});await mkdir(path.join(dataDir,'logs'),{recursive:true});
function browser(){if(process.env.FOURTH_SEAT_NO_BROWSER==='1')return;if(process.platform==='win32')spawn('rundll32.exe',['url.dll,FileProtocolHandler',origin],{stdio:'ignore'}).unref();else console.log('Open '+origin);}
try{const response=await fetch(origin+'/desktop/status',{signal:AbortSignal.timeout(1000)});const data=await response.json();if(data.application==='fourth-seat-desktop'){console.log('The Fourth Seat is already running.');browser();process.exit(0);}throw Error('Port 17890 is used by another program. Close that program and try again.');}catch(e){if(e.message.startsWith('Port'))throw e;}
const lockPath=path.join(dataDir,'running.lock');
try{lock=await open(lockPath,'wx');}catch(e){if(e.code!=='EEXIST')throw e;let pid;try{pid=Number(await readFile(lockPath,'utf8'));if(!Number.isInteger(pid)||pid<1)throw Error('Invalid stale lock');process.kill(pid,0);}catch{await unlink(lockPath);lock=await open(lockPath,'wx');}if(!lock)throw Error('The engine is already starting. Please wait for its launcher window.');}
await lock.writeFile(String(process.pid));
function stopEngine(){
  return engineCleanup??=(async()=>{
    await gateway?.close().catch(()=>{});
    for(const child of children.toReversed()){
      if(child.exitCode!==null||child.signalCode!==null||!child.pid)continue;
      child.kill();
      const timeout=AbortSignal.timeout(3000);
      await new Promise(resolve=>{
        const done=()=>{child.removeListener('exit',done);timeout.removeEventListener('abort',done);resolve();};
        child.once('exit',done);timeout.addEventListener('abort',done,{once:true});
      });
      if(child.exitCode===null&&child.signalCode===null)child.kill('SIGKILL');
    }
  })();
}
async function stop(code=0){
  if(stopping)return;
  stopping=true;startup.abort();show('stopping','Stopping the local engine…');
  await stopEngine();await web?.close().catch(()=>{});await lock?.close();await unlink(lockPath).catch(()=>{});process.exit(code);
}
function fail(error){
  if(stopping||phase==='failed')return;
  const detail=error instanceof Error?error.message:String(error);
  show('failed',detail);
  console.error('Startup/engine logs: '+path.join(dataDir,'logs'));
  startup.abort(error);
  if(web){void stopEngine();}
  else{void stop(1);}
}
for(const sig of ['SIGINT','SIGTERM'])process.on(sig,()=>void stop());
function launch(name,args,cwd){
  const log=createWriteStream(path.join(dataDir,'logs',name+'.log'));
  const child=spawn(java,args,{cwd,windowsHide:true,stdio:['ignore','pipe','pipe']});
  child.stdout.pipe(log,{end:false});child.stderr.pipe(log,{end:false});children.push(child);
  log.on('error',error=>fail(Error('Could not write '+name+'.log: '+error.code+'.')));
  child.once('error',error=>fail(Error('Could not start '+name+' ('+error.code+'). Extract the full ZIP and run the launcher from the extracted folder.')));
  child.once('close',(code,signal)=>{
    log.end();
    if(!stopping&&(name!=='prepare'||code!==0))fail(Error(name+' stopped ('+(signal??code)+'). Check '+name+'.log.'));
  });
  return child;
}
async function waitFor(check,label,timeout=480_000){
  const deadline=Date.now()+timeout;
  while(Date.now()<deadline){
    startup.signal.throwIfAborted();
    try{if(await check()){startup.signal.throwIfAborted();return;}}catch{startup.signal.throwIfAborted();}
    await new Promise(resolve=>setTimeout(resolve,750));
  }
  throw Error(label+' did not finish starting. Check the local engine logs.');
}
function prepareEngine(engine,runtime){
  show('preparing','Preparing the local card files on this computer. The first launch can take a few minutes.');
  const child=launch('prepare',['-cp',path.join(root,'runtime'),'PrepareEngine',path.join(engine,'mage-server.zip'),runtime,String(serverPort)],dataDir);
  const signal=AbortSignal.any([startup.signal,AbortSignal.timeout(300_000)]);
  return new Promise((resolve,reject)=>{
    const abort=()=>{child.removeListener('close',close);reject(Error('Engine file preparation did not finish. Check prepare.log.'));};
    const close=code=>{signal.removeEventListener('abort',abort);if(code===0)resolve();else reject(Error('Engine preparation failed. Check prepare.log.'));};
    child.once('close',close);signal.addEventListener('abort',abort,{once:true});
  });
}
const java=process.env.FOURTH_SEAT_JAVA??path.join(root,'jre','bin',process.platform==='win32'?'java.exe':'java');
try{
  const secret=randomBytes(32).toString('hex');
  web=await startDesktopHttp({port:webPort,dist:path.join(root,'dist'),dataDir,gatewayPort,secret,aiDeckPool,cardApi,status,shutdown:()=>void stop()});browser();
  for(const port of [serverPort,wsPort,httpPort,gatewayPort])await new Promise((resolve,reject)=>{const s=net.createServer();s.once('error',()=>reject(Error('Port '+port+' is already in use. Close the other engine or app using this port, then relaunch The Fourth Seat.')));s.listen(port,'127.0.0.1',()=>s.close(resolve));});
  const engine=path.join(root,'engine'),manifest=JSON.parse(await readFile(path.join(engine,'commander-ai-manifest.json'),'utf8'));
  for(const [file,hash] of Object.entries(manifest.files)){if(createHash('sha256').update(await readFile(path.join(engine,file))).digest('hex')!==hash)throw Error('Engine checksum failed: '+file+'. Extract a fresh download.');}

  const runtime=path.join(dataDir,'engine-'+manifest.files['mage-server.zip'].slice(0,16));
  try{await access(path.join(runtime,'.prepared'));}catch{await prepareEngine(engine,runtime);}
  show('server','Starting XMage, the local rules server…');
  const opens=['java.io','java.util','java.lang','java.lang.reflect','java.text'].map(n=>'--add-opens=java.base/'+n+'=ALL-UNNAMED');
  launch('server',['-Xmx1800m','-Djava.awt.headless=true','-Dh2.bindAddress=127.0.0.1',...opens,'-jar',path.join(runtime,'lib/mage-server-1.4.62.jar')],runtime);
  await waitFor(()=>new Promise(resolve=>{const socket=net.connect(serverPort,'127.0.0.1');socket.on('connect',()=>{socket.destroy();resolve(true);});socket.on('error',()=>resolve(false));socket.setTimeout(1000,()=>{socket.destroy();resolve(false);});}),'XMage (server.log)');
  show('cards','Loading the local card database…');
  const proxy=path.join(dataDir,'proxy');await mkdir(proxy,{recursive:true});
  launch('bridge',['-Xmx1200m','-Djava.awt.headless=true','-Dh2.bindAddress=127.0.0.1',...opens,'-jar',path.join(engine,'mage-proxy-1.4.62.jar'),'--host','127.0.0.1','--port',String(serverPort),'--bind','127.0.0.1','--wsPort',String(wsPort),'--httpPort',String(httpPort),'--sim','off','--restoreIds',path.join(dataDir,'restore-ids.json'),'--allowedOrigins','https://desktop-internal.invalid','--adminToken',randomBytes(32).toString('hex')],proxy);
  await waitFor(async()=>{const response=await fetch(`http://127.0.0.1:${httpPort}/ready`,{signal:AbortSignal.timeout(3000)});return response.ok&&(await response.json()).ready===true;},'Card database (bridge.log)');
  show('connecting','Connecting the table to the local rules engine…');
  gateway=await startGateway({secret,host:'127.0.0.1',port:gatewayPort,serverPort,origin,nexusUrl:`ws://127.0.0.1:${wsPort}`,readinessUrl:`http://127.0.0.1:${httpPort}/ready`,dataDir});
  startup.signal.throwIfAborted();show('ready','Local engine ready.');console.log('\nReady: '+origin+'\nKeep this window open while playing. Close it to stop all local services.\nLogs: '+path.join(dataDir,'logs'));
}catch(error){fail(error);}
