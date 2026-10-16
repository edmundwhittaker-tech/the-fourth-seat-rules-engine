# Manual activation after review: separate test server only

Nothing in this directory activates the bot. No server has been started, reconfigured or deployed by these templates. Do not apply them to the live Fourth Seat server, a shared game server, or the default development runtime.

## Why the existing enum stays unchanged

Verified against XMage commit `825d1205acca114825f235c6993f1e5a6a6db9fa`:

1. `Mage/src/main/java/mage/players/PlayerType.java` maps `COMPUTER_MAD` to the exact description `Computer - mad`; unknown descriptions throw. There is no `COMPUTER_FOURTH_SEAT` enum.
2. `Mage.Server/src/main/java/mage/server/Main.java` loads configured player types through `PluginUtil.loadPlugin`, then `PlayerFactory.addPlayerType` maps the description back to that enum.
3. `Mage.Server/src/main/java/mage/server/game/PlayerFactory.java` invokes the configured class's public `(String, RangeOfInfluence, int)` constructor. The new adapter provides exactly that signature.
4. `Mage.Server/src/main/java/mage/server/util/PluginUtil.java` resolves the configured jar under `plugins/`, relative to the test server working directory. It loads the configured class by name.
5. The unchanged Fourth Seat gateway already submits `COMPUTER_MAD` for AI seats (`integrations/fourth-seat/gateway/server.mjs`). No gateway, enum, client or deck-list change is needed for this isolated mapping.

This opt-in mapping replaces **every COMPUTER_MAD seat on that test server**, not just one room. The UI still calls the seat “Computer - mad”; this is a server-local experimental implementation substitution, not a new public player type. Never point ordinary/shared rooms at it by accident.

## Review/install checklist (instructions, not executed)

Use a new disposable server directory, referred to below as TEST_SERVER. It must be a complete server distribution built from the exact pinned revision with matching 1.4.62 engine, card set and plugins. Do not point TEST_SERVER at the existing live/default server directory, share its database files, or reuse live ports. The plugin jar alone is not a complete server installation.

1. Finish the isolated build and inspect all test results and limitations. Build output is BUILD_DIRECTORY/Mage.Server.Plugins/Mage.Player.AI/target/mage-player-ai-1.4.62.jar. Verify that jar contains `mage/player/ai/ComputerPlayerFourthSeat.class`, `FourthSeatCombatAdapter.class`, and `fourthseat/ai/` policy classes.
2. Confirm TEST_SERVER is stopped and no game uses it. Back up TEST_SERVER/config/config.xml outside its config folder. Record the exact original Computer - mad entry. If TEST_SERVER/plugins/mage-player-ai-1.4.62.jar already exists, back it up too. Do not replace TEST_SERVER/lib/mage-player-ai-1.4.62.jar or any MAD jar.
3. Copy only the newly built mage-player-ai-1.4.62.jar to TEST_SERVER/plugins/mage-player-ai-1.4.62.jar. Keep the original mage-player-ai-mad-1.4.62.jar unchanged for rollback.
4. In TEST_SERVER/config/config.xml, replace **only** its existing playerType entry whose name is exactly `Computer - mad` with the single entry in `player-type.example.xml`. Do not append a second entry with that name, and do not add an invented enum/description. Leave Human, game types, rules, defaults, databases and all other settings unchanged.
5. `test-server-release-config.patch` illustrates the exact one-line replacement for a rendered 1.4.62 release config. It is an offline review template, not an auto-apply script. If your original entry uses an unversioned jar name (`mage-player-ai-mad.jar`), replace that exact entry manually instead; do not force a nonmatching patch. `${project.version}` in the upstream release source is a Maven placeholder, not a valid filename to copy into a rendered distribution.
6. Only after separate approval to activate, start that isolated test server with its existing normal launcher, working directory TEST_SERVER, distinct local ports and no public exposure. Check logs for plugin-loading/PlayerFactory errors before creating a new test room. Explicitly select the isolated server in a separate test client/proxy configuration; do not alter the production gateway route. If the current gateway cannot be pointed at a separate server without changing production, stop and set up a separate test gateway first.
7. Validate one human plus three AI seats and all ten unchanged decks. Confirm the actual class in test diagnostics; the old “Computer - mad” UI label is not proof. Check legal payment, optional targets, commander casting/tax, all priority windows, resolving triggers, attacking/blocking and repeated turns. Do not treat a compile or the focused fixture suite as a full-game soak.

## Rollback

1. Stop only TEST_SERVER and finish/abandon its experimental games; do not deserialize/resume them using another player implementation.
2. Restore its exact saved config/config.xml, including the original ComputerPlayerControllableProxy class and original MAD jar filename.
3. Restore the previous plugins/mage-player-ai-1.4.62.jar if one existed, otherwise remove only the experimental copy. Keep every lib file and original MAD jar untouched.
4. When separately approved, restart that test server normally and verify fresh COMPUTER_MAD seats use the original implementation. No gateway rollback is necessary because no production gateway change was made.

## Scope and known limits

This is a manual activation path verified from the source loader contract, not a report that activation or deployment occurred. The adapter extends basic ComputerPlayer and does not install the MAD controllable-player wrapper. Turn-control cards and unusual choice flows need dedicated real-game validation. It retains heuristic/modal/payment/combat limitations documented in ../adapter/README.md. Timing caps are soft, and no win-rate or universal mastery claim is made.
