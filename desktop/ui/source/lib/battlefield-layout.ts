const CARD_ASPECT = 88 / 63;
export const BATTLEFIELD_CARD_SCALE = 1.2;
export const ATTACHMENT_STEP = { x: 14, y: 6 };
export const OVERVIEW_ATTACHMENT_STEP = { x: 5, y: 2 };

export type BattlefieldRowLayout = {
  cardWidth: number; cardHeight: number; spacing: number; rotationInset: number;
  contentWidth: number; contentHeight: number; cardTop: number;
  attackLabelTop: number; attackLabelTappedTop: number; attackLabelHoveredTop:number; attackLabelTappedHoveredTop:number;
  scrollX: boolean; scrollY: boolean;
};
export type BattlefieldZone = "creatures" | "other" | "lands";
export type BattlefieldLayoutOptions = {
  attachments?: number[]; overviewWidth?: number; scrollReserve?: number; attackLabelHeight?: number;
};
export function calculateBattlefieldSplit(creatureCount: number, otherCount: number) {
  const share = (Math.max(0, creatureCount) + 1) / (Math.max(0, creatureCount) + Math.max(0, otherCount) + 2);
  const creaturePercent = Math.round((55 + share * 15) * 10) / 10;
  return { creaturePercent, otherPercent: Math.round((100 - creaturePercent) * 10) / 10 };
}
function preferredWidth(count: number) {
  return count <= 3 ? 118 : count <= 5 ? 110 : count <= 7 ? 102 : count <= 10 ? 92 : count <= 14 ? 80 : 68;
}
/** Measured viewport -> card geometry and a complete, scrollable row footprint. */
export function calculateBattlefieldRowLayout(containerWidth: number, containerHeight: number, cardCount: number, zone: BattlefieldZone = "other", options: BattlefieldLayoutOptions = {}): BattlefieldRowLayout {
  const count = Math.max(0, Math.floor(cardCount));
  if(!count)return {cardWidth:68,cardHeight:95,spacing:0,rotationInset:18,contentWidth:0,
    contentHeight:Math.max(0,containerHeight-(options.scrollReserve??0)),cardTop:8,
    attackLabelTop:106,attackLabelTappedTop:92.5,attackLabelHoveredTop:103.9,attackLabelTappedHoveredTop:93.86,
    scrollX:false,scrollY:false};
  const attachments = Array.from({length:count}, (_, index) => Math.max(0, options.attachments?.[index] ?? 0));
  const step = options.overviewWidth ? OVERVIEW_ATTACHMENT_STEP : ATTACHMENT_STEP;
  const attachmentWidth = attachments.reduce((sum, n) => sum + n * step.x, 0);
  const attachmentHeight = Math.max(0, ...attachments.map(n => n * step.y));
  const reserve = options.scrollReserve ?? 0;
  const labelHeight = options.attackLabelHeight ?? 0;
  const width = Math.max(1, containerWidth - 6 - (options.scrollReserve ?? 0));
  const height = Math.max(1, reserve ? (containerHeight - 12 - reserve - labelHeight - attachmentHeight) / 1.04 : containerHeight - 8 - labelHeight - attachmentHeight);
  const scale = BATTLEFIELD_CARD_SCALE * (zone === "lands" ? 1 : 1.25);
  const maximum = Math.min(options.overviewWidth ?? Math.round(118 * scale), Math.max(42, Math.floor(height / CARD_ASPECT)));
  const minimum = Math.min(maximum, options.overviewWidth ? 32 : Math.round(52 * BATTLEFIELD_CARD_SCALE));
  const gap = count <= 5 ? 12 : count <= 9 ? 8 : zone === "lands" ? 3 : 5;
  const noOverlapWidth = Math.floor((width - attachmentWidth - gap * Math.max(0, count - 1)) / Math.max(1, count));
  const cardWidth = Math.round(Math.max(minimum, Math.min(options.overviewWidth ?? preferredWidth(count) * scale, maximum, noOverlapWidth)));
  const cardHeight = Math.round(cardWidth * CARD_ASPECT);
  // Reserve the transformed tapped-card edges, counter controls and selection stroke.
  const rotationInset = Math.ceil((cardHeight * 1.04 - cardWidth) / 2) + 6;
  const usableWidth = Math.max(cardWidth, width - rotationInset * 2);
  const availableStep = count > 1 ? (usableWidth - cardWidth - attachmentWidth) / (count - 1) : cardWidth;
  const minStep = cardWidth * (zone === "creatures" ? .5 : zone === "lands" ? .22 : .38);
  // Attached groups retain their entire diagonal span before the next group starts.
  const spacing = count <= 1 ? 0 : attachmentWidth ? gap : Math.round((Math.max(minStep, Math.min(cardWidth + gap, availableStep)) - cardWidth) * 10) / 10;
  const contentWidth = count ? cardWidth * count + attachmentWidth + spacing * Math.max(0, count - 1) + rotationInset * 2 : 0;
  const hoverInset = Math.ceil(6 + cardHeight * .02);
  const verticalFootprint = cardHeight + attachmentHeight + labelHeight + hoverInset * 2;
  const contentHeight = Math.max(containerHeight - reserve, verticalFootprint);
  const cardTop = Math.max(hoverInset, (contentHeight - cardHeight - attachmentHeight - labelHeight) / 2);
  return {cardWidth,cardHeight,spacing,rotationInset,contentWidth,contentHeight,cardTop,
    attackLabelTop:cardTop + cardHeight + 3,
    attackLabelTappedTop:cardTop + (cardHeight + cardWidth) / 2 + 3,
    attackLabelHoveredTop:cardTop + cardHeight * 1.02 - 4 + 3,
    attackLabelTappedHoveredTop:cardTop + (cardHeight + cardWidth * 1.04) / 2 + 3,
    scrollX:contentWidth > containerWidth,scrollY:contentHeight > containerHeight - reserve};
}
