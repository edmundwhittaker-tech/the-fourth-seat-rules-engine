# Published AI deck pool

The engine draws distinct decks without replacement for each new game and avoids the host commander. All lists are frozen, unchanged main-deck snapshots from public Archidekt decks, including the designated commander. Sideboards, maybeboards and tokens are not part of the Commander main deck. Different printings are aggregated by card name; no cards are added, removed or substituted.

These are Bracket 3 labels supplied by the authors or estimated by Archidekt, checked on 2026-10-16. They are not independent power-level certification. Popular commanders and straightforward themes guided selection; the lists are not claimed to be the ten most popular decks or equally strong.

| Commander | Theme | Published deck / author | Bracket label |
| --- | --- | --- | --- |
| Giada, Font of Hope | angels | [Giada bracket 3](https://archidekt.com/decks/23045205) — Puroa | Archidekt estimate: 3 |
| Krenko, Mob Boss | goblins | [Krenko Bracket 3](https://archidekt.com/decks/18019886) — Heavymetalfan81 | Author: 3 |
| Lathril, Blade of the Elves | elves | [Lathril - Verdant Dominance](https://archidekt.com/decks/24644469) — Degimod | Author: 3 |
| Ghalta, Primal Hunger | large creatures | [GHALTA, PRIMAL HUNGER](https://archidekt.com/decks/19894242) — jatabo | Author: 3 |
| Goreclaw, Terror of Qal Sisma | power-four creatures | [The Power of Four](https://archidekt.com/decks/22625596) — SunnyOfAThousandVoices | Author: 3 |
| Torbran, Thane of Red Fell | burn | [Torbran, Thane of Red Fell](https://archidekt.com/decks/11353536) — Polizei | Author: 3 |
| Talrand, Sky Summoner | drakes and spells | [Talrand](https://archidekt.com/decks/5319321) — Bublon98 | Archidekt estimate: 3 |
| Aesi, Tyrant of Gyre Strait | landfall | [Aesi](https://archidekt.com/decks/23009292) — SMGrubbin | Author: 3 |
| Gishath, Sun's Avatar | dinosaurs | [Gishath](https://archidekt.com/decks/25055843) — chester999 | Author: 3 |
| Adeline, Resplendent Cathar | human tokens | [Adeline](https://archidekt.com/decks/2755449) — Kdos | Archidekt estimate: 3 |

## Reproducible imports and validation

`gateway/ai-decks.json` records source URL, public author name, source update time, import time, bracket basis, and a SHA-256 of the exact card names and quantities. It includes factual deck lists, not copied primers, artwork or card rules text. Live source edits never change a game or deployment automatically.

From a cloud checkout, `python3 integrations/fourth-seat/tools/import-ai-decks.py` downloads the selected public lists into `build/ai-decks-candidate.json` for review. It refuses incomplete lists, multiple commanders, illegal cards, over three Game Changers, extra-turn cards, or mass land denial as flagged by the source data. Recheck the displayed bracket on each source page and review the entire diff before replacing the active file. Excluded categories take precedence over functional categories.

GitHub Actions resolves every card in all ten snapshots and runs the real pinned XMage Commander validator. The `ai-decks-result.json` artifact records per-list compatibility and content hashes. A list that cannot pass should be replaced by another complete published Bracket 3 list, never repaired with custom substitutions. The gateway validates selected lists before creating any table and caches those validated snapshots for that process. Failures produce an error; there is no fallback to the human's deck.

XMage enforces Commander legality and gameplay, not the social intent or expected win turn of a bracket. Bracket context: [Wizards' Commander Brackets update](https://magic.wizards.com/en/news/announcements/commander-brackets-beta-update-october-21-2025). Current bans: [Wizards' banned and restricted list](https://magic.wizards.com/en/banned-restricted-list).
