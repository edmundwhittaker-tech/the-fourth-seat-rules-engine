const CARD_ASPECT_RATIO = 88 / 63;
export type HandLayout = {
  cardWidth:number; cardHeight:number; spacing:number; contentWidth:number; contentHeight:number;
  inset:number; bottomInset:number; scrollX:boolean; scrollY:boolean;
};
/** client dimensions include padding; callers supply the active computed padding. */
export function calculateHandLayout(containerWidth:number, containerHeight:number, handSize:number, padding = {width:40,height:6}):HandLayout {
  const count = Math.max(1, handSize);
  const usableWidth = Math.max(0, containerWidth - padding.width);
  const usableHeight = Math.max(0, containerHeight - padding.height - 12);
  const countMaximum = count <= 5 ? 144 : count <= 8 ? 132 : count <= 12 ? 112 : 96;
  const preferredRatio = count <= 5 ? 1.08 : count <= 8 ? .9 : count <= 12 ? .72 : .55;
  const widthFromHeight = Math.floor((usableHeight - 18) / (CARD_ASPECT_RATIO * 1.12));
  const widthFromContainer = Math.floor(Math.max(0, usableWidth - 24) / (1 + preferredRatio * (count - 1)));
  const cardWidth = Math.max(68, Math.min(countMaximum, widthFromHeight, widthFromContainer));
  const cardHeight = Math.round(cardWidth * CARD_ASPECT_RATIO);
  const inset = Math.ceil(cardWidth * .06) + 4;
  const bottomInset = Math.ceil(cardHeight * .06) + 4;
  const availableStep = count > 1 ? (usableWidth - inset * 2 - cardWidth) / (count - 1) : cardWidth;
  const step = count > 1 ? Math.max(cardWidth * .45, Math.min(cardWidth * preferredRatio, availableStep)) : cardWidth;
  const contentWidth = cardWidth + (count - 1) * step + inset * 2;
  const contentHeight = Math.max(usableHeight, cardHeight + bottomInset * 2 + 10);
  return {cardWidth,cardHeight,spacing:step-cardWidth,contentWidth,contentHeight,inset,bottomInset,
    scrollX:contentWidth > usableWidth,scrollY:contentHeight > usableHeight};
}
