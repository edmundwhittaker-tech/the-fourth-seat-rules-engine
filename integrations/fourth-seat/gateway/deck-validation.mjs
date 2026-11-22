import { Problem } from './policy.mjs';

export async function validateCommanderDeck(client, deck) {
    if (!deck || !Array.isArray(deck.cards) || !Array.isArray(deck.commanders) || deck.cards.length > 250 || deck.commanders.length !== 1) throw new Problem('Choose a saved deck with one commander.');
    const names = [...new Set([...deck.cards, ...deck.commanders].map(c => c.cardName))];
    if (names.some(n => typeof n !== 'string' || !n || n.length > 200)) throw new Problem('Invalid deck card name.');
    const catalog = await client.call('resolvePrintings', { names });
    if (!catalog.ready) throw new Problem('The engine is still loading its card library. Try again shortly.', 503);
    const printing = new Map(catalog.results.filter(p => p.found).map(p => [p.name, p]));
    const missing = names.filter(name => !printing.has(name));
    if (missing.length) throw new Problem('This engine version does not support: ' + missing.join(', '), 422);
    const convert = cards => cards.map(c => {
      if (!Number.isInteger(c.amount) || c.amount < 1 || c.amount > 100) throw new Problem('Invalid card quantity.');
      const p = printing.get(c.cardName);
      return { cardName: p.cardName, setCode: p.setCode, cardNumber: p.cardNumber, amount: c.amount };
    });
    const result = { name: String(deck.name ?? 'Commander').slice(0, 80), cards: convert(deck.cards), commanders: convert(deck.commanders), sideboard: [] };
    const check = await client.call('validateDeckFormat', { deck: result, gameType: 'Commander Free For All', deckType: 'Variant Magic - Commander' });
    if (!check.supported || !check.valid) throw new Problem('Commander deck validation: ' + (check.errors ?? []).map(e => e.message ?? e.cardName ?? e.type).join('; '), 422);
    return result;
  }
