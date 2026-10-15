import test from 'node:test';
import assert from 'node:assert/strict';
import { createGame, applyAction, getPublicState, RuleError } from '../docs/assets/index.js';
import { createDemoGame } from '../docs/assets/demo.js';

function doIt(state, command) { return applyAction(state, command).state; }
function passRound(state) {
  const count = state.players.length;
  for (let i = 0; i < count; i++) state = doIt(state, { type: 'PASS_PRIORITY', playerId: state.priorityPlayerId });
  return state;
}
function reachMain1(state) { return passRound(passRound(state)); }
const x = (name, types, manaCost, effect) => ({ name, types, manaCost, effect });

// All v0.1 engine behavior should be repeatable from the same initial state + commands.
test('three-player game: first player draws on first turn and commands do not mutate prior snapshots', () => {
  const s0 = createDemoGame();
  assert.equal(s0.players[0].hand.length, 7);
  const s1 = passRound(s0);
  assert.equal(s1.step, 'draw');
  assert.equal(s1.players[0].hand.length, 8);
  assert.equal(s0.players[0].hand.length, 7);
});

test('play a single land during main phase, tap it, cast instant, resolve on unanimous passes', () => {
  let s = reachMain1(createDemoGame());
  const cliffId = s.players[0].hand.find(id => s.cards[id].definition.name === 'Cliffside');
  const boltId = s.players[0].hand.find(id => s.cards[id].definition.name === 'Spark Lance');
  s = doIt(s, { type: 'PLAY_LAND', playerId: 'ari', cardId: cliffId });
  assert.equal(s.players[0].landsPlayedThisTurn, 1);
  const otherLand = s.players[0].hand.find(id => s.cards[id].definition.types.includes('Land'));
  assert.throws(() => doIt(s, { type: 'PLAY_LAND', playerId: 'ari', cardId: otherLand }), RuleError);
  s = doIt(s, { type: 'TAP_FOR_MANA', playerId: 'ari', cardId: cliffId });
  assert.equal(s.players[0].mana.R, 1);
  assert.throws(() => doIt(s, { type: 'TAP_FOR_MANA', playerId: 'ari', cardId: cliffId }), RuleError);
  s = doIt(s, { type: 'CAST_SPELL', playerId: 'ari', cardId: boltId, targetPlayerId: 'blake' });
  assert.equal(s.players[0].mana.R, 0);
  assert.equal(s.stack.length, 1);
  s = passRound(s);
  assert.equal(s.players[1].life, 37);
  assert.equal(s.stack.length, 0);
  assert.equal(s.cards[boltId].zone, 'graveyard');
});

test('stack resolves in last-in-first-out order with interactive responses', () => {
  const zap = (n) => x(`Zap ${n}`, ['Instant'], '{0}', { kind: 'damage-player', amount: n });
  const blank = x('Other', ['Land']);
  let s = createGame([
    { id: 'a', name: 'A', library: [zap(2), ...Array(7).fill(blank)] },
    { id: 'b', name: 'B', library: [zap(3), ...Array(7).fill(blank)] },
  ]);
  const a = s.players[0].hand[0];
  const b = s.players[1].hand[0];
  s = doIt(s, { type: 'CAST_SPELL', playerId: 'a', cardId: a, targetPlayerId: 'b' });
  s = doIt(s, { type: 'PASS_PRIORITY', playerId: 'a' });
  s = doIt(s, { type: 'CAST_SPELL', playerId: 'b', cardId: b, targetPlayerId: 'a' });
  s = passRound(s);
  assert.equal(s.players[0].life, 37);
  assert.equal(s.players[1].life, 40);
  assert.equal(s.stack.length, 1);
  s = passRound(s);
  assert.equal(s.players[1].life, 38);
  assert.equal(s.stack.length, 0);
});

test('cannot cast sorceries off-turn or before main phase', () => {
  const sorcery = x('Study', ['Sorcery'], '{0}', { kind: 'draw', count: 1 });
  const blank = x('Other', ['Land']);
  const s = createGame([
    { id: 'a', name: 'A', library: [sorcery, ...Array(8).fill(blank)] },
    { id: 'b', name: 'B', library: Array(9).fill(blank) },
  ]);
  assert.throws(() => doIt(s, { type: 'CAST_SPELL', playerId: 'a', cardId: s.players[0].hand[0] }), RuleError);
  assert.throws(() => doIt(s, { type: 'CAST_SPELL', playerId: 'b', cardId: s.players[0].hand[0] }), RuleError);
});

test('public state does not expose hidden card names in hand or library', () => {
  const s = createDemoGame();
  const visible = getPublicState(s);
  assert.equal(visible.players[0].handCount, 7);
  assert.equal('hand' in visible.players[0], false);
  assert.equal('library' in visible.players[0], false);
  assert.equal(JSON.stringify(visible).includes('Spark Lance'), false);
});

test('mana clears at step boundaries; invalid actions leave original state alone', () => {
  let s = reachMain1(createDemoGame());
  const land = s.players[0].hand.find(id => s.cards[id].definition.name === 'Cliffside');
  s = doIt(s, { type: 'PLAY_LAND', playerId: 'ari', cardId: land });
  s = doIt(s, { type: 'TAP_FOR_MANA', playerId: 'ari', cardId: land });
  const before = structuredClone(s);
  assert.throws(() => doIt(s, { type: 'PLAY_LAND', playerId: 'ari', cardId: land }), RuleError);
  assert.deepEqual(s, before);
  s = passRound(s);
  assert.equal(s.step, 'begin-combat');
  assert.equal(s.players[0].mana.R, 0);
});
