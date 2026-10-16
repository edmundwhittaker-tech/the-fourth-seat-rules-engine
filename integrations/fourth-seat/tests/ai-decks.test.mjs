import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { aiDeckPool, chooseAIDecks } from '../gateway/ai-decks.mjs';
import { validateCommanderDeck } from '../gateway/deck-validation.mjs';

test('ten attributed published snapshots retain complete Commander lists and content hashes', () => {
  assert.equal(aiDeckPool.length, 10);
  assert.equal(new Set(aiDeckPool.map(d => d.id)).size, 10);
  assert.equal(new Set(aiDeckPool.map(d => d.deck.commanders[0].cardName)).size, 10);
  const basics = /^(Snow-Covered )?(Plains|Island|Swamp|Mountain|Forest)$|^Wastes$/;
  for (const { id, source, checks, deck } of aiDeckPool) {
    assert.match(source.url, /^https:\/\/archidekt\.com\/decks\/\d+$/);
    assert.equal(id, 'archidekt-' + source.url.split('/').at(-1));
    assert.ok(source.author && source.title && source.updatedAt && source.importedAt);
    assert.equal(source.bracket, 3);
    assert.ok(['author', 'estimated'].includes(source.bracketBasis));
    assert.equal(deck.commanders.length, 1);
    assert.equal(deck.commanders[0].amount, 1);
    assert.equal(deck.cards.reduce((n, c) => n + c.amount, 0), 99);
    const cards = [...deck.cards, ...deck.commanders];
    assert.equal(new Set(cards.map(c => c.cardName)).size, cards.length);
    assert.ok(cards.every(c => Number.isInteger(c.amount) && c.amount > 0 && (c.amount === 1 || basics.test(c.cardName))));
    assert.deepEqual(deck.sideboard, []);
    assert.ok(checks.gameChangers.length <= 3);
    const canonical = JSON.stringify({ cards: deck.cards, commanders: deck.commanders });
    assert.equal(createHash('sha256').update(canonical).digest('hex'), checks.contentSha256);
  }
});

test('random assignment gives distinct AI decks and avoids the human commander', () => {
  const before = JSON.stringify(aiDeckPool);
  for (const entry of aiDeckPool) {
    for (const count of [0, 1, 2, 3]) {
      const selected = chooseAIDecks(count, entry.deck.commanders[0].cardName);
      assert.equal(selected.length, count);
      assert.equal(new Set(selected.map(d => d.id)).size, count);
      assert.ok(selected.every(d => d.id !== entry.id));
    }
  }
  assert.equal(JSON.stringify(aiDeckPool), before);
  assert.throws(() => aiDeckPool[0].deck.cards.push({ cardName: 'Black Lotus', amount: 1 }));
  for (const count of [-1, 4, 1.5, NaN]) assert.throws(() => chooseAIDecks(count));
});

test('each published deck can occupy a random AI seat', () => {
  const firstChoices = aiDeckPool.map((_, index) => chooseAIDecks(1, 'Other commander', () => index)[0].id);
  assert.deepEqual(firstChoices, aiDeckPool.map(d => d.id));
});

test('unsupported cards fail before Commander validation rather than changing a list', async () => {
  const calls = [];
  const client = { call: async action => { calls.push(action); return { ready: true, results: [] }; } };
  await assert.rejects(validateCommanderDeck(client, aiDeckPool[0].deck), /does not support/);
  assert.deepEqual(calls, ['resolvePrintings']);
});

test('engine rejection fails closed after every card has resolved', async () => {
  const entry = aiDeckPool[0].deck;
  const client = { call: async (action, args) => action === 'resolvePrintings'
    ? { ready: true, results: args.names.map(name => ({ name, found: true, cardName: name, setCode: 'TST', cardNumber: '1' })) }
    : { supported: true, valid: false, errors: [{ message: 'Illegal deck' }] } };
  await assert.rejects(validateCommanderDeck(client, entry), /Illegal deck/);
});
