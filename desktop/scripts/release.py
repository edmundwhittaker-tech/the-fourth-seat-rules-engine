from pathlib import Path
import subprocess,zipfile,hashlib,json,urllib.request,shutil
root=Path(__file__).resolve().parents[2];out=root/'build/The Fourth Seat';release=root/'build/release';release.mkdir(parents=True,exist_ok=True)
licenses=out/'licenses';licenses.mkdir(exist_ok=True)
for package in ['react','react-dom','tailwindcss']:
 shutil.copy(root/'desktop/node_modules'/package/'LICENSE',licenses/(package+'-LICENSE.txt'))
# Exact source archives: server fork, bridge, and this complete integration tree.
for name,commit in [('xmage-server-source','825d1205acca114825f235c6993f1e5a6a6db9fa'),('xmage-bridge-source','850411ec4db71ea7c10f4c3869a63084760057c2')]:
 target=release/(name+'.tar.gz')
 if not target.exists():urllib.request.urlretrieve('https://api.github.com/repos/Adamychen/xmage-nexus/tarball/'+commit,target)
subprocess.run(['git','archive','--format=zip','--prefix=fourth-seat-source/','-o',str(release/'Fourth-Seat-Desktop-Source.zip'),'HEAD'],cwd=root,check=True)
with zipfile.ZipFile(release/'The-Fourth-Seat-Windows-x64.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(out.rglob('*')):
  if p.is_file():z.write(p,p.relative_to(out.parent))
# Keep the verified paired engine available after the original Actions artifact expires.
shutil.copy(root/'build/desktop-input/verified-engine.zip',release/'verified-engine-532e6e20.zip')
shutil.copy(out/'smoke-result.json',release/'windows-smoke-result.json')
(release/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(release.iterdir()) if p.is_file() and p.name!='SHA256SUMS.txt'))
