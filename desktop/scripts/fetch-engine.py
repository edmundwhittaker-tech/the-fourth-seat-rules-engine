from pathlib import Path
import os,urllib.request,hashlib,zipfile
root=Path(__file__).resolve().parents[2];cache=root/'build/desktop-input';cache.mkdir(parents=True,exist_ok=True);target=cache/'verified-engine.zip'
# Prefer permanent release storage on subsequent builds. The exact checksum is unchanged.
url='https://github.com/edmundwhittaker-tech/the-fourth-seat-rules-engine/releases/download/desktop-v0.1.0-preview/verified-engine-532e6e20.zip'
try:urllib.request.urlretrieve(url,target)
except Exception:
 req=urllib.request.Request('https://api.github.com/repos/edmundwhittaker-tech/the-fourth-seat-rules-engine/actions/artifacts/12542808118/zip',headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json'})
 with urllib.request.urlopen(req) as r:target.write_bytes(r.read())
if hashlib.sha256(target.read_bytes()).hexdigest()!='44b7ede12a1dfc825023b27ad2550babfa6ad905189096efc6d21419bc09b59d':raise ValueError('Engine artifact checksum mismatch')
with zipfile.ZipFile(target) as z:z.extractall(cache/'engine')
