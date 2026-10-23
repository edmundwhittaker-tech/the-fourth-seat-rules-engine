# AI activity and decision quality

The AI is a bounded, deck-aware heuristic running inside the real XMage engine.
Legal moves, successful game completion, and good strategy are separate checks.
These tests do not establish a human skill rating or a multiplayer win rate.

## Opening-hand defect found on 24 October 2026

The released `7633cc66` AI overrides the card-list chooser, but XMage's London
mulligan calls `chooseTarget(Outcome, Target, Ability, Game)` instead. That call
fell through to the generic ComputerPlayer chooser, which valued lands as cheap
discard fodder. A three-Mountain opening became a one-Mountain five-card hand.
The exact native callback reproduced this eight times in eight trials. In a
fresh four-player game, Krenko then played one land and cast no spells over five
observed main phases; the game still finished and passed the old liveness gate.

The correction handles source-less own-hand discard choices before the first
phase. XMage already labels this pregame state turn 1, so testing `turn == 0`
would miss it. The chooser preserves scarce lands and unique known colours,
trims excess lands, and prefers bottoming expensive payoffs over early plays.
Every selection still passes the native target filter and moves through XMage.
The one-free-mulligan rule, deck lists and regular in-game discard path are unchanged.

`FourthSeatOpeningHandTest` tests the actual callback and native library movement
in the unmodified initial game state. Three checks fail on the old binary; all
six pass on the corrected binary. The complete engine suite runs this test in CI.

## Repeatable evaluation

In GitHub Actions, run **Verify Fourth Seat engine integration** on the desired
`integration/xmage` commit. It builds the pinned engine and bridge, runs policy
and actual-engine scenarios, and plays nine real four-bot games covering all ten
frozen decks in rotated pods. The downloadable integration artifact contains
per-game `results.json` and full synthetic-game logs. This is entirely cloud-based.

Review each seat's lands, spells, commander casts, activations and attacks, as
well as failures and slow turns. `ownMainPhasesObserved` and
`activityReviewFlags` now expose `NO_SPELLS_AFTER_THREE_TURNS` and, where applicable,
`LOW_LAND_DEVELOPMENT`. These are review signals, not proof of an illegal move or
a strategic defect: an unlucky draw can produce the same symptoms. Missing
observations are not silently counted as successful development. A control deck
not attacking is likewise not enough evidence to make it attack indiscriminately.

Use fixed actual-engine positions to verify a proposed correction, then rerun
the entire suite and packaged-game checks. The current suite covers command-zone
casting and tax, mana restrictions and alternative costs, targeting and protection,
multiplayer combat, deck synergies, growth loops, and opening-hand resource choices.
Natural game completion alone is insufficient. UUID tie breaks and time budgets
mean that a random seed does not guarantee identical decisions or later draws.

The live game's inactive seat cannot be identified conclusively from these
synthetic tests. Live logs and its commander/turn are needed to distinguish this
opening defect from a disconnected seat, deliberate passing, or an expensive
token-heavy resolution. Deploy only after the exact-commit service/container CI
gates pass and after checking there are no active games to interrupt.
