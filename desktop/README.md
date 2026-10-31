# The Fourth Seat — Windows desktop preview

1. Download `The-Fourth-Seat-Windows-x64.zip` and choose **Extract All**.
2. Open the extracted folder and double-click **The Fourth Seat.exe**.
3. Keep the launcher window open. Your browser opens the local game automatically; choose a deck, create the AI table, then start the game.

No Java, Node, Docker, GitHub account, Codespace, or paid AI service is required.
Windows 10/11 x64; 8 GB RAM recommended; allow 2 GB free disk space. The first launch prepares the card database and can take several minutes. This is an unsigned preview, so Windows may show an unknown-publisher warning. Only use the download linked from the project's release page.

The same Fourth Seat board, stack, card targets, mana symbols, and priority controls are included. Rules and all three opponents run locally using the verified XMage engine and Fourth Seat Commander AI. Ten existing published decks are bundled; the local deck picker can import another 100-card Commander list. The engine validates deck legality before starting.

Internet is needed for card artwork and deck imports. Bundled decks and rules do not require a running Codespace. This first package is solo-only; website accounts, shared rooms, and saved website decks are not synced. Closing the engine ends the current game; in-progress games cannot be resumed after shutdown. Imported decks are retained locally.

## Stop, troubleshoot, remove

Use **Stop local engine** in the game menu, or close the launcher window. Both stop its Java services. Closing only the browser tab leaves the engine running so you can reopen http://127.0.0.1:17890/ . Launching again reopens an existing instance.

Logs and imported decks live in `%LOCALAPPDATA%\TheFourthSeat`. On a failed start, read the launcher message and the `logs` folder. Ports 17890, 17771, 18787, 18788, and 18789 must be free. All services bind to loopback; do not add firewall exceptions or expose them to the internet. If startup says another engine is running, close its launcher first.

To remove the app, close it and delete the extracted folder. Optionally delete `%LOCALAPPDATA%\TheFourthSeat` to remove saved decks and generated engine data.

## Reproduce and source

Source: https://github.com/edmundwhittaker-tech/the-fourth-seat-rules-engine/tree/integration/xmage/desktop
The release includes a matching source bundle, notices, and SHA256 checksums. Engine source is pinned to commit 532e6e20c2e6ea79ccdf09aec69c0daeddd4383d; its full paired-core manifest is in `engine/commander-ai-manifest.json`. The bridge and server/core are verified together before packaging. Java 17.0.20+8 and Node 24.19.0 are checksum-pinned in `scripts/package.py`.

The desktop workflow builds the shared web UI, compiles the launcher, verifies the engine artifact, starts a native Windows game with three AI opponents, tests end-game/rematch/shutdown and process cleanup, then publishes the ZIP. See the workflow for exact build steps. `ui/provenance.json` identifies the exported client files; private Site administration, database code, and deployment credentials are excluded.
