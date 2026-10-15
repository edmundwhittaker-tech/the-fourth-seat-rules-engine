import { createGame } from "./engine.js";
// Fictional sample cards: mechanics tests without distributing copyrighted card data.
const grove = { name: "Oakwood", types: ["Land"], producesMana: "G" };
const cliff = { name: "Cliffside", types: ["Land"], producesMana: "R" };
const river = { name: "Riverbed", types: ["Land"], producesMana: "U" };
const creature = { name: "Stonefoot Scout", types: ["Creature"], manaCost: "{1}{G}" };
const bolt = {
    name: "Spark Lance", types: ["Instant"], manaCost: "{R}",
    effect: { kind: "damage-player", amount: 3 },
};
const insight = {
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
