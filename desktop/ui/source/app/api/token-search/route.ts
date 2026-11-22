import {tokenName} from "../../../lib/rules/art";
import type { CardData } from "../../../lib/types";

type SearchCard = {
  id: string; name: string; mana_cost?: string; oracle_text?: string; type_line?: string; colors?: string[]; color_identity?: string[]; power?: string; toughness?: string; loyalty?: string;
  image_uris?: { normal?: string; art_crop?: string };
  card_faces?: SearchCard[];
};

export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  const exact = params.get("exact") === "1";
  const requested = params.get("q")?.trim().slice(0,120);
  const query = exact && requested ? tokenName(requested) : requested;
  if (!query) return Response.json({ cards: [] });
  try {
    const url = `https://api.scryfall.com/cards/search?q=${encodeURIComponent(exact ? `is:token !"${query.replace(/["\\]/g, " ")}"` : `is:token ${query}`)}&unique=cards`;
    const response = await fetch(url, { headers: { "User-Agent": "TheFourthSeat/1.0", Accept: "application/json" }, signal:AbortSignal.timeout(8000) });
    if (!response.ok) return Response.json({ cards: [] }, {status:exact&&response.status!==404?503:200});
    const data = await response.json() as { data?: SearchCard[] };
    const printings=(data.data??[]).flatMap(card=>card.image_uris?.normal?[card]:(card.card_faces??[]).map((face,index)=>({...card,...face,id:card.id+':'+index})));
    const cards: CardData[] = printings.filter(card=>!exact||card.name.toLowerCase()===query.toLowerCase()).slice(0, exact ? 175 : 12).map((card) => ({ id: card.id, name: card.name, manaCost: card.mana_cost ?? "", oracleText: card.oracle_text ?? "", typeLine: card.type_line ?? "Token", colors: card.colors ?? [], colorIdentity: card.color_identity ?? [], power: card.power, toughness: card.toughness, loyalty: card.loyalty, image: card.image_uris?.normal ?? "", artCrop: card.image_uris?.art_crop, quantity: 1 }));
    return Response.json({ cards }, {headers:{"Cache-Control":"public, max-age=3600"}});
  } catch { return Response.json({ cards: [] }, {status:exact?503:200}); }
}
