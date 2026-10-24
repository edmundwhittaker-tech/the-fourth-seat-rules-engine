# AI activity follow-up — 24 October 2026

Source commit: `532e6e20c2e6ea79ccdf09aec69c0daeddd4383d`.

**Status: supplemental tests pass; independent CI and live deployment pending.**

This follows the earlier [activity audit](ai-activity-audit-20261024.md). That
candidate was deliberately withheld after a failed progress gate. These changes
retain the opening-hand correction and address additional defects found while
reviewing actual games, not just whether the games finished.

## Corrections and their evidence

- **Opening resources:** London mulligan bottom choices preserve scarce lands and
  needed colours. The previous callback could reduce a three-land opening to one
  land while retaining expensive spells. Six native regressions cover the fix.
- **Main-phase development:** routine instant draw waits during the AI's own
  upkeep/draw step when the stack is empty. Talrand had been tapping out before
  its main phase. Four-Island native fixtures now cast the commander first;
  drawing in response to an incoming threat remains available.
- **Bounce targets:** `ReturnToHand` is treated as an adverse outcome when choosing
  targets. Into the Roil and Blink of an Eye no longer bounce an unthreatened own
  Sol Ring merely for their draw effect. Legal opposing targets remain usable.
- **Hybrid mana:** Eclipsed Elf could fail despite available coloured mana because
  the native mana chooser treated colourless mana as satisfying a B/G pip. A
  disposable chooser demand now selects an actually funded branch. The real
  spell retains its hybrid cost and native payment rules. Tests include Eclipsed
  Realms, Devoted Druid, Cavern of Souls, insufficient mana and a wrong creature type.
- **Huge boards:** Aesi can take combat with a ready, overwhelming trampling
  threat and stop redundant ramp when an already enormous army has enough
  resources. The policy may decline optional Burgeoning triggers in that state.
  Mandatory triggers, counter totals, token limits and legal actions are not changed.
- **Combat reserves:** damage value stops at an opponent's remaining life, with
  a separate elimination reward. The AI no longer values hundreds of excess
  attackers solely for overkill. Blocking-swarm regressions still require useful
  attacks through blockers.
- **Diagnostics:** eliminated-seat priority callbacks return immediately. Unexpected
  priority errors record bounded stack locations, and slow-turn sampling reserves
  evidence for later turns. The old isolated `IllegalArgumentException` was not
  reproduced; its exact throwing site remains unproven.

## Supplemental verification

All **364 real-engine scenarios, 27 cost contracts, 292 policy checks and nine
reporting tests pass**. The added activity, hybrid-payment and spell-timing suites
contain 27 actual-engine scenarios; the earlier six opening-hand checks remain.
The ten frozen published decks are unchanged.

Nine four-bot games used the exact candidate JAR recorded in the adjacent JSON,
rotated pods and seeds 20261024–20261026, 20261124–20261126 and
20261224–20261226. All nine finished naturally: **314 completed turns, 475 spell
casts, zero rejected actions, zero caught priority errors and zero error lines**.
The longest observed interval without a completed turn was **7.422 seconds**,
below the unchanged 30-second progress gate. No seat received the current
no-spells/low-development review flags.

| Commander | Games | Lands | Spells | Commander casts | Attack declarations |
|---|---:|---:|---:|---:|---:|
| Giada, Font of Hope | 4 | 35 | 50 | 7 | 24 |
| Krenko, Mob Boss | 3 | 10 | 22 | 2 | 6 |
| Lathril, Blade of the Elves | 4 | 26 | 31 | 5 | 3 |
| Ghalta, Primal Hunger | 4 | 34 | 44 | 5 | 4 |
| Goreclaw, Terror of Qal Sisma | 3 | 28 | 51 | 10 | 12 |
| Torbran, Thane of Red Fell | 3 | 21 | 37 | 3 | 4 |
| Talrand, Sky Summoner | 5 | 39 | 73 | 7 | 3 |
| Aesi, Tyrant of Gyre Strait | 3 | 38 | 61 | 5 | 3 |
| Gishath, Sun's Avatar | 3 | 29 | 54 | 3 | 4 |
| Adeline, Resplendent Cathar | 4 | 27 | 37 | 9 | 10 |

Talrand cast its commander in all five appearances in this sample. Before the
phase-timing change, several completed games contained no Talrand commander cast.
This is supportive gameplay evidence; the controlled phase fixtures establish
which behaviour the change corrects. Some individual seats legitimately never
attack or never reach their commander's cost before elimination.

The supplemental JAR overlays recompiled accepted policy and adapter classes on
the verified released AI JAR, using the paired baseline engine. It is not the
artifact used for deployment. The independent workflow rebuilds pinned upstream
sources and checks the complete server/bridge pair.

## Independent release and live verification

GitHub workflow [40048867619](https://github.com/edmundwhittaker-tech/the-fourth-seat-rules-engine/actions/runs/40048867619)
is the release gate for this exact source. Its result and deployment verification
will be recorded here before this follow-up is finalized. The previously failed
workflow `40042506392` remains a failed candidate, not a deployable release.

The live smoke driver is a bounded adaptation of the existing service test. It
uses the already-running gateway and its existing environment credential, creates
only synthetic rooms, checks three bots acting in a one-human game, then tests
mixed-player private views, legal land play, reconnect, end-to-lobby and rematch.
It cleans up only its own rooms. To run it, preserve this repository's relative
`docs/commander-ai-verification` and `integrations/fourth-seat` paths inside the
container and provide the gateway's existing `node_modules` directory. It does
not create a new gateway or print credentials.

## Limits and retained failures

The trace archive preserves intermediate findings: the `verified` candidate
still produced two failed Eclipsed Elf casts and a 63.858-second Aesi progress gap.
The `final` candidate removed those failures but still revealed Talrand's upkeep
spending and self-bounce. The `release` sample is the current candidate. None of
those earlier rounds should be substituted for the final source's release gates.
The original failed full-source workflow and initial timeout remain documented
in the earlier audit.

These tests demonstrate specific functional and decision-quality improvements,
not a human skill rating or a competitive win-rate gain. Seeds do not determine
UUID ties, timing budgets or subsequent draws. No controlled human/native-AI
head-to-head comparison was performed. The user's particular inactive live seat
has not been identified conclusively. Complex combo sequencing, rescue self-bounce
and broader strategic timing still need focused evaluation.

The adjacent JSON and compressed traces contain synthetic test evidence only.
They contain no live player hands, service credentials or environment files.
