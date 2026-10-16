# Ten frozen commander plans: implementation and review notes

This experiment is **not activated**, linked into the gateway, pushed, or deployed. These are deterministic heuristics for the exact deck pool in `integrations/fourth-seat/gateway/ai-decks.json`, imported 2026-10-16. They are not claims of tournament strength or completed full-game validation. Legal moves and costs remain XMage's responsibility.

## What is actually implemented

`DeckPlan.java` contains each frozen deck's complete unique card-name membership and shared, hand-authored roles for specific cards. Roles apply only when the named card belongs to that commander's frozen list. Modal/double-faced names normalize to their front face. Unclassified cards use their public mana value, creature flag, power and toughness rather than an invented oracle text. A named card can have several real-world roles, but this first implementation deliberately assigns only one primary role.

Scoring uses the player's visible hand, own public battlefield, opponents' public battlefields, public creature subtype/attacking flags, and controller/target metadata for public stack objects. Giada/Krenko/Lathril/Gishath tribal bonuses use actual public Angel/Goblin/Elf/Dinosaur subtypes; missing subtype data receives no inferred tribal membership. It does not read opponent hands, deck order, or the server's hidden state. Positive scores rank legal actions; negative scores normally prefer passing, subject to the policy's separate legal-target adjustment.

Common sequencing:
- Play legal lands; Aesi lands receive an additional engine bonus.
- Prioritize early ramp and card-advantage engines, especially with a short hand.
- Develop the commander and then reward the relevant army, large-creature, spell, or landfall plan.
- Preserve targeted removal for a material opposing permanent, rather than removing a small creature merely because a target exists. Creature removal bodies still retain development value.
- Prefer sweepers when the largest opposing creature army exceeds ours by at least three. This is a rough count comparison, not a lethal/wipe simulation.
- Cast counters only with a legal target ID matching a public spell whose controller is known to be an opponent. Respond with protection when an opposing public spell explicitly targets the player or an owned permanent, or a recognized broad destroy/exile spell threatens an army. Indestructibility answers destroy/damage wipes but not exile; Teferi's Protection can answer either. Redirect spells do not receive an untargeted-wipe bonus. This remains a limited semantic approximation, not proof that a selected mode works. Aetherize additionally requires at least three actual public attacking creatures, not merely an opposing board during combat. Attacker destinations are not available, so attacks on another opponent can still trigger this heuristic.
- Avoid blind sacrifice activations. Mana abilities are left to payment callbacks, not repeatedly activated for a positive policy score.
- Temporary team finishers are favored only with three creatures and a recognized precombat/begin-combat/blockers window. There is no combat damage proof.

## Giada, Font of Hope: angels

Actual cards emphasized: Starnheim Aspirant, Pearl Medallion, Urza's Incubator, Giada; Folk Hero, Firemane Commando, Inspiring Overseer; Righteous Valkyrie, Lyra Dawnbringer, Archangel of Thune; Swords to Plowshares, Generous Gift; Flawless Maneuver, Teferi's Protection.

Implemented: prioritize cost reduction and Giada before expensive threats; boost subsequent creature development when Giada is present; value draw engines and an established army's payoffs. Avoid a premature Akroma's Will or protective instant on an empty stack.

Limits: the creature bonus requires the actual public Angel subtype; unknown subtype data gets no tribal bonus. No exact Giada counter math, lifegain threshold, alternate-win, meld, protection-color, or commander mana-restriction planning.

## Krenko, Mob Boss: goblins

Actual cards emphasized: Skirk Prospector, Springleaf Drum; Goblin Chieftain, Goblin Warchief, Lightning Greaves; Impact Tremors, Goblin Bombardment, Shared Animosity; Skullclamp, Goblin Ringleader, Rundvelt Hordemaster; Chaos Warp, Bolt Bend.

