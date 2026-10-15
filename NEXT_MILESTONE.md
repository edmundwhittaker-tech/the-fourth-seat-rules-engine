# Architecture review and next milestone

## Review of the imported v0.1 starter

The core direction is sound: serializable TypeScript state, a command union,
and a transaction function that clones its input before validation. The kernel
has no network, storage, UI, or LLM dependency. The playground is a separate
consumer. Preserve that boundary for Sites integration and AI opponents.

The six tests cover basic priority, stack order, spell timing, simple mana,
hidden-card omission, and snapshot preservation. They demonstrate the supported
subset; they do not establish comprehensive Magic rules correctness.

Concrete gaps found in the source:

- `applyAction` has no default rejection for unknown runtime command types.
  TypeScript types alone cannot validate JSON received from a browser or AI.
- `version: 1` is a schema identifier, not an incrementing state revision.
  A future session service needs separate revision and retry/idempotency fields.
- `getPublicState` returns each player's original `mana` object by reference.
  A caller can mutate the authoritative state through that view. Private player
  views and isolated public snapshots need explicit tests before networking.
- Life reaching zero and failed draws do not eliminate players. There are no
  state-based actions, pending choices, or terminal game results.
- Untap is folded into upkeep, cleanup is absent, and combat steps have labels
  without their turn-based actions. Generic mana payment is automatic.
- `GameEvent` is a text message rather than a structured domain event. Replay
  should initially record validated commands and their initial state.
- The compiler currently writes core and playground modules into `docs/assets`.
  A separate library output and browser build will make backend reuse clearer.

## Proposed milestone: a reliable, replayable rules kernel

Build the deterministic foundations before adding Commander-specific abilities
or an opponent that makes strategic decisions.

1. Add runtime command validation, stable error codes, and state invariants.
   Reject unknown commands, invalid IDs and malformed inputs without mutation.
2. Separate schema version from a monotonically increasing state revision.
   Record accepted commands and replay them from a saved initial state.
3. Introduce explicit player status and state-based checks for the supported
   life-loss and failed-draw cases. Handle simultaneous losses, skip eliminated
   seats in priority and turns, and report game results. Define the supported
   elimination cleanup carefully, including objects owned/controlled by them.
4. Add detached `getPublicState` and seat-specific `getPlayerView` snapshots.
   Verify opposing hands and library identities never appear in a player's view.
5. Expose legal-action candidates for the currently supported card subset.
   Humans and a simple deterministic test bot must submit the same commands to
   the same validator. Candidate generation must not leak private information.
6. Split portable library output from the playground, and add browser scenarios
   showing a game ending, replaying identically, and rejecting an invalid action.

### Acceptance criteria

- Existing six regressions remain green in GitHub Actions.
- Targeted tests cover malformed commands, rejected-action immutability,
  detached views, simultaneous losses, priority after elimination, and replay.
- Two-, three-, and four-seat fixtures produce repeatable terminal outcomes
  using only the explicitly supported test-card subset.
- The browser playground can demonstrate and reset these scenarios.
- Every new rules behavior records the applicable official Comprehensive Rules
  reference, checked against the current published rules during implementation.

## Following milestones

After the reliable kernel: choice prompts and payment selection, correct turn
structure, a small structured ability system, Commander tax/replacement choices
and commander damage, then combat. In parallel, evaluate whether adapting an
existing mature engine better serves full-card Commander coverage.

For eventual integration, an authenticated session service owns hidden state,
serializes commands, checks revisions, and distributes filtered views. GitHub
Pages remains a static development playground. An AI opponent receives its seat
view and legal choices; the engine remains responsible for rule enforcement.
