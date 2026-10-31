import type { GameView, ManaPoolView } from './views';
import type { FeedbackPrompt } from './vendor/feedback/types';

export const manaColors = [
  { key: 'white', symbol: 'W', name: 'White' }, { key: 'blue', symbol: 'U', name: 'Blue' },
  { key: 'black', symbol: 'B', name: 'Black' }, { key: 'red', symbol: 'R', name: 'Red' },
  { key: 'green', symbol: 'G', name: 'Green' }, { key: 'colorless', symbol: 'C', name: 'Colorless' },
] as const;
export type ManaColor = typeof manaColors[number]['key'];
export function controlledMana(game: GameView, prompt: FeedbackPrompt | null) {
  const player = game.players?.find(p => p.controlled && !p.hasLeft && p.playerId === prompt?.playerId)
    ?? game.players?.find(p => p.controlled && !p.hasLeft && p.playerId === game.myPlayerId)
    ?? game.players?.find(p => p.controlled && !p.hasLeft);
  const pool: ManaPoolView = {white:0,blue:0,black:0,red:0,green:0,colorless:0};
  for (const {key} of manaColors) {
    const value = player?.manaPool?.[key];
    pool[key] = typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.floor(value) : 0;
  }
  return { player, pool, total: Object.values(pool).reduce((sum,n) => sum+n,0) };
}
// Only the engine can decide whether a unit can pay this cost (including restrictions).
// No optimistic decrement: wait for the next authoritative GameView.
export function manaPayment(game: GameView, prompt: FeedbackPrompt | null, color: ManaColor) {
  const { player, pool } = controlledMana(game, prompt);
  if (prompt?.mode !== 'mana' || !prompt.gameId || !prompt.playerId || player?.playerId !== prompt.playerId || !manaColors.some(c=>c.key===color) || pool[color] <= 0) return null;
  return { action: 'sendPlayerManaType', args: { gameId: prompt.gameId, playerId: prompt.playerId, manaType: color.toUpperCase() } };
}
