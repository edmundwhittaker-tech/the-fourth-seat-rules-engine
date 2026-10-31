import type { CardData, DeckBackId } from "./types";

export type BattlefieldRow = "creatures" | "other" | "lands";
export type ZoneName = "hand" | "library" | "graveyard" | "exile" | "battlefield" | "command" | "stack";

export type CardInstance = {
  instanceId: string;
  card: CardData;
  ownerSeatId: string;
  controllerSeatId: string;
  tapped: boolean;
  quantity: number;
  counters: Record<string, number>;
  copy: boolean;
  token: boolean;
  isCommander: boolean;
  row: BattlefieldRow;
  /** Public, manual battlefield relationship. Undefined means this is a top-level permanent. */
  attachedTo?: string;
  attachmentOrder?: number;
  /** Zero-based rendered face. The same game object remains in place when this changes. */
  faceIndex?: number;
};

export type PublicDeck = {
  id: string;
  name: string;
  cardCount: number;
  cardBack?: DeckBackId;
  commander: CardData;
};

export type RoomPlayer = {
  seatId: string;
  profileId: string;
  displayName: string;
  seat: number;
  ready: boolean;
  lastSeen: number;
  deck: PublicDeck;
  deckCards?: CardData[];
};

export type RoomSpectator = {
  profileId: string;
  displayName: string;
  joinedAt: number;
};

export type PlayerGameState = {
  life: number;
  /** Eliminated players remain visible for table history, but can no longer act or receive turns. */
  eliminated?: boolean;
  eliminatedAt?: number;
  eliminationReason?: "life" | "conceded";
  hand: CardInstance[];
  library: CardInstance[];
  graveyard: CardInstance[];
  exile: CardInstance[];
  battlefield: CardInstance[];
  commandZone: CardInstance | null;
  commanderReference: CardData;
  commanderCastCount: number;
  commanderWasCast: boolean;
  commanderDamage: Record<string, number>;
  playerCounters: Record<string, number>;
  /** False only while the player is choosing their opening hand. Undefined keeps legacy games playable. */
  openingHandKept?: boolean;
  mulligansTaken?: number;
};

export type StackObject = {
  id: string;
  kind: "card" | "ability";
  controllerSeatId: string;
  card?: CardInstance;
  source?: CardData;
  note?: string;
  abilityType?: string;
};

export type GameLogEntry = { id: string; text: string; at: number; groupKey?: string };

export type AttackerAssignment = {
  battlefieldObjectId: string;
  defenderType: "player" | "permanent";
  defendingPlayerId: string;
  defendingPermanentId?: string;
  quantity: number;
  isCommander: boolean;
};

export type BlockAssignment = {
  blockerObjectId: string;
  attackerObjectId: string;
};

export type CombatState = {
  active: boolean;
  attackingPlayerId: string;
  attackers: AttackerAssignment[];
  confirmedAttackers: boolean;
  blockers: BlockAssignment[];
};

export type PlayerGameStats = {
  turnsTaken: number;
  activeTurnMilliseconds: number;
  cardsDrawn: number;
  cardsPlayed: number;
  creaturesPlayed: number;
  tokensCreated: number;
  commanderCasts: number;
};

export type GameStats = {
  startedAt: number;
  finishedAt?: number;
  /** Always runs for summary accuracy, independently of the optional visible timer. */
  activeTurnStartedAt: number | null;
  players: Record<string, PlayerGameStats>;
};

export type UndoSnapshot = {
  actorSeatId: string;
  label: string;
  partial?: boolean;
  inverse?: GameAction;
  playerStates: Record<string, PlayerGameState>;
  stack: StackObject[];
  activePlayerId: string;
  turnNumber: number;
  phase: string;
  combat: CombatState;
  reveal: GameState["reveal"];
  capturedAt: number;
  turnTimerEnabled: boolean;
  activeTurnStartedAt: number | null;
  activeTurnTimeMs: Record<string, number>;
  stats?: GameStats;
};

