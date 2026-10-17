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
checks 291 policy assertions, all ten frozen decks, 270 real-engine scenarios and
27 cost contracts, completes nine real games using packaged JARs, tests gateway
policies, exercises the private service, and boots the actual container under a
4 GiB limit. A source commit is not a live deployment. Wait for all these gates
on the exact proposed commit before moving the integration branch or replacing
an existing service.

The runtime checks a SHA-256 manifest for both packaged artifacts, requires all
three policy/adapter marker classes, and verifies that the bridge embeds the
same ApprovingObject bytecode as the server. Missing/mismatched artifacts fail
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
5. Run `python3 scripts/codespace-engine.py build`. The existing service is not
   replaced by this command. Ensure enough free build memory and disk; the
   all-source build is heavier than ordinary startup.
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
