# The Fourth Seat — Rules Engine (prototype v0.1)

A small **independent, testable TypeScript rules kernel** for exploring how a Magic-style Commander engine could work before connecting it to The Fourth Seat's live site.

> **Status: experimental, not comprehensive Magic rules enforcement.** Never use this release as the source of truth for a real Commander match. It is deliberately limited to a handful of rules and fictional test cards.

## Work entirely in a web browser (recommended)

**No local development environment is required.** The repository can be edited and tested in GitHub Codespaces and its demo can be hosted on GitHub Pages. See **[CLOUD_SETUP.md](./CLOUD_SETUP.md)** for browser-only onboarding.

- [Repository](https://github.com/edmundwhittaker-tech/the-fourth-seat-rules-engine)
- [Browser playground](https://edmundwhittaker-tech.github.io/the-fourth-seat-rules-engine/) (available after Pages is enabled and deployment succeeds)
- [Automated tests and deployment](https://github.com/edmundwhittaker-tech/the-fourth-seat-rules-engine/actions)
- [Architecture review and next milestone](./NEXT_MILESTONE.md)

## Run the playground locally (optional)

Requires Node.js 20+.

```bash
npm ci
npm test
npm run dev
```

Open **http://localhost:8080**. The sample playground is designed to deploy to **GitHub Pages** with no server, and it exposes every demo player's hand to make debugging easier.

## Upload to GitHub and deploy the playground

1. Create a new GitHub repo, e.g. `fourth-seat-rules-engine`.
2. Upload/commit the contents of this folder to its `main` branch.
3. In the repo's **Settings → Pages**, select **GitHub Actions** as the deployment source.
4. The `.github/workflows/pages.yml` workflow runs tests, compiles TypeScript into `docs/assets`, and deploys `docs/` to Pages.
5. Visit the GitHub Pages URL once the workflow reports success.

GitHub Pages hosts the *browser demo*, **not a trusted multiplayer rules service**. When connecting to the live game, run the same pure engine **server-side** and send each player only a filtered game-state view.

## Code structure

- `src/types.ts` — portable, JSON-friendly game-state and command definitions
- `src/engine.ts` — state transitions, validation, priority, stack and simple mana
- `src/demo.ts` — fictional sample cards and ordered demo libraries
- `src/playground.ts` — browser debug UI; never trust as multiplayer enforcement
- `tests/engine.test.mjs` — independent automated tests
- `docs/` — statically hosted interactive browser playground
- `.github/workflows/pages.yml` — tests on pushes and pull requests; deploy after successful main-branch tests
- `.devcontainer/devcontainer.json` — ready-to-use GitHub Codespaces setup
- `CLOUD_SETUP.md` — browser-only deployment and development instructions

Import from `src/index.ts` (compiled to `docs/assets/index.js`) in a TypeScript/Web frontend or a server. Example:

```ts
const { state: next, events } = applyAction(current, {
  type: "PASS_PRIORITY", playerId: current.priorityPlayerId,
});
```

**Core contract:** `applyAction(previousState, command)` either returns `{ state, events }` or throws `RuleError`. A failed command **does not mutate** previous state.

## Precisely what v0.1 handles

- Two to four player sample games (initial 40 life; accepts small fixture decks)
- Opening seven-card hands from a preordered library
- Turn/step sequence, simplified untapping and draw steps (first-turn duel exception)
- Priority passing and last-in-first-out stack spell resolution
- One land play per turn; tap designated mana-producing permanents
- Simple mana costs (`{2}{G}`, `{R}` etc.) and automatic generic payment
- Basic spell type timing (instant vs other spell)
- A simple draw-card spell effect and player damage spell effect
- Read-only public view that omits hidden hand and library contents
- Repeatable tests and static browser debugger

## Explicitly unsupported — do not assume correct Magic behavior

- **Combat**: attacks, blocks, damage assignment, first strike, trample, etc.
- **Commander**: command-zone casts, tax, color identity, commander damage, replacements
- **Triggered/static/activated abilities**, except the single designated mana tap action
- **State-based actions** (including losing at 0 life, decking, lethal damage)
- Target legality beyond validating a player ID; hexproof, protection, hexproof-to-player, etc.
- Replacement/prevention effects, continuous effects and layer system
- Summoning sickness, counters, tokens, attachments, transform, modal spells
- Mana symbols other than generic and single-color, player-selected generic payment
- Mulligans, shuffling, randomized hidden deck order, library search
- Stack timing intricacies including spell-copying, triggered abilities and intervening state-based action checks
- Combat-related priority windows are *listed* but their turn-based actions are not implemented
- Player elimination, multiplayer priority skipping and win conditions
- Full card text interpretation: **there is no Oracle card parser**

## Recommended path to a real rules engine

1. **Keep manual mode:** users can continue to play The Fourth Seat without enforcement.
2. **Separate server authority:** install this library into a server worker; let clients send requests only, never push trusted state changes.
3. **Reliable foundations:** add state-based actions, choices, player elimination, target legality, resolution timing, zone-change tracking, comprehensive event sourcing, and replay.
4. **Commander-specific features:** command-zone replacement choices, tax, commander damage, multiplayer turn order.
5. **Ability definitions:** use structured behaviors for a narrow, curated card subset before attempting arbitrary Oracle text.
6. **Combat + continuous effects:** add attack/block legality, combat damage, layers, and comprehensive test fixtures.
7. **AI:** the AI chooses among server-generated legal actions; the authoritative engine validates them.

The official Comprehensive Rules are the reference: https://magic.wizards.com/en/rules. Note that card data services (such as Scryfall) provide card text and metadata; they **do not provide a runnable interpretation** of every Magic card interaction.

## Legal and IP

This project intentionally ships no Wizards card images, Oracle text database, or copied Comprehensive Rules. Before publishing a full card library or artwork, check the relevant rights and service requirements: https://company.wizards.com/en/legal/fancontentpolicy. The engine is unofficial, independent fan work, not affiliated with or endorsed by Wizards of the Coast. Wizards' trademarks and source materials belong to their owners.
