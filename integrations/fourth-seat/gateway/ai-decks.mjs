import { readFileSync } from 'node:fs';
import { randomInt } from 'node:crypto';

function freeze(value) {
  if (value && typeof value === 'object') {
    Object.values(value).forEach(freeze);
    Object.freeze(value);
  }
  return value;
}

// Frozen snapshots, not live downloads: source edits cannot change a running game.
export const aiDeckPool = freeze(JSON.parse(readFileSync(new URL('./ai-decks.json', import.meta.url), 'utf8')).decks);

export function chooseAIDecks(count, humanCommander, pick = randomInt) {
  if (!Number.isInteger(count) || count < 0 || count > 3) throw new RangeError('Choose zero to three AI decks.');
  const eligible = aiDeckPool.filter(entry => entry.deck.commanders[0].cardName !== humanCommander);
  const choices = [...(eligible.length >= count ? eligible : aiDeckPool)];
  for (let i = 0; i < count; i++) {
    const j = i + pick(choices.length - i);
    [choices[i], choices[j]] = [choices[j], choices[i]];
  }
  return choices.slice(0, count);
}
