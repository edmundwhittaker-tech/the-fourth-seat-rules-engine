import type { DeckBackId } from "./types";

export const DEFAULT_DECK_BACK: DeckBackId = "standard";

export const DECK_BACKS: ReadonlyArray<{
  id: DeckBackId;
  name: string;
  description: string;
  image: string;
}> = [
  { id: "standard", name: "Classic Magic", description: "The standard Magic card back.", image: "/deck-backs/standard.webp" },
  { id: "obsidian-sigil", name: "Obsidian Sigil", description: "Black lacquer and warm gold filigree.", image: "/deck-backs/obsidian-sigil.webp" },
  { id: "astral-vault", name: "Astral Vault", description: "A silver compass set in a midnight sky.", image: "/deck-backs/astral-vault.webp" },
  { id: "verdant-reliquary", name: "Verdant Reliquary", description: "Ancient bronze framed by living vines.", image: "/deck-backs/verdant-reliquary.webp" },
  { id: "azorius", name: "Azorius", description: "White and blue guild back.", image: "/deck-backs/azorius.webp" },
  { id: "boros", name: "Boros", description: "Red and white guild back.", image: "/deck-backs/boros.webp" },
  { id: "dimir", name: "Dimir", description: "Blue and black guild back.", image: "/deck-backs/dimir.webp" },
  { id: "golgari", name: "Golgari", description: "Black and green guild back.", image: "/deck-backs/golgari.webp" },
  { id: "gruul", name: "Gruul", description: "Red and green guild back.", image: "/deck-backs/gruul.webp" },
  { id: "izzet", name: "Izzet", description: "Blue and red guild back.", image: "/deck-backs/izzet.webp" },
  { id: "orzhov", name: "Orzhov", description: "White and black guild back.", image: "/deck-backs/orzhov.webp" },
  { id: "rakdos", name: "Rakdos", description: "Black and red guild back.", image: "/deck-backs/rakdos.webp" },
  { id: "selesnya", name: "Selesnya", description: "Green and white guild back.", image: "/deck-backs/selesnya.webp" },
  { id: "simic", name: "Simic", description: "Green and blue guild back.", image: "/deck-backs/simic.webp" },
] as const;

const deckBackIds = new Set<DeckBackId>(DECK_BACKS.map((option) => option.id));

export function normalizeDeckBack(value: unknown): DeckBackId {
  return typeof value === "string" && deckBackIds.has(value as DeckBackId) ? value as DeckBackId : DEFAULT_DECK_BACK;
}

export function deckBackImage(value: unknown) {
  const id = normalizeDeckBack(value);
  return DECK_BACKS.find((option) => option.id === id)?.image ?? DECK_BACKS[0].image;
}
