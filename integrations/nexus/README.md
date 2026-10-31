# Nexus bridge evaluation — stage 2

## Candidate architecture and pins

The bridge is `Adamychen/xmage-nexus` at
`850411ec4db71ea7c10f4c3869a63084760057c2`. It translates JSON/WebSocket requests
into XMage sessions. The evaluated server is the same repository's separate
`nexus` fork, locked at `825d1205acca114825f235c6993f1e5a6a6db9fa`.

This is explicitly **not the unmodified upstream server** from stage 1.
Nexus directly calls fork-only APIs (`SessionImpl.cheatSetup`,
`MatchOptions.setSkipInitShuffling`, `setSkipStartingPlayerChoice`). Its fork is
five commits ahead of our upstream release baseline
`eb69d05b88142b5c4eec4869bcb23a0fec13c091`, with changes in 15 files.
`fork-delta.json` retains the source comparison for review. The changes include
test controls, extra controller/view fields and deck-loading behavior; this
comparison is provenance, not a security or correctness audit.

The stage-1 upstream pin, build workflow and working playground are retained.
We pin this compatibility candidate separately so we can later decide which
fork changes to retain, upstream, or replace with an adapter. No vendor source
is edited by this evaluation.

## What the cloud workflow checks

Open **Actions → Verify private XMage Nexus bridge** on `integration/xmage`.
The workflow:

1. Verifies both checked-out source SHAs.
2. Builds the pinned fork's server, native AI and Commander plugins.
3. Runs the bridge's Java tests and packages its shaded JAR.
4. Starts our own server and the bridge on loopback inside the Actions runner.
5. Waits for the actual server socket and bridge card-database readiness.
6. Exercises JSON protocol checks against that server and fails on a rejected
   assertion or timeout. See `scripts/verify-xmage-bridge.mjs` for the assertions.
7. Stops both processes and uploads logs, evidence, binaries and checksums.

Runtime arguments explicitly select `127.0.0.1:17171` for XMage and disable
proxy SIM seats. The local fixture server permits anonymous test accounts and
runs in test mode. It is a disposable private CI environment, not a public
deployment configuration. Fixture accounts and decks are generated test data.

## Evidence and limits

The artifact includes `runtime-result.json`, `protocol-result.json`,
toolchain versions, source pins, the server config, server and bridge logs,
Maven test reports, both MIT notices, binaries and SHA-256 checksums.
Always read the workflow conclusion and JSON `passed` fields; a diagnostic
artifact also uploads after failures and by itself does not mean success.

The checks cover a two-human-seat fixture match and native AI seat staging.
XMage's `SessionImpl.joinGame` acknowledges sending a void remote call; its
boolean does not establish seat authorization. The server's `GameController.join`
checks its user-to-player map. The negative check therefore inspects actual
private views and prompts after an unseated account attempts to join, including
through a subsequent legitimate game update.
They test representative private views and an unauthorized join, not all
possible hidden-information leaks or every game callback. Rejoining uses a
second window attached to the same live session; it does not simulate a
server crash or persistence across a restart.

This stage does not run Nexus's entire web/real-game suite or the upstream
XMage gameplay suite. It does not expose a live browser interface or prove
a complete Commander game. Source and Java pins improve repeatability;
runner images, Maven repository availability, build timestamps, and transitive
build tools still prevent a bit-for-bit reproducibility claim.

## Next gate

Stage 3 should verify an actual browser-controlled Commander game with one
human and three native XMage AI opponents, including targets, mana payments,
combat and Commander choices. The existing Fourth Seat website should only
be integrated after that gate passes. Persistent hosting is a separate setup
from these private Actions tests.
