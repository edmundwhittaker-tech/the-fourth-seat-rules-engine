import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const root = process.cwd();
const out = path.join(root, 'build/nexus/browser');
await mkdir(out, { recursive: true });
const { chromium } = await import(pathToFileURL(path.join(root,
  '.upstream/nexus/web/node_modules/playwright/index.mjs')).href);
const checks = [], journal = [], pageErrors = [], sent = [];
const snapshots = {};
const prompts = new Set(['GAME_SELECT', 'GAME_ASK', 'GAME_TARGET', 'GAME_PLAY_MANA',
  'GAME_PLAY_XMANA', 'GAME_CHOOSE_ABILITY', 'GAME_CHOOSE_CHOICE', 'GAME_GET_AMOUNT']);
let prompt = null, eventSeq = 0, gameId, tableId, browser, context, page, admin;
let stage = 'opening', failure, defenderId, spellTarget, castCount = 0;
let manaClicks = 0, castManaStart = 0, firstCastMana, recastMana;
let combatStarted = false, combatVerified = false, shockVerified = false;
let commanderReturned = false, initialCommanderId;
const meOf = g => g?.players?.find(p => p.controlled);
const oppsOf = g => g?.players?.filter(p => !p.controlled) ?? [];
const values = x => Object.values(x ?? {});
const nameOf = c => c?.name ?? c?.displayName;
const commanderOf = g => values(meOf(g)?.commandList).find(c => nameOf(c) === 'Krenko, Mob Boss');
const krenkoOf = g => Object.entries(meOf(g)?.battlefield ?? {}).find(([, c]) => nameOf(c) === 'Krenko, Mob Boss');
const landsOf = p => Object.entries(p?.battlefield ?? {}).filter(([, c]) => nameOf(c) === 'Mountain');
const check = (name, condition, detail) => {
  assert.ok(condition, `${name}: ${JSON.stringify(detail ?? '')}`);
  checks.push({ name, passed: true, detail });
  console.log(`PASS ${name}${detail === undefined ? '' : ` ${JSON.stringify(detail)}`}`);
};
const delay = ms => new Promise(r => setTimeout(r, ms));
async function waitFor(fn, label, ms = 45000) {
  const deadline = Date.now() + ms;
  while (Date.now() < deadline) {
    const value = await fn();
    if (value) return value;
    await delay(100);
  }
  throw new Error(`Timeout: ${label}; stage=${stage}; prompt=${JSON.stringify({
    seq: prompt?.seq, method: prompt?.method, message: prompt?.data?.message,
    step: prompt?.data?.gameView?.step, turn: prompt?.data?.gameView?.turn })}`);
}
async function state() {
  return page.evaluate(() => {
    const s = window.__mageStore?.getState();
    return s ? { game: s.game, gameId: s.gameId, feedback: s.feedback,
      combat: s.combat, playableIds: s.playableIds, phase: s.phase,
      error: s.error, gameEnd: s.gameEnd } : null;
  });
}
async function shot(name, s = null) {
  await page.mouse.move(2, 2);
  await page.screenshot({ path: path.join(out, `${name}.png`), fullPage: true });
  snapshots[name] = s ?? await state();
  console.log(`SCREENSHOT ${name}`);
}

// This socket performs setup only. Gameplay always comes from browser clicks.
async function setupConnection() {
  const socket = new WebSocket('ws://127.0.0.1:8787');
  const pending = new Map();
  let request = 0;
  socket.onmessage = event => {
    const m = JSON.parse(String(event.data));
    if (pending.has(m.requestId)) { pending.get(m.requestId)(m); pending.delete(m.requestId); }
  };
  await new Promise((resolve, reject) => {
    socket.onopen = resolve;
    socket.onerror = reject;
  });
  return { socket, call(action, args = {}) {
    assert.ok(!/^sendPlayer|^cheatSetup|^updatePreferences/.test(action), 'Setup socket cannot play');
    const requestId = `setup-${++request}`;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { pending.delete(requestId); reject(new Error(`Setup timeout ${action}`)); }, 30000);
      pending.set(requestId, value => { clearTimeout(timer); resolve(value); });
      socket.send(JSON.stringify({ requestId, action, args }));
    });
  } };
}

