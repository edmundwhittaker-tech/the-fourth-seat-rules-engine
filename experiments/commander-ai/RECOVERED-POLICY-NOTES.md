# Recovered policy changes, 2026-10-17

This is a semantic reconstruction on hosted base 24468d, not a byte-identical recovery of the missing accepted revision. Earlier engine/soak counts are not acceptance evidence for this source.

## Restored contracts

- Action preserves every existing constructor and adds a trailing Map<String, Set<String>> targetOutcomes after sacrifices. Both map and nested sets are defensively copied and immutable.
- Observation preserves existing constructors and adds four trailing booleans after combatBeforeDevelopment: developmentSaturated, reserveGrowthSaturated, reserveUsesProjectedMana, ashayaLandfallDevelopment. Defaults are false. reserveUsesProjectedMana is diagnostic only.
- CommanderPolicy.isStrategicallyHeld is the shared public admission predicate. Normal candidate ranking and zero-budget land fallback both obey it. Adapter code must call the same predicate for any separate admission/fallback path.
- Reserve growth holds land/ramp/PutLand development in any phase, on either player's turn, and with a pending stack. With the Ashaya flag it also holds own-hand BODY/RAMP/ENGINE/SUPPORT creature spells. It preserves payoff/removal/sweeper/protection/counter exceptions.
- Ordinary development saturation holds land/ramp development only on an empty stack, including off-turn. Combat-first holds additional development during own upkeep/draw/precombat with an empty stack, not indefinitely after combat.
- An owned pending spell delays additional proactive BODY/RAMP/ENGINE/PAYOFF/SUPPORT spells and untargeted removal creatures whose interaction happens on entry. It does not delay targeted removal or counter/protection responses.
- Typed per-target effects determine ownership safety and threat utility. A combined spell may benefit an owned target and remove an opposing target; aggregate beneficial effects no longer conceal harmful target ownership. Counter and Detriment count as harmful effects.

## Fresh validation

Compiled policy sources with JDK 17 using --release 8 and independently ran:
- CommanderPolicyTest: 24 checks.
- DeckPlanTest: 122 checks.
- RecoveredPolicyContractTest: 41 new checks.

The new regression suite covers phase/active-player/stack reserve behavior, projected-mana diagnostics, zero-budget continuity, empty-stack saturation, combat-window boundaries, Ashaya growth, owned pending spells, deep immutability, mixed-mode ownership, and typed Detriment. Full suite and engine/soak acceptance must be rerun on the final integrated reconstruction. No deployment or live activation was performed.
