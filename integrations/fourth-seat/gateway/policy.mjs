import { aiDeckPool } from './ai-decks.mjs';
import { createHmac, timingSafeEqual } from 'node:crypto';

export class Problem extends Error {
  constructor(message, status = 400) { super(message); this.status = status; }
}
export function equal(a, b) {
  const x = Buffer.from(String(a ?? '')), y = Buffer.from(String(b ?? ''));
  return x.length === y.length && timingSafeEqual(x, y);
}
export function identity(secret, key) {
  if (!/^[a-f0-9]{64}$/.test(key ?? '')) throw new Problem('Your browser identity is missing. Reopen Rules Engine play.');
  return 'F' + createHmac('sha256', secret).update(key).digest('hex').slice(0, 12);
}
export function signTicket(secret, user, expires = Date.now() + 60_000) {
  const body = Buffer.from(JSON.stringify({ user, expires })).toString('base64url');
  return body + '.' + createHmac('sha256', secret).update(body).digest('base64url');
}
export function readTicket(secret, ticket) {
  const [body, signature] = String(ticket ?? '').split('.');
  if (!body || !equal(signature, createHmac('sha256', secret).update(body).digest('base64url'))) throw new Problem('Reconnect to your engine session.', 401);
  const data = JSON.parse(Buffer.from(body, 'base64url').toString());
  if (!/^F[a-f0-9]{12}$/.test(data.user) || !Number.isFinite(data.expires) || data.expires < Date.now()) throw new Problem('Reconnect to your engine session.', 401);
  return data.user;
}
const actions = new Set(['sendPlayerUUID', 'sendPlayerBoolean', 'sendPlayerInteger', 'sendPlayerString', 'sendPlayerManaType', 'sendPlayerAction']);
const playerActions = new Set(['CONCEDE', 'MANA_AUTO_PAYMENT_ON', 'MANA_AUTO_PAYMENT_OFF', 'MANA_AUTO_PAYMENT_RESTRICTED_ON', 'MANA_AUTO_PAYMENT_RESTRICTED_OFF', 'PASS_PRIORITY_UNTIL_END_TURN', 'PASS_PRIORITY_UNTIL_STACK_RESOLVED']);
export function authorizeAction(message, gameId) {
  if (!message || !actions.has(message.action)) throw new Problem('That control is unavailable in Rules Engine play.', 403);
  if (!gameId || message.args?.gameId !== gameId) throw new Problem('You are not seated in that game.', 403);
  if (message.action === 'sendPlayerAction' && !playerActions.has(message.args.action)) throw new Problem('That engine action is unavailable.', 403);
  return { action: message.action, args: { ...message.args, gameId } };
}
export function publicRoom(room, user) {
  if (!room.players.some(p => p.user === user && p.kind === 'human')) throw new Problem('Join this engine table first.', 403);
  return { code: room.code, status: room.status, humanSeats: room.humanSeats, gameId: room.gameId, isHost: room.host === user,
    lobbyEditable: true,
    aiDecks: aiDeckPool.map(entry => ({ id: entry.id, name: entry.deck.name, commander: entry.deck.commanders[0].cardName, sourceUrl: entry.source.url })),
    players: room.players.map(({ user: id, name, kind, commander, deckName, seat, aiDeckId: deckId }) => ({ id, name, kind, commander, deckName, seat, deckId })) };
}
