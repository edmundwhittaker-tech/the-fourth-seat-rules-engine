# AI activity audit — 24 October 2026

**Status: one confirmed opening-hand defect corrected in source; not deployed.
The broader candidate audit did not pass its strict completion/performance gate.**

Source: `f74aebb0cecbda3b938f7d8daceaec722c06682e`. Baseline: the verified
`7633cc66` release artifact, not a reconstructed or different XMage AI.

The baseline bot put two Mountains on the bottom of a three-land opening after
paid London mulligans. It kept expensive spells, played one land and cast nothing
over five own main phases. The actual native callback reproduced the bad selection
eight times. The correction retains scarce lands and useful colours and bottoms
late payoffs first. In the corresponding fresh game Krenko played three lands and
cast four spells. This identifies a real source of inactivity, but does not prove
that it was the cause in the user's particular live game.

| Check | Released AI | Corrected candidate |
|---|---:|---:|
| Naturally completed games | 9/9 | 8/9 |
| Seats flagged for no spells after three observed turns | 1 | 0 |
| Rejected activations | 0 | 0 |
| Caught priority exceptions | 0 | 1 |
| Maximum observed interval without a completed turn | 3.407 s | 142.576 s |
| Strict completion/performance gate | Pass | **Fail** |

All 337 actual-engine scenarios, 27 cost contracts, 291 policy assertions and nine
reporting checks pass on the candidate. Six new engine regressions cover native
mulligan selection, repeated bottom choices, scarce lands, flooding, the sole
coloured source, target legality and the unchanged in-game discard path; three of
those tests fail on the old binary. The new reporting flags expose inactive seats
even when the game as a whole completes.

Two unresolved findings are deliberately retained:

- Batch 2, pod 0 hit the unchanged 300-second process timeout. Aesi reached the
  native 500-token limit. Stack samples showed expensive state copies, mana
  discovery and continuous-effect work during queued trigger resolution; the next
  turn also remained in combat until timeout. It is not a completed game, and the
  diagnostic evidence does not justify guessing a safe general rules shortcut.
- Batch 1, pod 0 logged one caught `IllegalArgumentException` during priority just
  after Goreclaw's elimination. The game completed, but this remains an error.
  The existing logger records the exception class without its stack trace, so the
  precise throwing site has not been established.

The full-source GitHub build/service/container check was started as run
`40042506392`; its final result is separate from this isolated candidate audit.
Neither the live Codespace container nor the website was replaced. Terminal/log
access is blocked by VS Code's **Trust Folder & Continue** security prompt.

This is a functional and diagnostic assessment, not a human skill rating. The AI
can develop, cast commanders, respond and use its deck synergies in tested cases,
but it is not consistently reliable on large boards. No win-rate improvement is
claimed: before/after seeds do not fix UUID ties, timing or later draws, and these
were not controlled head-to-head games against human or native-AI opponents.

The adjacent JSON contains every per-seat result and artifact hashes. The trace
archive contains only synthetic test games, including the failure; it contains no
live player hands, service configuration or credentials.
