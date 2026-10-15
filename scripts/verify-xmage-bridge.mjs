import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';

const output = new URL('../build/nexus/protocol-result.json', import.meta.url);
const checks = [];
const clients = [];
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const viewOf = message => message?.data?.gameView ?? message?.data;
const controlled = view => view?.players?.find(player => player.controlled);
const check = (name, condition, detail) => {
  assert.ok(condition, `${name}: ${JSON.stringify(detail ?? '')}`);
  checks.push({ name, passed: true, ...(detail === undefined ? {} : { detail }) });
  console.log(`PASS ${name}`);
};

async function client(username) {
  const socket = new WebSocket('ws://127.0.0.1:8787');
  const frames = [];
  const pending = new Map();
  let sequence = 0;
  const c = {
    username, socket, frames,
    call(action, args = {}) {
      const requestId = `${username}-${++sequence}`;
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => { pending.delete(requestId); reject(new Error(`${action} timed out`)); }, 20000);
        pending.set(requestId, message => { clearTimeout(timer); resolve(message); });
        socket.send(JSON.stringify({ requestId, action, args }));
      });
    },
    async wait(predicate, after = 0, milliseconds = 35000) {
      const deadline = Date.now()+milliseconds;
      while (Date.now()<deadline) {
        const found = frames.slice(after).find(predicate);
        if (found) return found;
        await sleep(75);
      }
      throw new Error(`${username}: event timeout; last=${JSON.stringify(frames.slice(-4))}`);
    },
  };
  socket.onmessage = event => {
    const message = JSON.parse(String(event.data));
    if (message.type === 'result' && pending.has(message.requestId)) {
      const done = pending.get(message.requestId);
      pending.delete(message.requestId);
      done(message);
    } else {
      frames.push(message);
    }
  };
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('WebSocket open timeout')), 10000);
    socket.onopen = () => { clearTimeout(timer); resolve(); };
    socket.onerror = () => { clearTimeout(timer); reject(new Error('WebSocket failed')); };
  });
  clients.push(c);
  const login = await c.call('connect', { host: '127.0.0.1', port: 17171, username, password: 'fixture' });
  check(`${username}: independent login`, login.ok, login.error);
  return c;
}