async function uiAction(label, locator, expectedAction, value) {
  const at = sent.length;
  const oldPrompt = prompt?.seq ?? -1;
  await locator.click({ timeout: 10000 });
  const outgoing = await waitFor(() => sent.slice(at).find(m =>
    m.action === expectedAction && (value === undefined || String(m.args?.value) === String(value))),
  `browser dispatch: ${label}`, 5000);
  journal.push({ kind: 'ui', label, action: outgoing.action, args: outgoing.args });
  console.log(`UI ${label}`);
  await waitFor(() => prompt && prompt.seq > oldPrompt, `next server prompt after ${label}`, 90000);
  await delay(200); // allow React to render the newly received prompt
}
function card(id) { return page.locator(`[data-card-id="${id}"]:visible`).first(); }
function player(id) { return page.locator(`.player-info-bar[data-player-id="${id}"]`).first(); }
async function pass(label = 'pass priority') {
  await uiAction(label, page.locator('.big-action-btn'), 'sendPlayerBoolean', false);
}
async function cast(id, label) {
  castManaStart = manaClicks;
  await uiAction(label, card(id), 'sendPlayerUUID', id);
}

try {
  browser = await chromium.launch({ headless: true });
  context = await browser.newContext({ viewport: { width: 1920, height: 1080 },
    locale: 'en-GB', recordVideo: { dir: out, size: { width: 1920, height: 1080 } } });
  await context.tracing.start({ screenshots: true, snapshots: true, sources: true });
  page = await context.newPage();
  page.setDefaultTimeout(15000);
  page.on('pageerror', e => pageErrors.push(String(e)));
  page.on('websocket', ws => {
    if (!ws.url().includes(':8787')) return;
    ws.on('framesent', ({ payload }) => {
      const m = JSON.parse(String(payload));
      if (m.action?.startsWith('sendPlayer') || m.action === 'cheatSetup') sent.push(m);
    });
    ws.on('framereceived', ({ payload }) => {
      const m = JSON.parse(String(payload));
      if (m.type !== 'event') return;
      const seq = ++eventSeq;
      if (m.method === 'START_GAME') gameId = m.objectId;
      const d = m.data ?? {};
      const g = d.gameView ?? (d.players ? d : null);
      journal.push({ kind: 'event', seq, method: m.method,
        message: d.message ?? d.question, turn: g?.turn, step: g?.step,
        active: g?.activePlayerName, life: g?.players?.map(p => [p.name, p.life]) });
      if (prompts.has(m.method)) prompt = { seq, method: m.method, data: d };
    });
  });
  await page.goto('http://127.0.0.1:5173/?proxyPort=8787');
  const wizard = page.getByTestId('setup-wizard');
  await wizard.waitFor({ state: 'visible', timeout: 15000 });
  await page.getByTestId('setup-skip').click();
  const closed = page.locator('details.login-network-box:not([open])');
  if (await closed.count()) await page.locator('summary.login-network-header').click();
  await page.getByLabel(/^Proxy WS:/).fill('127.0.0.1');
  await page.getByLabel(/^XMage Server:/).fill('127.0.0.1');
  await page.getByLabel(/^Port:/).fill('17171');
  await page.getByLabel(/Username/i).fill('Stage3Human');
  const password = page.getByLabel(/Password/i);
  if (await password.isEnabled()) await password.fill('x');
  await page.locator('.login-submit-btn').click();
  await page.getByRole('heading', { name: /Lobby|XMage Nexus/i }).waitFor();
  check('human logs in through browser to our private server', true);
  // Configure the real client's preferences only; never inject game state.
  await page.evaluate(() => {
    const store = window.__mageStore;
    const settings = store.getState().settings;
    for (const [key, value] of Object.entries({ autoKeepMulligan: false,
      smartStops: false, effects: false, animationSpeed: 0, soundEnabled: false,
      musicEnabled: false, browserNotifications: false, boardLayout: 'pod',
      boardLayoutManual: true, cardRender: 'rendered',
      manaPayment: { ...settings.manaPayment, smart: false, auto: true } })) store.setSetting(key, value);
  });
  admin = await setupConnection();
  const login = await admin.call('connect', { host: '127.0.0.1', port: 17171,
    username: 'Stage3Human', password: 'x' });
  check('setup connection attaches to the human session', login.ok && login.data?.attached === true, login.data);
  const info = await admin.call('getServerInfo');
  check('game service is our independent XMage server', info.ok && info.data?.host === '127.0.0.1', info.data);
  const names = ['Mountain', 'Lightning Bolt', 'Shock', 'Krenko, Mob Boss', 'Purphoros, God of the Forge'];
  const catalog = await admin.call('resolvePrintings', { names });
  const printing = name => {
    const p = catalog.data?.results?.find(c => c.name === name && c.found);
    assert.ok(p, `Engine printing missing: ${name}`);
    return { cardName: p.cardName, setCode: p.setCode, cardNumber: p.cardNumber, amount: 1 };
  };
  const humanDeck = { name: 'Stage 3 legal human Commander', cards: [
    { ...printing('Mountain'), amount: 4 }, printing('Lightning Bolt'), printing('Shock'),
    { ...printing('Mountain'), amount: 93 }], commanders: [printing('Krenko, Mob Boss')], sideboard: [] };
  const aiDeck = { name: 'Stage 3 legal native AI Commander', cards: [
    { ...printing('Mountain'), amount: 99 }], commanders: [printing('Purphoros, God of the Forge')], sideboard: [] };
  for (const deck of [humanDeck, aiDeck]) {
    const validation = await admin.call('validateDeckFormat', { deck,
      deckType: 'Variant Magic - Commander', gameType: 'Commander Free For All' });
    check(`${deck.name}: official Commander validator accepts 100 cards`,
      validation.ok && validation.data?.supported && validation.data?.valid, validation.data);
  }
  const created = await admin.call('createTable', { name: 'The Fourth Seat Stage 3',
    gameType: 'Commander Free For All', deckType: 'Variant Magic - Commander',
    winsNeeded: 1, playerTypes: ['HUMAN', 'COMPUTER_MAD', 'COMPUTER_MAD', 'COMPUTER_MAD'],
    attackOption: 'MULTIPLE', range: 'ALL', skipInitShuffling: true, skipStartingPlayerChoice: true });
  tableId = created.data?.tableId;
  check('real four-seat Commander table created', created.ok && tableId, created.error);
  const joined = await admin.call('joinTable', { tableId, playerName: 'Stage3Human',
    playerType: 'HUMAN', skill: 1, deck: humanDeck });
  check('human joins Commander table', joined.ok, joined.error);
  for (let seat = 1; seat <= 3; seat++) {
    const joinedAI = await admin.call('joinTable', { tableId, playerName: `NativeAI${seat}`,
      playerType: 'COMPUTER_MAD', skill: 1, deck: aiDeck });
    check(`native XMage AI seat ${seat} joins`, joinedAI.ok, joinedAI.error);
  }
  const started = await admin.call('startMatch', { tableId });
  check('real Commander game starts', started.ok, started.error);
  await waitFor(async () => (await state())?.game?.players?.length === 4, 'four-seat browser game');
  await page.getByTestId('pod-board').waitFor();
  await waitFor(async () => commanderOf((await state())?.game), 'initial command zone');
  let s = await state();
  check('browser displays four players at 40 life', s.game.players.every(p => p.life === 40)
    && await page.locator('.player-info-bar').count() === 4);
  check('server identifies one human and three native computer players',
    s.game.players.filter(p => p.isHuman).length === 1 && oppsOf(s.game).every(p => !p.isHuman),
    s.game.players.map(p => ({ name: p.name, isHuman: p.isHuman })));
  initialCommanderId = commanderOf(s.game)?.id;
  check('Krenko begins in the genuine command zone', Boolean(initialCommanderId));
  await shot('01-four-seat-commander', s);
  await waitFor(() => prompt?.method === 'GAME_ASK', 'human mulligan');
  await uiAction('keep opening hand', page.locator('.mulligan-bar-keep'), 'sendPlayerBoolean', false);
  stage = 'develop mana';
  const deadline = Date.now() + 600000;
  let handledSeq = -1;
  while (Date.now() < deadline && stage !== 'complete') {
    if (!prompt || prompt.seq === handledSeq) { await delay(100); continue; }
    const p = prompt;
    handledSeq = p.seq;
    s = await state();
    const g = s?.game, me = meOf(g);
    if (!g || !me) { await delay(200); continue; }
    assert.ok(!s.gameEnd, `Game ended early: ${JSON.stringify(s.gameEnd)}`);
    const k = krenkoOf(g);
    if (castCount === 1 && k && firstCastMana === undefined) {
      firstCastMana = manaClicks - castManaStart;
      check('Krenko resolves from command zone after paying four mana', firstCastMana === 4,
        { manaClicks: firstCastMana, commanderId: initialCommanderId, permanentId: k[0] });
      await shot('02-commander-cast-four-mana', s);
      stage = 'combat and targeting';
    }
    if (shockVerified === false && spellTarget && me.graveyard
      && values(me.graveyard).some(c => nameOf(c) === 'Shock')) {
      const target = g.players.find(p => p.playerId === spellTarget);
      check('Shock resolves for two damage to the chosen AI opponent', target?.life === 38, target);
      shockVerified = true;
      await shot('03-targeted-shock-resolved', s);
    }
    if (!combatVerified && combatStarted && defenderId && g.step !== 'DECLARE_ATTACKERS') {
      const target = g.players.find(p => p.playerId === defenderId);
      const rules = values(me.battlefield).flatMap(c => c.rules ?? []).join(' ').replace(/<[^>]*>/g, ' ');
      if (rules.includes('did 3 combat damage to player')) {
        check('chosen AI defender loses three life to commander combat damage', target?.life === 37,
          { defender: target?.name, life: target?.life, rules });
        combatVerified = true;
        await shot('05-commander-combat-damage', s);
      }
    }
    if (commanderReturned && castCount === 2 && k) {
      recastMana = manaClicks - castManaStart;
      check('commander recast resolves with the additional two-mana tax', recastMana === 6,
        { firstCastMana, recastMana });
      for (const ai of oppsOf(g)) {
        check(`${ai.name}: native AI played at least four lands`, landsOf(ai).length >= 4,
          { lands: landsOf(ai).length });
      }
      const aiCasters = oppsOf(g).filter(p => values(p.battlefield).some(c => nameOf(c) === 'Purphoros, God of the Forge'));
      check('native AI independently cast a commander', aiCasters.length > 0, aiCasters.map(p => p.name));
      check('targeting and commander combat both verified', shockVerified && combatVerified);
      check('no browser JavaScript errors', pageErrors.length === 0, pageErrors);
      check('all gameplay came from browser controls without cheatSetup',
        sent.length > 0 && !sent.some(m => m.action === 'cheatSetup'));
      await shot('08-commander-recast-six-mana', s);
      stage = 'complete';
      break;
    }
    if (p.method === 'GAME_PLAY_MANA') {
      await page.locator('.mana-prompt-bar').waitFor();
      const source = landsOf(me).find(([, c]) => !c.tapped);
      assert.ok(source, `No untapped Mountain: ${JSON.stringify(p.data)}`);
      manaClicks++;
      await uiAction(`pay mana with Mountain (${manaClicks})`, card(source[0]), 'sendPlayerUUID', source[0]);
      continue;
    }
    if (p.method === 'GAME_TARGET') {
      const q = String(p.data.message ?? p.data.question ?? '');
      if (/discard/i.test(q)) {
        const id = Object.entries(g.myHand ?? {}).find(([, c]) => nameOf(c) === 'Mountain')?.[0];
        assert.ok(id, 'Cleanup can discard a Mountain');
        await uiAction('discard Mountain at cleanup', card(id), 'sendPlayerUUID', id);
      } else if (/attack/i.test(q)) {
        defenderId = oppsOf(g)[2].playerId;
        await shot('04-choose-combat-defender', s);
        await uiAction('choose third AI as combat defender', player(defenderId), 'sendPlayerUUID', defenderId);
      } else {
        const id = stage === 'return commander' ? k?.[0] : spellTarget;
        assert.ok(id, `No target for ${q}`);
        await page.locator('.targeting-bar, .card-grid').first().waitFor();
        await shot(stage === 'return commander' ? '06-target-own-commander' : '03-shock-targeting', s);
        await uiAction(stage === 'return commander' ? 'target Krenko with Lightning Bolt' : 'target first native AI with Shock',
          stage === 'return commander' ? card(id) : player(id), 'sendPlayerUUID', id);
      }
      continue;
    }
    if (p.method === 'GAME_ASK') {
      const q = String(p.data.message ?? p.data.question ?? '');
      assert.match(q, /command zone/i, `Unexpected human decision: ${q}`);
      await page.locator('.ask-prompt-bar').waitFor();
      await shot('07-return-commander-decision', s);
      await uiAction('move Krenko back to command zone', page.locator('.ask-prompt-bar button').filter({ hasText: /Move to command|command zone/i }).first(),
        'sendPlayerBoolean', true);
      s = await state();
      check('commander returns to command zone after the browser choice', Boolean(commanderOf(s.game)) && !krenkoOf(s.game));
      commanderReturned = true;
      stage = 'recast commander';
      continue;
    }
    assert.equal(p.method, 'GAME_SELECT', `Unsupported prompt: ${JSON.stringify(p)}`);
    if (s.feedback && s.feedback.mode !== 'combat') {
      throw new Error(`Unexpected rendered feedback ${JSON.stringify(s.feedback)}`);
    }
    if (s.combat?.mode === 'attack' && me.isActive) {
      if (!combatStarted && k && !k[1].summoningSickness && s.combat.selectable?.includes(k[0])) {
        combatStarted = true;
        await uiAction('declare Krenko as attacker', card(k[0]), 'sendPlayerUUID', k[0]);
      } else {
        await pass('confirm attackers');
      }
      continue;
    }
    const myMain = me.isActive && (g.phase === 'PRECOMBAT_MAIN' || g.phase === 'POSTCOMBAT_MAIN');
    const playable = s.playableIds ?? [];
    if (myMain && values(g.stack).length === 0) {
      const land = Object.entries(g.myHand ?? {}).find(([id, c]) => nameOf(c) === 'Mountain' && playable.includes(id));
      if (land) {
        await uiAction('play Mountain from hand', card(land[0]), 'sendPlayerUUID', land[0]);
        continue;
      }
      const available = landsOf(me).filter(([, c]) => !c.tapped).length;
      const cmd = commanderOf(g);
      if (castCount === 0 && cmd && available >= 4) {
        castCount = 1;
        await cast(cmd.id, 'cast Krenko from command zone');
        continue;
      }
      if (castCount === 1 && k && !spellTarget && available >= 1) {
        const shock = Object.entries(g.myHand).find(([id, c]) => nameOf(c) === 'Shock' && playable.includes(id));
        if (shock) {
          spellTarget = oppsOf(g)[0].playerId;
          await cast(shock[0], 'cast Shock');
          continue;
        }
      }
      if (combatVerified && shockVerified && k && available >= 1 && stage !== 'return commander') {
        const bolt = Object.entries(g.myHand).find(([id, c]) => nameOf(c) === 'Lightning Bolt' && playable.includes(id));
        assert.ok(bolt, 'Lightning Bolt still in hand');
        stage = 'return commander';
        await cast(bolt[0], 'cast Lightning Bolt on our commander');
        continue;
      }
      if (commanderReturned && cmd && available >= 6) {
        check('browser displays the two-mana commander tax',
          await page.locator('.command-zone.my [data-testid="commander-tax"][data-tax="2"]').count() === 1);
        castCount = 2;
        await cast(cmd.id, 'recast Krenko with commander tax');
        continue;
      }
    }
    await pass();
  }
  check('all Stage 3 checkpoints completed before deadline', stage === 'complete');
} catch (error) {
  failure = String(error.stack ?? error);
  console.error(failure);
  if (page) {
    try { await shot('FAILED'); } catch {}
    try { await writeFile(path.join(out, 'failure-dom.txt'), await page.locator('body').innerText()); } catch {}
  }
} finally {
  if (admin) {
    try { if (tableId) await admin.call('quitMatch', { tableId }); } catch {}
    admin.socket.close();
  }
  if (context) {
    try { await context.tracing.stop({ path: path.join(out, 'trace.zip') }); } catch {}
    await context.close();
  }
  await browser?.close();
  await writeFile(path.join(out, 'journal.json'), JSON.stringify(journal, null, 2));
  await writeFile(path.join(out, 'snapshots.json'), JSON.stringify(snapshots, null, 2));
  await writeFile(path.join(root, 'build/nexus/browser-result.json'), JSON.stringify({
    passed: !failure, stage, checks, failure, gameId, tableId, firstCastMana, recastMana,
    humanGameplayActions: sent.length, pageErrors,
    scope: 'Chromium UI; real four-seat Commander; three native XMage MAD AI; mechanics proof, not a game to victory',
    deterministicOpening: true, cheatSetupUsed: false, simulatedBackendUsed: false,
  }, null, 2) + '\n');
}
process.exit(failure ? 1 : 0);
