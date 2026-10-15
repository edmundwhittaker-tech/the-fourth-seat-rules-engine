import { createGame } from "./engine.js";
import type { CardDefinition } from "./types.js";

// Fictional sample cards: mechanics tests without distributing copyrighted card data.
const grove: CardDefinition = { name: "Oakwood", types: ["Land"], producesMana: "G" };
const cliff: CardDefinition = { name: "Cliffside", types: ["Land"], producesMana: "R" };
const river: CardDefinition = { name: "Riverbed", types: ["Land"], producesMana: "U" };
const creature: CardDefinition = { name: "Stonefoot Scout", types: ["Creature"], manaCost: "{1}{G}" };
const bolt: CardDefinition = {
  name: "Spark Lance", types: ["Instant"], manaCost: "{R}",
  effect: { kind: "damage-player", amount: 3 },
};
const insight: CardDefinition = {
  name: "Quick Study", types: ["Sorcery"], manaCost: "{1}{U}",
  effect: { kind: "draw", count: 2 },
};
const filler = [grove, cliff, river, creature, bolt, insight];

export function createDemoGame() {
  return createGame([
    { id: "ari", name: "Ari", library: [grove, cliff, grove, creature, bolt, river, insight, ...filler, ...filler] },
    { id: "blake", name: "Blake", library: [cliff, grove, river, bolt, creature, insight, grove, ...filler, ...filler] },
    { id: "casey", name: "Casey", library: [river, grove, cliff, insight, creature, bolt, grove, ...filler, ...filler] },
  ]);
}
