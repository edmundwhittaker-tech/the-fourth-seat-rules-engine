import type {
  CardId, GameCommand, GameEvent, GameResult, GameState,
  ManaColor, ManaPool, PlayerId, PlayerState, StartingPlayer, Step, Zone,
} from "./types.js";

const COLORS: ManaColor[] = ["W", "U", "B", "R", "G", "C"];
const STEPS: Step[] = [
  "upkeep", "draw", "main1", "begin-combat", "declare-attackers",
  "declare-blockers", "combat-damage", "end-combat", "main2", "end",
];

export class RuleError extends Error {
  constructor(message: string) { super(message); this.name = "RuleError"; }
}

function ensure(condition: unknown, message: string): asserts condition {
  if (!condition) throw new RuleError(message);
}

export function emptyManaPool(): ManaPool {
  return { W: 0, U: 0, B: 0, R: 0, G: 0, C: 0 };
}

function player(state: GameState, id: PlayerId): PlayerState {
  const found = state.players.find((p) => p.id === id);
  ensure(found, `Unknown player: ${id}`);
  return found;
}

function card(state: GameState, id: CardId) {
  const found = state.cards[id];
  ensure(found, `Unknown card: ${id}`);
  return found;
}

function message(events: GameEvent[], type: string, text: string): void {
  events.push({ type, text });
}

function draw(state: GameState, playerId: PlayerId, events: GameEvent[]): void {
  const p = player(state, playerId);
  const id = p.library.shift();
  if (!id) {
    // TODO: implement library-exhaustion losses using state-based actions.
    message(events, "unsupported", `${p.name} cannot draw: empty library (loss checks not implemented).`);
    return;
  }
  p.hand.push(id);
  card(state, id).zone = "hand";
  message(events, "draw", `${p.name} draws a card.`);
}

/** Supply a predetermined deck order; no shuffle is performed by this kernel. */
export function createGame(seats: StartingPlayer[]): GameState {
  ensure(seats.length >= 2 && seats.length <= 4, "The prototype supports 2–4 players.");
  ensure(new Set(seats.map((p) => p.id)).size === seats.length, "Player IDs must be unique.");
  ensure(seats.every((p) => p.library.length >= 7), "Each demo player needs at least seven cards.");

  const players: PlayerState[] = [];
  const cards: GameState["cards"] = {};
  seats.forEach((seat) => {
    const library: CardId[] = [];
    seat.library.forEach((definition, i) => {
      const id = `${seat.id}:card:${i + 1}`;
      cards[id] = {
        id, ownerId: seat.id, controllerId: seat.id, zone: "library",
        tapped: false, definition: structuredClone(definition),
      };
      library.push(id);
    });
    players.push({
      id: seat.id, name: seat.name, life: 40, library, hand: [],
      battlefield: [], graveyard: [], exile: [], command: [],
      mana: emptyManaPool(), landsPlayedThisTurn: 0,
    });
  });

  const state: GameState = {
    version: 1, players, cards, turnNumber: 1,
    activePlayerId: players[0].id, priorityPlayerId: players[0].id,
    step: "upkeep", consecutivePasses: 0, stack: [], nextStackId: 1,
  };
  const openingEvents: GameEvent[] = [];
  for (const seat of players) for (let i = 0; i < 7; i++) draw(state, seat.id, openingEvents);
  return state;
}

function moveFromArray(array: CardId[], id: CardId): void {
  const index = array.indexOf(id);
  ensure(index >= 0, `Card ${id} was not in the expected zone.`);
  array.splice(index, 1);
}

function leaveHand(state: GameState, playerId: PlayerId, cardId: CardId, newZone: Zone): void {
  moveFromArray(player(state, playerId).hand, cardId);
  card(state, cardId).zone = newZone;
}

function hasPriority(state: GameState, playerId: PlayerId): void {
  player(state, playerId);
  ensure(state.priorityPlayerId === playerId, "It is not this player's priority.");
}

function mainPhaseWithEmptyStack(state: GameState, playerId: PlayerId): boolean {
  return state.activePlayerId === playerId
    && (state.step === "main1" || state.step === "main2")
    && state.stack.length === 0;
}

