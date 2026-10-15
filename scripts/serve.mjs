import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { resolve, sep } from "node:path";
const root = resolve("docs");
const contentTypes = { ".html": "text/html", ".css": "text/css", ".js": "text/javascript", ".json": "application/json" };
createServer(async (req, res) => {
  try {
    const path = decodeURI((req.url || "/").split("?")[0]);
    const file = resolve(root, "." + (path === "/" ? "/index.html" : path));
    if (file !== root && !file.startsWith(root + sep)) { res.writeHead(403).end(); return; }
    if (!(await stat(file)).isFile()) { res.writeHead(404).end(); return; }
    const ext = file.slice(file.lastIndexOf("."));
    res.writeHead(200, { "Content-Type": contentTypes[ext] || "application/octet-stream" });
    res.end(await readFile(file));
  } catch { res.writeHead(404).end("Not found"); }
}).listen(8080, () => { console.log("Playground: http://localhost:8080"); });