let failure;
try {
  const a = await client('Stage2Red');
  const b = await client('Stage2Blue');
  const info = await a.call('getServerInfo');
  check('bridge connected to our loopback server', info.ok && info.data?.connected && info.data?.host === '127.0.0.1', info.data);
  const types = await a.call('getPlayerTypes');
  check('native XMage AI advertised', types.ok && JSON.stringify(types.data).includes('COMPUTER_MAD'), types.data);
  const games = await a.call('getGameTypes');
  check('multiplayer Commander advertised', games.ok && JSON.stringify(games.data).includes('Commander'), games.data);
  const makeDeck = (cardName, cardNumber) => ({ name: `${cardName} fixture`, cards: [
    { cardName, cardNumber, setCode: 'LEA', amount: 60 }], sideboard: [] });
  const table = await a.call('createTable', { name: 'Private bridge check', gameType: 'Two Player Duel',
    deckType: 'Constructed - Freeform', winsNeeded: 1, playerTypes: ['HUMAN', 'HUMAN'],
    skipInitShuffling: true, skipStartingPlayerChoice: false });
  const tableId = table.data?.tableId ?? table.data?.table?.tableId;
  check('create private test table', table.ok && tableId, table.error);
  for (const [c, deck] of [[a, makeDeck('Mountain', '292')], [b, makeDeck('Island', '288')]]) {
    const joined = await c.call('joinTable', { tableId, playerName: c.username, playerType: 'HUMAN', skill: 1, deck });
    check(`${c.username}: join own seat`, joined.ok, joined.error);
  }
  const started = await a.call('startMatch', { tableId });
  check('start real server match', started.ok, started.error);
  const startup = await a.wait(m => m.method === 'START_GAME');
  const gameId = startup.objectId ?? startup.data?.gameId;
  check('real game ID received', Boolean(gameId));
  // START_GAME announces the game; each player must attach its game panel.
  // Attach both seats before awaiting GAME_INIT so neither can block startup.
  for (const c of [a, b]) {
    await c.wait(m => m.method === 'START_GAME');
    const attached = await c.call('joinGame', { gameId });
    check(`${c.username}: game panel attachment submitted`, attached.ok, attached.error);
  }
  for (const c of [a, b]) {
    const init = await c.wait(m => m.method === 'GAME_INIT');
    check(`${c.username}: seat-specific initial view`, viewOf(init)?.players?.length === 2);
  }
  // The server asks players sequentially, in engine order. Answer whichever
  // seat is asked next rather than waiting for Red before allowing Blue to act.
  // Choose Red through the real starting-player prompt: the skip flag's first
  // map entry is not guaranteed to be table-seat order.
  const kept = new Set();
  const answeredPrompts = new Set();
  const redPlayerId = startup.data?.playerId;
  check('red seat identity received from server', Boolean(redPlayerId));
  const openingDeadline = Date.now()+90000;
  while (kept.size < 2 && Date.now()<openingDeadline) {
    for (const c of [a, b]) {
      for (const m of [...c.frames]) {
        const key = `${c.username}:${m.seq ?? m.messageId}`;
        if (answeredPrompts.has(key)) continue;
        const question = String(m.data?.message ?? m.data?.question ?? '');
        if (m.method === 'GAME_TARGET' && /starting player/i.test(question)) {
          answeredPrompts.add(key);
          const answer = await c.call('sendPlayerUUID', { gameId, value: redPlayerId });
          check('red selected through real starting-player prompt', answer.ok);
        } else if (m.method === 'GAME_ASK' && /mulligan/i.test(question)) {
          answeredPrompts.add(key);
          check(`${c.username}: mulligan prompt received`, true);
          const answered = await c.call('sendPlayerBoolean', { gameId, value: false });
          check(`${c.username}: mulligan answer submitted`, answered.ok, answered.error);
          kept.add(c.username);
        }
      }
    }
    await sleep(75);
  }
  check('both players kept their opening hands', kept.size === 2);
  const priority = await a.wait(m => m.method === 'GAME_SELECT' && controlled(viewOf(m))?.hasPriority);
  const state = viewOf(priority);
  const hand = Object.values(state.myHand ?? {});
  check('own seven-card hand delivered', hand.length === 7 && hand.every(card => (card.name ?? card.displayName) === 'Mountain'));
  check('opponent hand faces absent from red view', !JSON.stringify(state).includes('Island'));
  const blueView = [...b.frames].reverse().map(viewOf).find(v => v?.myHand && Object.keys(v.myHand).length === 7);
  check('blue receives its own hand', Boolean(blueView) && Object.values(blueView.myHand).every(card => (card.name ?? card.displayName) === 'Island'));
  check('red hand faces absent from blue view', Boolean(blueView) && !JSON.stringify(blueView).includes('Mountain'));
  const outsider = await client('Stage2Guest');
  const guestCursor = outsider.frames.length;
  const acknowledged = await outsider.call('joinGame', { gameId });
  check('unseated join request receives transport response', typeof acknowledged.ok === 'boolean');
  await sleep(3000);
  const guestHasPlayerAccess = () => outsider.frames.slice(guestCursor).some(m =>
    ['GAME_INIT', 'GAME_UPDATE', 'GAME_UPDATE_AND_INFORM', 'GAME_SELECT', 'GAME_ASK'].includes(m.method)
    && m.objectId === gameId);
  check('unseated account receives no player state or prompts', !guestHasPlayerAccess());
  let current = priority;
  let landId;
  for (let step = 0; step<8; step++) {
    const game = viewOf(current);
    landId = Object.keys(game.canPlayObjects?.objects ?? {}).find(id => game.myHand?.[id]?.name === 'Mountain');
    if (landId) break;
    const cursor = a.frames.length;
    const pass = await a.call('sendPlayerBoolean', { gameId, value: false });
    check('priority answer accepted', pass.ok);
    current = await a.wait(m => m.method === 'GAME_SELECT' && controlled(viewOf(m))?.hasPriority, cursor);
  }
  check('land is legal through server prompt', Boolean(landId));
  const cursor = a.frames.length;
  const play = await a.call('sendPlayerUUID', { gameId, value: landId });
  check('land action accepted by engine', play.ok, play.error);
  const update = await a.wait(m => ['GAME_UPDATE', 'GAME_UPDATE_AND_INFORM', 'GAME_SELECT'].includes(m.method)
    && Object.values(controlled(viewOf(m))?.battlefield ?? {}).some(card => (card.name ?? card.displayName) === 'Mountain'), cursor);
  check('updated battlefield returned over bridge', Boolean(update));
  const observerCursor = b.frames.length;
  await b.wait(m => ['GAME_UPDATE', 'GAME_UPDATE_AND_INFORM'].includes(m.method)
    && JSON.stringify(viewOf(m)).includes('Mountain'), Math.max(0, observerCursor-5));
  check('other seat receives public battlefield update', true);
  check('unseated account stays isolated through a real game update', !guestHasPlayerAccess());
  const attached = await client('Stage2Red');
  const ownInfo = await attached.call('getServerInfo');
  check('same-account window reuses its session', ownInfo.ok && ownInfo.data?.sessionId === info.data?.sessionId);
  const replayCursor = attached.frames.length;
  const rejoin = await attached.call('joinGame', { gameId });
  check('rejoin game accepted', rejoin.ok);
  const replay = await attached.wait(m => ['GAME_INIT', 'GAME_UPDATE'].includes(m.method) && viewOf(m)?.players?.length === 2, replayCursor);
  check('current player view replayed after rejoin', Boolean(replay));
  const nativeTable = await outsider.call('createTable', { name: 'Native AI staging', gameType: 'Two Player Duel',
    deckType: 'Constructed - Freeform', playerTypes: ['HUMAN', 'COMPUTER_MAD'], winsNeeded: 1 });
  const nativeId = nativeTable.data?.tableId ?? nativeTable.data?.table?.tableId;
  check('native AI table can be created without SIM', nativeTable.ok && nativeId, nativeTable.error);
  const aiJoined = await outsider.call('joinTable', { tableId: nativeId, playerName: 'NativeAI',
    playerType: 'COMPUTER_MAD', skill: 1, deck: makeDeck('Island', '288') });
  check('native AI seat accepted by our server', aiJoined.ok, aiJoined.error);
  await outsider.call('removeTable', { tableId: nativeId });
  await a.call('quitMatch', { gameId });
} catch (error) {
  failure = String(error.stack ?? error);
  console.error(failure);
} finally {
  for (const c of clients) {
    try { await c.call('disconnect'); } catch {}
    c.socket.close();
  }
  await writeFile(output, JSON.stringify({ passed: !failure, checks, failure,
    scope: 'Private server/bridge protocol; no browser UI or four-seat Commander match' }, null, 2)+'\n');
}
process.exit(failure ? 1 : 0);
