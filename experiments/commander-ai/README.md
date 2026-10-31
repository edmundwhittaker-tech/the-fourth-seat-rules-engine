> Reconstructed candidate validated (2026-10-17): this is a semantic reconstruction, not the lost binary. The exact AI/core pair passes270 actual-engine scenarios,27 cost contracts,291 pure checks and the fresh nine-game gate. The standalone runtime/security audit and239 bridge tests pass. It remains an experimental offline test build, not a live-site deployment. See VALIDATION.md for exact hashes and limits.

# Isolated deck-aware XMage Commander AI

A separately compiled experiment for The Fourth Seat's ten frozen Commander decks, **not deployed to the live site**. The Windows test package is configured to use this player locally when launched. Standalone source installation remains opt-in; nothing here changes the live site, gateway or default bot.

Start with [the ten deck explanations](DECKS.md), [adapter integration and limitations](adapter/README.md), and [the model boundary](CONTRACT.md).

## Verify locally

Requirements: JDK 17 (emits Java 8 bytecode), Maven, and the exact XMage source revision `825d1205acca114825f235c6993f1e5a6a6db9fa`.

```sh
bash experiments/commander-ai/test.sh
bash experiments/commander-ai/scripts/build-adapter.sh /path/to/pinned/xmage /new/disposable/build
bash experiments/commander-ai/adapter/test/run-real-engine-tests.sh /new/disposable/build
```

The build script rejects an existing output directory and never edits the source checkout. It overlays the new policy and adapter into a disposable copy of the basic AI module. It does not register the bot or start a game server. The GitHub workflow is test-only and restricted to this experiment's feature branch.

## Scope

This is executable engine-side strategy code, not a language-model prompt and not an autonomous rules engine. It chooses from XMage's legal actions, uses separate plans for each supported commander, and never intentionally accesses opponents' hidden card identities. It does not solve arbitrary Magic, promise human-level strength, guarantee a hard think-time limit, or replace every upstream card-specific dialog.

The original XMage MIT notice is retained in `LICENSE-XMAGE.txt`. Deck lists remain in the repository's frozen sourced pool, with their source URLs/hashes recorded in `DECKS.md`.

## Reviewed build artifact

The feature-branch test workflow creates a downloadable `commander-ai-experimental-<commit>` artifact **only after** the pure suite, real-engine scenarios and nine mixed-deck games all pass its gates. It contains the inactive plugin JAR, license, frozen deck/source pins, sanitized validation summaries and SHA-256 checksums. Failed validation produces no ready artifact. Download it from the successful workflow run's Artifacts section, inspect `READ-ME-FIRST.txt` and `BUILD-MANIFEST.json`, and verify `SHA256SUMS.txt` before any separately approved test-server installation. Artifact retention is 30 days; source and build instructions remain available afterward.

This is not a complete server distribution and is not installed automatically. Read [validation evidence and boundaries](VALIDATION.md) and [manual activation/rollback instructions](activation/README.md) first.
