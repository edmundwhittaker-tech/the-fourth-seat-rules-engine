export function formatActiveTurnTime(milliseconds: number) {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  const seconds = totalSeconds % 60;
  const totalMinutes = Math.floor(totalSeconds / 60);
  const minutes = totalMinutes % 60;
  const hours = Math.floor(totalMinutes / 60);
  if (hours > 0) return `${hours}:${String(minutes).padStart(2,"0")}:${String(seconds).padStart(2,"0")}`;
  return `${String(totalMinutes).padStart(2,"0")}:${String(seconds).padStart(2,"0")}`;
}

export function activeTurnTime(
  seatId: string,
  activePlayerId: string,
  enabled: boolean,
  activeTurnStartedAt: number | null | undefined,
  totals: Record<string,number> | undefined,
  now = Date.now(),
) {
  const stored = Math.max(0, totals?.[seatId] ?? 0);
  if (!enabled || seatId !== activePlayerId || !activeTurnStartedAt) return stored;
  return stored + Math.max(0, now - activeTurnStartedAt);
}
