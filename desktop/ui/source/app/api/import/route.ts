import type { CardData, CardFace, CardPrinting } from "../../../lib/types";
import { MAX_DECK_CARDS, MAX_DECK_ENTRIES } from "../../../lib/deck-safety.ts";
import { enforceRateLimit, readJsonBody, reportServerFailure, RequestProblem } from "../../../lib/request-safety.ts";

type ScryfallImage = { normal?: string; art_crop?: string };
type ScryfallCard = {
  id: string;
  oracle_id?: string;
  name: string;
  mana_cost?: string;
  oracle_text?: string;
  type_line?: string;
  colors?: string[];
  color_identity?: string[];
  power?: string;
  toughness?: string;
  loyalty?: string;
  image_uris?: ScryfallImage;
  card_faces?: Array<{ name?: string; mana_cost?: string; oracle_text?: string; type_line?: string; image_uris?: ScryfallImage; power?:string; toughness?:string; loyalty?:string }>;
  all_parts?: Array<{ component?: string; name?: string }>;
  set?: string;
  set_name?: string;
  collector_number?: string;
  released_at?: string;
  border_color?: string;
  frame_effects?: string[];
  promo_types?: string[];
  finishes?: string[];
  promo?: boolean;
  full_art?: boolean;
  layout?: string;
  prints_search_uri?: string;
};

export type ParsedDeckLine = {
  line: string;
  quantity: number;
  name: string;
  setCode?: string;
  collectorNumber?: string;
  flags: string[];
  foil: boolean;
};

const SCRYFALL_HEADERS = {
  "User-Agent": "TheFourthSeat/1.0",
  Accept: "application/json",
};

export function normalizeDoubleFacedName(name: string) {
  return name.replace(/\s+\/\s+/g, " // ").replace(/\s*\/\/\s*/g, " // ").replace(/\s+/g, " ").trim();
}

export function parseDeckLine(raw: string): ParsedDeckLine | null {
  const line = raw.trim();
  if (!line) return null;
  const quantityMatch = line.replace(/^\s*[-•]\s*/, "").match(/^(\d+)\s*(?:[xX×]\s*)?(.+)$/);
  if (!quantityMatch) return null;

  let remainder = quantityMatch[2].trim();
  const flags: string[] = [];
  while (true) {
    const flagMatch = remainder.match(/\s+(\*[^*]+\*|\[[^\]]+\])\s*$/);
    if (!flagMatch) break;
    flags.unshift(flagMatch[1]);
    remainder = remainder.slice(0, flagMatch.index).trim();
  }

  let setCode: string | undefined;
  let collectorNumber: string | undefined;
  const printingMatch = remainder.match(/\s+\(([a-z0-9]+)\)\s+([^\s]+)\s*$/i);
  if (printingMatch) {
    setCode = printingMatch[1].toLowerCase();
    collectorNumber = printingMatch[2];
    remainder = remainder.slice(0, printingMatch.index).trim();
  } else {
    const setOnlyMatch = remainder.match(/\s+\(([a-z0-9]+)\)\s*$/i);
    if (setOnlyMatch) {
      setCode = setOnlyMatch[1].toLowerCase();
      remainder = remainder.slice(0, setOnlyMatch.index).trim();
    }
  }

  const name = normalizeDoubleFacedName(remainder);
  if (!name) return null;
  return {
    line,
    quantity: Number(quantityMatch[1]),
    name,
    setCode,
    collectorNumber,
    flags,
    foil: flags.some((flag) => flag.replace(/[^a-z]/gi, "").toLowerCase() === "f" || flag.toLowerCase().includes("foil")),
  };
}

function cardImage(card: ScryfallCard) {
  const face = card.card_faces?.find((item) => item.image_uris?.normal) ?? card.card_faces?.[0];
  return { image: card.image_uris?.normal ?? face?.image_uris?.normal ?? "", artCrop: card.image_uris?.art_crop ?? face?.image_uris?.art_crop };
}