function parseCost(cost: string | undefined): { generic: number; colors: ManaPool } {
  const needed = emptyManaPool();
  if (!cost) return { generic: 0, colors: needed };
  const pieces = [...cost.matchAll(/\{([^{}]+)\}/g)];
  ensure(pieces.map((match) => match[0]).join("") === cost, `Unsupported mana cost: ${cost}`);
  let generic = 0;
  for (const piece of pieces) {
    const symbol = piece[1];
    if (/^\d+$/.test(symbol)) generic += Number(symbol);
    else if (COLORS.includes(symbol as ManaColor)) needed[symbol as ManaColor]++;
    else throw new RuleError(`Mana symbol ${symbol} is not supported by v0.1.`);
  }
  return { generic, colors: needed };
}

function spendMana(pool: ManaPool, cost: string | undefined): void {
  const { generic, colors } = parseCost(cost);
  const remaining = { ...pool };
  for (const color of COLORS) {
    ensure(remaining[color] >= colors[color], `Not enough ${color} mana.`);
    remaining[color] -= colors[color];
  }
  ensure(COLORS.reduce((sum, color) => sum + remaining[color], 0) >= generic, "Not enough mana to pay the generic cost.");
  let genericRemaining = generic;
  // Predictable generic payment; later replace with player choice.
  for (const color of COLORS) {
    const amount = Math.min(genericRemaining, remaining[color]);
    remaining[color] -= amount;
    genericRemaining -= amount;
  }
  for (const color of COLORS) pool[color] = remaining[color];
}

function nextPlayer(state: GameState, id: PlayerId): PlayerId {
  const index = state.players.findIndex((p) => p.id === id);
  ensure(index >= 0, `Unknown player: ${id}`);
  return state.players[(index + 1) % state.players.length].id;
}

function resolveTop(state: GameState, events: GameEvent[]): void {
  const item = state.stack.pop();
  ensure(item, "Cannot resolve an empty stack.");
  const spell = card(state, item.cardId);
  const owner = player(state, spell.ownerId);
  const controller = player(state, item.controllerId);
  message(events, "resolve", `${spell.definition.name} resolves.`);

  if (spell.definition.effect?.kind === "draw") {
    for (let i = 0; i < spell.definition.effect.count; i++) draw(state, controller.id, events);
  } else if (spell.definition.effect?.kind === "damage-player") {
    ensure(item.targetPlayerId, "Damage spell needs a player target.");
    const target = player(state, item.targetPlayerId);
    target.life -= spell.definition.effect.amount;
    message(events, "damage", `${target.name} takes ${spell.definition.effect.amount} damage (now ${target.life} life).`);
    // TODO: state-based actions, prevention, replacement and loss checks.
  }

  const isNonPermanent = spell.definition.types.includes("Instant") || spell.definition.types.includes("Sorcery");
  if (isNonPermanent) {
    spell.zone = "graveyard";
    owner.graveyard.push(spell.id);
  } else {
    spell.zone = "battlefield";
    spell.controllerId = controller.id;
    controller.battlefield.push(spell.id);
  }
}

function advanceStep(state: GameState, events: GameEvent[]): void {
  for (const p of state.players) p.mana = emptyManaPool();
  const index = STEPS.indexOf(state.step);
  if (index === STEPS.length - 1) {
    state.turnNumber += 1;
    state.activePlayerId = nextPlayer(state, state.activePlayerId);
    const active = player(state, state.activePlayerId);
    active.landsPlayedThisTurn = 0;
    for (const id of active.battlefield) card(state, id).tapped = false;
    state.step = "upkeep";
    message(events, "turn", `Turn ${state.turnNumber}: ${active.name} untaps and begins upkeep.`);
  } else {
    state.step = STEPS[index + 1];
    message(events, "step", `Step: ${state.step}.`);
    if (state.step === "draw") {
      // In normal two-player Magic, the starting player skips their first draw.
      const firstTurnDuel = state.players.length === 2 && state.turnNumber === 1;
      if (!firstTurnDuel) draw(state, state.activePlayerId, events);
    }
  }
  state.priorityPlayerId = state.activePlayerId;
}