Implemented: favor commander development with a creature base; add a Krenko-present bonus to Impact Tremors and haste lords; reward broad-board payoffs and card draw. Legal Krenko activations remain high-value commander actions. Reserve reactive redirection until a public threat appears.

Limits: Goblin counts now use public subtype data rather than all creatures. No token doubling projection, haste/summoning-sickness simulation, deterministic sacrifice loop, exact sacrifice-fodder ranking, or Skullclamp equip/death-chain planning. Engine legality guards activations but does not supply strategic timing.

## Lathril, Blade of the Elves: elves

Actual cards emphasized: Llanowar Elves, Elvish Mystic, Priest of Titania, Elvish Archdruid; Beast Whisperer, Realmwalker, Shamanic Revelation; Shaman of the Pack, Overrun, Overwhelming Stampede; Tyvar's Stand, Golgari Charm, Vitalize.

Implemented: establish cheap acceleration and draw, then Lathril and an army; reward finishers only when a board exists. Vitalize gets a specific positive score when the commander is present, at least eleven public Elves exist, and fewer than ten are untapped.

Limits: Vitalize now counts actual public Elves and their tapped state, but this remains a coarse setup signal, not proof of an available Lathril drain activation; no summoning-sickness, chosen costs, infect, or exact damage-to-token calculation. Untap/protection modal decisions remain engine fallback.

## Ghalta, Primal Hunger: power ramp

Actual cards emphasized: Llanowar Elves, Fanatic of Rhonas, Selvala, Heart of the Wilds; Pugnacious Hammerskull, Gigantosaurus, Steel Leaf Champion; Garruk's Uprising, The Great Henge, Tribute to the World Tree; Beast Within, Collective Resistance.

Implemented: early acceleration, then high-power bodies before Ghalta. The commander receives an additional bonus proportional to visible own creature power, and high-power hand creatures receive extra development weight while Ghalta is absent. Card draw is valued to keep the threat chain moving.

Limits: XMage, not the policy, calculates Ghalta's discount. No exact devotion, mana-pip, sacrifice-draw, X-spell, or recursion planning; some newer cards retain generic public-stat valuation.

## Goreclaw, Terror of Qal Sisma: large-creature chain

Actual cards emphasized: Nature's Lore, Emerald Medallion, Gwenna, Eyes of Gaea; Goreclaw; Guardian Project, Garruk's Uprising, Soul of the Harvest; Craterhoof Behemoth, Overwhelming Stampede, Zopandrel, Hunger Dominus; Beast Within, Heroic Intervention.

Implemented: Goreclaw is more valuable when the hand contains power-four creatures. Those creatures receive a bonus after Goreclaw is on board. Early cost reduction and draw engines feed the large-creature chain; established-board finishers are rewarded.

Limits: no exact power-modification or cost-reduction graph, enter-the-battlefield chain simulation, Craterhoof lethal proof, or instant-speed Yeva timing analysis.

## Torbran, Thane of Red Fell: red damage

Actual cards emphasized: Sol Ring, Ruby Medallion, Neheb, the Eternal; Torbran; Mechanized Warfare, Fiery Emancipation, City on Fire, Solphim, Mayhem Dominus; Boltwave, Price of Progress; Chaos Warp, Deflecting Swat.

Implemented: establish mana and commander, then increase the score of damage payoffs when Torbran is present. Public-board threat and reactive-answer rules keep scarce interaction from being used without a reason. Jeska's Will is less attractive with almost no hand to spend its resources on. Lightning Bolt has an exact named 3-damage estimate, increased to 5 with own Torbran present; a matching opposing player at or below that life receives strong priority over an irrelevant creature. Nonlethal face burn is preserved, as are Bolts that cannot kill the selected creature by this estimate.

Limits: no replacement-effect ordering, red-source/color verification, exact Price of Progress damage, self-damage/Manabarbs risk, or general face-damage lethal proof. The Lightning Bolt estimate does not model prevention, protection, other replacement effects, already-marked creature damage, or player loss-prevention effects. Burn target choice is only as good as the generated legal candidates and generic target evaluation.

