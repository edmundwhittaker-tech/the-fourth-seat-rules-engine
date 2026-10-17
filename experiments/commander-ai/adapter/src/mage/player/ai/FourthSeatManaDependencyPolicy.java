package mage.player.ai;

import java.util.*;
import mage.Mana;
import mage.ConditionalMana;
import mage.MageObject;
import mage.abilities.Ability;
import mage.abilities.ActivatedAbility;
import mage.abilities.costs.Cost;
import mage.abilities.costs.common.TapSourceCost;
import mage.abilities.costs.common.TapTargetCost;
import mage.abilities.costs.common.PutCountersSourceCost;
import mage.abilities.costs.mana.ManaCost;
import mage.abilities.effects.Effect;
import mage.abilities.effects.mana.BasicManaEffect;
import mage.abilities.effects.common.UntapSourceEffect;
import mage.abilities.mana.ActivatedManaAbilityImpl;
import mage.abilities.mana.ManaOptions;
import mage.constants.Zone;
import mage.counters.CounterType;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.game.stack.StackObject;
import mage.players.Player;
import mage.target.Target;

/** Bounded public mana dependency certificates, not a payment or game-state simulator.
 * Every returned action must execute normally and yield to engine priority before replanning.
 * Reconstructed from retained contracts; fresh tests, not historical hashes, establish fitness. */
