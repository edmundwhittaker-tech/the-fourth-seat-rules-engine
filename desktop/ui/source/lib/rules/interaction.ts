import type { EngineState } from './session';
import type { GameView } from './views';

// Use only the playable-action snapshot attached to this exact priority callback.
// Mana taps alone should not force a stop, but unfamiliar data fails closed.
export function knownPriorityWindow(game: GameView | null, options: Record<string, unknown>): boolean {
  if (!game || game.special || Object.hasOwn(options, 'possibleAttackers') || Object.hasOwn(options, 'possibleBlockers') || options.specialButton) return false;
  const me = game.players?.find(player => player.controlled && player.hasPriority && !player.hasLeft);
  if (!me) return false;
  const objects = game.canPlayObjects?.objects;
  if (!objects || typeof objects !== 'object' || Array.isArray(objects)) return false;
  const fields = ['basicManaAbilities', 'basicPlayAbilities', 'basicCastAbilities', 'other'] as const;
  return Object.values(objects).every(stats => stats && fields.every(field => Array.isArray(stats[field]))
    && Object.keys(stats).every(field => fields.includes(field as typeof fields[number])));
}

export function emptyPriorityWindow(game: GameView | null, options: Record<string, unknown>): boolean {
  return knownPriorityWindow(game, options) && Object.values(game!.canPlayObjects!.objects!).every(stats =>
    stats.basicPlayAbilities!.length === 0 && stats.basicCastAbilities!.length === 0 && stats.other!.length === 0);
}

export function shouldAutoPass(state: EngineState): boolean {
  return !!state.gameId && !!state.promptKey && !state.error && !state.ended && state.priorityWindowEmpty
    && state.prompt?.method === 'GAME_SELECT' && state.prompt.title === 'Your priority';
}

export type TurnPass = { gameId: string; turn: number; activePlayerId: string; untilPlayerId?: string; checkpoint?: string; skipResponses?: boolean };
export function currentTurnPass(state: EngineState): TurnPass | null {
  const game = state.game;
  if (!state.gameId || state.ended || !game?.activePlayerId || !Number.isInteger(game.turn)
    || !game.players?.some(player => player.controlled && !player.hasLeft)) return null;
  return { gameId: state.gameId, turn: game.turn, activePlayerId: game.activePlayerId };
}
// Opt-in cross-turn yielding requires an unambiguous controlled player and a known
// opposing active player. Any missing/changed identity or backward turn fails closed.
export function currentNextTurnPass(state: EngineState): TurnPass | null {
  const intent=currentTurnPass(state),game=state.game;
  const controlled=game?.players?.filter(player=>player.controlled&&!player.hasLeft)??[];
  if(!intent||controlled.length!==1||controlled[0].playerId===intent.activePlayerId
    ||!game?.players?.some(player=>player.playerId===intent.activePlayerId&&!player.hasLeft))return null;
  return {...intent,untilPlayerId:controlled[0].playerId};
}
export function matchesTurnPass(state: EngineState, intent: TurnPass | null): boolean {
  const turn = currentTurnPass(state);
  if(!intent||!turn||intent.gameId!==turn.gameId)return false;
  if(intent.skipResponses&&!state.game?.players?.some(player=>player.controlled&&!player.hasLeft&&player.playerId===intent.activePlayerId))return false;
  if(intent.untilPlayerId){
    const current=currentNextTurnPass(state);
    return !!current&&current.untilPlayerId===intent.untilPlayerId&&current.turn>=intent.turn;
  }
  return intent.turn===turn.turn&&intent.activePlayerId===turn.activePlayerId;
}
export function canPassTurn(state: EngineState): boolean {
  return !!currentTurnPass(state) && !!state.promptKey && !state.error
    && state.prompt?.method === 'GAME_SELECT' && state.prompt.title === 'Your priority';
}

export type PriorityStops = { beforeCombat: boolean; opponentEndStep: boolean };
export const defaultPriorityStops: PriorityStops = { beforeCombat: false, opponentEndStep: false };
export function readPriorityStops(value: string | null): PriorityStops {
  try {
    const parsed = JSON.parse(value ?? '{}');
    return { beforeCombat: parsed?.beforeCombat === true, opponentEndStep: parsed?.opponentEndStep === true };
  } catch { return defaultPriorityStops; }
}
export function priorityStopKey(state: EngineState, stops: PriorityStops): string | null {
  const game = state.game, turn = currentTurnPass(state);
  if (!turn || !game) return null;
  const step = normalize(game.step);
  const beforeCombat = stops.beforeCombat && ['BEGIN_COMBAT', 'BEGINNING_OF_COMBAT'].includes(step);
  const opponentEndStep = stops.opponentEndStep && ['END_TURN', 'END_STEP'].includes(step)
    && !game.players?.some(p => p.controlled && p.playerId === game.activePlayerId);
  return beforeCombat || opponentEndStep ? `${turn.gameId}:${turn.turn}:${turn.activePlayerId}:${beforeCombat ? 'combat' : 'end'}` : null;
}

