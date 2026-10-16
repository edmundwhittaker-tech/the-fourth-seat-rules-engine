import { parseFeedback } from './vendor/feedback/parse.ts';
import type { FeedbackPrompt } from './vendor/feedback/types.ts';
import type { GameView, CardView } from './views.ts';

export type EnginePlayer = { id: string; name: string; kind: 'human' | 'ai'; commander: string; deckName: string };
export type EngineRoom = { code: string; status: 'lobby' | 'starting' | 'game' | 'finished'; humanSeats: number; gameId?: string; isHost: boolean; players: EnginePlayer[] };
export type EngineFrame = { type: string; method?: string; objectId?: string; seq?: number; messageId?: number; data?: unknown; error?: string; ok?: boolean; requestId?: string };
export type EngineState = { gameId: string | null; game: GameView | null; prompt: FeedbackPrompt | null; promptKey: string | null; selectable: string[]; log: string[]; error: string; ended: string | null; seq: number };
export const emptyEngineState = (): EngineState => ({ gameId: null, game: null, prompt: null, promptKey: null, selectable: [], log: [], error: '', ended: null, seq: -1 });
export const records = <T>(value: Record<string,T> | T[] | null | undefined): T[] => Object.values(value ?? {});
const record = (value: unknown): Record<string,unknown> => value && typeof value === 'object' ? value as Record<string,unknown> : {};
const clean = (value: unknown) => String(value ?? '').replace(/<[^>]*>/g,'').replaceAll('{this}','this card');
export function reduceEngineFrame(state: EngineState, frame: EngineFrame): EngineState {
  if (frame.type === 'error' || frame.type === 'result' && frame.ok === false) return { ...state, error: frame.error ?? 'The engine could not apply that action.' };
  if (frame.type !== 'event') return state;
  if (frame.seq !== undefined && frame.seq <= state.seq) return state;
  const data = record(frame.data), method = frame.method ?? '';
  if (method === 'START_GAME') return { ...emptyEngineState(), gameId: String(data.gameId ?? frame.objectId), seq: frame.seq ?? state.seq };
  if (state.gameId && frame.objectId && /^GAME_/.test(method) && state.gameId !== frame.objectId) return state;
  const embedded = record(data.gameView ?? (Array.isArray(data.players) ? data : null));
  const game = Array.isArray(embedded.players) ? embedded as unknown as GameView : state.game;
  let next = { ...state, game, seq: frame.seq ?? state.seq, gameId: /^GAME_/.test(method) ? frame.objectId ?? state.gameId : state.gameId };
  if (method === 'GAME_OVER' || method === 'END_GAME_INFO') return { ...next, prompt: null, selectable: [], ended: clean(data.message ?? data.gameInfo ?? 'Game finished') };
  if (method === 'GAME_ERROR') return { ...next, error: clean(data.message ?? frame.data) };
  if (method === 'GAME_INFORM' || method === 'GAME_UPDATE_AND_INFORM' || method === 'CHATMESSAGE') {
    const message = clean(data.message ?? frame.data);
    if (message) next = { ...next, log: [...state.log, message].slice(-150) };
  }
  const feedback = parseFeedback(method, frame.objectId ?? next.gameId, { ...data, gameView: game });
  if (feedback || method === 'GAME_SELECT') {
    const options = record(data.options);
    const combatIds = [...records(options.possibleAttackers as string[]), ...records(options.possibleBlockers as string[])];
    const priority: FeedbackPrompt | null = method === 'GAME_SELECT' && !feedback ? {
      method, gameId: next.gameId ?? '', title: 'Your priority', message: clean(data.message ?? 'Play a card, activate an ability, or pass priority.'), mode: 'combat', options: [], min: 0, max: 1,
    } : null;
    const prompt = feedback ?? priority;
    next = { ...next, prompt, promptKey: String(frame.seq ?? frame.messageId ?? Date.now()), error: '',
      selectable: feedback?.mode === 'uuid' ? feedback.options.map(o => o.value) : combatIds.length ? combatIds : method==='GAME_SELECT'||feedback?.mode==='mana' ? Object.keys(game?.canPlayObjects?.objects ?? {}) : [] };
  }
  return next;
}
export function promptAnswer(prompt: FeedbackPrompt, value: string | number | boolean) {
  const action = prompt.mode === 'boolean' ? 'sendPlayerBoolean' : prompt.mode === 'integer' ? 'sendPlayerInteger' : prompt.mode === 'uuid' ? 'sendPlayerUUID' : 'sendPlayerString';
  return { action, args: { gameId: prompt.gameId, value: prompt.mode === 'boolean' ? value === true || value === 'true' : prompt.mode === 'integer' ? Number(value) : String(value) } };
}
export function deckForEngine(deck: { name: string; commander: { name: string }; cards: Array<{name: string; quantity: number}> }) {
  let designated = false;
  const cards = deck.cards.flatMap(card => {
    const commander = !designated && card.name === deck.commander.name;
    if (commander) designated = true;
    const amount = card.quantity - (commander ? 1 : 0);
    return amount > 0 ? [{ cardName: card.name, amount }] : [];
  });
  return { name: deck.name, cards, commanders: [{ cardName: deck.commander.name, amount: 1 }] };
}
export function namedCards(game: GameView | null): CardView[] {
  if (!game) return [];
  return [...records(game.myHand), ...records(game.stack), ...records(game.myHelperEmblems),
    ...(game.players ?? []).flatMap(p => [...records(p.battlefield), ...records(p.graveyard), ...records(p.exile), ...records(p.commandList as CardView[])]),
    ...[...records(game.revealed), ...records(game.lookedAt), ...records(game.exiles)].flatMap(z => records(z.cards))];
}
