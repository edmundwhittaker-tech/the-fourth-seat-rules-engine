import {build} from 'esbuild';
import postcss from 'postcss';
import tailwind from '@tailwindcss/postcss';
import {readFile,mkdir,writeFile,copyFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');process.chdir(root);
await mkdir('dist',{recursive:true});await mkdir('ui/source/lib',{recursive:true});await copyFile('ui/request-safety.ts','ui/source/lib/request-safety.ts');
await build({entryPoints:['ui/source/tools/desktop/entry.tsx'],bundle:true,external:['/tabletop/*'],outdir:'dist',entryNames:'app',minify:true,jsx:'automatic',define:{'process.env.NODE_ENV':'"production"'},loader:{'.woff2':'file','.svg':'file'},plugins:[{name:'tailwind',setup(b){b.onLoad({filter:/globals\.css$/},async args=>({contents:(await postcss([tailwind({base:path.join(root,'ui/source')})]).process(await readFile(args.path,'utf8'),{from:args.path})).css,loader:'css'}));}}]});
await build({entryPoints:['ui/card-api.ts'],bundle:true,platform:'node',format:'esm',outfile:'dist/card-api.mjs',minify:true});
await writeFile('dist/index.html','<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>The Fourth Seat — Desktop</title><link rel="stylesheet" href="/app.css"></head><body><div id="root"></div><script type="module" src="/app.js"></script></body></html>');
const assets=JSON.parse(await readFile('ui/assets.json','utf8'));const {createHash}=await import('node:crypto');
for(const asset of assets){const target=path.join(root,'dist',asset.path);await mkdir(path.dirname(target),{recursive:true});let data;try{data=await readFile(path.join('/workspace/sites/fourth-seat/public',asset.path));}catch{const response=await fetch('https://thefourthseat.net/'+asset.path);if(!response.ok)throw Error('Asset download failed: '+asset.path);data=Buffer.from(await response.arrayBuffer());}if(createHash('sha256').update(data).digest('hex')!==asset.sha256)throw Error('Asset checksum mismatch: '+asset.path);await writeFile(target,data);}
console.log('Built the shared Fourth Seat desktop client.');
