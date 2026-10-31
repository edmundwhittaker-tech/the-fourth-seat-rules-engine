import type { CardData, DeckData } from "./types";

export const MAX_DECK_ENTRIES = 250;
export const MAX_DECK_CARDS = 250;

function shortText(value: unknown, maximum: number) { return typeof value === "string" && value.length > 0 && value.length <= maximum; }

export function isSafeCard(card: unknown): card is CardData {
  if (!card || typeof card !== "object" || Array.isArray(card)) return false;
  const value = card as Partial<CardData>;
  return shortText(value.id, 100) && shortText(value.name, 250) && shortText(value.typeLine, 500) &&
    typeof value.oracleText === "string" && value.oracleText.length <= 12_000 && typeof value.manaCost === "string" && value.manaCost.length <= 300 &&
    typeof value.image === "string" && value.image.length <= 2_000 && Array.isArray(value.colors) && value.colors.length <= 6 &&
    Array.isArray(value.colorIdentity) && value.colorIdentity.length <= 6 && Number.isInteger(value.quantity) && value.quantity! >= 1 && value.quantity! <= MAX_DECK_CARDS;
}

export function validateDeckCards(commander: unknown, cards: unknown): asserts commander is CardData {
  if (!isSafeCard(commander)) throw new Error("The commander data is invalid. Re-import the deck and try again.");
  if (!Array.isArray(cards) || !cards.length || cards.length > MAX_DECK_ENTRIES || !cards.every(isSafeCard)) {
    throw new Error("The deck contains invalid or excessive card data. Re-import it and try again.");
  }
  const total = cards.reduce((sum, card) => sum + card.quantity, 0);
  if (total < 1 || total > MAX_DECK_CARDS) throw new Error(`A deck may contain at most ${MAX_DECK_CARDS} cards in this beta.`);
}

export function validateDeck(deck: DeckData) { validateDeckCards(deck.commander, deck.cards); }