## Talrand, Sky Summoner: spells and drakes

Actual cards emphasized: Arcane Signet, Sapphire Medallion; Talrand; Ponder, Preordain, Consider, Opt; Archmage Emeritus, Rhystic Study, Reconnaissance Mission; Counterspell, Arcane Denial, Negate; Favorable Winds, Murmuring Mystic.

Implemented: develop mana and Talrand, then boost noncreature draw/body-role spells while the commander is present. Avoid spending ordinary cantrips immediately before a plausible commander turn with a full hand. Counters need an identifiable opposing public stack target; own spells are never intentionally countered by this rule. With Talrand on board during own main phase, a counter in hand, and public mana-production hints available, decline proactive actions when no hint leaves the counter's printed mana value and a rough blue requirement.

Limits: no full instant/sorcery type field, counter-specific restrictions beyond legal-candidate generation, exact draw-before-land sequencing, or spell-chain simulation. Cantrips may still be spent before Talrand to find resources. The reserve check is deliberately approximate: it compares printed values rather than actual modified costs, does not allocate blue between the new spell and the counter, cannot recover conditional mana restrictions lost in upstream hints, and does nothing when hints are unavailable. It is not a guarantee that the reserved counter can be paid for.

## Aesi, Tyrant of Gyre Strait: lands and landfall

Actual cards emphasized: Nature's Lore, Three Visits, Cultivate, Harrow; Exploration, Azusa, Lost but Seeking, Oracle of Mul Daya; Aesi, Tatyova, Benthic Druid; Scute Swarm, Rampaging Baloths, Ancient Greenwarden; Counterspell, Heroic Intervention.

Implemented: ramp toward Aesi; reward subsequent lands and land-ramp actions with the engine active. Aesi is worth more with lands in hand. Exploration/Burgeoning/Azusa are discounted when no land is in hand; landfall payoffs receive a land-in-hand bonus.

Limits: no library/landfall stack simulation, fetchland ordering, extra-land allowance tracking, graveyard land inventory, exact token growth, or Crop Rotation sacrifice planning.

## Gishath, Sun's Avatar: dinosaur ramp

Actual cards emphasized: Farseek, Nature's Lore, Three Visits, Thunderherd Migration, Urza's Incubator; Gishath; Pantlaza, Sun-Favored, Guardian Project, The Great Henge; Etali, Primal Storm, Ghalta, Stampede Tyrant, Quartzwood Crasher; Swords to Plowshares, Heroic Intervention.

Implemented: stronger ramp preference below eight lands, but a specific additional commander bonus prioritizes a legal Gishath cast over more ordinary ramp. Value follow-up dinosaur-sized creatures, draw engines, and established-board finishers.

Limits: lands are a resource proxy, not total available colored mana. No hidden top-deck lookahead or reveal-hit prediction beyond publicly known subtypes, attack/block proof, or library stacking for Congregation at Dawn.

## Adeline, Resplendent Cathar: human tokens

Actual cards emphasized: Knight of the White Orchid, Oketra's Monument; Adeline; Mentor of the Meek, Welcoming Vampire, Rumor Gatherer; Anointed Procession, Cathars' Crusade, Divine Visitation; Swords to Plowshares, Teferi's Protection, Akroma's Will.

Implemented: develop commander and token infrastructure; Anointed Procession and Cathars' Crusade receive an extra bonus with Adeline present. Card-advantage engines are prioritized; wide-board payoffs rise with creature count; avoid temporary finishers without an army or after the recognized attack window.

Limits: no exact Human subtype/count, attack-trigger distribution among three opponents, token replacement-effect arithmetic, Cathars' Crusade counter simulation, or complete protection coverage for every untargeted wipe.

## Mulligan behavior and known limitations

