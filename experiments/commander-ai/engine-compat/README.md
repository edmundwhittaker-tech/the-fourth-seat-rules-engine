# Pinned engine compatibility patches

The first patch updates the pinned XMage `ApprovingObject` constructor. A resolved cast permission can remain valid after the source card owner leaves a multiplayer game. If no live source object exists and either stack LKI is absent or the retained state has advanced past the actual stack LKI incarnation, its reference uses the retained game-state zone-change counter, matching the preceding live card identity. This fallback requires a positive captured ability stack-moment counter and a retained state counter at least that large. Never-activated unknown sources retain the original diagnostic path. The positive captured identity is the bounded provenance check; no new reflective state access is introduced. Existing live-object, same-incarnation stack-LKI and null-source paths remain unchanged. Stale stack LKI must not replace the later graveyard identity after owner departure.

The patch does not remove permissions, suppress a logger, alter costs, extend durations, or avoid particular cards. Mission Briefing stolen by Etali exposed the upstream reference-lifetime defect. Cast permissions and exile replacement effects must remain functional until their normal expiry.

The baseline engine checkout and JAR remain immutable. Build a separate source copy with the pinned root POM and an isolated dependency repository. The online build packages its patched core with a matching bridge. Artifact provenance must record the baseline and candidate core hashes, exact AI JAR hash and combined runtime classpath. This source preparation alone is not acceptance evidence; fresh actual lifetime tests and the complete engine suite are required before any new soak.

## Mana forecast compatibility

The online integration also applies `mana-options-trigger-forecast.patch` to
`ManaOptions.addManaVariation`. Each multi-color output is an alternative
activation; its forecast triggers must be consumed once before the next output
is inspected. Otherwise Regal Behemoth bonuses can be counted repeatedly for
the following land, admitting unaffordable casts. The patch changes forecasting
only. The pinned preimage is checked by both build scripts, and the online
manifest verifies matching ManaOptions bytecode in the server and bridge.

## Counter event batching

`prepare-counter-batching.sh` runs after the baseline core and Sets compile. It
checks framework preimages, resolves callback source symbols with the JDK
compiler, and generates `CounterObserverRegistry`. The auditor accepts only
side-effect-free paths that ignore both ADD_COUNTER and COUNTER_ADDED for all
possible game states. Unknown control flow, calls, bindings and observers are
excluded. Final reference switch selectors also require a non-null runtime
metadata check. Twenty-one compiler-backed adversarial cases exercise the audit.

`counter-event-batching.patch` adds a conservative fast path for at least 64
BoostCounters on standard permanent/state types. The original ADD_COUNTERS group
replacement, maximum bound, COUNTERS_ADDED event and user information remain in
place. It falls back to the original loop whenever any active watcher, trigger,
rule modifier, replacement or prevention callback is not proven irrelevant;
delayed triggers, custom event dispatch and integrity checks also disable it.
Production never invokes a callback as a probe. Exact counter totals are preserved.

The four patched framework classes and generated proof/helper are packaged in
both server and bridge. Startup compares every core class, not just patch marker
classes. Twelve actual-engine regressions cover large native doubling, group
replacements/triggers and the per-counter fallback. Build provenance includes the
patch, auditor and registry source hashes; the artifact manifest pairs all core
class bytecode and checks both final binaries.
