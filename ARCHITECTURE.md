# Architecture and integration decisions

## Three components, three responsibilities

1. **The Fourth Seat browser UI (existing Sites project):** shows cards, prompts players, and displays authoritative updates. It may suggest an action but cannot approve its own rules changes.
2. **Rules kernel (this repo):** knows game rules; accepts explicit commands; validates preconditions; produces next state plus event messages; has no database or UI dependencies.
3. **Future game session server (separate deploy, same repo or separate):** authenticates a player/seat, keeps hidden state, serializes concurrent commands, calls the kernel, persists state, and sends a different state projection to each player.

### Proposed HTTP or websocket message contract (not implemented yet)

Client → session server:

```json
{"roomId":"room-123", "actionId":"uuid-per-attempt", "expectedVersion":42,
 "command":{"type":"PASS_PRIORITY","playerId":"seat-1"}}
```

Server → client:

```json
{"roomId":"room-123", "version":43,
 "events":[{"type":"pass","text":"Ari passes priority."}],
 "stateView":{"turnNumber":1,"step":"main1"}}
```

A future implementation should check `playerId` against the authenticated room seat (never trust the client-supplied value), check the expected version, apply the action atomically, save the result, and send views that only reveal each player's permitted private information. The JSON snippets are architecture sketches, **not working endpoints**.

## Why start with TypeScript?

- Works in both browser and Node.js environments.
- Fits a browser-first Sites frontend without rewriting its UI.
- Allows plain deterministic tests without hosting a server.
- Lets us move the exact same command rules from development playground to a trusted backend.

## Rules fidelity gates

Before turning on automated validation in live games, require at minimum:

- Stable unique identity and per-zone card visibility and ownership/control rules
- Correct rule ordering: spell/action, triggers, state-based actions, priority, stack resolution
- Timing model: all steps, turn-based actions, cleanup, mana-emptying
- Choice prompts and *pending decisions* (targets, payments, replacements)
- Commander mechanics, player elimination, end-game results
- Nontrivial combat and targeting + priority after declaration events
- Multiplayer permission checks and no information leaks
- Regression tests using specific official Comprehensive Rules references

**Do not use an LLM to rule on the next game state.** It may propose actions or explain rules, but the rules kernel must deterministically validate and carry them out. An AI player should be another participant submitting candidate actions to the same API.

## Buy-versus-build checkpoint

XMage (https://github.com/magefree/mage) is a mature Java server-based Magic rules engine with multiplayer and AI. Forge (https://github.com/Card-Forge/forge) is another mature Java engine. For a production-grade game enforcing thousands of real cards, investigate adapting one of these before expanding this lightweight TypeScript engine. Integration complexity, licensing and card-data rights matter, and neither Java implementation will run as a GitHub Pages backend.

## References

- Official rules and revisions: https://magic.wizards.com/en/rules
- GitHub Pages hosting: https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages
- Wizards of the Coast fan policy: https://company.wizards.com/en/legal/fancontentpolicy
