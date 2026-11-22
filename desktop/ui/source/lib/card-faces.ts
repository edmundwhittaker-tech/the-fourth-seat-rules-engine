import type { CardFace, CardData } from "./types.ts";
import type { CardInstance } from "./game-types.ts";

function legacyBackImage(image: string) {
  return image.includes("/front/") ? image.replace("/front/", "/back/") : "";
}

export function cardFaces(card: CardData): CardFace[] {
  const stored=card.printing?.faces?.length?card.printing.faces:card.faces;
  if(stored?.length)return stored;
  if(!(card.printing?.doubleFaced||card.name.includes(" // ")))return [];
  const names=card.name.split(" // ").map((name)=>name.trim());
  const backImage=legacyBackImage(card.image);
  if(names.length<2||!backImage)return [];
  return [
    {name:names[0],manaCost:card.manaCost,oracleText:card.oracleText,typeLine:card.typeLine,image:card.image,artCrop:card.artCrop,power:card.power,toughness:card.toughness,loyalty:card.loyalty},
    {name:names[1],manaCost:"",oracleText:"",typeLine:card.typeLine,image:backImage,power:card.power,toughness:card.toughness,loyalty:card.loyalty},
  ];
}

export function activeCardData(item: CardInstance): CardData {
  const faces=cardFaces(item.card);
  if(faces.length<2)return item.card;
  const face=faces[Math.abs(item.faceIndex??0)%faces.length];
  return {...item.card,...face,quantity:item.card.quantity};
}

export function nextFaceIndex(item: CardInstance) {
  const faces=cardFaces(item.card);
  return faces.length>1?((item.faceIndex??0)+1)%faces.length:item.faceIndex??0;
}
