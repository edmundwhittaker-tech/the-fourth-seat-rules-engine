export const SIGNED_PT_COUNTER = "P/T Modifier";

const LEGACY_POSITIVE_COUNTER = "+1/+1";
const LEGACY_NEGATIVE_COUNTER = "-1/-1";

export function isPowerToughnessCounter(name: string) {
  return name === SIGNED_PT_COUNTER || name === LEGACY_POSITIVE_COUNTER || name === LEGACY_NEGATIVE_COUNTER;
}

export function signedPowerToughnessModifier(counters: Record<string, number>) {
  if (SIGNED_PT_COUNTER in counters) return Number(counters[SIGNED_PT_COUNTER]) || 0;
  return (Number(counters[LEGACY_POSITIVE_COUNTER]) || 0) - (Number(counters[LEGACY_NEGATIVE_COUNTER]) || 0);
}

export function formatSignedPowerToughnessModifier(amount: number) {
  if (!amount) return "";
  const signed = amount > 0 ? `+${amount}` : String(amount);
  return `${signed}/${signed}`;
}

export function changeCardCounter(counters: Record<string, number>, name: string, delta: number) {
  const next = { ...counters };
  if (isPowerToughnessCounter(name)) {
    const signedDelta = name === LEGACY_NEGATIVE_COUNTER ? -delta : delta;
    const amount = Math.max(-999, Math.min(999, signedPowerToughnessModifier(counters) + signedDelta));
    delete next[LEGACY_POSITIVE_COUNTER];
    delete next[LEGACY_NEGATIVE_COUNTER];
    delete next[SIGNED_PT_COUNTER];
    if (amount) next[SIGNED_PT_COUNTER] = amount;
    return next;
  }
  const amount = Math.max(0, (Number(next[name]) || 0) + delta);
  if (amount) next[name] = amount;
  else delete next[name];
  return next;
}
