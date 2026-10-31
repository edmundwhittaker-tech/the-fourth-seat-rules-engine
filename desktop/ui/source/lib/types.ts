export type CardFace = {
  name: string;
  manaCost: string;
  oracleText: string;
  typeLine: string;
  image: string;
  artCrop?: string;
  power?: string;
  toughness?: string;
  loyalty?: string;
};

export type CardPrinting = {
  id: string;
  setCode: string;
  setName: string;
  collectorNumber: string;
  releasedAt?: string;
  image: string;
  artCrop?: string;
  borderColor?: string;
  frameEffects?: string[];
  promoTypes?: string[];
  finishes?: string[];
  promo?: boolean;
  fullArt?: boolean;
  layout?: string;
  doubleFaced?: boolean;
  faces?: CardFace[];
};

export type CardData = {
  id: string;
  oracleId?: string;
  name: string;
  manaCost: string;
  oracleText: string;
  typeLine: string;
  colors: string[];
  colorIdentity: string[];
  power?: string;
  toughness?: string;
  loyalty?: string;
  image: string;
  artCrop?: string;
  quantity: number;
  tokenNames?: string[];
  printing?: CardPrinting;
  printsSearchUri?: string;
  layout?: string;
  foil?: boolean;
  faces?: CardFace[];
};

export type DeckBackId =
  | "standard"
  | "obsidian-sigil"
  | "astral-vault"
  | "verdant-reliquary"
  | "azorius"
  | "boros"
  | "dimir"
  | "golgari"
  | "gruul"
  | "izzet"
  | "orzhov"
  | "rakdos"
  | "selesnya"
  | "simic";

export type DeckData = {
  id: string;
  ownerId: string;
  name: string;
  commander: CardData;
  cards: CardData[];
  cardCount: number;
  /** Optional for backwards compatibility; legacy decks resolve to the standard Magic back. */
  cardBack?: DeckBackId;
  createdAt: number;
  updatedAt: number;
};

export type ImportResult = {
  cards: CardData[];
  errors: Array<{
    line: string;
    message: string;
    parsed?: { name: string; setCode?: string; collectorNumber?: string; flags?: string[] };
  }>;
  commanderCandidates: string[];
  total: number;
};
