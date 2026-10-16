# Real Commander progression soak

This harness runs isolated, local, four-bot games under XMage's actual `GameCommanderImpl`. It does not contact or alter a server, a website, a Codespace or any active game. It uses every card and quantity in the frozen `integrations/fourth-seat/gateway/ai-decks.json`, not synthetic 99-land lists. Each card is an actual pinned XMage card class. Unknown cards fail preparation; none are replaced.

After building the adapter with `scripts/build-adapter.sh`, run from the repository:

```sh
experiments/commander-ai/scripts/run-soak.sh /path/to/built-xmage /path/to/maven-repository /tmp/commander-soak 20261016 120 300
```

Arguments after the output directory are seed, maximum turn number and wall-clock seconds per pod, and an optional deck-index rotation (default zero). For three coverage batches, use fresh output directories, distinct seeds and rotations 0, 3 and 6; each batch still includes all ten decks. The runner compiles the harness into a temporary directory, runs three separate JVMs sequentially (four players each), and deletes the temporary compilation afterward. `timeout` terminates only the disposable JVM it started; its five-second kill grace prevents a stuck engine thread from keeping the harness alive. No server is started. The pods cover all ten commanders, with Giada and Talrand repeated to fill twelve seats. Output logs and the frozen-deck manifest are retained in the supplied directory; use a fresh directory per run.

`TURN_DONE` means a complete individual player turn, not a table rotation. `FINISHED` means natural engine game end, whereas `TURN_BOUND` means the configured turn limit was reached and is **not** a completed game. A process timeout or crash has no final result and must not be counted as success. Ten-second heartbeats plus per-turn state summaries expose a silent within-turn hang; inspect consecutive heartbeats with no completed turn and the last event. Normal empty-candidate or deliberate passes are not by themselves stalls. Adapter DEBUG output records selected action activation results and elapsed decision time, and WARN/ERROR output is retained.

`results.json` reports completion, completed-turn counts, spell-cast count, failed activations, caught priority failures, errors, and decision timing. Inspect missing results and nonzero failures rather than judging only JVM exit status. `summarize.py OUTPUT_DIRECTORY` can regenerate it. The summarizer exits nonzero for missing/non-finished games, zero completed turns, failed activations, caught priority failures or error lines. A deliberate turn bound is useful diagnostic evidence but intentionally not a passing completion gate. Seed controls XMage's random generator, but UUID-based tie breaking and timing budgets mean it is **not** a promise of bit-for-bit deterministic replay.

Game setup follows the actual match initialization sequence: player init, load main/sideboard cards, add player, then engine start; the single commander is the sideboard card and is moved to the command zone by the real Commander implementation. London mulligans with one free mulligan and forty starting life are used. The harness subclasses only game metadata and end-turn/event logging, not bot decisions, targeting, damage, or card rules.

This is integration/liveness evidence, not evidence of human-level strategic strength, balanced win rates or complete card coverage. A game can finish despite bad strategic decisions. Separate targeted regressions and human review remain necessary. Hosting capacity cannot be inferred from these single-machine disposable runs.

Test-only own-hand snapshots are logged once at each player's own precombat main phase to distinguish poor draws from skipped development. These local synthetic-game traces are diagnostic only; they are never injected into another player's observation or strategy.

For a three-batch gate, run `summarize-batches.py PARENT_OUTPUT_DIRECTORY`, where the subdirectories are named batch0, batch1 and batch2. It checks that all nine games naturally completed, every commander appeared at least three times, and the main compiled artifact hashes are consistent. It preserves all failures and returns nonzero for unresolved errors. The runner now performs lazy expansion-discovery preflight to reject duplicate XMage classes/JARs before playing.
