> Recovery status (2026-10-17): the reconstructed exact AI/core pair now has fresh clean acceptance evidence from recovery3. Earlier recovered runs failed and remain retained. The unavailable historical candidate-17 binary/ZIP was not used or represented as recovered; only the exact pair recorded in recovery3-paired-artifact-hashes.json is covered by the new result.

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

## Payment-corrected snapshot: remaining performance failures

The next coherent installed snapshot (92 real-engine fixture checks) was rerun with the corrected, preflight-validated classpath. Seven of nine games naturally completed, but **two Aesi games reached the unchanged 300-second wall-clock limit** while resolving Scute Swarm token growth. These were advancing through hundreds of queued effects, not a silent rules deadlock; they still fail the performance/completion gate. The existing XMage 500-token cap was observed, not added or modified by the harness.

Across all nine games: 401 completed turns, eight failed activations (Archangel of Thune 1, Luminarch Ascension 1, Collective Effort 2, Idol of Oblivion 2, Adeline 1, Growth Spiral 1), zero caught priority exceptions and zero engine ERROR lines. Main compiled hashes remained identical across the three batches. Seven completed games consumed 502.632 seconds; total observed time including the incomplete games was at least 1,083.770 seconds. The time limit was not extended to hide the failures.

Latency is now included explicitly: the largest progress gap in a naturally completed game was 11.483 seconds. The two unfinished Scute turns had observed gaps of 244.055 and 244.236 seconds, despite no failed actions during most of that time. Completed-game p95 decision times ranged from approximately 49 to 110 milliseconds; the worst individual decision in a token-heavy game reached 649 milliseconds. `payment-fixed-performance-failures.json` preserves per-pod, per-seat and timing details. This is improvement evidence, **not acceptance**.

## Recovered exact-JAR run 1 (2026-10-17): one rejected spell

Fresh exact Maven JAR e0cf7920844acb95bea40912ec9ffabc9d7360d61e2e7ace1978b33b1f57511d completed all nine games ordinarily, with 392 turns and 779.816 seconds combined runtime. Maximum observed progress gap was 19.912 seconds. No timeout, actual loop draw, engine error or caught priority exception occurred. Recursive class manifests agreed across batches, and the JAR checksum remained unchanged after the final JVM exited. All ten commanders appeared at least three times, all were cast, and all decks attacked.

Acceptance is FALSE: Gishath's World War Hulk cast was rejected once in batch 2 pod 0, turn 39 precombat. The exact payment context is retained in the durable raw log. No earlier accepted result was transferred to this build. Full fresh evidence lives at /workspace/shared/commander-ai-recovery/soak-recovery1/aggregate-results.json. The run ended cleanly before allowing a targeted artifact correction.

## Recovered exact-JAR run 2: missing-reference engine errors

Candidate JAR 867be83acfeff7a4a8292a2943f1959bca7fb69ecf3f0ba7f6a19451454441ee completed all nine games ordinarily, 344 turns in 600.587 seconds. There were zero rejected activations, loop draws, timeouts or caught priority exceptions. Maximum progress gap was 27.015 seconds and recursive class manifests agreed. Acceptance remains FALSE due to 34 engine ERROR lines in batch 0 pod 2.

Gishath stole/cast Mission Briefing from Talrand's library through Etali. After Talrand was eliminated, MageObjectReference reported that the Mission Briefing source object was missing and absent from last-known information. These messages are retained, not suppressed. Full evidence: /workspace/shared/commander-ai-recovery/soak-recovery2/aggregate-results.json. All disposable game JVMs ended before the runtime freeze was lifted.


## Recovered exact-JAR run 3: clean fresh acceptance

The d0d6eaf2 AI / dbf95f7d core pair passed the unchanged complete nine-game gate with 323 turns and 623.420 seconds combined runtime. Zero rejected activations, engine errors, caught exceptions, timeouts or actual loop draws. Maximum progress gap18.229 seconds and maximum decision920.590 ms. All ten decks appeared at least three times; every commander was cast and every deck attacked, including two Lathril drain activations. Both artifact hashes remained unchanged after the final JVM ended. Full durable evidence is soak-recovery3/aggregate-results.json; accepted summary and activity grid are copied alongside this document. This is new recovered-build evidence, not a reuse of the unavailable older accepted binary.
