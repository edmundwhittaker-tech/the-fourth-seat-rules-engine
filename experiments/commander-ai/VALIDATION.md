# Validation and readiness boundary

## Current reconstructed candidate, 2026-10-17

This is a semantic reconstruction from hosted commit `24468d879c31fb7c7f96e2c260789356e47c7aa2`, not a byte-exact recovery of the lost local accepted source. Historical329-scenario/nine-game acceptance does not certify these new bytes.

Fresh verification completed:
- Original pinned XMage AI-module Maven package built successfully against XMage `825d1205acca114825f235c6993f1e5a6a6db9fa`, using JDK17 and Java8-compatible class version52.
- Exact packaged JAR SHA256: `d0d6eaf271b868a58b7c5611efb8dd29495f563380ee0d9ecbcd823b11c3b774`.
- **270 actual-engine scenarios plus27 additional-cost contract assertions passed**, exit0, with zero ERROR/FAIL log entries. Tests ran against the exact Maven JAR, not earlier separately compiled classes.
- **291 pure-policy checks passed**:13 activation,87 combat,28 shared-policy,122 deck-plan,41 recovered-contract checks.
- Paired isolated core compatibility JAR SHA256: `dbf95f7db572f6f11d090ca860db2fcb9055bc32e5ea5dee8e0b48bd7b0e6943`. Eight of the actual scenarios verify stolen-spell permission lifetime, identity and watcher behavior. This runtime is explicitly patched, not untouched upstream.
- Rebuilt bridge with the same patched core passes239 tests. The isolated standalone startup/security audit passed.
- Frozen deck memberships match all ten policy plans.

The exact runtime classpath is the pinned assembled distribution's dependency JAR list with the experimental AI JAR replacing the base AI JAR. It is not a broad local-cache scan and is not represented as Maven-generated dependency-plugin output.

## Fresh full-game acceptance passed

On 2026-10-17 the exact paired runtime above passed the unchanged nine-game gate: **nine ordinary completions, 323 completed turns, 623.420 seconds total**, zero rejected activations, engine errors, caught priority exceptions, timeouts or loop-confirmed draws. Every frozen deck appeared at least three times, every commander was cast, and every deck declared attacks. Lathril's drain activated twice.

All games stayed within120 turns and300 seconds. The maximum observed progress gap was18.229 seconds, below the30-second gate; the maximum individual decision was920.590 milliseconds. Recursive class manifests matched across batches and both JAR hashes were rechecked unchanged after completion. The delivered Windows candidate therefore uses the same binaries that earned this result; a replacement binary is not required merely to update its earlier pending-validation label.

The isolated server/bridge startup and security audit also passed: services bound to loopback, no unexpected listening ports, and the bridge's browser-origin rejection/internal readiness controls behaved as expected. The patched bridge passed239 tests. This is acceptance for **experimental offline testing**, not production deployment, human-level strength or every possible Magic interaction.

Earlier reconstructed inventories found a two-land triggered-mana planning defect and an upstream stolen-spell approval-reference defect. Both were corrected and covered by actual positive/countercase fixtures before this fresh passing run. Failed inventory evidence is retained separately; it does not certify current bytes.

## Evidence and coverage

See `adapter/test/results/latest.txt`, its source hash manifest, and `adapter/test/RECOVERY-COVERAGE.md` for exact reconstructed contracts. Suite grouping differs from the lost source; no old scenario count is reused as a new pass. New suites cover actual resource payment, conditional/dependent mana, group taps/Devoted, dynamic and exile costs, selected modes/targets, bounded combat, growth restraint, finite optional-chain handling, cache safety and reserve continuity. Positive and negative cases include previously observed regressions.

`RECOVERED-POLICY-NOTES.md` documents restored immutable policy interfaces. `REVIEW-HANDOFF.md` explains each deck and review limitations. Historical failed-run JSON files are defect evidence only, not certificates for this artifact.

## Isolation and limitations

No live activation, deployment, default player registration or main/integration branch change occurred. Manual opt-in instructions are in `activation/README.md` and must be reviewed for a separate test server.

The planning budget is soft: native legality/targets, continuous effects, triggers and state copying can exceed it. Bounded modal sampling and combat projections are intentionally incomplete. Dependency planning supports specific certified resource shapes rather than arbitrary combos. No opponent private cards or hidden library order are policy inputs; rules and final legality remain with XMage.