function faceData(card:ScryfallCard):CardFace[]{
  const frontImage=cardImage(card).image;
  return (card.card_faces??[]).map((face,index)=>({
    name:face.name??card.name.split(" // ")[index]?.trim()??card.name,
    manaCost:face.mana_cost??"",
    oracleText:face.oracle_text??"",
    typeLine:face.type_line??card.type_line??"",
    image:face.image_uris?.normal??(index===0?frontImage:frontImage.replace("/front/","/back/")),
    artCrop:face.image_uris?.art_crop,
    power:face.power,
    toughness:face.toughness,
    loyalty:face.loyalty,
  })).filter((face)=>!!face.image);
}

function printing(card: ScryfallCard): CardPrinting {
  const image = cardImage(card);
  return {
    id: card.id,
    setCode: card.set ?? "",
    setName: card.set_name ?? card.set?.toUpperCase() ?? "Unknown set",
    collectorNumber: card.collector_number ?? "",
    releasedAt: card.released_at,
    image: image.image,
    artCrop: image.artCrop,
    borderColor: card.border_color,
    frameEffects: card.frame_effects,
    promoTypes: card.promo_types,
    finishes: card.finishes,
    promo: card.promo,
    fullArt: card.full_art,
    layout: card.layout,
    doubleFaced: !!card.card_faces?.length,
    faces: faceData(card),
  };
}

function normalize(card: ScryfallCard, item: ParsedDeckLine): CardData {
  const face = card.card_faces?.[0];
  const selectedPrinting = printing(card);
  return {
    id: card.id,
    oracleId: card.oracle_id,
    name: card.name,
    manaCost: card.mana_cost ?? face?.mana_cost ?? "",
    oracleText: card.oracle_text ?? card.card_faces?.map((entry) => entry.oracle_text).filter(Boolean).join("\n\n") ?? "",
    typeLine: card.type_line ?? face?.type_line ?? "",
    colors: card.colors ?? [],
    colorIdentity: card.color_identity ?? [],
    power: card.power,
    toughness: card.toughness,
    loyalty: card.loyalty,
    image: selectedPrinting.image,
    artCrop: selectedPrinting.artCrop,
    quantity: item.quantity,
    tokenNames: card.all_parts?.filter((part) => part.component === "token" && part.name).map((part) => part.name as string),
    printing: selectedPrinting,
    printsSearchUri: card.prints_search_uri,
    layout: card.layout,
    foil: item.foil,
    faces: selectedPrinting.faces,
  };
}

function cardMatchesName(card: ScryfallCard, requested: string) {
  const normalized = normalizeDoubleFacedName(requested).toLowerCase();
  const fullName = normalizeDoubleFacedName(card.name).toLowerCase();
  return fullName === normalized || fullName.split(" // ")[0] === normalized || !!card.card_faces?.some((face) => face.name?.toLowerCase() === normalized);
}

async function collection(identifiers: Array<Record<string, string>>) {
  const response = await fetch("https://api.scryfall.com/cards/collection", {
    method: "POST",
    headers: { ...SCRYFALL_HEADERS, "Content-Type": "application/json" },
    body: JSON.stringify({ identifiers }),
  });
  if (!response.ok) throw new Error("Scryfall could not be reached. Please try the import again.");
  return await response.json() as { data?: ScryfallCard[] };
}

async function fuzzyCard(name: string) {
  const response = await fetch(`https://api.scryfall.com/cards/named?fuzzy=${encodeURIComponent(name)}`, { headers: SCRYFALL_HEADERS });
  if (!response.ok) return undefined;
  return await response.json() as ScryfallCard;
}

function isCommanderCandidate(card: CardData) {
  const text = `${card.typeLine}\n${card.oracleText}`.toLowerCase();
  return (text.includes("legendary creature") || text.includes("can be your commander")) && !text.includes("background");
}

