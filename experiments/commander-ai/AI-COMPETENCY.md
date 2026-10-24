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

## Follow-up corrections (24 October 2026)

The next candidate retains the native mulligan fix and adds regression-backed
activity improvements. It prefers combat with a ready, overwhelming landfall
creature, limits redundant ramp with an already enormous funded army, and may
decline Burgeoning's optional land placement in that state. These are strategic
choices; mandatory triggers and native cost/target legality remain unchanged.

Combat values damage only up to the defender's remaining life and separately
values elimination. This keeps reserves instead of sending hundreds of surplus
tokens at an already defeated opponent, while still accepting useful token
trades through blockers.

A new full-game failure exposed native HybridManaCost.testPay accepting colorless
mana for a colored hybrid pip. The adapter chooses a funded branch on a
disposable mana-producer demand; the actual spell keeps its original hybrid
costs and native payment. Actual Eclipsed Elf casts using Eclipsed Realms,
Cavern of Souls, Forests, and Devoted Druid now have regression coverage, including
insufficient resources and the wrong chosen creature type.

Queued priority for an eliminated player is ignored. Unexpected decision errors
now include bounded class/stack locations without exception messages or private
card choices. Slow-turn sampling reserves samples for later turns instead of
spending its entire allowance on one early stall.

The focused suite now contains **364 real-engine scenarios, 27 cost contracts,
and 292 policy checks**. Passing these is not a release or a claim of competitive
strength: the exact source must also pass full-game, gateway, and container gates
before deployment. Preserve failed-game evidence when evaluating the candidate.

A subsequent per-deck review caught Talrand spending its mana on routine instant
draw during its own upkeep/draw step. Those spells now wait for main phase when
the stack is empty, while responses to an incoming threat remain available.
Actual four-Island tests verify that Talrand then casts its commander before
Think Twice. Native ReturnToHand outcomes are classified as hostile for removal
spells, so Into the Roil and Blink of an Eye no longer bounce an unthreatened
own Sol Ring merely because their spell also offers a draw effect.
