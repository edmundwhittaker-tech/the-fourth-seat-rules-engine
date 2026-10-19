# Ten-deck online AI integration

This change builds the accepted Commander policy, its narrow ApprovingObject
compatibility patch, and the browser bridge together. It does not change the Site,
access controls, broker credentials, published port, or frozen deck lists.
The existing `Computer - mad` protocol seat label maps to
`mage.player.ai.ComputerPlayerFourthSeat` in this deployment only. Humans remain
unchanged. The ten commanders are Giada, Krenko, Lathril, Ghalta, Goreclaw, Torbran,
Talrand, Aesi, Gishath, and Adeline. See `experiments/commander-ai/DECKS.md`.

## Validation gates

The Fourth Seat workflow builds a fresh isolated server/AI and matching bridge,
checks 291 policy assertions, all ten frozen decks, 285 real-engine scenarios and
27 cost contracts, completes nine real games using packaged JARs, tests gateway
policies, exercises the private service, and boots the actual container under a
4 GiB limit. A source commit is not a live deployment. Wait for all these gates
on the exact proposed commit before replacing
an existing service.

The runtime checks a SHA-256 manifest for both packaged artifacts, requires all
three policy/adapter marker classes, and verifies that the bridge embeds the
same ApprovingObject and ManaOptions bytecode as the server. Missing/mismatched artifacts fail
closed. Java services and H2 listen on loopback; gateway authentication is unchanged.

## Existing Codespace deployment, after CI passes

Use the existing repository checkout and existing authorized connection. Do not
create a new secret, publish another port, or paste credentials into chat.

1. Verify `git status --short` is empty and record the checkout's current commit.
   Verify `.codespace-engine/engine.env` already exists. Stop if it does not.
2. Check the online lobby and confirm there are no active games. Container
   replacement interrupts all games; obtain a maintenance window if needed.
3. Record the running container image ID (`docker inspect --format '{{.Image}}'
   fourth-seat-engine`) and tag that ID `fourth-seat-engine:before-commander-ai`.
   Do not inspect or print the container environment.
4. Fetch and check out the exact CI-approved integration commit. Do not overwrite
   a dirty working tree or use an unreviewed moving branch.
5. Prefer the successful workflow's `fourth-seat-engine-<commit>` artifact, which
   contains the exact paired binaries tested by CI. With the existing GitHub CLI
   authentication, download that artifact into `build/nexus` using
   `gh run download <run-id> --name fourth-seat-engine-<commit> --dir build/nexus`.
   Require `integration-commit.txt` to equal `git rev-parse HEAD`, compare
   `ai-source-sha256.txt` with the checkout's accepted source manifest, and run
   `python3 integrations/fourth-seat/hosting/online_ai.py build/nexus` to verify
   the binary digests and paired bytecode. Then build the runtime image:
   `docker build --build-arg BINARIES=artifact --file integrations/fourth-seat/hosting/Dockerfile --tag fourth-seat-engine:codespace .`
   This does not replace the running service. As an alternative, run
   `python3 scripts/codespace-engine.py build` to rebuild from pinned sources;
   that route requires more build memory, disk and time.
6. Recheck that no games started during the build. Run
   `python3 scripts/codespace-engine.py stop` followed by
   `python3 scripts/codespace-engine.py start`, then
   `python3 scripts/codespace-engine.py status` after database startup completes.
7. Confirm the current Site broker reports ready, then start a fresh test game
   with distinct commander decks. Verify decisions progress, card choices and
   priority passing, then end the test game through the lobby control.

Rollback if readiness or a smoke game fails: stop the new service, tag the saved
`fourth-seat-engine:before-commander-ai` image as
`fourth-seat-engine:codespace`, and start it with the existing script and env file.
Preserve the named data volume and existing broker settings. Do not delete user
data or secrets. Rolling back the code alone does not replace a running container.

## Build and artifact provenance

`scripts/build-online-commander-ai.sh` checks pinned pristine upstream commits,
accepted source SHA-256 values and the core preimage before applying the patch.
It uses an isolated Maven repository to avoid mixing core versions. CI records
source/toolchain pins and artifact hashes. The source Docker build explicitly
installs its patch tool. Container-only verification uses these same built
artifacts rather than rebuilding an unrelated baseline.

## Completing Winston's integration

The first online build passed compilation and engine regression tests but failed
the existing 30-second progress gate: one game had a 93.7-second gap. Stack samples
showed funding simulations for candidates that the strategy subsequently held.
Funding is now delayed until after the exact option's strategic hold check, with
one funding result shared only among that base ability's options in the current
priority callback. No mana restriction, target, cost, game-length or progress
threshold is relaxed. A regression fixture checks that held development avoids
funding simulations and that protection can still respond on the same stack.
The workflow retains full soak logs for diagnosis and publishes deployment
binaries only after every validation gate succeeds.

A subsequent cloud run exposed another hot path: repeated funding of idle answers.
The adapter now checks whether any exact option could receive a positive policy
score before funding that source. Singleton scores are optimistic because the
policy's cross-candidate adjustments only subtract value. If any option is useful,
the complete original option set is retained, preserving modal diversity. A real
conditional-mana fixture checks idle protection skips funding and still answers
an actual threat. Cache-safety scans are also deferred past the no-action exit.

The final soak also reproduced a native mana-forecast defect with Regal Behemoth,
Command Tower and Unclaimed Territory: a source with several color alternatives
left simulated mana triggers queued for the next source. The narrow
`mana-options-trigger-forecast.patch` consumes those triggers per alternative,
outside the loop over existing mana totals, and copies the net-mana template.
The actual production/resolution path is unchanged. Nine real-engine regressions
cover insufficient floating mana and exact seven-mana payment with three pool
colors. Both build paths verify the pinned source preimage; startup compares the
patched ManaOptions bytecode between server and bridge as well as ApprovingObject.

The online service proof requires both native bots to make real battlefield plays
before lifecycle cleanup, so the strategy class is exercised rather than merely
instantiated. The bounded finite-chain recovery also recognizes Garruk's Uprising,
Outcaster Trailblazer and Vaultborn Tyrant alongside Garruk's Packleader; it
declines optional Kodama choices after one confirmed library-progress refusal
while leaving mandatory draws and a second unresolved loop prompt to XMage.

## Known upstream counter-growth performance limit

An additional stress run of the packaged build at
`95e1605daff503a8f7ea8b25b1d4f9d7fd82c0a5` exceeded the 30-second progress
bound on an Aesi board with Mossborn Hydra, Ancient Greenwarden, Ashaya and
repeated land entries. The Hydra reached millions of +1/+1 counters; thread
samples were inside XMage's `CardImpl.addCounters` / `DoubleCountersSourceEffect`,
which process individual counters and their events. This is an observed engine
resolution limit. The frozen decks, actual counter totals and card rules are not
changed to hide it. Keep the release in the existing admin-only testing mode.
The next milestone is a dedicated performance fix that preserves replacement
effects and counter-trigger behavior, with a reproducible large-counter fixture.

The service driver plays a land for each human fixture and handles the server's
mandatory discard choice, so random seating cannot terminate the verification
before an AI acts. Its 24 checks include both human and both AI battlefield plays,
private views, mulligans, reconnect, ending a game, lobby reset and rematch.
