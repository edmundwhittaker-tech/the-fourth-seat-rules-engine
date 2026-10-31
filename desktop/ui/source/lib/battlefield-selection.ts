export type SelectionRect = { left:number; top:number; right:number; bottom:number; width:number; height:number };

export function normalizedSelectionRect(startX:number,startY:number,endX:number,endY:number):SelectionRect{
  const left=Math.min(startX,endX);const top=Math.min(startY,endY);const right=Math.max(startX,endX);const bottom=Math.max(startY,endY);
  return {left,top,right,bottom,width:right-left,height:bottom-top};
}

export function sufficientlyIntersects(card:SelectionRect,marquee:SelectionRect,minimumRatio=.16){
  const width=Math.max(0,Math.min(card.right,marquee.right)-Math.max(card.left,marquee.left));
  const height=Math.max(0,Math.min(card.bottom,marquee.bottom)-Math.max(card.top,marquee.top));
  const cardArea=Math.max(1,card.width*card.height);
  return width*height/cardArea>=minimumRatio;
}

export function isTypingTarget(target:EventTarget|null){
  if(!(target instanceof HTMLElement))return false;
  return !!target.closest("input, textarea, select, [contenteditable='true'], [role='textbox']");
}

/** Marquee selection only considers the portion actually visible in a scroll viewport. */
export function clipSelectionRect(card:SelectionRect, viewport:SelectionRect):SelectionRect|null {
  const left=Math.max(card.left,viewport.left);const top=Math.max(card.top,viewport.top);
  const right=Math.min(card.right,viewport.right);const bottom=Math.min(card.bottom,viewport.bottom);
  return right>left&&bottom>top?{left,top,right,bottom,width:right-left,height:bottom-top}:null;
}
