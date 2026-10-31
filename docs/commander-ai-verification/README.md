# Winston AI in The Fourth Seat

The rules-engine bot seats use `mage.player.ai.ComputerPlayerFourthSeat`, which
runs Winston's ten-deck Commander policy. XMage remains the authority for
legality, costs, targets, combat, triggers and resolution. Existing Fourth Seat
sessions, table controls and normal play modes use the same interface. The
rules-engine mode remains admin-only.

| Component | Responsibility |
| --- | --- |
| Fourth Seat interface | Table, card/player selections, phases, mana, stack and lobby |
| Authenticated gateway | Sessions, seats, decks, reconnect and game lifecycle |
| Pinned Nexus bridge | Browser protocol and native XMage prompts |
| XMage server | Authoritative game state and rules |
| Winston policy and adapter | Bot decisions and native cost/choice handling |

The server and bridge are built together from pinned sources in GitHub Actions.
Startup verifies artifact hashes and all 4,505 shared core classes, including the
counter-observer proof registry. The existing Codespace, data volume, website
origin and broker credentials are retained. No website deployment is required
for this bot upgrade.

## Verification

The accepted code revision is `7633cc66aed5d1bd0fc3978bd3a9a108818c0a3d`.
Its [cloud verification run](https://github.com/edmundwhittaker-tech/the-fourth-seat-rules-engine/actions/runs/39334033420)
builds the exact deployable artifacts. See `release-20261019.json` for the final
artifact/deployment receipt and live smoke result.

The workflow covers 291 policy checks, 331 actual-engine scenarios, 27 cost
contracts, 239 bridge tests, 24 gateway tests, ten artifact-pairing tests, nine
packaged games across all ten published decks, the 24-check private service
lifecycle, and the real container under a 4 GiB limit. The original six-test
TypeScript playground is independent.

The live smoke creates one human and three distinct bot decks, requires actual
battlefield plays by all three bots and the human, checks hidden information,
and ends the test game through the retained lobby before cleaning up.

## Scope and next milestone

Large token boards remain a performance limitation. An additional local stress
run on this revision completed its six games with zero rejected actions but
exceeded the 30-second completed-turn interval on one Aesi turn (48,492 ms).
Stack samples show native priority discovery and state copying while processing
hundreds of tokens. This is a long busy turn, not evidence of an incorrect card
outcome. The release's independent cloud suite is recorded separately; the
extra stress result is not counted as a passing performance gate.

The next milestone is to record and replay these large-board scenarios, then
reduce native state-copy and priority-discovery work while preserving legal
responses, mandatory triggers and token totals. Keep the current admin testing
scope while this work proceeds. Broader card support and AI decision quality
need continued scenario coverage; the tests do not certify every Commander
interaction or every deck.