/** Pure transaction API: every accepted command returns a new state and event list. */
export function applyAction(previous: GameState, action: GameCommand): GameResult {
  const state = structuredClone(previous);
  const events: GameEvent[] = [];
  const actor = player(state, action.playerId);
  hasPriority(state, actor.id);

  switch (action.type) {
    case "PLAY_LAND": {
      ensure(mainPhaseWithEmptyStack(state, actor.id), "Lands can only be played on your main phase with an empty stack.");
      ensure(actor.landsPlayedThisTurn === 0, "You have already played a land this turn.");
      const c = card(state, action.cardId);
      ensure(c.zone === "hand" && c.ownerId === actor.id, "Land must be in your hand.");
      ensure(c.definition.types.includes("Land"), "That card is not a land.");
      leaveHand(state, actor.id, c.id, "battlefield");
      actor.battlefield.push(c.id);
      actor.landsPlayedThisTurn++;
      state.consecutivePasses = 0;
      message(events, "land", `${actor.name} plays ${c.definition.name}.`);
      break;
    }
    case "TAP_FOR_MANA": {
      const c = card(state, action.cardId);
      ensure(c.zone === "battlefield" && c.controllerId === actor.id, "You don't control that permanent.");
      ensure(!c.tapped, "This permanent is already tapped.");
      ensure(c.definition.producesMana, "This permanent cannot produce mana in this prototype.");
      c.tapped = true;
      actor.mana[c.definition.producesMana]++;
      state.consecutivePasses = 0;
      message(events, "mana", `${actor.name} taps ${c.definition.name} for {${c.definition.producesMana}}.`);
      break;
    }
    case "CAST_SPELL": {
      const c = card(state, action.cardId);
      ensure(c.zone === "hand" && c.ownerId === actor.id, "Spell must be in your hand.");
      ensure(!c.definition.types.includes("Land"), "Lands are not cast as spells.");
      const instant = c.definition.types.includes("Instant");
      ensure(instant || mainPhaseWithEmptyStack(state, actor.id), "That spell needs main-phase timing and an empty stack.");
      if (c.definition.effect?.kind === "damage-player") {
        ensure(action.targetPlayerId, "This spell needs a target player.");
        player(state, action.targetPlayerId);
      } else {
        ensure(action.targetPlayerId === undefined, "This spell cannot select a player target.");
      }
      spendMana(actor.mana, c.definition.manaCost);
      leaveHand(state, actor.id, c.id, "stack");
      state.stack.push({
        id: `stack:${state.nextStackId++}`, cardId: c.id,
        controllerId: actor.id, targetPlayerId: action.targetPlayerId,
      });
      state.consecutivePasses = 0;
      message(events, "cast", `${actor.name} casts ${c.definition.name}.`);
      break;
    }
    case "PASS_PRIORITY": {
      state.consecutivePasses++;
      message(events, "pass", `${actor.name} passes priority.`);
      if (state.consecutivePasses === state.players.length) {
        state.consecutivePasses = 0;
        if (state.stack.length > 0) resolveTop(state, events);
        else advanceStep(state, events);
        state.priorityPlayerId = state.activePlayerId;
      } else {
        state.priorityPlayerId = nextPlayer(state, actor.id);
      }
      break;
    }
  }
  return { state, events };
}

/** Never expose this full state to remote opponents: it contains hidden hands/libraries. */
export function getPublicState(state: GameState) {
  return {
    turnNumber: state.turnNumber, activePlayerId: state.activePlayerId,
    priorityPlayerId: state.priorityPlayerId, step: state.step,
    stack: state.stack.map((item) => ({
      id: item.id, controllerId: item.controllerId, targetPlayerId: item.targetPlayerId,
      card: state.cards[item.cardId].definition.name,
    })),
    players: state.players.map((p) => ({
      id: p.id, name: p.name, life: p.life, handCount: p.hand.length,
      libraryCount: p.library.length, graveyard: p.graveyard.map((id) => state.cards[id].definition.name),
      battlefield: p.battlefield.map((id) => ({
        id, name: state.cards[id].definition.name, tapped: state.cards[id].tapped,
      })),
      mana: p.mana,
    })),
  };
}
