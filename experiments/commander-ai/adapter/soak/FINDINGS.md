# Soak findings (intermediate, not a release sign-off)

## 2026-10-16 initial real-game run

Frozen deck JSON SHA-256: `9e5625b2060db2ddd457ade4b67f623716e301f77cb72178efe052920e94748c`.

A source overlay rebuilt at approximately 10:22 UTC ran three actual four-bot games, with seed inputs 20261016–20261018 and a 120 individual-turn / 240-second limit per pod. All ten frozen decks were represented. All games naturally finished, not because of the bounds:

- Giada / Krenko / Lathril / Ghalta: 24 completed turns, 21.489 seconds, Krenko winner, 24 spells cast, no failed activations.
- Goreclaw / Torbran / Talrand / Aesi: 39 completed turns, 24.135 seconds, Torbran winner, 42 spells cast, **105 failed activations**, all associated with Goreclaw decisions after Kamahl entered.
- Gishath / Adeline / Giada / Talrand: 36 completed turns, 24.498 seconds, Talrand winner, 53 spells cast, **4 failed activations** (Adeline 2 and Giada 2).

This first runner used the broad local Maven cache classpath, since replaced by the exact generated Maven runtime classpath. The results are defect-discovery evidence, not the final reproducible sign-off. UUID tie ordering and real time budgets prevent deterministic replay solely from a seed.

No caught priority exceptions were logged in those three games. This did not mean clean play. Specific defects reported for correction:

1. Tribal choices were inappropriate: Gishath named Gamorrean for Unclaimed Territory and Surrakar for Urza's Incubator; Giada named Caribou for Vanquisher's Banner and Barabel for Urza's Incubator.
2. Repeated activation failures could hide opportunities despite the game finishing. Original debug output lacked source/action identity; better diagnostics were requested.
3. Ghalta played four lands and only Beast Within, never cast the commander or attacked before losing. This is insufficient to conclude bad draws or a strategy bug without own-hand data. Test-only precombat hand snapshots were added for further diagnosis; these are not shared with bot opponents.
4. A separate preliminary run reached turn 73 and then remained in its beginning phase for over a minute, with Bristly Bill repeatedly doubling counters into the millions. Its disposable process hit the 240-second timeout. This is a liveness/performance failure, not a finished game. Some equipment also re-equipped to its existing target unnecessarily.

Full-game completion must therefore be combined with per-seat land/cast/commander/attack activity, action failures, targeted scenario tests and strategic inspection. A clean subsequent run does not erase these findings unless the relevant defect has a regression test or a clear verified fix.

## First exact-classpath rerun (intermediate adapter)

Three more natural completions occurred at 24, 38 and 87 completed turns (23.071, 35.090 and 110.328 seconds). The repeated-activation limit activated 49 times in the third game; no priority exceptions occurred. Failures were now attributable: Flare of Fortitude 2, Castle Embereth 3, By Force 2, Myriad Landscape 2 and Entreat the Angels 6. These were reported for alternate-payment, source-tap cost and X-cost handling fixes. This was not a clean gate pass. Tribal choices remained wrong in this compiled snapshot, and Lathril cast Get a Leg Up on an opposing Giada; both were reported for structural fixes. Ghalta's logged hand was top-heavy, but this version cast Questing Beast and Pugnacious Hammerskull and attacked, rather than only casting removal. No hidden-hand explanation is asserted for the prior run.

The committed intermediate JSON files deliberately preserve these failures. They are not the final acceptance result and should not be interpreted as one.

## Nine-game frozen-snapshot defect inventory

The 10:41 UTC installed snapshot completed all nine rotated-pod games naturally: 414 completed player turns, 435.632 seconds of combined game time. Every commander appeared at least three times; all ten cast spells, played lands, cast their commander and attacked across the batch. The main adapter and policy compiled hashes matched across all three batches. Source edits in the working repository were allowed during later games, but the build overlay and compiled artifact remained frozen; measurements concern the frozen compiled artifact, not later source edits.

This was still **not a passing acceptance run**: 197 failed activations were logged, principally Goreclaw (64), Smuggler's Surprise (40), War Room (34), Thunderfoot Baloth (10), Tender Wildguide (7) and Idol of Oblivion (7), with other lower-frequency cost/payment failures. None were suppressed from the report. No caught priority exception or process timeout occurred. Tribal names and positive-buff ownership were corrected in this snapshot, but beneficial color choices were still wrong (for example, Utopia Sprawl naming Black in Ghalta and Heraldic Banner naming the wrong color).

The first exact-classpath runner also duplicated Mage.Sets through both its classes directory and its installed dependency JAR. Lazy expansion discovery in two games logged 589 duplicate-set errors each (1,178 total). This was a **harness classpath defect**, not a clean result. The runner now uses only the AI overlay classes plus the exact installed runtime dependency JARs. A new preflight forces expansion discovery and fails on ERROR output; independently verified loading 589 sets with zero errors. These old errors remain visible in `nine-game-defect-inventory.json` rather than being retroactively erased.

Subsequent fixes and reruns must be reported separately. Natural game completion is not sufficient to claim strategic or payment correctness.
