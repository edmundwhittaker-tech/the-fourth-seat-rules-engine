import type { EnginePlayer, EngineRoom } from './session';

/** Presentation only: engine identifiers must remain unchanged for actions and reconnects. */
export function enginePlayerName(player: EnginePlayer): string {
  return player.kind === 'ai' ? player.commander?.trim() || player.deckName?.trim() || player.name : player.name;
}

export function engineText(value: string, room: EngineRoom | null | undefined): string {
  const aliases = new Map<string, string>();
  for (const player of room?.players ?? []) {
    const name = enginePlayerName(player);
    if (player.id) aliases.set(player.id, name);
    if (player.kind === 'ai' && player.name && player.name !== name) aliases.set(player.name, name);
  }
  const text = value.replace(/<[^>]*>/g, '');
  if (!aliases.size) return text;
  const escaped = [...aliases.keys()].sort((a,b)=>b.length-a.length).map(key=>key.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'));
  return text.replace(new RegExp(`(?<![\\w-])(?:${escaped.join('|')})(?![\\w-])`, 'g'), match=>aliases.get(match) ?? match);
}
