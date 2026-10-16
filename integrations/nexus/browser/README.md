# Stage 3: real four-seat Commander browser proof

The isolated `integration/xmage` branch runs Chromium, the pinned Nexus web
client, its bridge and our own XMage compatibility server in GitHub Actions.
Nothing runs on the contributor's computer and no public XMage server is used.
It can reuse the immutable Stage 2 build recorded in `reusable-build.json`,
after checking the source/toolchain pins, build-script hash and binary
checksums. If that artifact expires or no longer matches, it builds fresh.

The test uses a real `Commander Free For All` table, the official Commander deck
validator, one human seat and three `COMPUTER_MAD` seats. Bridge SIM is disabled.
The human is driven by Playwright through visible browser controls. A second
connection to the same human session only creates/seats the table and resolves
card printings; it does not answer gameplay prompts. Assertions use server
events and resulting game state, not transport acknowledgements alone.
For overlapping hand cards and permanent piles the test finds an exposed
surface, hovers to expand the fan and uses real pointer input. It confirms
each land has entered the battlefield and plays at most one per human turn.

Decks are deliberately small in variety: the human has 97 Mountains, Lightning
Bolt, Shock and Krenko, Mob Boss as commander; each AI has 99 Mountains and
Purphoros, God of the Forge. All are legal 100-card singleton Commander decks.
Initial shuffling and starting-player selection are disabled for a repeatable
opening. Cards are drawn and lands played normally; no cheatSetup or injected
game state is used. This is a mechanics proof, not a claim that all Commander
cards, strong AI play or an entire game to victory have been validated.

The required checkpoints are four seats at 40 life, three native AI opponents
playing lands, casting Krenko for four mana, targeting an opponent with Shock,
choosing a defender and dealing commander combat damage, targeting Krenko with
Lightning Bolt, accepting his return to the command zone, and recasting for six
mana including the commander tax. Native AI casting is also recorded.

The Actions artifact contains a JSON assertion report, event/action journal,
screenshots, browser video/trace, web build/test logs, and server/bridge logs.
Failures retain partial evidence and fail the workflow. Open the Actions run in
a browser to inspect or download it. The runner stops all services on exit;
this test does not create a permanent public play URL. The main playground and
website remain separate.
