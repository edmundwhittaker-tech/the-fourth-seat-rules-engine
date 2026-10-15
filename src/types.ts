/** Public types are intentionally UI-agnostic and serializable. */
export type PlayerId = string;
export type CardId = string;
export type ManaColor = "W" | "U" | "B" | "R" | "G" | "C";
export type ManaPool = Record<ManaColor, number>;
export type CardType = "Land" | "Creature" | "Instant" | "Sorcery" | "Artifact" | "Enchantment";
export type Zone = "library" | "hand" | "battlefield" | "graveyard" | "exile" | "stack" | "command";
export type Step =
  | "upkeep" | "draw" | "main1"
  | "begin-combat" | "declare-attackers" | "declare-blockers"
  | "combat-damage" | "end-combat" | "main2" | "end";

export type CardEffect =
  | { kind: "draw"; count: number }
  | { kind: "damage-player"; amount: number };

export interface CardDefinition {
  name: string;
  types: CardType[];
  manaCost?: string; // v0.1: supports only {number}, {W}, {U}, {B}, {R}, {G}, {C}
  producesMana?: ManaColor; // v0.1: one colorless or colored mana
  effect?: CardEffect;
}

export interface CardInstance {
  id: CardId;
  ownerId: PlayerId;
  controllerId: PlayerId;
  zone: Zone;
  tapped: boolean;
  definition: CardDefinition;
}

export interface PlayerState {
  id: PlayerId;
  name: string;
  life: number;
  library: CardId[]; // index 0 is top; deck order is supplied by caller
  hand: CardId[];
  battlefield: CardId[];
  graveyard: CardId[];
  exile: CardId[];
  command: CardId[];
  mana: ManaPool;
  landsPlayedThisTurn: number;
}

export interface StackEntry {
  id: string;
  cardId: CardId;
  controllerId: PlayerId;
  targetPlayerId?: PlayerId;
}

export interface GameState {
  version: 1;
  players: PlayerState[];
  cards: Record<CardId, CardInstance>;
  turnNumber: number;
  activePlayerId: PlayerId;
  priorityPlayerId: PlayerId;
  step: Step;
  consecutivePasses: number;
  stack: StackEntry[]; // top is last element
  nextStackId: number;
}

export type GameCommand =
  | { type: "PLAY_LAND"; playerId: PlayerId; cardId: CardId }
  | { type: "TAP_FOR_MANA"; playerId: PlayerId; cardId: CardId }
  | { type: "CAST_SPELL"; playerId: PlayerId; cardId: CardId; targetPlayerId?: PlayerId }
  | { type: "PASS_PRIORITY"; playerId: PlayerId };

export interface GameEvent {
  type: string;
  text: string;
}

export interface GameResult { state: GameState; events: GameEvent[] }
export interface StartingPlayer { id: PlayerId; name: string; library: CardDefinition[] }
