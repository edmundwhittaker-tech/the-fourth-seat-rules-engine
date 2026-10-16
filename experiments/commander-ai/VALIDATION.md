# Validation and readiness boundary

## Current evidence

- The isolated plugin compiles against XMage `825d1205acca114825f235c6993f1e5a6a6db9fa`, using JDK 17 and Java 8 bytecode compatibility.
- The pure policy suite passes 227 checks: 13 activation, 68 combat, 24 shared-policy, and 122 deck-plan checks.
- The real-engine suite passes 117 scenarios plus 27 additional-cost contract checks on the 11:22:26 build using the Maven-resolved runtime classpath, actual `GameCommanderImpl`, real card classes, real cost payment and stack resolution. Its evidence and scenario source are in `adapter/test/`.
- The latest nine-game mixed-deck run completed naturally (414 turns), but failed its acceptance gate with 197 rejected activations and a harness classpath duplication issue. Shared affordability/payment-dialog fixes have passed the expanded focused suite. Actual rollback-context cleanup also passes. A subsequent frozen inventory completed seven games and hit two genuine Scute-trigger performance timeouts, with eight rejected activations. Corrected fast-pass, combat, payment and optional-draw source now passes the expanded focused suite; a fresh full nine-game acceptance run is **in progress**. Earlier exploratory runs are retained as defect evidence, not a readiness certificate. Do not interpret a green focused suite as proof of full-game readiness.

## Per-deck focused evidence

All ten have funded actual command-zone casting and unfunded rejection scenarios, plus policy-level positive/counter cases. The commander-tax scenario validates a second command-zone cast. Representative deck-specific real scenarios are:

| Deck | Executed strategy/counter evidence |
| --- | --- |
| Giada | Commander before follow-up Angel; hold protection without a threat; Vanquisher's Banner chooses Angel; Entreat pays XXWWW correctly with floating mana and Sol Ring; reject insufficient white. |
| Krenko | Cast and resolve token maker before tap; tapped activation rejected; multiplication resolves; lethal Grenade sacrifices expendable Goblin rather than commander. |
| Lathril | Menace recognizes one versus two distinct blockers; ten other untapped Elves allow drain, nine do not; drain resolves against every opponent; preserve the live drain during combat; protect the threatened Elf; pump the current attacker, not an idle creature, and hold pump during upkeep. |
| Ghalta | Actual command-zone discounted GG cast; attack the available lethal opponent; trample attacks through a small chump while dangerous counterexamples preserve defense. |
| Goreclaw | Develop rather than waste a postcombat finisher; Kamahl does not animate the same land repeatedly; Gwenna restricted mana pays the supported creature ability and double-green creature. |
| Torbran | Amplified Bolt chooses a lethal opponent; no imaginary bonus while commander is absent; By Force pays and destroys the actual one/two artifact targets. |
| Talrand | Counter a meaningful threat at low life; ignore harmless bait and own spell; preserve UU in main phase; use a cantrip at an opponent's end step. |
| Aesi | Cast already-funded commander before using land drop; still play land when commander is not affordable. |
| Gishath | Choose the connectable opponent; project trample through a chump with danger counterexamples. |
| Adeline | Other attackers can trigger the commander while she stays back. |

Shared real regressions include correct Boros Charm mode and survival through Wrath, Tyvar's Stand survival through Swords, Flare's normal payment, nonredundant equipment, Castle Embereth/Myriad Landscape tap-source reservation, public subtype/changeling extraction, opponent-hand privacy, valid versus invalid Giada conditional mana, Selvala input-mana dependencies with commander tax, typed beneficial Heraldic Banner color choices, and affordable versus unaffordable Offspring/Spree costs. Cost-contract checks separately prove probes leave original abilities/tags unchanged and do not spend real mana or double-charge costs.

## What readiness does not mean

This is an experimental deterministic heuristic player for the ten frozen lists, not human-level play or a complete semantic implementation of every card. Candidate generation has a soft time budget because upstream enumeration is eager. Unknown activated-effect semantics are conservatively passed. Strategic scoring does not solve multiplayer politics, commander damage, full combat math, arbitrary combos, or every replacement/optional dialog. Restricted mana remains engine-validated, with conservative approximations for strategic availability. Successful focused scenarios do not measure win rate.

Only a separate test server should be considered after review. Nothing here registers the plugin, changes the production gateway, merges a branch, starts a server, or deploys code. Follow `activation/README.md` for the explicit manual installation and rollback boundary.
