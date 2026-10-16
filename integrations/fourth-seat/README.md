# Optional XMage rules in The Fourth Seat

Manual multiplayer rooms and solo testing remain the default experience. Rules Engine play is an optional game setup with one to four human seats; native `COMPUTER_MAD` seats fill the table to four. AI seats currently use the host's selected Commander deck.

## Same table, different rules authority

The Site keeps its actual `GameTable`, responsive hand, battlefield rows, stone surface, player strip, commander dock, card previews, zone inspection, and overview navigation. `lib/rules/projection.ts` translates private XMage views into the existing renderer's input. It does not resolve spells, infer legal moves, or write manual game state. `EngineControlsContext` replaces game mutations with allowed selections and suppresses manual-only controls. The original manual reducer, room API, D1 records and synchronization remain independent.

The prompt parser is adapted from the pinned Nexus client. It preserves engine response types for priority, mana, targets, combat, opening hands, Commander decisions, abilities, choices and amounts. The current release supports a single designated commander; imported decks are resolved against the engine's own catalog and must pass its official Commander validator. Partner deck editing remains outside this release.

`client/` is an independent raw-protocol adapter. The Fourth Seat website source stays in its separate private Site repository. The public engine repository does not require or publish a copy of its renderer.

## Session boundary

The browser calls the Site's `/api/engine` broker. Only the broker knows `FOURTH_SEAT_ENGINE_SECRET`. Each browser keeps a random 256-bit key; the gateway derives a stable, opaque XMage account from it. Short-lived signed tickets authenticate WebSocket attachment and never appear in URL query strings. Browser storage remains sensitive, as with the existing anonymous profile; cross-device account recovery is not provided.

The public gateway accepts the configured Site origin. It supplies all Java connection details itself and restricts gameplay to the caller's joined game. Raw XMage and Nexus ports stay on loopback. Browser clients cannot choose upstream hosts, attach to another account, create arbitrary tables, use SIM, or invoke cheats. The Java server runs without test mode and the bridge with `--sim off`.

Room records contain lobby membership and table references, never a second rules state. Reloads reconnect through the Nexus private replay cache. **A Java server/container restart ends its in-memory games.** The gateway marks stored rooms expired at startup instead of pretending they can resume. Run one replica; scaling tables and durable match recovery need a later service design.

## Cloud verification

`.github/workflows/fourth-seat.yml` verifies the adapter and policy tests, then launches our pinned private XMage server, Nexus bridge, and gateway. The service check creates a two-human/two-native-AI room, starts Commander, answers opening prompts, plays a land, verifies private views, and reconnects. No full game to victory is required. Container startup is separately verified under a 4 GiB memory limit.

The real Fourth Seat interface also passed a two-browser integration check: 12 checks, zero JavaScript errors, shared authoritative land play, private hands, and reload. Its harness belongs in the private Site repository (`tools/engine/verify-ui.mjs`), rather than a public copy of the website implementation. Public CI verifies the independent service contract.

## Test from GitHub Codespaces, with no local installation

Use the **Fourth Seat + XMage cloud test** dev-container configuration on branch `integration/xmage`. The original Node playground configuration remains available. From GitHub's Code → Codespaces → New with options, select this branch and `.devcontainer/xmage/devcontainer.json`. Select a machine with at least 4 cores and 8 GB RAM for the initial Java source build. Review your account's included usage and spending settings first; this setup does not create a paid hosting subscription.

The first setup installs the starter dependencies and builds the exact pinned Java/bridge sources into a Docker image. Subsequent Codespace starts automatically start that image. Only the authenticated gateway is forwarded; Java and Nexus ports are private inside Docker. The image runs with a 4 GiB memory limit and two CPUs, matching the verified container test. Initial compilation and card database startup take several minutes.

In the **browser's Codespaces terminal**:

```sh
python3 scripts/codespace-engine.py status
python3 scripts/codespace-engine.py connection
```

When ready:

1. Open Codespaces' **Ports** panel. Set only **8080** to **Public**. Its forwarded address starts with `https://` even though the process inside Docker speaks HTTP. Leave the port protocol set to HTTP; Codespaces terminates TLS. Do not forward 17171, 8787, or 8788. Organizations may prohibit public ports.
2. Set the existing Site's server environment variable `FOURTH_SEAT_ENGINE_URL` to the HTTPS origin printed by `connection`.
3. Open the ignored, owner-readable `.codespace-engine/engine.env` file in the Codespaces editor. Copy the `ENGINE_APP_SECRET` value into the Site's server secret `FOURTH_SEAT_ENGINE_SECRET`. Do not paste it into chat, commit it, or place it in browser variables. The Site broker and gateway must use the same value.
4. Apply the Site environment settings and open <https://thefourthseat.net>. Choose **Rules Engine play**, select a saved single-commander deck and choose the human seat count. AI seats fill the table to four. The same table, hand, previews and zones are used; engine prompts replace manual state editing.
5. Verify two independent human browsers, native AI seats, private hands, an accepted action and a reload. A full game is unnecessary. CI's browser test has already verified these behaviors; the live forwarded connection still needs this short check after activation.

When finished, use GitHub's **Stop codespace** action. Closing the browser tab alone is not an immediate stop. Games end when the Codespace/container stops; restart creates a fresh engine session. The Docker volume preserves catalog data and expired room references, not running games. Codespace storage remains until deletion. To update after pulling engine changes, run `python3 scripts/codespace-engine.py stop`, `build`, then `start`.

The website honestly reports Rules Engine play unavailable until these server settings point to a ready service. Regular modes remain available while the Codespace is stopped. Codespaces is an on-demand test host, not an always-on public service. No Codespace has been provisioned by this commit.

GitHub references: <https://docs.github.com/en/codespaces/developing-in-a-codespace/forwarding-ports-in-your-codespace> and <https://docs.github.com/en/codespaces/developing-in-a-codespace/stopping-and-starting-a-codespace>.

## Optional later permanent host

`hosting/railway.json` remains an optional Docker deployment recipe if persistent hosting is wanted later. Use one replica, a `/data` volume, `ENGINE_APP_SECRET`, `SITE_ORIGIN=https://thefourthseat.net`, gateway port 8080 and `/health`. Configure the same two server-only Site settings described above. No Railway account or paid service is required for Codespaces testing.

The container uses Node 24.19.0 and Temurin 17.0.20+8. Source versions and gateway dependencies are pinned; OS package repositories and base-image rebuilds can change. For stricter image reproducibility, record an image digest after the cloud image check.

Railway reference: <https://docs.railway.com/builds/dockerfiles>, <https://docs.railway.com/quick-start>, <https://docs.railway.com/pricing>. No hosting account, paid service or domain has been created automatically.
