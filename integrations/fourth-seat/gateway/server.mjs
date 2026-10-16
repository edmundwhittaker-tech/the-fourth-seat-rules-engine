import http from 'node:http';
import { randomBytes, createHmac } from 'node:crypto';
import { readFile, mkdir, writeFile, rename } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import WebSocket, { WebSocketServer } from 'ws';
import { validateCommanderDeck } from './deck-validation.mjs';
import { chooseAIDecks } from './ai-decks.mjs';
import { Problem, equal, identity, signTicket, readTicket, authorizeAction, publicRoom } from './policy.mjs';

export async function startGateway(options = {}) {
  const secret = options.secret ?? process.env.ENGINE_APP_SECRET;
  if (!secret || secret.length < 32) throw new Error('ENGINE_APP_SECRET must contain at least 32 random characters.');
  const origin = options.origin ?? process.env.SITE_ORIGIN ?? 'https://thefourthseat.net';
  const nexusUrl = options.nexusUrl ?? 'ws://127.0.0.1:8787';
  const readinessUrl = options.readinessUrl ?? 'http://127.0.0.1:8788/ready';
  const dataDir = options.dataDir ?? process.env.DATA_DIR ?? '/data';
  await mkdir(dataDir, { recursive: true });
  const file = path.join(dataDir, 'rooms.json');
  let saved;
  try { saved = JSON.parse(await readFile(file, 'utf8')); } catch (error) { if (error.code !== 'ENOENT') throw error; saved = []; }
  const rooms = new Map(saved.filter(r => r.createdAt > Date.now() - 86400_000).map(r => [r.code, { ...r, status: 'expired' }]));
  const clients = new Map();
  const locks = new Map();
  const validatedAIDecks = new Map();
  async function validatedAI(client, entry) {
    if (!validatedAIDecks.has(entry.id)) {
      const pending = validateCommanderDeck(client, entry.deck).catch(error => { validatedAIDecks.delete(entry.id); throw error; });
      validatedAIDecks.set(entry.id, pending);
    }
    return validatedAIDecks.get(entry.id);
  }
  let saveChain = Promise.resolve();
  const persist = () => saveChain = saveChain.then(async () => {
    const temporary = file + '.tmp';
    await writeFile(temporary, JSON.stringify([...rooms.values()]), { mode: 0o600 });
    await rename(temporary, file);
  });
  async function locked(key, operation) {
    const previous = locks.get(key) ?? Promise.resolve();
    const current = previous.catch(() => {}).then(operation);
    locks.set(key, current);
    try { return await current; } finally { if (locks.get(key) === current) locks.delete(key); }
  }
  function roomFor(user) { return [...rooms.values()].find(r => r.status !== 'expired' && r.players.some(p => p.user === user)); }
  async function connection(user) {
    const existing = clients.get(user);
    if (existing) { existing.touched = Date.now(); await existing.ready; return existing; }
    if (clients.size >= 32) throw new Problem('The engine is at capacity. Please try again shortly.', 503);
    const client = { socket: new WebSocket(nexusUrl, { maxPayload: 8 * 1024 * 1024 }), pending: new Map(), browsers: new Set(), counter: 0, touched: Date.now(), gameId: null };
    clients.set(user, client);
    client.call = (action, args = {}) => new Promise((resolve, reject) => {
      const requestId = 'gateway-' + ++client.counter;
      const timer = setTimeout(() => { client.pending.delete(requestId); reject(new Problem('The rules service did not respond. Reconnect and try again.', 504)); }, 35_000);
      client.pending.set(requestId, m => { clearTimeout(timer); m.ok && m.data !== false ? resolve(m.data) : reject(new Problem(m.error ?? 'The rules engine rejected that request.', 422)); });
      client.socket.send(JSON.stringify({ requestId, action, args }));
    });
    client.socket.on('message', raw => {
      let message;
      try { message = JSON.parse(String(raw)); } catch { return; }
      const pending = client.pending.get(message.requestId);
      if (pending) { client.pending.delete(message.requestId); pending(message); return; }
      if (message.type === 'event' && message.method === 'START_GAME') {
        client.gameId = message.data?.gameId ?? message.objectId;
        const room = roomFor(user);
        if (room) { room.gameId = client.gameId; room.status = 'game'; void persist().catch(console.error); }
        void client.call('joinGame', { gameId: client.gameId }).catch(error => broadcast(client, { type: 'error', error: error.message }));
      }
      if (message.type === 'event' && message.method === 'GAME_OVER') {
        const room = roomFor(user);
        if (room) { room.status = 'finished'; void persist().catch(console.error); }
      }
      if (['event', 'error', 'serverLink', 'disconnected'].includes(message.type)) {
        if (message.type === 'event' && !/^(GAME_|START_GAME|END_GAME_INFO|CHATMESSAGE)/.test(message.method ?? '')) return;
        if (message.method === 'CHATMESSAGE' && message.data?.messageType !== 'GAME') return;
        broadcast(client, message);
      }
    });
    client.ready = new Promise((resolve, reject) => {
      client.socket.once('error', reject);
      client.socket.once('open', async () => {
        try {
          await client.call('connect', { host: '127.0.0.1', port: 17171, username: user,
            password: createHmac('sha256', secret).update('password:' + user).digest('hex') });
          resolve();
        } catch (error) { reject(error); client.socket.close(); }
      });
    });
    client.socket.on('close', () => {
      clients.delete(user);
      for (const receive of client.pending.values()) receive({ ok: false, error: 'Engine connection closed.' });
      client.pending.clear();
      for (const browser of client.browsers) browser.close(1012, 'Reconnect to the engine.');
    });
    await client.ready;
    return client;
  }
  function broadcast(client, message) {
    const json = JSON.stringify(message);
    for (const socket of client.browsers) if (socket.readyState === WebSocket.OPEN) socket.send(json);
  }
  async function operate(body) {
    const user = identity(secret, body.browserKey);
    if (body.operation === 'session') {
      return { ticket: signTicket(secret, user), user, websocketPath: '/play', room: roomFor(user) ? publicRoom(roomFor(user), user) : null };
    }
    const client = await connection(user);
    return locked(body.code ?? user, async () => {
      const name = String(body.displayName ?? 'Player').trim().slice(0, 24) || 'Player';
      if (body.operation === 'create') {
        if (roomFor(user)) throw new Problem('Return to your current engine table or leave it first.', 409);
        if (rooms.size >= 50) throw new Problem('The engine has reached its table limit.', 503);
        const humanSeats = Number(body.humanSeats);
        if (!Number.isInteger(humanSeats) || humanSeats < 1 || humanSeats > 4) throw new Problem('Choose one to four human seats.');
        const deck = await validateCommanderDeck(client, body.deck);
        const selectedAI = chooseAIDecks(4 - humanSeats, deck.commanders[0].cardName);
        // Validate every selected list before creating a table; never silently change it.
        const aiDecks = [];
        for (const entry of selectedAI) aiDecks.push(await validatedAI(client, entry));
        let code;
        do { code = randomBytes(8).toString('hex').slice(0, 5).toUpperCase(); } while (rooms.has(code));
        const password = randomBytes(24).toString('hex');
        const created = await client.call('createTable', { name: 'Fourth Seat ' + code, password,
          gameType: 'Commander Free For All', deckType: 'Variant Magic - Commander', winsNeeded: 1,
          playerTypes: [...Array(humanSeats).fill('HUMAN'), ...Array(4 - humanSeats).fill('COMPUTER_MAD')],
          attackOption: 'MULTIPLE', range: 'ALL', skipInitShuffling: false, skipStartingPlayerChoice: false });
        const room = { code, host: user, tableId: created.tableId, password, status: 'lobby', humanSeats, createdAt: Date.now(), players: [] };
        if (!room.tableId) throw new Problem('The engine did not create a table.', 502);
        try {
          await client.call('joinTable', { tableId: room.tableId, playerName: user, playerType: 'HUMAN', skill: 1, deck, password });
          room.players.push({ user, name, kind: 'human', commander: deck.commanders[0].cardName, deckName: deck.name });
          for (let i = humanSeats; i < 4; i++) {
            const aiName = 'AI' + code + (i + 1);
            const aiDeck = aiDecks[i - humanSeats];
            const entry = selectedAI[i - humanSeats];
            await client.call('joinTable', { tableId: room.tableId, playerName: aiName, playerType: 'COMPUTER_MAD', skill: 1, deck: aiDeck, password });
            room.players.push({ user: aiName, name: 'AI ' + (i + 1), kind: 'ai', commander: aiDeck.commanders[0].cardName, deckName: aiDeck.name, aiDeckId: entry.id });
          }
          rooms.set(code, room); await persist();
        } catch (error) { await client.call('removeTable', { tableId: room.tableId }).catch(() => {}); throw error; }
        return { room: publicRoom(room, user) };
      }
      const room = rooms.get(String(body.code ?? '').toUpperCase());
      if (!room) throw new Problem('That engine room was not found. Check the code.', 404);
      if (room.status === 'expired') throw new Problem('The service restarted. This table has ended; create a new engine game.', 410);
      if (body.operation === 'join') {
        if (room.players.some(p => p.user === user)) return { room: publicRoom(room, user) };
        if (roomFor(user)) throw new Problem('Leave your current engine table first.', 409);
        if (room.status !== 'lobby' || room.players.filter(p => p.kind === 'human').length >= room.humanSeats) throw new Problem('This engine table has no open human seats.', 409);
        const deck = await validateCommanderDeck(client, body.deck);
        await client.call('joinTable', { tableId: room.tableId, playerName: user, playerType: 'HUMAN', skill: 1, deck, password: room.password });
        room.players.push({ user, name, kind: 'human', commander: deck.commanders[0].cardName, deckName: deck.name });
        await persist(); return { room: publicRoom(room, user) };
      }
      const view = publicRoom(room, user);
      if (body.operation === 'room') return { room: view };
      if (body.operation === 'start') {
        if (room.host !== user) throw new Problem('Only the host can start this game.', 403);
        if (room.status !== 'lobby' || room.players.length !== 4) throw new Problem('Wait for every human seat to be filled.', 409);
        room.status = 'starting';
        try { await client.call('startMatch', { tableId: room.tableId }); } catch (error) { room.status = 'lobby'; throw error; }
        await persist(); return { room: publicRoom(room, user) };
      }
      if (body.operation === 'leave') {
        if (room.status !== 'lobby' && room.status !== 'finished' && !room.players.find(p => p.user === user)?.conceded) throw new Problem('Concede before leaving an active engine game.', 409);
        if (room.host === user && (room.status === 'lobby' || room.status === 'finished')) {
          if (room.status === 'lobby') await client.call('removeTable', { tableId: room.tableId });
          rooms.delete(room.code);
        } else {
          if (room.status === 'lobby') await client.call('leaveTable', { tableId: room.tableId });
          room.players = room.players.filter(p => p.user !== user);
        }
        await persist(); return { left: true };
      }
      throw new Problem('Unknown engine operation.');
    });
  }
  const server = http.createServer(async (request, response) => {
    response.setHeader('Cache-Control', 'no-store');
    response.setHeader('X-Content-Type-Options', 'nosniff');
    try {
      if (request.url === '/health') {
        let ready = false;
        try { ready = (await (await fetch(readinessUrl, { signal: AbortSignal.timeout(3000) })).json()).ready === true; } catch {}
        response.writeHead(ready ? 200 : 503, { 'Content-Type': 'application/json' }); response.end(JSON.stringify({ ready })); return;
      }
      if (request.method !== 'POST' || request.url !== '/api') throw new Problem('Not found.', 404);
      if (!equal(request.headers.authorization, 'Bearer ' + secret)) throw new Problem('Unauthorized.', 401);
      let raw = '';
      for await (const chunk of request) { raw += chunk; if (Buffer.byteLength(raw) > 256_000) throw new Problem('Request too large.', 413); }
      let body;
      try { body = JSON.parse(raw); } catch { throw new Problem('Invalid JSON.'); }
      const result = await operate(body);
      response.writeHead(200, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(result));
    } catch (error) {
      response.writeHead(error.status ?? 500, { 'Content-Type': 'application/json' }); response.end(JSON.stringify({ error: error.status ? error.message : 'The engine request failed. Please reconnect.' }));
      if (!error.status) console.error('Engine request failed:', error.message);
    }
  });
  const wss = new WebSocketServer({ noServer: true, maxPayload: 16_384 });
  server.on('upgrade', (request, socket, head) => {
    if (request.url !== '/play' || request.headers.origin !== origin) { socket.end('HTTP/1.1 403 Forbidden\r\n\r\n'); return; }
    wss.handleUpgrade(request, socket, head, browser => {
      let user, client;
      let chain = Promise.resolve();
      const authTimer = setTimeout(() => browser.close(1008, 'Session required.'), 10_000);
      browser.on('message', raw => {
        chain = chain.then(async () => {
          let message;
          try { message = JSON.parse(String(raw)); } catch { throw new Problem('Invalid message.'); }
          if (!user) {
            user = readTicket(secret, message.ticket);
            clearTimeout(authTimer);
            client = await connection(user); client.browsers.add(browser);
            browser.send(JSON.stringify({ type: 'ready', user }));
            const room = roomFor(user);
            if (room?.gameId) { client.gameId = room.gameId; await client.call('joinGame', { gameId: room.gameId }); }
            return;
          }
          if (message.action === 'ping') { client.touched = Date.now(); browser.send(JSON.stringify({ type: 'pong' })); return; }
          const room = roomFor(user);
          const allowed = authorizeAction(message, room?.gameId);
          client.touched = Date.now();
          try {
            const data = await client.call(allowed.action, allowed.args);
            if (allowed.action === 'sendPlayerAction' && allowed.args.action === 'CONCEDE') {
              const player = room.players.find(p => p.user === user); if (player) player.conceded = true;
              await persist();
            }
            browser.send(JSON.stringify({ type: 'result', requestId: message.requestId, ok: true, data }));
          } catch (error) { browser.send(JSON.stringify({ type: 'result', requestId: message.requestId, ok: false, error: error.message })); }
        }).catch(error => {
          if (browser.readyState === WebSocket.OPEN) browser.send(JSON.stringify({ type: 'error', error: error.message }));
          if (!client) browser.close(1008, 'Invalid session.');
        });
      });
      browser.on('close', () => { clearTimeout(authTimer); client?.browsers.delete(browser); });
      browser.on('error', () => {});
    });
  });
  const cleanup = setInterval(() => {
    for (const client of clients.values()) {
      if (client.browsers.size === 0 && client.touched < Date.now() - 180_000) client.socket.close();
    }
    let changed = false;
    for (const [code, room] of rooms) if (room.createdAt < Date.now() - 86400_000) { rooms.delete(code); changed = true; }
    if (changed) void persist().catch(console.error);
  }, 30_000);
  cleanup.unref();
  await new Promise(resolve => server.listen(options.port ?? Number(process.env.PORT ?? 8080), '0.0.0.0', resolve));
  return { server, port: server.address().port, close: async () => {
    clearInterval(cleanup); for (const socket of wss.clients) socket.terminate();
    for (const client of clients.values()) client.socket.terminate();
    await saveChain; await new Promise(resolve => server.close(resolve));
  } };
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const gateway = await startGateway();
  console.log('Fourth Seat engine gateway listening on port ' + gateway.port);
  for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => void gateway.close().then(() => process.exit(0)));
}
