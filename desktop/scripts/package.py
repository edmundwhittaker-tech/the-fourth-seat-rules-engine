"""Assemble a portable Windows package from checksum-pinned inputs."""
from pathlib import Path
import sys,os,json,hashlib,shutil,urllib.request,zipfile,subprocess
root=Path(__file__).resolve().parents[2];out=root/'build'/'The Fourth Seat';cache=root/'build'/'desktop-input';cache.mkdir(parents=True,exist_ok=True)
def get(name,url,sha):
 p=cache/name
 if not p.exists():urllib.request.urlretrieve(url,p)
 if hashlib.sha256(p.read_bytes()).hexdigest()!=sha:raise ValueError('Checksum mismatch: '+name)
 return p
node=get('node-win.zip','https://nodejs.org/dist/v24.19.0/node-v24.19.0-win-x64.zip','57f71ab3652e797d84acddc79c81cc9ff1c6ddb2a1974cdb83f00fee9bff4c73')
jre=get('jre-win.zip','https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20%2B8/OpenJDK17U-jre_x64_windows_hotspot_17.0.20_8.zip','7fe2324cc5901a89aaa0e8dc232075c59f2aca5270c533bdb1b861a5274af834')
engine=cache/'engine'
sys.path.insert(0,str(root/'integrations/fourth-seat/hosting'));from online_ai import verify_pair
verify_pair(engine)
if (engine/'integration-commit.txt').read_text().strip()!='532e6e20c2e6ea79ccdf09aec69c0daeddd4383d':raise ValueError('Unexpected engine source')
if out.exists():shutil.rmtree(out)
out.mkdir(parents=True)
for archive,dest in [(node,'node'),(jre,'jre')]:
 temp=cache/(dest+'-extracted');shutil.rmtree(temp,ignore_errors=True);temp.mkdir()
 with zipfile.ZipFile(archive) as z:z.extractall(temp)
 folders=list(temp.iterdir());assert len(folders)==1
 shutil.copytree(folders[0],out/dest)
for src,dest in [('desktop/runtime','runtime'),('desktop/dist','dist'),('integrations/fourth-seat/gateway','gateway')]:shutil.copytree(root/src,out/dest,ignore=shutil.ignore_patterns('node_modules','*.java'))
shutil.copytree(engine,out/'engine');shutil.copytree(root/'desktop/node_modules/ws',out/'node_modules/ws')
shutil.copy(root/'desktop/README.md',out/'READ ME FIRST.txt')
shutil.copy(root/'desktop/THIRD-PARTY-NOTICES.md',out/'THIRD-PARTY-NOTICES.txt')
subprocess.run(['javac','--release','17','-d',str(out/'runtime'),str(root/'desktop/runtime/PrepareEngine.java')],check=True)
csc=Path(os.environ['WINDIR'])/'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
subprocess.run([str(csc),'/nologo','/target:exe','/platform:x64','/out:'+str(out/'The Fourth Seat.exe'),str(root/'desktop/launcher/Launcher.cs')],check=True)
print(out)