function cardIdentity(card: CardData) { return card.oracleId ?? card.id; }
function parsedForError(item: ParsedDeckLine) {
  return { name:item.name, setCode:item.setCode, collectorNumber:item.collectorNumber, flags:item.flags };
}

export async function POST(request: Request) {
  try {
    const body = await readJsonBody<{ decklist?: string }>(request,100_000);
    await enforceRateLimit(request,"deck-import",String(body.decklist?.length??0),30,60*60*1000);
    const lines = (body.decklist ?? "").split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    if(!lines.length)return Response.json({error:"Paste at least one deck line."},{status:400});
    if(lines.length>MAX_DECK_ENTRIES)return Response.json({error:`Deck imports are limited to ${MAX_DECK_ENTRIES} lines.`},{status:400});
    if(lines.some((line)=>line.length>300))return Response.json({error:"One or more deck lines are too long."},{status:400});
    const parsed: ParsedDeckLine[] = [];
    const errors: Array<{ line:string;message:string;parsed?:ReturnType<typeof parsedForError> }> = [];

    for (const line of lines) {
      const item = parseDeckLine(line);
      if (!item || item.quantity < 1 || item.quantity>MAX_DECK_CARDS) errors.push({ line, message:"Use a reasonable quantity followed by a card name." });
      else parsed.push(item);
    }
    if(parsed.reduce((sum,item)=>sum+item.quantity,0)>MAX_DECK_CARDS)return Response.json({error:`Deck imports are limited to ${MAX_DECK_CARDS} total cards during beta.`},{status:400});

    const resolved = new Map<number, ScryfallCard>();

    const printingIndexes = parsed.map((item,index)=>({item,index})).filter(({item})=>item.setCode&&item.collectorNumber);
    for (let start=0;start<printingIndexes.length;start+=75) {
      const batch=printingIndexes.slice(start,start+75);
      const data=await collection(batch.map(({item})=>({set:item.setCode!,collector_number:item.collectorNumber!})));
      for (const {item,index} of batch) {
        const match=data.data?.find((card)=>card.set?.toLowerCase()===item.setCode&&card.collector_number?.toLowerCase()===item.collectorNumber?.toLowerCase());
        if(match)resolved.set(index,match);
      }
    }

    const nameIndexes=parsed.map((item,index)=>({item,index})).filter(({index})=>!resolved.has(index));
    for(let start=0;start<nameIndexes.length;start+=75){
      const batch=nameIndexes.slice(start,start+75);
      const data=await collection(batch.map(({item})=>({name:item.name})));
      for(const {item,index} of batch){const match=data.data?.find((card)=>cardMatchesName(card,item.name));if(match)resolved.set(index,match);}
    }

    for(const {item,index} of nameIndexes){
      if(resolved.has(index))continue;
      const card=await fuzzyCard(item.name);
      if(card)resolved.set(index,card);
      await new Promise((resolve)=>setTimeout(resolve,90));
    }

    const cards:CardData[]=[];
    parsed.forEach((item,index)=>{
      const card=resolved.get(index);
      if(card)cards.push(normalize(card,item));
      else errors.push({line:item.line,message:"Could not identify this card.",parsed:parsedForError(item)});
    });

    return Response.json({
      cards,
      errors,
      commanderCandidates:cards.filter(isCommanderCandidate).map(cardIdentity),
      total:cards.reduce((sum,card)=>sum+card.quantity,0),
    });
  } catch (error) {
    const text=error instanceof Error?error.message:"Deck import failed.";
    if(error instanceof RequestProblem)return Response.json({error:text},{status:error.status});
    if(text.includes("Scryfall"))return Response.json({error:text},{status:503});
    const reference=reportServerFailure("deck.import",error);
    return Response.json({ error:`Deck import failed safely. Reference ${reference}.`,diagnosticId:reference },{status:500});
  }
}