// Only public, decision-relevant state. Priority owner, phase transitions, clocks,
// card artwork and ordinary mana taps do not create another response question.
// A new stack object (even with the same name), targets, combat, or board change does.
export function priorityCheckpoint(game: GameView): string {
  const cards = (zone: GameView['stack'] | undefined) => Object.entries(zone ?? {}).map(([id, card]) => [
    id, card.id, card.parentId, card.controllerId, card.name, card.targets, card.counters,
    card.power, card.toughness, card.loyalty, card.defense, card.transformed,
    (card as {damage?: number}).damage, (card as {attachments?: string[]}).attachments,
    (card as {phasedIn?: boolean}).phasedIn,
  ]);
  return JSON.stringify({
    stack: cards(game.stack), combat: game.combat ?? [],
    players: game.players?.map(p => [p.playerId, p.controlled, p.hasLeft, p.life, p.counters,
      cards(p.battlefield), cards(p.graveyard), cards(p.exile), p.monarch, p.initiative]),
  });
}
export function smartPassUnchanged(state: EngineState, intent: TurnPass | null): boolean {
  // End turn explicitly yields all known priority windows for this own turn.
  // Required choices, stale legal data and turn/identity changes still stop it.
  return matchesTurnPass(state, intent) && canPassTurn(state) && state.priorityWindowKnown
    && !!state.game && (intent?.skipResponses===true || intent?.checkpoint === priorityCheckpoint(state.game));
}

export type PlayerAnswer = { action: string; args: Record<string, unknown> };
export function playerPromptAnswers(state: EngineState): Map<string, PlayerAnswer> {
  const answers = new Map<string, PlayerAnswer>(), prompt = state.prompt;
  if (!prompt || state.ended || !['uuid', 'string'].includes(prompt.mode)) return answers;
  for (const player of state.game?.players ?? []) {
    if (player.hasLeft) continue;
    const option = prompt.options.find(option => option.value === player.playerId)
      ?? (prompt.mode === 'string' ? prompt.options.find(option => option.value === player.name) : undefined);
    if (option) answers.set(player.playerId, { action: prompt.mode === 'uuid' ? 'sendPlayerUUID' : 'sendPlayerString', args: { gameId: prompt.gameId, value: option.value } });
  }
  return answers;
}

const normalize = (value: string | null | undefined) => (value ?? '').trim().toUpperCase().replace(/[\s-]+/g, '_');
const steps: Record<string, [number, string]> = {
  UNTAP: [0, 'Untap'], UPKEEP: [0, 'Upkeep'], DRAW: [0, 'Draw'],
  PRECOMBAT_MAIN: [1, 'Main 1'], PRECOMBAT_MAIN_PHASE: [1, 'Main 1'],
  BEGIN_COMBAT: [2, 'Beginning of combat'], BEGINNING_OF_COMBAT: [2, 'Beginning of combat'],
  DECLARE_ATTACKERS: [2, 'Declare attackers'], DECLARE_BLOCKERS: [2, 'Declare blockers'],
  FIRST_COMBAT_DAMAGE: [2, 'First-strike damage'], FIRST_STRIKE_DAMAGE: [2, 'First-strike damage'],
  COMBAT_DAMAGE: [2, 'Combat damage'], END_COMBAT: [2, 'End of combat'], END_OF_COMBAT: [2, 'End of combat'],
  POSTCOMBAT_MAIN: [3, 'Main 2'], POSTCOMBAT_MAIN_PHASE: [3, 'Main 2'],
  END_TURN: [4, 'End step'], END_STEP: [4, 'End step'], CLEANUP: [4, 'Cleanup'],
};
export const turnPhases = ['Beginning', 'Main 1', 'Combat', 'Main 2', 'Ending'];
export function enginePhase(game: Pick<GameView, 'phase' | 'step' | 'activePlayerId'>): { index: number; step: string } {
  if (!game.activePlayerId) return { index: -1, step: 'Opening hands' };
  const known = steps[normalize(game.step)] ?? steps[normalize(game.phase)];
  if (known) return { index: known[0], step: known[1] };
  const phase = normalize(game.phase);
  const index = phase === 'BEGINNING' ? 0 : phase === 'COMBAT' ? 2 : phase === 'ENDING' ? 4 : -1;
  return { index, step: game.step?.replaceAll('_', ' ') || game.phase?.replaceAll('_', ' ') || 'Waiting for the engine' };
}
