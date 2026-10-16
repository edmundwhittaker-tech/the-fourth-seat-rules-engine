# Opt-in XMage adapter

Source pin: Adamychen/xmage-nexus at `825d1205acca114825f235c6993f1e5a6a6db9fa`.
Entry point: `mage.player.ai.ComputerPlayerFourthSeat` (standard name/range/skill constructor).
This is a separate experimental class extending basic `ComputerPlayer`, not MAD/ComputerPlayer7.
No default player registration, launcher, existing engine, deployment or runtime configuration changes are included.

## Isolated build

Run `MAVEN=/path/to/mvn ./scripts/build-adapter.sh /path/to/pinned/xmage /new/disposable/build` from this experiment.
The script verifies the exact commit, archives it to a new directory, overlays policy and adapter sources in the basic AI module, then builds the module with its real dependencies. It refuses an existing output directory and does not modify its input checkout. Maven's local dependency repository is inside that disposable directory. Build requires Java, Maven and access to dependencies. Compiling is not game validation.

The output jar contains both existing basic AI and the new class. Do not replace a deployed jar or register this class until separately authorized and validation is complete. A future dedicated test-server registration would name the new class and its jar through XMage's normal playerTypes mechanism. This document does not activate it.

## Information and decision boundary

- `observe` extracts only own hand, public commanders, all players' life and hand counts, public battlefield and public stack information. Face-down names/rules are redacted. The immutable policy model contains no `Game`, engine card or player references.
- `getPlayable(game, true)` is intentional: in this exact upstream API, the `hidden` boolean includes this player's own hand. `false` would incorrectly omit ordinary spells and lands. It does not enable reading opponents' hands. Upstream internally makes a legality-calculation simulation copy; this adapter does not run full-state search, score that copy or expose it to the policy.
- Legal candidates come from `getPlayable` plus `getPlayableOptions`, and chosen abilities execute through `activateAbility`, retaining engine timing, cost, targeting and activation checks. Enumeration is not proof execution will succeed. A rejected activation passes instead of faking success.
- All engine priority callbacks are considered, without blanket skipping upkeep/draw/end step/opponents' turns. Mana producers are left to engine payment logic.
- Candidate scoring is capped at 128. Enumeration checks a monotonic 75 ms budget between engine calls; ranking reserves at least 5 ms. Upstream `getPlayable` and eager `getPlayableOptions` cannot be interrupted by this adapter, so this is NOT a hard wall-clock bound. Large modal/target fan-out can exceed it.
- A per-turn-step guard suppresses repeated zero-cost actions from the same source/ability. This prevents trivial loops but can conservatively omit useful repeated zero-cost actions.
- Logs expose reason, candidate count, elapsed time and activation outcome, not hidden hand contents. Runtime exceptions during priority yield a logged pass; fatal errors are not swallowed.

## Choices and limits

Own-hand mulligans use the deck plan, stop after three retries, and reset between games. Normal XMage handles the mulligan rules and bottoming; the policy does not optimize London bottoming.
Typed own-library card-selection callbacks use tutor ranking only when the engine supplies the offered cards and a non-target library choice. Typed own-hand discard and own-battlefield sacrifice callbacks likewise score only engine-eligible choices and recheck eligibility before adding them. Other choices inherit the basic bot. The creature-type helper is overridden to avoid its upstream library scan.

Combat uses `FourthSeatCombatAdapter` and its public-board policy. Complex simultaneous requirements, attack costs, planeswalker/battle attacks, commander damage and optimal multi-blocking are not solved. XMage remains the legality authority. Unsupported modal, replacement, X-payment and special-card dialogs retain upstream basic behavior; compatibility is not a claim of strategic competence. No win rate, universal deck mastery or hard think-time guarantee is claimed.

Mana observations include at most 16 own-player `getManaAvailable` options (W/U/B/R/G/colorless/generic/any). These are approximate strategic hints, not payment proofs: upstream's `ManaOptions` loses conditional-mana restrictions. Computing these options makes another engine legality copy and may exceed the soft enumeration budget. Candidate mana value remains printed/base ability cost, not an advertised exact net/discounted payment; real affordability and payment are validated by XMage.

Candidate diversity: base abilities are deterministically ordered with stack interaction first, then lands, then removal, then other actions. At most eight target/modal options per base ability enter scoring, ordered by a simple public-target threat heuristic. This avoids one large target set filling the entire 128-candidate pool; truncation is still heuristic and can miss the best move. At least one non-mana candidate is retained even if engine enumeration consumes the soft budget, avoiding repeated all-budget passes in the same expensive state.

Build customization: `MAVEN_SETTINGS=/path/settings.xml` selects an existing Maven settings file; `MAVEN_REPO=/path/disposable-cache` explicitly overrides the default isolated dependency cache. Additional Maven arguments may follow the two required paths. `MAVEN` selects the executable and standard `JAVA_HOME`/`MAVEN_OPTS` apply. No TLS verification bypass is included.

Public tribe features use XMage `hasSubtype` (including changeling) for Angel, Goblin, Elf, Dinosaur and Human only. Face-down cards export no subtype labels. Mutable adapter state consists of mulligan count, last turn/step and the zero-cost-action set; the copy constructor copies scalar fields and makes an independent set, and `init` resets all of them. No mutable policy or Game reference is stored in the player.
