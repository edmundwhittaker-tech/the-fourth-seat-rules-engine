# Commander AI review handoff

## Status

This candidate was reconstructed after loss of the former local accepted artifact. This source starts from hosted commit24468d and restores later behavior semantically. It is not byte-identical recovery. The exact rebuilt JAR with its explicitly patched engine core passes270 actual-engine scenarios plus27 cost-contract assertions, and291 pure-policy checks pass. The fresh nine-game gate passed with zero rejected actions, engine errors, exceptions, timeouts or loop draws. The maximum observed progress gap was18.229 seconds. Its exact runtime also passes compatibility, startup/security and239 bridge checks. The Windows TEST CANDIDATE is suitable for isolated offline review, not a production-readiness claim. Do not activate on the live site. No live game or default bot registration is changed.

## Intended deck behavior

| Commander | Plan and intended AI behavior |
|---|---|
| Giada, Font of Hope | Establish Giada before follow-up Angels, use her restricted mana only legally, choose Angel for tribal effects, sequence large Angels and preserve meaningful protection. |
| Krenko, Mob Boss | Build Goblins before multiplying, allow token-maker spells to resolve before tapping Krenko, choose useful attacks without sacrificing the next engine activation, and use supported lethal burn/sacrifice lines without wasting the commander. |
| Lathril, Blade of the Elves | Build actual Elves, exploit menace, retain ten other untapped Elves for the drain when available, and use protection or combat pumps on the relevant threatened/attacking creature. |
| Ghalta, Primal Hunger | Develop board power and green mana, pay the actual discounted commander cost and tax, then use trample pressure while preserving defense against a lethal counterattack. |
| Goreclaw, Terror of Qal Sisma | Ramp into large creatures, respect cost reductions and dependent mana producers, use finishers in a useful combat window, and avoid repeatedly paying for redundant land animation. |
| Torbran, Thane of Red Fell | Develop the damage amplifier and use audited burn amounts for actual lethal targets, without assuming Torbran's bonus when he is absent. |
| Talrand, Sky Summoner | Protect interaction mana, counter meaningful opposing threats rather than own spells or small bait, use suitable end-step cantrips, and turn Drake development into attacks. |
| Aesi, Tyrant of Gyre Strait | Establish the draw engine before landfall when funded, sequence lands/ramp and reusable mana, preserve interaction/payoffs, decline unnecessary explosive growth when public board/resources already suffice, and avoid optional empty-library draws. |
| Gishath, Sun's Avatar | Ramp and fix Naya mana, choose Dinosaur tribal options, attack a legal connectable opponent using trample/haste and supported prevention interactions, and preserve defenses where necessary. |
| Adeline, Resplendent Cathar | Develop a wide board and useful attacks that trigger Adeline even if she stays back; choose Human/white options and preserve relevant protection. |

These are bounded heuristics, not a human-strength claim or a general solution to every Magic interaction. Actual engine legality, costs, triggers and resolution remain authoritative. The policy does not inspect opposing private hands or library order.

## Fresh acceptance established

1. Pinned engine compilation and actual-card payment, choice, sequencing and combat regressions, including previously discovered failures.
2. Nine mixed full games with all ten decks represented at least three times; no rejected activations, engine errors, caught priority exceptions or loop-confirmed draws.
3. Unchanged bounds: at most120 turns and300 seconds per game, with observed progress gaps at most30 seconds.
4. A new source revision, class hashes and artifact manifest tied to these fresh results. Historical passing counts do not certify the reconstructed source.

Manual activation belongs on a separate test server after review; use activation/README.md. Nothing here automatically deploys or connects the experimental player to the live site.

## Review limitations

- Opponent hands, unrevealed library cards and hidden ordering are not policy inputs. Public battlefield, stack, life, hand counts, legal choices and the bot's own hand are available.
- The candidate budget is soft. Native engine legality, target expansion, triggers and state copying can exceed it; the full-game latency gate is separate evidence, not a universal hard real-time guarantee.
- Supported modal target sampling deliberately bounds combinations. It retains useful legal mode diversity but does not exhaustively search every cross-target combination.
- Mana dependency planning is bounded and must perform a real action before replanning. It is not a general solver for arbitrary mana combos or alternate costs.
- Combat uses public, bounded approximations with actual engine legality. It does not fully predict every replacement effect, combat trick, multiway bargain or future turn.
- Explosive-growth restraint is a strategic decision about optional development, not a change to card rules, mandatory triggers, token limits or hidden information.
- Frozen deck changes can invalidate hand-authored role coverage. Re-run membership verification, focused scenarios and mixed games after changing a list.
