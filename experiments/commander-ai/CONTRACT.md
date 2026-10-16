# Implementation boundary

This experiment is inactive. No production configuration, registry, deployment script or gateway path is changed.

- `PolicyModel` consists of immutable Java 8-compatible value classes with no XMage references. It includes the bot's own hand; public per-seat life/hand counts/board; public stack controller/targets; attacking and five relevant creature subtypes; approximate own mana-production options. It contains no opponent hand identities or library order. Mana options are hints because upstream conditional mana modeling is incomplete.
- `DeckPlan` holds the exact ten frozen source memberships and audited roles, hand keep heuristics, card values and context-dependent action priorities.
- `CommanderPolicy` ranks only engine-supplied legal candidates. It applies deck intent, public-target threat weighting, Aesi land-drop sequencing, Krenko production sequencing, deterministic tie handling and a budget-safe land/pass fallback. Its `evaluate` helper is diagnostic; it is not a hidden full-state minimax search and is not claimed to drive every choice.
- `CombatPolicy` sees legal attack/block edges and public metrics. Actual engine callbacks validate declarations. Its value/risk model is approximate, not a complete damage simulator.
- `ComputerPlayerFourthSeat` extends basic `ComputerPlayer`, avoiding MAD's full-state search. `getPlayable(game, true)` includes **its own hand**, as required by this pinned API; upstream internal copies are legality calculations, not exposed to policy. Actual choices execute via XMage `activateAbility`, and payment/targets/resolution remain the engine's responsibility.
- Priority considers all windows. Enumeration has a soft budget and capped source/target diversity; upstream eager option expansion can exceed it. Unsupported dialogs inherit basic XMage behavior. A deadline is not a guarantee of optimal or human-strength play.

Exact source pin and operational restrictions are in `adapter/README.md`; per-deck behavior and source fingerprints are in `DECKS.md`. Do not interpret assertion counts or a compile as full-game performance validation.