The first two keep decisions require 2–5 recognized lands and at least one nonland of mana value three or less. Two-land Aesi/Gishath hands additionally need a cheap ramp card. Mono-color plans require a rough matching color source; Lathril and Aesi require green plus their secondary color or a ramp proxy; Gishath requires green and a second Naya color or a ramp proxy. From two mulligans onward the rule tolerates any 2–5-land non-all-land hand to avoid excessive mulligans.

Color detection is **approximate**, using basic-land names, a short explicit dual-land list, and mana symbols in public text. It does not distinguish symbols in activation costs from production, parse colored casting costs, solve fetch targets, model enters-tapped conditions, or account correctly for tribal-only mana. An arbitrary cheap ramp card is not proof of fixing. MDFC front faces are not counted as lands unless the input's land flag says they are. The policy is not a London-bottoming optimizer.

## Validation boundary

`DeckPlanTest` exercises all ten plans through `CommanderPolicy`, with positive development, an unnecessary-answer counterexample, an opposing-stack response, an own-stack veto, and commander development. Each plan also has a distinct commander-synergy counterfactual (including Gishath cast-versus-ramp). Additional checks cover combat payoff timing prerequisites, Aesi land-ramp synergy, rough Naya mulligans, cross-deck role isolation, actual Talrand pass-versus-develop decisions with tight, sufficient, and absent mana hints, untargeted destruction versus exile protection, redirection rejection for untargeted wipes, and actual-attacker versus idle-board Aetherize timing. The current run passes 103 DeckPlanTest checks under JDK 17 compiled with --release 8. These are deterministic synthetic observation tests, not full XMage matches, win-rate evidence, complete card-rules coverage, or proof of correct inherited modal/combat decisions.

## Frozen source fingerprints

Pool file SHA-256: `9e5625b2060db2ddd457ade4b67f623716e301f77cb72178efe052920e94748c`.

- Giada, Font of Hope: https://archidekt.com/decks/23045205; content SHA-256 `3151e8efcfd2015b3368959a584e9f311f1d42bbbd170aab727ec7764178fa82`.
- Krenko, Mob Boss: https://archidekt.com/decks/18019886; content SHA-256 `c9da1ceae0c8f4b60e64cb1ee77b4b70af825789058d631477697bc2686299b0`.
- Lathril, Blade of the Elves: https://archidekt.com/decks/24644469; content SHA-256 `2776678d8316efc2f70e318ec105d1cff0d82db1025004df7f3b02485139e7d7`.
- Ghalta, Primal Hunger: https://archidekt.com/decks/19894242; content SHA-256 `138ba174f22eb3ab2ea4c9737b6707016fc325516459542fa33748a45d188d51`.
- Goreclaw, Terror of Qal Sisma: https://archidekt.com/decks/22625596; content SHA-256 `61bc2ac5e63149f87a2f54e010f04d3953013c2f4fe65b8de670f7096c252310`.
- Torbran, Thane of Red Fell: https://archidekt.com/decks/11353536; content SHA-256 `6fd9eb8988eff20ede76d19b2bed98ba7ada297f7e6f3d0983b87e4f12f62d93`.
- Talrand, Sky Summoner: https://archidekt.com/decks/5319321; content SHA-256 `d9b6682f7c6185f868ce7206051042513e3bf4fa505c3edb90e7bae005149c36`.
- Aesi, Tyrant of Gyre Strait: https://archidekt.com/decks/23009292; content SHA-256 `e68cfa61708c89865f7a55ddc9ca657e11bb8528307a3f219f2e6a222032e2bf`.
- Gishath, Sun's Avatar: https://archidekt.com/decks/25055843; content SHA-256 `0b89e9f8944fdff4d1644b8993fcc3bd32be719d0f98f11112ef4d69e6c30084`.
- Adeline, Resplendent Cathar: https://archidekt.com/decks/2755449; content SHA-256 `06282d4ddfb031d476e6b6bfb9e8938af4d9cd3c19ed2bb5b2b584c7c36e726e`.