public final class FourthSeatManaDependencyPolicy {
    private FourthSeatManaDependencyPolicy() {}
    private static final int LIMIT = 64, SOURCE_LIMIT = 32;
    private static final class Budget { int left = 4096; boolean take() { return left-- > 0; } }
    public static final class ActionPlan {
        public final ActivatedAbility ability;
        public final ManaCost demand;
        public final String reason;
        ActionPlan(ActivatedAbility ability, ManaCost demand, String reason) {
            this.ability = ability.copy(); this.demand = demand.copy(); this.reason = reason;
        }
    }
    public static final class GroupTapPlan {
        public final ActivatedManaAbilityImpl ability;
        public final Set<UUID> reservedTapIds;
        public final Mana output;
        GroupTapPlan(ActivatedManaAbilityImpl ability, Set<UUID> ids, Mana output) {
            this.ability = ability; this.reservedTapIds = Collections.unmodifiableSet(new HashSet<UUID>(ids)); this.output = output.copy();
        }
    }
    public static final class SelfUntapPlan {
        public final ActivatedAbility ability;
        public final UUID sourceId;
        public final Mana output;
        SelfUntapPlan(ActivatedAbility ability, UUID sourceId, Mana output) {
            this.ability = ability.copy(); this.sourceId = sourceId; this.output = output.copy();
        }
    }
    public static boolean isGroupTapProducer(Ability ability) {
        return ability.isManaAbility() && ability.getCosts().stream().anyMatch(c -> c instanceof TapTargetCost);
    }
    public static List<GroupTapPlan> listGroupTapPlans(Game game, ComputerPlayerFourthSeat player) {
        List<GroupTapPlan> result = new ArrayList<GroupTapPlan>();
        int examined = 0;
        for (Permanent p : game.getBattlefield().getAllActivePermanents()) {
            for (ActivatedManaAbilityImpl original : p.getAbilities(game).getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, player.getId(), game)) {
                if (!isGroupTapProducer(original)) continue;
                if (++examined > 32 || result.size() >= 8) return result;
                if (!original.getManaCosts().isEmpty() || original.getCosts().size() != 1
                        || !(original.getCosts().get(0) instanceof TapTargetCost)) continue;
                Mana output = new Mana(); boolean supported = true;
                for (Effect effect : original.getEffects()) {
                    if (!(effect instanceof BasicManaEffect)) { supported = false; break; }
                    Mana one = ((BasicManaEffect) effect).getManaTemplate();
                    if (one instanceof ConditionalMana || one.getAny() != 0) { supported = false; break; }
                    output.add(one);
                }
                if (!supported || output.count() <= 0) continue;
                ActivatedManaAbilityImpl copy = (ActivatedManaAbilityImpl) original.copy();
                Target target = ((TapTargetCost) copy.getCosts().get(0)).getTarget();
                if (target.getMinNumberOfTargets() <= 0 || target.getMinNumberOfTargets() != target.getMaxNumberOfTargets() || target.getMinNumberOfTargets() > 16) continue;
                List<UUID> legal = new ArrayList<UUID>(target.possibleTargets(player.getId(), copy, game));
                legal.removeIf(id -> player.reservedTapIdsForPlanning().contains(id)
                        || game.getPermanent(id) == null || game.getPermanent(id).isTapped());
                Collections.sort(legal, Comparator.comparing((UUID id) -> !game.getPermanent(id).getAbilities(game)
                        .getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, player.getId(), game).isEmpty()).thenComparing(UUID::toString));
                if (legal.size() < target.getMinNumberOfTargets()) continue;
                target.clearChosen(); Set<UUID> selected = new LinkedHashSet<UUID>();
                for (UUID id : legal) {
                    if (selected.size() == target.getMinNumberOfTargets()) break;
                    if (!target.canTarget(player.getId(), id, copy, game)) continue;
                    target.addTarget(id, copy, game); selected.add(id);
                }
                if (selected.size() == target.getMinNumberOfTargets() && target.isChosen(game)) result.add(new GroupTapPlan(copy, selected, output));
            }
        }
        return result;
    }
    public static List<SelfUntapPlan> listSelfUntapPlans(Game game, ComputerPlayerFourthSeat player,
            Set<UUID> additionallyTapped) {
        List<SelfUntapPlan> result = new ArrayList<>();
        int examined = 0;
        for (Permanent source : game.getBattlefield().getAllActivePermanents(player.getId())) {
            mage.cards.Card card = game.getCard(source.getId());
            if (!(card instanceof mage.cards.d.DevotedDruid)
                    || player.reservedTapIdsForPlanning().contains(source.getId())
                    || (!source.isTapped() && !additionallyTapped.contains(source.getId()))
                    || !source.canTap(game)
                    || source.getToughness().getValue() <= source.getDamage() + 1) continue;
            for (ActivatedAbility ability : source.getAbilities(game).getActivatedAbilities(Zone.BATTLEFIELD)) {
                if (++examined > SOURCE_LIMIT) return result;
                if (ability.isManaAbility() || !ability.getManaCosts().isEmpty()
                        || ability.getCosts().size() != 1 || !(ability.getCosts().get(0) instanceof PutCountersSourceCost)
                        || ability.getEffects().size() != 1 || !(ability.getEffects().get(0) instanceof UntapSourceEffect)
                        || !ability.canActivate(player.getId(), game).canActivate()) continue;
                boolean printed = false;
                for (Ability original : card.getAbilities())
                    if (original.getOriginalId().equals(ability.getOriginalId())
                            && original.getCosts().size() == 1 && original.getCosts().get(0) instanceof PutCountersSourceCost
                            && original.getEffects().size() == 1 && original.getEffects().get(0) instanceof UntapSourceEffect)
                        printed = true;
                if (!printed) continue;
                boolean pending = false;
                for (StackObject object : game.getStack()) {
                    Ability stackAbility = object.getStackAbility();
                    if (stackAbility != null && source.getId().equals(stackAbility.getSourceId())
                            && ability.getOriginalId().equals(stackAbility.getOriginalId())) pending = true;
                }
                if (pending) continue;
                Game after = game.createSimulationForPlayableCalc();
                Permanent ready = after.getPermanent(source.getId());
                if (ready == null) continue;
                ready.addCounters(CounterType.M1M1.createInstance(), ability, after);
                ready.setTapped(false); after.applyEffects();
                if (ready.getToughness().getValue() <= ready.getDamage()) continue;
                for (ActivatedManaAbilityImpl tap : ready.getAbilities(after)
                        .getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, player.getId(), after)) {
                    boolean printedTap = card.getAbilities().stream()
                            .anyMatch(original -> original.getOriginalId().equals(tap.getOriginalId()));
                    if (!printedTap || !tap.getManaCosts().isEmpty() || tap.getCosts().size() != 1
                            || !(tap.getCosts().get(0) instanceof TapSourceCost)
                            || tap.getEffects().size() != 1 || !(tap.getEffects().get(0) instanceof BasicManaEffect)
                            || !tap.canActivate(player.getId(), after).canActivate()) continue;
                    Mana output = ((BasicManaEffect) tap.getEffects().get(0)).getManaTemplate();
                    if (!(output instanceof ConditionalMana) && output.count() == 1 && output.getGreen() == 1) {
                        result.add(new SelfUntapPlan(ability, source.getId(), output)); break;
                    }
                }
                if (result.size() >= 8) return result;
            }
        }
        return result;
    }
    private static boolean applicable(Mana mana, Ability outer, Game game, UUID producer, ManaCost demand) {
        return !(mana instanceof ConditionalMana) || ((ConditionalMana) mana).apply(outer, game, producer, demand);
    }
    private static Mana pool(Ability outer, ManaCost demand, Game game, Player player) {
        Mana result = player.getManaPool().getMana().copy();
        for (ConditionalMana conditional : player.getManaPool().getConditionalMana())
            if (applicable(conditional, outer, game, conditional.getManaProducerId(), demand)) result.add(conditional);
        return result;
    }
    private static Map<UUID,List<ActivatedManaAbilityImpl>> freeSources(Game game, ComputerPlayerFourthSeat player) {
        Map<UUID,List<ActivatedManaAbilityImpl>> result = new LinkedHashMap<>();
        Map<UUID,MageObject> candidates = new LinkedHashMap<>();
        for (MageObject object : player.getAvailableManaProducers(game)) candidates.put(object.getId(), object);
        for (MageObject object : player.getAvailableManaProducersWithCost(game)) candidates.put(object.getId(), object);
        int examined = 0;
        for (MageObject object : candidates.values()) {
            for (ActivatedManaAbilityImpl ability : object.getAbilities()
                    .getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, player.getId(), game)) {
                if (++examined > SOURCE_LIMIT) return result;
                if (!ability.getManaCosts().isEmpty() || ability.getCosts().size() != 1
                        || !(ability.getCosts().get(0) instanceof TapSourceCost)
                        || !ability.canActivate(player.getId(), game).canActivate()) continue;
                result.computeIfAbsent(object.getId(), k -> new ArrayList<>()).add(ability);
            }
        }
        return result;
    }
    private static ManaOptions totals(Mana initial, Map<UUID,List<ActivatedManaAbilityImpl>> sources, Set<UUID> excluded,
            Ability outer, ManaCost demand, Game game) {
        ManaOptions result = new ManaOptions(); result.add(initial.copy());
        for (Map.Entry<UUID,List<ActivatedManaAbilityImpl>> entry : sources.entrySet()) {
            if (excluded.contains(entry.getKey())) continue;
            ManaOptions next = result.copy();
            for (ActivatedManaAbilityImpl ability : entry.getValue()) for (Mana output : ability.getNetMana(game)) {
                if (!applicable(output, outer, game, ability.getSourceId(), demand)) continue;
                for (Mana previous : result) {
                    Mana combined = previous.copy(); combined.add(output); next.add(combined);
                    if (next.size() >= 64) break;
                }
                if (next.size() >= 64) break;
            }
            // Keep strongest totals; zero-output options need not consume scarce bounded slots.
            List<Mana> ordered = new ArrayList<>(next);
            ordered.sort(Comparator.comparingInt((Mana mana) -> -mana.count()));
            result.clear(); for (int i = 0; i < Math.min(LIMIT, ordered.size()); i++) result.add(ordered.get(i));
        }
        return result;
    }
    private static boolean payable(ManaCost demand, Collection<Mana> totals) {
        for (Mana cost : demand.getOptions(false)) for (Mana available : totals) if (cost.enough(available)) return true;
        return demand.getOptions(false).isEmpty();
    }
    public static boolean ordinaryPaymentSuffices(Ability outer, ManaCost unpaid, Game game, ComputerPlayerFourthSeat player) {
        if (outer == null || unpaid == null) return false;
        Game calculation = game.createSimulationForPlayableCalc();
        ComputerPlayerFourthSeat owner = (ComputerPlayerFourthSeat) calculation.getPlayer(player.getId()).getRealPlayer();
        Set<UUID> excluded = new HashSet<>(player.reservedTapIdsForPlanning());
        if (outer.getCosts().stream().anyMatch(c -> c instanceof TapSourceCost)) excluded.add(outer.getSourceId());
        return payable(unpaid, totals(pool(outer, unpaid, calculation, owner), freeSources(calculation, owner), excluded, outer, unpaid, calculation));
    }
    public static ActionPlan nextAction(ActivatedAbility adjusted, Game game, ComputerPlayerFourthSeat player) {
        return nextAction(adjusted, game, player, null);
    }
    public static ActionPlan nextAction(ActivatedAbility adjusted, Game game, ComputerPlayerFourthSeat player, ActivatedAbility raw) {
        if (adjusted == null || adjusted.isManaAbility()) return null;
        Game calculation = game.createSimulationForPlayableCalc();
        ComputerPlayerFourthSeat owner = (ComputerPlayerFourthSeat) calculation.getPlayer(player.getId()).getRealPlayer();
        Budget budget = new Budget();
        ManaCost demand = adjusted.getManaCostsToPay();
        Mana outerPool = pool(adjusted, demand, calculation, owner);
        if (payable(demand, Collections.singleton(outerPool))) return null;
        // Conditional floating mana is not a transferable converter/untap input.
        Mana pool = owner.getManaPool().getMana().copy();
        Map<UUID,List<ActivatedManaAbilityImpl>> free = freeSources(calculation, owner);
        Set<UUID> reserved = new HashSet<>(player.reservedTapIdsForPlanning());
        if (adjusted.getCosts().stream().anyMatch(c -> c instanceof TapSourceCost)) reserved.add(adjusted.getSourceId());
        if (payable(demand, totals(pool, free, reserved, adjusted, demand, calculation))) return null;
        ActionPlan triggered = triggeredPaymentAction(adjusted, demand, outerPool, free, reserved, calculation, budget);
        if (triggered != null) return triggered;
        for (GroupTapPlan group : listGroupTapPlans(game, player)) {
            if (!Collections.disjoint(reserved, group.reservedTapIds)) continue;
            Set<UUID> excluded = new HashSet<UUID>(reserved); excluded.addAll(group.reservedTapIds);
            Mana combined = pool.copy(); combined.add(group.output);
            if (payable(demand, totals(combined, free, excluded, adjusted, demand, calculation)))
                return new ActionPlan(group.ability, demand, "GROUP_TAP");
            for (SelfUntapPlan untap : listSelfUntapPlans(game, player, group.reservedTapIds)) {
                if (!group.reservedTapIds.contains(untap.sourceId)) continue;
                if (untapFunds(adjusted, raw, game, player, untap, combined, excluded))
                    return new ActionPlan(group.ability, demand, "GROUP_TAP_THEN_UNTAP");
            }
        }
        for (SelfUntapPlan untap : listSelfUntapPlans(game, player, Collections.<UUID>emptySet()))
            if (untapFunds(adjusted, raw, game, player, untap, pool, reserved)) return new ActionPlan(untap.ability, demand, "SELF_UNTAP");
        int examined = 0;
        for (Permanent permanent : owner.getAvailableManaProducersWithCost(calculation)) {
            if (reserved.contains(permanent.getId())) continue;
            for (ActivatedManaAbilityImpl converter : permanent.getAbilities(calculation).getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, player.getId(), calculation)) {
                if (++examined > 32) return null;
                if (converter.getManaCosts().isEmpty() || converter.getManaCosts().manaValue() > 8
                        || converter.getCosts().size() != 1 || !(converter.getCosts().get(0) instanceof TapSourceCost)
                        || !converter.canActivate(player.getId(), calculation).canActivate()) continue;
                List<ActivatedManaAbilityImpl> feeders = new ArrayList<ActivatedManaAbilityImpl>();
                for (Map.Entry<UUID,List<ActivatedManaAbilityImpl>> entry : free.entrySet()) if (!entry.getKey().equals(converter.getSourceId()) && !reserved.contains(entry.getKey())) feeders.addAll(entry.getValue());
                if (feeders.size() > 32) feeders = feeders.subList(0, 32);
                ActionPlan plan = conversion(converter, null, null, adjusted, demand, owner.getManaPool().getMana(), pool, free, reserved, calculation, budget);
                if (plan != null) return plan;
                for (ActivatedManaAbilityImpl first : feeders) {
                    if (!budget.take()) return null;
                    plan = conversion(converter, first, null, adjusted, demand, owner.getManaPool().getMana(), pool, free, reserved, calculation, budget);
                    if (plan != null) return plan;
                }
                // Prefer every one-feeder certificate before considering two taps.
                for (int i = 0; i < feeders.size(); i++) for (int j = i + 1; j < feeders.size(); j++) {
                    ActivatedManaAbilityImpl first = feeders.get(i), second = feeders.get(j);
                    if (!first.getSourceId().equals(second.getSourceId())) {
                        plan = conversion(converter, first, second, adjusted, demand, owner.getManaPool().getMana(), pool, free, reserved, calculation, budget);
                        if (plan != null) return plan;
                    }
                }
            }
        }
        return null;
    }
    private static ActionPlan conversion(ActivatedManaAbilityImpl converter, ActivatedManaAbilityImpl first,
            ActivatedManaAbilityImpl second, ActivatedAbility outer, ManaCost demand, Mana ordinaryPool, Mana outerPool,
            Map<UUID,List<ActivatedManaAbilityImpl>> free, Set<UUID> reserved, Game game, Budget budget) {
        List<Mana> firstOutputs = first == null ? Collections.singletonList(new Mana()) : first.getNetMana(game);
        List<Mana> secondOutputs = second == null ? Collections.singletonList(new Mana()) : second.getNetMana(game);
        for (Mana one : firstOutputs) for (Mana two : secondOutputs) {
            if (!budget.take()) return null;
            if (one instanceof ConditionalMana || two instanceof ConditionalMana) continue;
            Mana before = ordinaryPool.copy(); before.add(one); before.add(two);
            for (Mana input : converter.getManaCostsToPay().getOptions(false)) for (Mana paid : pay(before, input)) {
                for (Mana output : converter.getNetMana(game)) {
                    if (!budget.take()) return null;
                    if (!applicable(output, outer, game, converter.getSourceId(), demand)) continue;
                    Mana after = paid.copy(); after.add(output);
                    Set<UUID> excluded = new HashSet<>(reserved); excluded.add(converter.getSourceId());
                    if (first != null) excluded.add(first.getSourceId()); if (second != null) excluded.add(second.getSourceId());
                    // Require both strict useful progress and a complete remaining route.
                    if (deficit(demand, after) >= deficit(demand, ordinaryPool)
                            || deficit(demand, after) >= deficit(demand, before)
                            || !payable(demand, totals(after, free, excluded, outer, demand, game))) continue;
                    return first == null ? new ActionPlan(converter, demand, "POOL_INPUT")
                            : new ActionPlan(first, converter.getManaCostsToPay(), second == null ? "FEEDER" : "TWO_FEEDERS");
                }
            }
        }
        return null;
    }
    private static long deficit(ManaCost demand, Mana available) {
        long best = Long.MAX_VALUE; int[] a = vector(available);
        for (Mana cost : demand.getOptions(false)) {
            int[] c = vector(cost); long colored = 0;
            for (int i = 0; i < 5; i++) colored += Math.max(0, c[i] - a[i]);
            colored = Math.max(0, colored - a[7]) + Math.max(0, c[5] - a[5]);
            best = Math.min(best, colored * 1000L + Math.max(0L, (long) cost.count() - available.count()));
        }
        return best;
    }
    // Enumerate bounded generic allocations; never silently spend the only needed colour.
    private static List<Mana> pay(Mana available, Mana input) {
        List<Mana> result = new ArrayList<>(); if (!input.enough(available)) return result;
        int[] a = vector(available), c = vector(input);
        for (int i = 0; i < 6; i++) {
            int used = Math.min(a[i], c[i]); a[i] -= used; int missing = c[i] - used;
            if (missing > 0) { if (i == 5 || a[7] < missing) return result; a[7] -= missing; }
        }
        spendGeneric(a, c[6], 0, result); return result;
    }
    private static void spendGeneric(int[] a, int remaining, int index, List<Mana> result) {
        if (result.size() >= LIMIT) return;
        if (remaining == 0) { result.add(new Mana(a[0],a[1],a[2],a[3],a[4],a[6],a[7],a[5])); return; }
        if (index >= 8) return;
        for (int used = 0; used <= Math.min(a[index], remaining) && result.size() < LIMIT; used++) {
            a[index] -= used; spendGeneric(a, remaining-used, index+1, result); a[index] += used;
        }
    }
    private static int[] vector(Mana m) { return new int[]{m.getWhite(),m.getBlue(),m.getBlack(),m.getRed(),m.getGreen(),m.getColorless(),m.getGeneric(),m.getAny()}; }
    /** Trigger inspection follows public engine mana events only on a disposable
     * calculation. Reject conditional bonuses before ManaOptions flattens them. */
    private static ActionPlan triggeredPaymentAction(ActivatedAbility outer, ManaCost demand, Mana pool,
            Map<UUID,List<ActivatedManaAbilityImpl>> free, Set<UUID> reserved, Game game, Budget budget) {
        // Keep trigger expansion separate from ordinary availability. At most two
        // distinct free sources may certify a route; only the first is executed.
        List<TriggeredOutput> candidates = new ArrayList<>();
        for (Map.Entry<UUID,List<ActivatedManaAbilityImpl>> entry : free.entrySet()) {
            if (reserved.contains(entry.getKey())) continue;
            for (ActivatedManaAbilityImpl ability : entry.getValue()) {
                if (!budget.take()) return null;
                Game calculation = game.createSimulationForPlayableCalc();
                List<Mana> options = ability.getNetMana(calculation);
                if (options.size() != 1 || options.get(0) instanceof ConditionalMana) continue;
                Mana base = options.get(0).copy();
                if (base.count() <= 0 || base.getAny() > 0) continue;
                Player snapshotPlayer = calculation.getPlayer(ability.getControllerId());
                if (snapshotPlayer == null) continue;
                snapshotPlayer.getAvailableTriggeredMana().clear();
                mage.game.events.ManaEvent tap = new mage.game.events.TappedForManaEvent(
                        ability.getSourceId(), ability, ability.getControllerId(), base, calculation);
                if (calculation.replaceEvent(tap)) continue;
                calculation.fireEvent(tap);
                mage.game.events.ManaEvent added = new mage.game.events.ManaEvent(
                        mage.game.events.GameEvent.EventType.MANA_ADDED,
                        ability.getSourceId(), ability, ability.getControllerId(), base);
                added.setData(base.toString()); calculation.fireEvent(added);
                boolean restricted = false;
                for (List<Mana> alternatives : snapshotPlayer.getAvailableTriggeredMana())
                    for (Mana output : alternatives) if (output instanceof ConditionalMana) restricted = true;
                if (restricted || snapshotPlayer.getAvailableTriggeredMana().isEmpty()) continue;
                Mana ordinary = pool.copy(); ordinary.add(base);
                for (Mana expanded : ManaOptions.getTriggeredManaVariations(calculation, ability, base.copy())) {
                    if (!budget.take()) return null;
                    Mana after = pool.copy(); after.add(expanded);
                    if (deficit(demand, after) >= deficit(demand, ordinary)) continue;
                    Set<UUID> excluded = new HashSet<>(reserved); excluded.add(ability.getSourceId());
                    if (payable(demand, totals(after, free, excluded, outer, demand, game)))
                        return new ActionPlan(ability, demand, "TRIGGERED_MANA");
                    if (candidates.size() < LIMIT)
                        candidates.add(new TriggeredOutput(ability, base.copy(), expanded.copy()));
                }
            }
        }
        // A first bonus may be insufficient alone even though two legal land
        // bonuses complete the spell. Never count alternatives of one source twice.
        for (TriggeredOutput first : candidates) for (TriggeredOutput second : candidates) {
            if (!budget.take()) return null;
            if (first.ability.getSourceId().equals(second.ability.getSourceId())) continue;
            Mana beforeSecondBonus = pool.copy(); beforeSecondBonus.add(first.output); beforeSecondBonus.add(second.base);
            Mana after = pool.copy(); after.add(first.output); after.add(second.output);
            if (deficit(demand, after) >= deficit(demand, beforeSecondBonus)) continue;
            Set<UUID> excluded = new HashSet<>(reserved);
            excluded.add(first.ability.getSourceId()); excluded.add(second.ability.getSourceId());
            if (payable(demand, totals(after, free, excluded, outer, demand, game)))
                return new ActionPlan(first.ability, demand, "TWO_TRIGGERED_SOURCES");
        }
        return null;
    }
    private static final class TriggeredOutput {
        final ActivatedManaAbilityImpl ability;
        final Mana base, output;
        TriggeredOutput(ActivatedManaAbilityImpl ability, Mana base, Mana output) {
            this.ability = ability; this.base = base; this.output = output;
        }
    }
    private static boolean untapFunds(ActivatedAbility adjusted, ActivatedAbility raw, Game game, ComputerPlayerFourthSeat player,
            SelfUntapPlan untap, Mana initial, Set<UUID> excluded) {
        if (raw == null && game.getCard(adjusted.getSourceId()) instanceof mage.cards.g.GhaltaPrimalHunger) return false;
        Game future = game.createSimulationForPlayableCalc();
        Permanent p = future.getPermanent(untap.sourceId); if (p == null) return false;
        p.addCounters(CounterType.M1M1.createInstance(), untap.ability, future);
        p.setTapped(false); future.applyEffects();
        if (p.getToughness().getValue() <= p.getDamage()) return false;
        ActivatedAbility outer = adjusted;
        if (raw != null) {
            FourthSeatAdditionalCostPolicy.PreparedCosts prepared = FourthSeatAdditionalCostPolicy.prepareSelectedCosts(raw, future);
            if (!prepared.distinctTapCostsPayable) return false;
            outer = prepared.ability;
        }
        ComputerPlayerFourthSeat owner = (ComputerPlayerFourthSeat) future.getPlayer(player.getId()).getRealPlayer();
        Set<UUID> remainingExcluded = new HashSet<UUID>(excluded); remainingExcluded.remove(untap.sourceId);
        return payable(outer.getManaCostsToPay(), totals(initial, freeSources(future, owner), remainingExcluded, outer, outer.getManaCostsToPay(), future));
    }
}
