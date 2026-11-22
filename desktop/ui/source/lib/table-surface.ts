export type TableSurfaceBounds = {
  width: number;
  height: number;
  commander: { left: number; right: number; top: number; bottom: number };
};

/** Presentation geometry only: one perimeter and texture origin for the whole stone. */
export function tableSurfaceOutline({ width, height, commander }: TableSurfaceBounds) {
  if (![width, height, commander.left, commander.right, commander.top, commander.bottom].every(Number.isFinite)
    || width < 80 || height < 80 || commander.right <= commander.left || commander.bottom <= commander.top) return null;
  const edge = 1.5;
  const right = width - edge;
  const shoulderY = Math.max(32, Math.min(commander.top, height - 40));
  const bottom = Math.min(height - edge, Math.max(shoulderY + 32, commander.bottom));
  const leftWing = Math.max(edge + 2, commander.left - 16);
  const rightWing = Math.min(right - 2, commander.right + 16);
  const taper = Math.min(14, (rightWing - leftWing) / 10, (bottom - shoulderY) / 4);
  const lowerLeft = leftWing + taper;
  const lowerRight = rightWing - taper;
  const leftCorner = Math.min(15, (leftWing - edge) / 2);
  const rightCorner = Math.min(15, (right - rightWing) / 2);
  const leftShoulder = Math.min(46, leftWing - edge - leftCorner);
  const rightShoulder = Math.min(46, right - rightWing - rightCorner);
  const curve = Math.min(44, (bottom - shoulderY) / 4, (lowerRight - lowerLeft) / 4);
  const sideSlope = (taper - 4) / (bottom - shoulderY - curve);
  const shoulderHandle = sideSlope * (curve - 4);
  const lowerHandle = sideSlope * curve;
  const corner = Math.min(15, shoulderY / 3);
  const n = (value: number) => Number(value.toFixed(2));
  // Cubic shoulders meet the horizontal board edge tangentially. Both lower
  // sides taper gently inward, with broad sweeps into the commander's base.
  const path = [
    `M ${n(edge + corner)} ${edge}`,
    `H ${n(right - corner)} Q ${n(right)} ${edge} ${n(right)} ${n(edge + corner)}`,
    `V ${n(shoulderY - rightCorner)} Q ${n(right)} ${n(shoulderY)} ${n(right - rightCorner)} ${n(shoulderY)}`,
    `H ${n(rightWing + rightShoulder)}`,
    `C ${n(rightWing + rightShoulder / 3)} ${n(shoulderY)} ${n(rightWing - 4 + shoulderHandle)} ${n(shoulderY + 4)} ${n(rightWing - 4)} ${n(shoulderY + curve)}`,
    `L ${n(lowerRight + lowerHandle)} ${n(bottom - curve)}`,
    `Q ${n(lowerRight)} ${n(bottom)} ${n(lowerRight - curve)} ${n(bottom)}`,
    `H ${n(lowerLeft + curve)}`,
    `Q ${n(lowerLeft)} ${n(bottom)} ${n(lowerLeft - lowerHandle)} ${n(bottom - curve)}`,
    `L ${n(leftWing + 4)} ${n(shoulderY + curve)}`,
    `C ${n(leftWing + 4 - shoulderHandle)} ${n(shoulderY + 4)} ${n(leftWing - leftShoulder / 3)} ${n(shoulderY)} ${n(leftWing - leftShoulder)} ${n(shoulderY)}`,
    `H ${n(edge + leftCorner)} Q ${edge} ${n(shoulderY)} ${edge} ${n(shoulderY - leftCorner)}`,
    `V ${n(edge + corner)} Q ${edge} ${edge} ${n(edge + corner)} ${edge} Z`,
  ].join(" ");
  return { path, width, height, shoulderY, bottom, leftWing, rightWing, lowerLeft, lowerRight };
}