export type GameState = {
  startingPlayerId: string;
  /** Clockwise table order, randomized once when the game starts. */
  turnOrder: string[];
  activePlayerId: string;
  turnNumber: number;
  phase: string;
  combat: CombatState;
  playerStates: Record<string, PlayerGameState>;
  stack: StackObject[];
  reveal: null | { playerSeatId: string; displayName: string; cards: CardData[]; card?: CardData };
  ping: null | { objectId: string; playerSeatId: string; at: number };
  log: GameLogEntry[];
  history: UndoSnapshot[];
  turnTimerEnabled: boolean;
  activeTurnStartedAt: number | null;
  activeTurnTimeMs: Record<string, number>;
  stats: GameStats;
};

export type RoomState = {
  code: string;
  mode: "multiplayer" | "solo";
  hostProfileId: string;
  status: "lobby" | "game" | "finished";
  players: RoomPlayer[];
  spectator?: RoomSpectator | null;
  game: GameState | null;
  recentActionIds: string[];
  createdAt: number;
  lastActivity: number;
  finishedAt?: number;
  finishedBy?: string;
  winnerSeatId?: string | null;
};

/** Client views retain public counts even when private zone contents are omitted. */
export type ClientPlayerGameState = PlayerGameState & {
  handCount?: number;
  libraryCount?: number;
};

export type ClientGameState = Omit<GameState, "playerStates"> & {
  playerStates: Record<string, ClientPlayerGameState>;
};

export type GameAction = {
  id: string;
  type: string;
  payload?: Record<string, unknown>;
};

export type PublicPlayerSyncState = PlayerGameState & {
  handCount: number;
  libraryCount: number;
};

export type PublicObjectUpdate = {
  seatId: string;
  instanceId: string;
  toZone: "battlefield" | "graveyard" | "exile" | "command" | "stack" | "hand" | "library" | "removed";
  card: CardInstance | null;
  handCount: number;
  libraryCount: number;
};

export type RoomSyncPatch =
  | { kind: "noop" }
  | { kind: "reconcile" }
  | { kind: "card"; seatId: string; instanceId: string; card: CardInstance | null }
  | { kind: "objects"; updates: PublicObjectUpdate[]; stack: StackObject[]; combat: CombatState }
  | { kind: "life"; seatId: string; life: number }
  | { kind: "metrics"; seatId: string; life: number; commanderDamage: Record<string, number>; playerCounters: Record<string, number>; commanderCastCount: number }
  | { kind: "combat"; phase: string; combat: CombatState; cards: Array<{ seatId: string; card: CardInstance }> }
  | { kind: "phase"; activePlayerId: string; turnNumber: number; phase: string; combat: CombatState }
  | { kind: "timer"; turnTimerEnabled: boolean; activeTurnStartedAt: number | null; activeTurnTimeMs: Record<string, number> }
  | { kind: "turn"; activePlayerId: string; turnNumber: number; phase: string; combat: CombatState; seatId: string; state: PublicPlayerSyncState; turnTimerEnabled: boolean; activeTurnStartedAt: number | null; activeTurnTimeMs: Record<string, number> }
  | { kind: "player"; seatId: string; state: PublicPlayerSyncState; stack: StackObject[]; combat: CombatState; reveal: GameState["reveal"] }
  | { kind: "game"; activePlayerId: string; turnNumber: number; phase: string; combat: CombatState; playerStates: Record<string, PublicPlayerSyncState>; stack: StackObject[]; reveal: GameState["reveal"]; ping: GameState["ping"]; turnTimerEnabled: boolean; activeTurnStartedAt: number | null; activeTurnTimeMs: Record<string, number> }
  | { kind: "stack"; stack: StackObject[] }
  | { kind: "reveal"; reveal: GameState["reveal"] }
  | { kind: "ping"; ping: GameState["ping"] };

export type RoomSyncEvent = {
  id: string;
  actionId: string;
  actionType: string;
  actorSeatId: string;
  version: number;
  serverAt: number;
  patch: RoomSyncPatch;
  logEntry?: GameLogEntry;
  debugTiming?: { serverReceivedAt: number; stateUpdatedAt: number; eventCreatedAt: number };
  /** Viewers in this list should privately reconcile after a public hidden-zone update. */
  privateSeatIds?: string[];
  /** Transient public-play cue. Card data remains in the normal public object patch. */
  publicPlay?: { instanceId: string; destination: "battlefield" | "stack" };
};
