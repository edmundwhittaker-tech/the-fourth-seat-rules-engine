package mage.player.ai;

import fourthseat.ai.CommanderPolicy;
import fourthseat.ai.DeckPlan;
import fourthseat.ai.PolicyModel.*;
import mage.MageObject;
import mage.Mana;
import mage.cards.n.NykthosShrineToNyx;
import mage.abilities.dynamicvalue.common.DevotionCount;
import mage.ConditionalMana;
import mage.abilities.mana.ActivatedManaAbilityImpl;
import mage.abilities.mana.ManaOptions;
import mage.ObjectColor;
import mage.abilities.Ability;
import mage.abilities.Mode;
import mage.abilities.costs.Cost;
import mage.abilities.costs.SacrificeCost;
import mage.abilities.costs.common.SacrificeSourceCost;
import mage.abilities.costs.common.TapSourceCost;
import mage.abilities.ActivatedAbility;
import mage.abilities.PlayLandAbility;
import mage.abilities.keyword.EquipAbility;
import mage.abilities.keyword.VigilanceAbility;
import mage.abilities.keyword.IndestructibleAbility;
import mage.abilities.keyword.HasteAbility;
import mage.abilities.effects.common.continuous.BecomesCreatureTargetEffect;
import mage.cards.Card;
import mage.choices.Choice;
import mage.choices.ChoiceColor;
import org.apache.log4j.Logger;
import mage.cards.Cards;
import mage.constants.CommanderCardType;
import mage.constants.Outcome;
import mage.constants.RangeOfInfluence;
import mage.constants.Zone;
import mage.constants.SubType;
import mage.constants.AbilityType;
import mage.abilities.effects.Effect;
import mage.counters.CounterType;
import mage.abilities.costs.mana.ManaCost;
import mage.abilities.costs.mana.ManaCosts;
import mage.constants.ColoredManaSymbol;
import mage.constants.MultiAmountType;
import mage.util.MultiAmountMessage;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.game.stack.StackObject;
import mage.players.Player;
import mage.target.Target;
import mage.target.TargetCard;
import java.io.Serializable;
import java.util.*;

public final class ComputerPlayerFourthSeat extends ComputerPlayer {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ComputerPlayerFourthSeat.class);
    private static final int CANDIDATE_LIMIT = 128;
    private static final long BUDGET_NANOS = 75_000_000L;
    private int mulligans;
    private int actionTurn = -1;
    private String actionStep = "";
    private final Set<String> costlessActions = new HashSet<String>();
    private final Set<String> failedActions = new HashSet<String>();
    private final Map<String, Integer> successfulActivations = new HashMap<String, Integer>();
    private int stepActions;
    private transient ManaCost currentUnpaid;
    private transient UUID reservedTapSource;
    private transient ActivatedAbility activatingAbility;
    private transient boolean prefundingMana;
    private transient boolean optionalCostProbe;
    // Scoped to one getPlayable call; deliberately NOT copied into simulation players.
    private transient boolean capturePlayableMana;
    private transient ManaOptions capturedPlayableMana;
    private transient Set<UUID> pendingTapReservations = Collections.emptySet();
    transient Set<UUID> lastHeldSources = new HashSet<UUID>();
    transient Set<UUID> lastAdmittedSources = new HashSet<UUID>();
    transient Map<UUID, Set<String>> lastAdmittedModeSignatures = new HashMap<UUID, Set<String>>();
    transient long observationBuildCount; // diagnostics only, never used by policy or copied

    public ComputerPlayerFourthSeat(String name, RangeOfInfluence range, int skill) {
        super(name, range);
    }

    public ComputerPlayerFourthSeat(String name, RangeOfInfluence range) {
        super(name, range);
    }

    public ComputerPlayerFourthSeat(ComputerPlayerFourthSeat other) {
        super(other);
        mulligans = other.mulligans;
        reserveObservedTurn = other.reserveObservedTurn;
        reserveAttackCommittedTurn = other.reserveAttackCommittedTurn;
        readyArmySeenTurn = other.readyArmySeenTurn;
        optionalChainLibrary = other.optionalChainLibrary;
        optionalChainRefused = other.optionalChainRefused;
        optionalChainDraining = other.optionalChainDraining;
        actionTurn = other.actionTurn;
        actionStep = other.actionStep;
        costlessActions.addAll(other.costlessActions);
        failedActions.addAll(other.failedActions);
        successfulActivations.putAll(other.successfulActivations);
        stepActions = other.stepActions;
        currentUnpaid = other.currentUnpaid == null ? null : other.currentUnpaid.copy();
        reservedTapSource = other.reservedTapSource;
        activatingAbility = other.activatingAbility == null ? null : other.activatingAbility.copy();
        prefundingMana = other.prefundingMana;
        optionalCostProbe = other.optionalCostProbe;
        pendingTapReservations = other.pendingTapReservations == null ? Collections.<UUID>emptySet() : new HashSet<UUID>(other.pendingTapReservations);
    }

    @Override public ComputerPlayerFourthSeat copy() { return new ComputerPlayerFourthSeat(this); }

    @Override public void init(Game game) {
        super.init(game);
        mulligans = 0;
        reserveObservedTurn = reserveAttackCommittedTurn = readyArmySeenTurn = -1;
        optionalChainLibrary = -1; optionalChainRefused = optionalChainDraining = false;
        actionTurn = -1;
        actionStep = "";
        costlessActions.clear();
        failedActions.clear();
        successfulActivations.clear();
        stepActions = 0;
        currentUnpaid = null;
        reservedTapSource = null;
        activatingAbility = null;
        prefundingMana = false;
        optionalCostProbe = false;
        capturePlayableMana = false;
        capturedPlayableMana = null;
        pendingTapReservations = Collections.emptySet();
        observationBuildCount = 0;
    }

    private int reserveObservedTurn = -1;
    private int reserveAttackCommittedTurn = -1;
    private int readyArmySeenTurn = -1;
    private int optionalChainLibrary = -1;
    private boolean optionalChainRefused;
    private boolean optionalChainDraining;

    private static boolean hasEffect(Permanent permanent, Game game, String className) {
        for (Ability ability : permanent.getAbilities(game))
            for (Effect effect : ability.getEffects()) if (effect.getClass().getName().equals(className)) return true;
        return false;
    }

    private int[] scuteCounts(Game game) {
        int[] result = new int[3];
        for (Permanent p : game.getBattlefield().getAllActivePermanents(getId()))
            if (!p.isFaceDown(game) && "Scute Swarm".equals(p.getName())) {
                result[0]++;
                if (!p.isTapped()) result[1]++;
                if (p.isAttacking()) result[2]++;
            }
        return result;
    }

    /** One public, unrestricted resource snapshot; projected mana is never spendable now. */
    private ManaOptions ordinaryResourceOptions(Game game, boolean projectedLands) {
        Game snapshot = game.createSimulationForPlayableCalc();
        Player owner = snapshot.getPlayer(getId());
        for (ConditionalMana conditional : owner.getManaPool().getConditionalMana()) conditional.clear();
        ManaOptions totals = new ManaOptions();
        totals.add(projectedLands ? new Mana() : owner.getManaPool().getMana());
        int sources = 0;
        for (Permanent p : snapshot.getBattlefield().getAllActivePermanents(getId())) {
            if (projectedLands) {
                if (!p.isLand(snapshot) || p.isCreature(snapshot)) continue;
                p.setTapped(false);
            }
            List<Mana> outputs = new ArrayList<Mana>();
            for (ActivatedManaAbilityImpl a : p.getAbilities(snapshot).getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, getId(), snapshot)) {
                if (!a.getManaCosts().isEmpty() || a.getCosts().isEmpty()
                        || !a.getCosts().stream().allMatch(c -> c instanceof TapSourceCost)
                        || !a.canActivate(getId(), snapshot).canActivate()) continue;
                for (Mana output : a.getNetMana(snapshot)) if (!(output instanceof ConditionalMana)) outputs.add(output.copy());
            }
            if (outputs.isEmpty()) continue;
            if (++sources > 64) break;
            ManaOptions next = new ManaOptions();
            for (Mana previous : totals) for (Mana output : outputs) {
                Mana combined = previous.copy(); combined.add(output); next.add(combined);
                if (next.size() >= 64) break;
            }
            totals = next;
        }
        return totals;
    }

    private boolean fixedHandCovered(Game game, ManaOptions available, boolean allowHarrow) {
        for (Card card : getHand().getCards(game)) {
            if (card.isLand(game)) continue;
            mage.abilities.SpellAbility spell = card.getSpellAbility();
            if (spell == null || spell.isModal() || !spell.getManaCosts().getVariableCosts().isEmpty()) return false;
            if (!spell.getCosts().isEmpty()) {
                boolean harrow = allowHarrow && card instanceof mage.cards.h.Harrow && spell.getCosts().size() == 1
                        && spell.getCosts().get(0) instanceof mage.abilities.costs.common.SacrificeTargetCost;
                if (!harrow) return false;
            }
            boolean funded = false;
            for (Mana cost : spell.getManaCostsToPay().getOptions(false)) {
                Mana total = cost.copy(); total.add(Mana.GenericMana(2));
                for (Mana option : available) if (total.enough(option)) { funded = true; break; }
                if (funded) break;
            }
            if (!funded) return false;
        }
        return true;
    }

    private int reserveGrowthFunding(Game game) {
        boolean ownTurn = game.isActivePlayer(getId());
        int[] scutes = scuteCounts(game);
        if (!ownTurn || reserveObservedTurn != game.getTurnNum() || scutes[0] < 32) {
            reserveObservedTurn = -1; reserveAttackCommittedTurn = -1;
        }
        boolean previous = ownTurn && reserveObservedTurn == game.getTurnNum();
        if (previous && scutes[2] >= 32) reserveAttackCommittedTurn = game.getTurnNum();
        boolean continued = previous && scutes[0] >= 32
                && (scutes[1] >= 32 || reserveAttackCommittedTurn == game.getTurnNum());
        if (previous && !continued) { reserveObservedTurn = -1; reserveAttackCommittedTurn = -1; }
        if (!"Aesi, Tyrant of Gyre Strait".equals(commanderName(game))
                || (!continued && getHand().size() < 5)
                || (!continued && scutes[1] < 32 && !FourthSeatCounterGrowthPolicy.hasOverwhelmingMultiplicativeThreat(getId(), game))) return 0;
        int funding = fixedHandCovered(game, ordinaryResourceOptions(game, false), true) ? 1
                : fixedHandCovered(game, ordinaryResourceOptions(game, true), true) ? 2 : 0;
        if (funding == 0) { reserveObservedTurn = -1; reserveAttackCommittedTurn = -1; }
        else if (ownTurn && scutes[1] >= 32) reserveObservedTurn = game.getTurnNum();
        return funding;
    }

    private boolean developmentSaturated(Game game, boolean credible) {
        if (!"Aesi, Tyrant of Gyre Strait".equals(commanderName(game)) || !game.getStack().isEmpty() || getHand().size() < 5) return false;
        if (credible && game.isActivePlayer(getId())) readyArmySeenTurn = game.getTurnNum();
        boolean remembered = game.isActivePlayer(getId()) && readyArmySeenTurn == game.getTurnNum() && scuteCounts(game)[0] >= 16;
        return (credible || remembered) && fixedHandCovered(game, ordinaryResourceOptions(game, false), false);
    }

    private boolean declineRedundantSpringheartCopy(Outcome outcome, Ability source, Game game) {
        if (outcome != Outcome.PutCreatureInPlay || source == null || !getId().equals(source.getControllerId())
                || !"Aesi, Tyrant of Gyre Strait".equals(commanderName(game)) || getHand().size() < 8) return false;
        Permanent nantuko = game.getPermanent(source.getSourceId());
        if (nantuko == null || !getId().equals(nantuko.getControllerId())
                || !hasEffect(nantuko, game, "mage.cards.s.SpringheartNantukoEffect")) return false;
        Permanent enchanted = game.getPermanent(nantuko.getAttachedTo());
        if (enchanted == null || !getId().equals(enchanted.getControllerId()) || !enchanted.isCreature(game)
                || !hasEffect(enchanted, game, "mage.cards.a.AncientGreenwardenEffect")) return false;
        int amplifiers = 0, readyBodies = 0;
        for (Permanent p : game.getBattlefield().getAllActivePermanents(getId())) {
            if (p.isFaceDown(game)) continue;
            if (hasEffect(p, game, "mage.cards.a.AncientGreenwardenEffect")) amplifiers++;
            if (p.isCreature(game) && !p.isTapped()) readyBodies++;
        }
        if (amplifiers < 4 || readyBodies < 8) return false;
        Mana need = new mage.abilities.costs.mana.ManaCostsImpl<ManaCost>("{4}{G}{G}{U}{U}").getMana();
        for (Mana capacity : ordinaryResourceOptions(game, true)) if (need.enough(capacity)) return true;
        return false;
    }

    private void resetOptionalChainIfEmpty(Game game) {
        if (game.getStack().isEmpty()) {
            optionalChainLibrary = -1; optionalChainRefused = false; optionalChainDraining = false;
        }
    }

    private boolean ownedFiniteChain(Game game) {
        boolean kodama = false, baloths = false, drawEngine = false;
        for (Permanent p : game.getBattlefield().getAllActivePermanents(getId())) {
            if (p.isFaceDown(game)) continue;
            Card card = game.getCard(p.getId());
            kodama |= card instanceof mage.cards.k.KodamaOfTheEastTree;
            baloths |= card instanceof mage.cards.r.RampagingBaloths;
            // These published-deck engines also draw for Baloths tokens. Their
            // mandatory draws stay engine-controlled; only Kodama's optional
            // hand-to-battlefield choice is declined while draining the chain.
            drawEngine |= card instanceof mage.cards.g.GarruksPackleader
                    || card instanceof mage.cards.g.GarruksUprising
                    || card instanceof mage.cards.o.OutcasterTrailblazer
                    || card instanceof mage.cards.v.VaultbornTyrant;
        }
        return kodama && baloths && drawEngine;
    }

    // Engine-only affordability snapshot, never strategic full-state scoring.
    // Restricted outputs are tested BEFORE ManaOptions flattens their conditions.
    transient long manaAvailabilityBuildCount;
    transient long manaAvailabilityCacheHits;
    transient long candidateFundingCheckCount;
    private transient Map<String, ManaOptions> callbackManaCache;

    private ManaOptions manaAvailableFor(ActivatedAbility ability, Game game) {
        return manaAvailableFor(ability, game, Collections.<UUID>emptySet());
    }
    private ManaOptions manaAvailableFor(ActivatedAbility ability, Game game, Set<UUID> taps) {
        Set<UUID> reserved = reservedTapIdsForPlanning(); reserved.addAll(taps);
        if (ability.getCosts().stream().anyMatch(c -> c instanceof TapSourceCost)) reserved.add(ability.getSourceId());
        ManaOptions available = manaAvailableWithoutGroups(ability, game, reserved, true);
        for (FourthSeatManaDependencyPolicy.GroupTapPlan group : FourthSeatManaDependencyPolicy.listGroupTapPlans(game, this)) {
            if (!Collections.disjoint(reserved, group.reservedTapIds)) continue;
            Set<UUID> groupReserved = new HashSet<UUID>(reserved); groupReserved.addAll(group.reservedTapIds);
            ManaOptions variant = manaAvailableWithoutGroups(ability, game, groupReserved, true);
            for (Mana mana : variant) { Mana combined = mana.copy(); combined.add(group.output); available.add(combined); }
        }
        return available;
    }

    private ManaOptions manaAvailableWithoutGroups(ActivatedAbility ability, Game game, Set<UUID> reserved, boolean checkInputs) {
        manaAvailabilityBuildCount++;
        Game calculation = game.createSimulationForPlayableCalc();
        ComputerPlayerFourthSeat owner = (ComputerPlayerFourthSeat) calculation.getPlayer(getId()).getRealPlayer();
        for (UUID id : reserved) { Permanent p = calculation.getPermanent(id); if (p != null) p.setTapped(true); }
        if (ability.getAbilityType() == AbilityType.SPELL) owner.getHand().remove(ability.getSourceId());
        for (ConditionalMana mana : owner.getManaPool().getConditionalMana())
            if (!mana.apply(ability, calculation, mana.getManaProducerId(), ability.getManaCostsToPay())) mana.clear();
        for (Permanent permanent : calculation.getBattlefield().getAllActivePermanents()) {
            List<ActivatedManaAbilityImpl> remove = new ArrayList<ActivatedManaAbilityImpl>();
            for (ActivatedManaAbilityImpl producer : permanent.getAbilities(calculation)
                    .getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, getId(), calculation)) {
                boolean valid = !FourthSeatManaDependencyPolicy.isGroupTapProducer(producer);
                for (Mana output : producer.getNetMana(calculation)) if (output instanceof ConditionalMana
                        && !((ConditionalMana) output).apply(ability, calculation, producer.getSourceId(), ability.getManaCostsToPay())) valid = false;
                if (!producer.getManaCosts().isEmpty()) {
                    if (!checkInputs) valid = false;
                    else {
                        Set<UUID> inputsReserved = new HashSet<UUID>(reserved);
                        if (producer.getCosts().stream().anyMatch(c -> c instanceof TapSourceCost)) inputsReserved.add(producer.getSourceId());
                        ManaOptions inputs = owner.manaAvailableWithoutGroups(producer, calculation, inputsReserved, false);
                        if (!owner.canPayMinimumManaCost(producer, inputs, calculation)) valid = false;
                    }
                }
                if (!valid) remove.add(producer);
                else if (producer.getManaCosts().size() > 1) {
                    ManaCost complete = producer.getManaCosts().copy();
                    producer.getManaCosts().clear();
                    producer.getManaCosts().add(new FourthSeatAtomicManaCost(complete));
                }
            }
            for (ActivatedManaAbilityImpl producer : remove) permanent.removeAbility(producer, producer.getSourceId(), calculation);
        }
        return owner.getManaAvailable(calculation);
    }

    private boolean canFundCandidate(ActivatedAbility ability, Game game) {
        candidateFundingCheckCount++;
        if (ability instanceof PlayLandAbility) return true;
        Game calculation = game.createSimulationForPlayableCalc();
        ComputerPlayerFourthSeat owner = (ComputerPlayerFourthSeat) calculation.getPlayer(getId()).getRealPlayer();
        ActivatedAbility probe = ability.copy();
        ActivatedAbility.ActivationStatus status = probe.canActivate(getId(), calculation);
        if (!status.canActivate()) return false;
        Set<mage.MageIdentifier> identifiers = new HashSet<mage.MageIdentifier>();
        identifiers.add(mage.MageIdentifier.Default);
        for (mage.ApprovingObject approving : status.getApprovingObjects())
            if (approving != null && approving.getApprovingAbility() != null) identifiers.add(approving.getApprovingAbility().getIdentifier());
        Set<mage.MageIdentifier> registered = new HashSet<mage.MageIdentifier>(owner.getCastSourceIdWithAlternateMana()
                .getOrDefault(probe.getSourceId(), Collections.<mage.MageIdentifier>emptySet()));
        registered.retainAll(identifiers);
        if (owner.getCastSourceIdWithAlternateMana().containsKey(probe.getSourceId()))
            owner.getCastSourceIdWithAlternateMana().put(probe.getSourceId(), registered);
        mage.MageIdentifier exclusive = null;
        if (status.getApprovingObjects().size() == 1) {
            mage.ApprovingObject approving = status.getApprovingObjects().iterator().next();
            if (approving != null && approving.getApprovingAbility() != null) {
                mage.MageIdentifier id = approving.getApprovingAbility().getIdentifier();
                if (registered.contains(id)) exclusive = id;
                else if (registered.contains(mage.MageIdentifier.Default)) exclusive = mage.MageIdentifier.Default;
            }
        }
        if (exclusive != null) {
            ManaCosts<ManaCost> mana = owner.getCastSourceIdManaCosts().get(probe.getSourceId()).get(exclusive);
            mage.abilities.costs.Costs<Cost> costs = owner.getCastSourceIdCosts().get(probe.getSourceId()).get(exclusive);
            probe.clearManaCosts(); probe.clearManaCostsToPay(); probe.clearCosts();
            if (mana != null) probe.addCost(mana.copy());
            if (costs != null) probe.addCost(costs.copy());
            FourthSeatAdditionalCostPolicy.PreparedCosts prepared = FourthSeatAdditionalCostPolicy.prepareSelectedCosts(probe, calculation);
            return prepared.distinctTapCostsPayable && prepared.ability.getCosts().canPay(prepared.ability, prepared.ability, getId(), calculation)
                    && owner.canPayMinimumManaCost(prepared.ability, owner.optionMana(prepared.ability, calculation, prepared.reservedTapIds), calculation);
        }
        ManaOptions available = owner.manaAvailableFor(probe, calculation);
        if (probe.getAbilityType() == AbilityType.SPELL) owner.getHand().remove(probe.getSourceId());
        if (owner.canPlay(probe, available, calculation.getObject(probe.getSourceId()), calculation)) return true;
        return dependencyPlanFor(ability, game, Collections.<UUID>emptySet()) != null;
    }

    @Override protected boolean canPlayCardByAlternateCost(Card card, ManaOptions available, Ability ability, Game game) {
        if (card instanceof mage.cards.s.SepharaSkysBlade && ability instanceof ActivatedAbility) {
            Game snapshot = game.createSimulationForPlayableCalc();
            if (preparedCostsPayable(FourthSeatAdditionalCostPolicy.prepareSepharaAlternative((ActivatedAbility) ability, snapshot), snapshot)) return true;
            // Keep native global/granted alternatives. Remove only this intrinsic
            // alternative from a disposable card copy after its combined cost failed.
            Card copy = card.copy();
            copy.getAbilities().removeIf(a -> a.getClass() == mage.abilities.costs.AlternativeCostSourceAbility.class);
            return super.canPlayCardByAlternateCost(copy, available, ability, game);
        }
        return super.canPlayCardByAlternateCost(card, available, ability, game);
    }

    private boolean preparedCostsPayable(FourthSeatAdditionalCostPolicy.PreparedCosts costs, Game game) {
        return costs != null && costs.distinctTapCostsPayable
                && costs.ability.getCosts().canPay(costs.ability, costs.ability, getId(), game)
                && canPayMinimumManaCost(costs.ability, optionMana(costs.ability, game, costs.reservedTapIds), game);
    }

    static String availabilityCacheKey(ActivatedAbility ability, Set<UUID> reserved) {
        List<String> ids = new ArrayList<String>(); for (UUID id : reserved) ids.add(id.toString()); Collections.sort(ids);
        return ability.getControllerId() + ":" + ability.getSourceId() + ":" + ids;
    }

    private boolean cacheSafe(Game game) {
        if (!getManaPool().getConditionalMana().isEmpty()) return false;
        Game calculation = game.createSimulationForPlayableCalc();
        for (Permanent p : calculation.getBattlefield().getAllActivePermanents()) {
            for (Ability a : p.getAbilities(calculation)) if (a instanceof mage.abilities.mana.TriggeredManaAbility) return false;
            for (ActivatedManaAbilityImpl a : p.getAbilities(calculation).getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, getId(), calculation))
                for (Mana output : a.getNetMana(calculation)) if (output instanceof ConditionalMana) return false;
        }
        return true;
    }

    private ManaOptions optionMana(ActivatedAbility ability, Game game, Set<UUID> taps) {
        if (callbackManaCache == null) return manaAvailableFor(ability, game, taps);
        Set<UUID> reserved = reservedTapIdsForPlanning(); reserved.addAll(taps);
        if (ability.getCosts().stream().anyMatch(c -> c instanceof TapSourceCost)) reserved.add(ability.getSourceId());
        String key = availabilityCacheKey(ability, reserved);
        ManaOptions cached = callbackManaCache.get(key);
        if (cached != null) { manaAvailabilityCacheHits++; return cached.copy(); }
        ManaOptions result = manaAvailableFor(ability, game, reserved);
        callbackManaCache.put(key, result.copy()); return result;
    }

    private boolean hasPreferredOption(List<Ability> options, Game game, Observation observation, CommanderPolicy policy) {
        // Cross-candidate policy adjustments only subtract value. If every exact
        // option is rejected even alone, none can win the final choice. Avoid
        // funding idle answers/unsupported activations on every priority callback.
        // When ANY option is worthwhile, retain the entire original option set,
        // including its modal diversity, for the final engine-validated choice.
        for (Ability raw : options) {
            Ability option = normalizePartialGraveExileCosts(raw);
            if (!(option instanceof ActivatedAbility)) continue;
            MageObject source = game.getObject(option.getSourceId());
            Action action = new Action("preview", source == null ? "" : source.getName(),
                    option.getRule(), option.getManaCosts().manaValue(), option instanceof PlayLandAbility,
                    option.isManaAbility(), option.isUsesStack(), targetIds(option), actionKind(option),
                    effectOutcomes(option), String.valueOf(option.getSourceId()), effectText(option),
                    sacrifices(option), targetOutcomes(option));
            if (CommanderPolicy.isStrategicallyHeld(observation, action)) {
                lastHeldSources.add(option.getSourceId()); continue;
            }
            if (policy.choose(observation, Collections.singletonList(action), Long.MAX_VALUE).actionId != null) return true;
        }
        return false;
    }

    private ActivatedAbility previewVariableCost(ActivatedAbility raw, Game game) {
        ActivatedAbility copy = raw.copy();
        List<mage.abilities.costs.mana.VariableManaCost> variable = new ArrayList<mage.abilities.costs.mana.VariableManaCost>();
        for (ManaCost cost : copy.getManaCostsToPay()) if (cost instanceof mage.abilities.costs.mana.VariableManaCost)
            variable.add((mage.abilities.costs.mana.VariableManaCost) cost);
        if (variable.isEmpty()) return copy;
        mage.abilities.costs.mana.VariableManaCost first = variable.get(0);
        if (first.getFilter() != null && !first.getFilter().isGeneric()) return copy;
        int x = FourthSeatVariableManaPolicy.chooseX(first.getMinX(), first.getMaxX(), copy, game, this, manaAvailableFor(copy, game));
        int instances = 0; for (mage.abilities.costs.mana.VariableManaCost cost : variable) instances += cost.getXInstancesCount();
        copy.getManaCostsToPay().removeIf(cost -> cost instanceof mage.abilities.costs.mana.VariableManaCost);
        copy.addManaCostsToPay(new mage.abilities.costs.mana.GenericManaCost(x * instances));
        copy.setCostsTag("X", x);
        return copy;
    }

    private void supplementUntapCandidates(List<ActivatedAbility> playable, Game game) {
        Set<String> existing = new HashSet<String>(); for (ActivatedAbility a : playable) existing.add(actionKey(a));
        for (FourthSeatManaDependencyPolicy.SelfUntapPlan plan : FourthSeatManaDependencyPolicy.listSelfUntapPlans(game, this, Collections.<UUID>emptySet())) {
            Game snapshot = game.createSimulationForPlayableCalc();
            Permanent source = snapshot.getPermanent(plan.sourceId); if (source == null) continue;
            source.addCounters(CounterType.M1M1.createInstance(), getId(), plan.ability, snapshot);
            source.setTapped(false); snapshot.applyEffects();
            ComputerPlayerFourthSeat owner = (ComputerPlayerFourthSeat) snapshot.getPlayer(getId()).getRealPlayer();
            int examined = 0;
            for (ActivatedAbility candidate : owner.getPlayable(snapshot, true)) {
                if (++examined > 64) break;
                if (candidate.isManaAbility() || existing.contains(actionKey(candidate))) continue;
                FourthSeatManaDependencyPolicy.ActionPlan certified = dependencyPlanFor(candidate, game, Collections.<UUID>emptySet());
                if (certified != null && "SELF_UNTAP".equals(certified.reason) && plan.sourceId.equals(certified.ability.getSourceId())) {
                    playable.add(candidate.copy()); existing.add(actionKey(candidate));
                }
            }
        }
    }

    private FourthSeatManaDependencyPolicy.ActionPlan dependencyPlanFor(ActivatedAbility raw, Game game, Set<UUID> taps) {
        Set<UUID> previous = pendingTapReservations;
        pendingTapReservations = reservedTapIdsForPlanning(); pendingTapReservations.addAll(taps);
        try {
            Game snapshot = game.createSimulationForPlayableCalc();
            FourthSeatAdditionalCostPolicy.PreparedCosts costs = FourthSeatAdditionalCostPolicy.prepareSelectedCosts(previewVariableCost(raw, snapshot), snapshot);
            if (!costs.distinctTapCostsPayable) return null;
            pendingTapReservations.addAll(costs.reservedTapIds);
            for (UUID id : pendingTapReservations) { Permanent p = snapshot.getPermanent(id); if (p != null) p.setTapped(true); }
            return FourthSeatManaDependencyPolicy.nextAction(costs.ability, game, this, raw);
        } finally { pendingTapReservations = previous; }
    }

    @Override public boolean activateAbility(ActivatedAbility ability, Game game) {
        ActivatedAbility previous = activatingAbility;
        Set<UUID> previousTaps = pendingTapReservations;
        if (ability instanceof ActivatedManaAbilityImpl && previous != null && currentUnpaid != null) {
            Game calculation = game.createSimulationForPlayableCalc();
            for (Mana output : ((ActivatedManaAbilityImpl) ability).getNetMana(calculation))
                if (output instanceof ConditionalMana && !((ConditionalMana) output).apply(previous, calculation, ability.getSourceId(), currentUnpaid)) return false;
            if (!ability.getManaCosts().isEmpty() && FourthSeatManaDependencyPolicy.ordinaryPaymentSuffices(previous, currentUnpaid, game, this)) return false;
            if (dominatedManaOutput((ActivatedManaAbilityImpl) ability, calculation)) return false;
        }
        activatingAbility = ability;
        try { return super.activateAbility(ability, game); }
        finally {
            activatingAbility = previous;
            pendingTapReservations = previousTaps;
            ComputerPlayerFourthSeat restored = restoredPlayer(game);
            if (restored != null && restored != this) {
                restored.activatingAbility = previous == null ? null : previous.copy();
                restored.pendingTapReservations = new HashSet<UUID>(previousTaps);
            }
        }
    }

    private boolean dominatedManaOutput(ActivatedManaAbilityImpl chosen, Game calculation) {
        if (!pureTapMana(chosen)) return false;
        List<Mana> outputs = chosen.getNetMana(calculation);
        if (outputs.size() != 1 || outputs.get(0) instanceof ConditionalMana) return false;
        Mana output = outputs.get(0);
        if (currentUnpaid.getMana().enough(output)) return false;
        Permanent source = calculation.getPermanent(chosen.getSourceId());
        if (source == null) return false;
        for (ActivatedManaAbilityImpl other : source.getAbilities(calculation).getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, getId(), calculation)) {
            if (other.getId().equals(chosen.getId()) || !pureTapMana(other) || !other.canActivate(getId(), calculation).canActivate()) continue;
            List<Mana> alternatives = other.getNetMana(calculation);
            if (alternatives.size() != 1 || alternatives.get(0) instanceof ConditionalMana) continue;
            Mana better = alternatives.get(0);
            if (better.getAny() == 0 && output.getAny() == 0 && better.count() > output.count()
                    && better.getWhite() >= output.getWhite() && better.getBlue() >= output.getBlue()
                    && better.getBlack() >= output.getBlack() && better.getRed() >= output.getRed()
                    && better.getGreen() >= output.getGreen() && better.getColorless() >= output.getColorless()) return true;
        }
        return false;
    }

    private static boolean pureTapMana(ActivatedManaAbilityImpl ability) {
        return ability.getManaCosts().isEmpty() && ability.getCosts().size() == 1
                && ability.getCosts().get(0) instanceof TapSourceCost && !ability.getEffects().isEmpty()
                && ability.getEffects().stream().allMatch(e -> e instanceof mage.abilities.effects.mana.BasicManaEffect);
    }

    @Override public boolean chooseUse(Outcome outcome, String message, String secondMessage,
            String trueText, String falseText, Ability source, Game game) {
        resetOptionalChainIfEmpty(game);
        if (outcome == Outcome.Detriment && source == null && "Draw game because of infinite looping?".equals(message)
                && !game.getStack().isEmpty() && !optionalChainRefused && ownedFiniteChain(game)
                && optionalChainLibrary >= 0 && getLibrary().size() < optionalChainLibrary) {
            optionalChainRefused = optionalChainDraining = true;
            LOG.debug("fourth-seat optional-chain=BOUNDED_FINITE_PROGRESS_REFUSAL");
            return false;
        }
        if (source != null && getId().equals(source.getControllerId()) && ownedFiniteChain(game)) {
            Card card = game.getCard(source.getSourceId());
            boolean chainChoice = (card instanceof mage.cards.k.KodamaOfTheEastTree && outcome == Outcome.Benefit)
                    || (card instanceof mage.cards.g.GarruksPackleader && outcome == Outcome.DrawCard);
            if (chainChoice) {
                if (optionalChainDraining) return false;
                if (optionalChainLibrary < 0) optionalChainLibrary = getLibrary().size();
            }
        }
        if (declineRedundantSpringheartCopy(outcome, source, game)) return false;
        if (outcome == Outcome.DrawCard && getLibrary().size() == 0) return false;
        if (optionalCostProbe) return true;
        if (isCelestialReunionActivation(source, game)
                && "Choose a creature type and behold two creatures of that type?".equals(message))
            return celestialBeholdType(null, game) != null;
        if (activatingAbility != null && source instanceof ActivatedAbility
                && Objects.equals(source.getSourceId(), activatingAbility.getSourceId())
                && FourthSeatAdditionalCostPolicy.hasOffspring(source, game)) {
            Game snapshot = game.createSimulationForPlayableCalc();
            Player probePlayer = snapshot.getPlayer(getId()).getRealPlayer();
            if (!(probePlayer instanceof ComputerPlayerFourthSeat)) return false;
            ComputerPlayerFourthSeat probe = (ComputerPlayerFourthSeat) probePlayer;
            boolean previous = probe.optionalCostProbe;
            probe.optionalCostProbe = true;
            try {
                ActivatedAbility prepared = FourthSeatAdditionalCostPolicy.prepareOffspringCost((ActivatedAbility) source, snapshot);
                if (prepared == null) return false;
                prepared.adjustX(snapshot);
                snapshot.getContinuousEffects().costModification(prepared, snapshot);
                return probe.canPayMinimumManaCost(prepared, probe.manaAvailableFor(prepared, snapshot), snapshot);
            } finally { probe.optionalCostProbe = previous; }
        }
        return super.chooseUse(outcome, message, secondMessage, trueText, falseText, source, game);
    }

    @Override public boolean choose(Outcome outcome, Choice choice, Game game) {
        if (outcome == Outcome.PutManaInPool && choice instanceof ChoiceColor
                && activatingAbility instanceof ActivatedManaAbilityImpl
                && game.getCard(activatingAbility.getSourceId()) instanceof NykthosShrineToNyx) {
            String[] colors = {"White", "Blue", "Black", "Red", "Green"};
            DevotionCount[] counts = {DevotionCount.W, DevotionCount.U, DevotionCount.B, DevotionCount.R, DevotionCount.G};
            Mana demand = currentUnpaid == null ? new Mana() : currentUnpaid.getMana();
            int[] coloredDemand = {demand.getWhite(), demand.getBlue(), demand.getBlack(), demand.getRed(), demand.getGreen()};
            String best = null; long bestScore = Long.MIN_VALUE;
            for (int i = 0; i < colors.length; i++) if (choice.getChoices().contains(colors[i])) {
                int amount = counts[i].calculate(game, activatingAbility, null);
                if (amount <= 0) continue;
                long score = 1000L * Math.min(amount, coloredDemand[i]) + amount;
                if (score > bestScore) { best = colors[i]; bestScore = score; }
            }
            if (best != null) { choice.setChoice(best); return true; }
            return false; // No productive permitted color, do not invent mana.
        }
        if (outcome == Outcome.PutManaInPool && choice.isManaColorChoice() && currentUnpaid != null) {
            // Match ComputerPlayer.playManaHandling's actual next cost, not its whole
            // unpaid bundle: e.g. Tower must produce U for GU when a Forest pays G.
            ManaCost next = currentUnpaid;
            if (currentUnpaid instanceof ManaCosts) {
                ManaCosts<?> costs = (ManaCosts<?>) currentUnpaid;
                if (!costs.isEmpty()) next = costs.get(costs.size() - 1);
            }
            String[] colors = {"White", "Blue", "Black", "Red", "Green"};
            ColoredManaSymbol[] symbols = {ColoredManaSymbol.W, ColoredManaSymbol.U,
                    ColoredManaSymbol.B, ColoredManaSymbol.R, ColoredManaSymbol.G};
            for (int i = 0; i < colors.length; i++)
                if (next.containsColor(symbols[i]) && choice.getChoices().contains(colors[i])) {
                    choice.setChoice(colors[i]); return true;
                }
            if (next.getMana().getColorless() > 0 && choice.getChoices().contains("Colorless")) {
                choice.setChoice("Colorless"); return true;
            }
        }
        // Pinned AbilityImpl uses an untyped keyed dialog for alternative costs.
        // Bind its exact protocol to the current activation, not arbitrary card text.
        if (activatingAbility != null && activatingAbility.getAbilityType() == AbilityType.SPELL
                && choice.isKeyChoice() && ("You may choose an alternative cost".equals(choice.getMessage())
                || "Choose an alternative cost".equals(choice.getMessage()))) {
            String normal = null;
            for (Map.Entry<String, String> entry : choice.getKeyChoices().entrySet())
                if (entry.getValue().startsWith("Cast with no alternative cost: ")) normal = entry.getKey();
            ActivatedAbility adjusted = activatingAbility.copy();
            adjusted.adjustX(game);
            game.getContinuousEffects().costModification(adjusted, game);
            if (normal != null && canPayMinimumManaCost(adjusted, manaAvailableFor(adjusted, game), game)) {
                choice.setChoiceByKey(normal); return true;
            }
            String unfundedSephara = null;
            if (game.getCard(activatingAbility.getSourceId()) instanceof mage.cards.s.SepharaSkysBlade) {
                Game snapshot = game.createSimulationForPlayableCalc();
                FourthSeatAdditionalCostPolicy.PreparedCosts costs = FourthSeatAdditionalCostPolicy.prepareSepharaAlternative(activatingAbility, snapshot);
                for (Ability ability : game.getCard(activatingAbility.getSourceId()).getAbilities(game))
                    if (ability.getClass() == mage.abilities.costs.AlternativeCostSourceAbility.class) {
                        String text = ((mage.abilities.costs.AlternativeCostSourceAbility) ability).getAlternativeCostText(activatingAbility, game);
                        for (Map.Entry<String,String> entry : choice.getKeyChoices().entrySet()) if (entry.getValue().equals(text)) {
                            if (preparedCostsPayable(costs, snapshot)) {
                                pendingTapReservations = reservedTapIdsForPlanning();
                                pendingTapReservations.addAll(costs.reservedTapIds);
                                choice.setChoiceByKey(entry.getKey()); return true;
                            }
                            unfundedSephara = entry.getKey();
                        }
                    }
            }
            // The engine only offers alternatives after canActivateAlternativeCostsNow.
            // Prefer the first available alternative deterministically, never an unfunded
            // normal cast. Actual costs and sacrifice choices are revalidated by XMage.
            for (String key : new TreeSet<String>(choice.getKeyChoices().keySet())) {
                if (!key.equals(normal) && !key.equals(unfundedSephara)) { choice.setChoiceByKey(key); return true; }
            }
            return false;
        }
        if ((choice instanceof ChoiceColor || choice.isManaColorChoice())
                && (outcome != Outcome.PutManaInPool || currentUnpaid == null)) {
            Map<String, Integer> values = new LinkedHashMap<String, Integer>();
            for (String color : ChoiceColor.getBaseColors()) if (choice.getChoices().contains(color)) values.put(color, 0);
            for (Permanent permanent : game.getBattlefield().getAllActivePermanents()) {
                boolean own = getId().equals(permanent.getControllerId());
                if (permanent.isFaceDown(game) || !permanent.isCreature(game)
                        || own == (outcome == Outcome.Detriment)) continue;
                scoreColors(values, permanent.getColor(game), 5);
            }
            if (outcome != Outcome.Detriment) {
                for (Card card : getHand().getCards(game)) scoreColors(values, card.getColor(game), 1);
                for (UUID id : game.getCommandersIds(this, CommanderCardType.COMMANDER_OR_OATHBREAKER, false)) {
                    Card commander = game.getCard(id);
                    if (commander != null) scoreColors(values, commander.getColor(game), 3);
                }
            }
            String best = null; int score = -1;
            for (Map.Entry<String, Integer> entry : values.entrySet()) if (entry.getValue() > score) {
                best = entry.getKey(); score = entry.getValue();
            }
            if (best != null) { choice.setChoice(best); return true; }
        }
        return super.choose(outcome, choice, game);
    }

    private static void scoreColors(Map<String, Integer> scores, ObjectColor color, int weight) {
        for (String name : scores.keySet()) if (color.contains(ChoiceColor.getColorFromString(name)))
            scores.put(name, scores.get(name) + weight);
    }

    public String commanderName(Game game) {
        for (UUID id : game.getCommandersIds(this, CommanderCardType.COMMANDER_OR_OATHBREAKER, false)) {
            Card card = game.getCard(id);
            if (card != null) return card.getName();
        }
        return "";
    }

    @Override public boolean chooseMulligan(Game game) {
        if (hand.size() < 6 || mulligans >= 3) return false;
        boolean keep = DeckPlan.forCommander(commanderName(game)).keep(ownHand(game), mulligans);
        if (!keep) mulligans++;
        return !keep;
    }

    @Override public boolean priority(Game game) {
        game.resumeTimer(getTurnControlledBy());
        try {
            game.getState().setPriorityPlayerId(getId());
            game.firePriorityEvent(getId());
            long start = System.nanoTime();
            resetOptionalChainIfEmpty(game);
            manaAvailabilityBuildCount = manaAvailabilityCacheHits = candidateFundingCheckCount = 0;
            lastAdmittedModeSignatures.clear(); lastHeldSources.clear(); lastAdmittedSources.clear();
            callbackManaCache = null;
            String step = String.valueOf(game.getTurnStepType());
            if (game.getTurnNum() != actionTurn || !step.equals(actionStep)) {
                costlessActions.clear();
                // Conservative defense: a failed source/ability is not retried this turn,
                // even if resources change. Always reset next turn, never disable forever.
                if (game.getTurnNum() != actionTurn) failedActions.clear();
                successfulActivations.clear();
                stepActions = 0;
                actionTurn = game.getTurnNum();
                actionStep = step;
            }
            if (stepActions >= 64) {
                LOG.debug("fourth-seat guard=STEP_ACTION_LIMIT turn=" + actionTurn + " step=" + actionStep);
                pass(game); return false;
            }
            List<ActivatedAbility> playable;
            ManaOptions observationMana;
            capturePlayableMana = true;
            capturedPlayableMana = null;
            try { playable = getPlayable(game, true); }
            finally { capturePlayableMana = false; }
            supplementUntapCandidates(playable, game);
            observationMana = capturedPlayableMana;
            capturedPlayableMana = null;
            // No strategy or timing shortcut: the engine found no non-mana action.
            // Keep priority events/timers above, and never bypass a counter/protection.
            if (playable.stream().noneMatch(ability -> !ability.isManaAbility())) {
                LOG.debug("fourth-seat pass reason=NO_NONMANA_PLAYABLE candidates=0 elapsedNanos="
                        + (System.nanoTime() - start));
                pass(game); return false;
            }
            callbackManaCache = cacheSafe(game) ? new HashMap<String, ManaOptions>() : null;
            Observation observation = observe(game, observationMana);
            CommanderPolicy admissionPolicy = new CommanderPolicy();
            List<Action> actions = new ArrayList<Action>();
            Map<String, ActivatedAbility> engineActions = new LinkedHashMap<String, ActivatedAbility>();
            Map<String, Set<UUID>> tapReservations = new HashMap<String, Set<UUID>>();
            Collections.sort(playable, Comparator.comparingInt((ActivatedAbility ability) -> -urgency(ability, observation))
                    .thenComparing(ComputerPlayerFourthSeat::actionKey));
            for (ActivatedAbility base : playable) {
                if (actions.size() >= CANDIDATE_LIMIT || (!actions.isEmpty() && System.nanoTime() - start >= BUDGET_NANOS)) break;
                if (base.isManaAbility() || costlessActions.contains(guardKey(base))) continue;
                if (failedActions.contains(guardKey(base))) {
                    LOG.debug("fourth-seat guard=FAILED_THIS_TURN source=" + base.getSourceId());
                    continue;
                }
                if (base.getAbilityType().isNonManaActivatedAbility()) {
                    MageObject source = game.getObject(base.getSourceId());
                    String name = source == null ? "" : source.getName();
                    int limit = name.equals("Goblin Bombardment") ? 64
                            : name.equals("Krenko, Mob Boss") || name.equals("Lathril, Blade of the Elves") ? 4 : 1;
                    if (name.equals("Bristly Bill, Spine Sower") && game.getBattlefield().getAllActivePermanents(getId()).stream()
                            .filter(permanent -> permanent.isCreature(game))
                            .noneMatch(permanent -> permanent.getCounters(game).getCount(CounterType.P1P1) > 0)) {
                        LOG.debug("fourth-seat guard=NO_COUNTERS_TO_DOUBLE source=" + name);
                        continue;
                    }
                    if (successfulActivations.getOrDefault(guardKey(base), 0) >= limit) {
                        LOG.debug("fourth-seat guard=ACTIVATION_REPEAT_LIMIT source=" + name + " limit=" + limit);
                        continue;
                    }
                }
                List<Ability> options = FourthSeatBoundedOptions.generate(base, game);
                boolean bounded = options != null;
                if (!bounded) { options = getPlayableOptions(base.copy(), game); if (options.isEmpty()) options = Collections.<Ability>singletonList(base); }
                if (options.isEmpty()) continue;
                options = new ArrayList<Ability>(options);
                Collections.sort(options, Comparator.comparingDouble((Ability ability) -> -targetPriority(ability, observation))
                        .thenComparing(ability -> actionKey(ability) + ":" + targetIds(ability)));
                if (bounded) {
                    List<Ability> diverse = new ArrayList<Ability>(); Set<String> seenModes = new HashSet<String>();
                    for (Ability option : options) if (seenModes.add(option.getModes().getSelectedModes().toString())) diverse.add(option);
                    for (Ability option : options) if (!diverse.contains(option)) diverse.add(option);
                    options = diverse;
                }
                if (!hasPreferredOption(options, game, observation, admissionPolicy)) continue;
                int sourceOptions = 0;
                Boolean baseFunded = null;
                for (Ability rawOption : options) {
                    Ability option = normalizePartialGraveExileCosts(rawOption);
                    if (actions.size() >= CANDIDATE_LIMIT || sourceOptions >= (bounded ? 16 : 8)) break;
                    if (!(option instanceof ActivatedAbility)) continue;
                    MageObject semanticSource = game.getObject(option.getSourceId());
                    Action semanticAction = new Action("preview", semanticSource == null ? "" : semanticSource.getName(),
                            option.getRule(), option.getManaCosts().manaValue(), option instanceof PlayLandAbility,
                            option.isManaAbility(), option.isUsesStack(), targetIds(option), actionKind(option),
                            effectOutcomes(option), String.valueOf(option.getSourceId()), effectText(option), sacrifices(option), targetOutcomes(option));
                    if (CommanderPolicy.isStrategicallyHeld(observation, semanticAction)) { lastHeldSources.add(option.getSourceId()); continue; }
                    // Resolve strategic holds before expensive mana simulations. A large
                    // stack can otherwise repeat the same funding work for spells the
                    // policy will never cast. Keep the exact selected-mode/target check
                    // above, and share only this base ability's result within this callback.
                    if (baseFunded == null) baseFunded = canFundCandidate(base, game);
                    if (!baseFunded) break;
                    Set<UUID> reservedTaps = Collections.emptySet();
                    {
                        Game calculation = game.createSimulationForPlayableCalc();
                        FourthSeatAdditionalCostPolicy.PreparedCosts prepared = FourthSeatAdditionalCostPolicy
                                .prepareSelectedCosts((ActivatedAbility) option, calculation);
                        if (calculation.getCard(option.getSourceId()) instanceof mage.cards.s.SepharaSkysBlade
                                && !preparedCostsPayable(prepared, calculation)) {
                            FourthSeatAdditionalCostPolicy.PreparedCosts alternative = FourthSeatAdditionalCostPolicy
                                    .prepareSepharaAlternative((ActivatedAbility) option, calculation);
                            if (preparedCostsPayable(alternative, calculation)) prepared = alternative;
                        }
                        if (!prepared.distinctTapCostsPayable || !prepared.ability.getCosts().canPay(
                                prepared.ability, prepared.ability, getId(), calculation)) continue;
                        if (!canPayMinimumManaCost(prepared.ability,
                                optionMana(prepared.ability, calculation, prepared.reservedTapIds), calculation)
                                && dependencyPlanFor((ActivatedAbility) option, game, prepared.reservedTapIds) == null
                                && (option.isModal() || !canFundCandidate((ActivatedAbility) option, game))) continue;
                        reservedTaps = prepared.reservedTapIds;
                    }
                    if (option instanceof EquipAbility) {
                        Permanent equipment = game.getPermanent(option.getSourceId());
                        if (equipment != null && equipment.getAttachedTo() != null
                                && equipment.getAttachedTo().equals(option.getFirstTarget())) continue;
                    }
                    if (redundantKamahlAnimation(option, game)) continue;
                    String id = actionKey(option) + ":" + targetIds(option) + ":" + option.getModes().getSelectedModes();
                    if (engineActions.containsKey(id)) continue;
                    MageObject source = game.getObject(option.getSourceId());
                    String name = source == null ? "" : source.getName();
                    Action selectedAction = new Action(id, name, option.getRule(), option.getManaCosts().manaValue(),
                            option instanceof PlayLandAbility, option.isManaAbility(), option.isUsesStack(), targetIds(option),
                            actionKind(option), effectOutcomes(option), String.valueOf(option.getSourceId()),
                            effectText(option), sacrifices(option), targetOutcomes(option));
                    if (CommanderPolicy.isStrategicallyHeld(observation, selectedAction)) continue;
                    actions.add(selectedAction);
                    lastAdmittedSources.add(option.getSourceId());
                    lastAdmittedModeSignatures.computeIfAbsent(option.getSourceId(), key -> new HashSet<String>())
                            .add(new TreeSet<UUID>(option.getModes().getSelectedModes()).toString());
                    engineActions.put(id, (ActivatedAbility) option);
                    tapReservations.put(id, new HashSet<UUID>(reservedTaps));
                    sourceOptions++;
                }
            }
            Decision decision = new CommanderPolicy().choose(observation, actions,
                    Math.max(5_000_000L, BUDGET_NANOS - (System.nanoTime() - start)));
            ActivatedAbility chosen = decision.actionId == null ? null : engineActions.get(decision.actionId);
            if (chosen != null) {
                if (chosen.getManaCosts().manaValue() == 0 && chosen.getCosts().isEmpty()) costlessActions.add(guardKey(chosen));
                boolean activated;
                Set<UUID> previousTaps = pendingTapReservations == null ? Collections.<UUID>emptySet() : pendingTapReservations;
                pendingTapReservations = tapReservations.getOrDefault(decision.actionId, Collections.<UUID>emptySet());
                try {
                    callbackManaCache = null;
                    FourthSeatManaDependencyPolicy.ActionPlan dependency = dependencyPlanFor(chosen, game, pendingTapReservations);
                    if (dependency != null) {
                        ManaCost previousDemand = currentUnpaid;
                        currentUnpaid = dependency.demand.copy();
                        try { activated = activateAbility(dependency.ability, game); }
                        finally { currentUnpaid = previousDemand; }
                        if (activated) { LOG.debug("fourth-seat mana-plan=" + dependency.reason); return true; }
                    } else activated = activateAbility(chosen, game);
                }
                finally {
                    pendingTapReservations = previousTaps;
                    ComputerPlayerFourthSeat restored = restoredPlayer(game);
                    if (restored != null && restored != this) restored.pendingTapReservations = new HashSet<UUID>(previousTaps);
                }
                LOG.debug("fourth-seat decision=" + decision.reason + " candidates=" + actions.size()
                        + " elapsedNanos=" + (System.nanoTime() - start) + " activated=" + activated);
                if (activated) {
                    stepActions++;
                    if (chosen.getAbilityType().isNonManaActivatedAbility())
                        successfulActivations.put(guardKey(chosen), successfulActivations.getOrDefault(guardKey(chosen), 0) + 1);
                    return true;
                }
                MageObject failedSource = game.getObject(chosen.getSourceId());
                LOG.debug("fourth-seat activation-failed source=" + (failedSource == null ? chosen.getSourceId() : failedSource.getName())
                        + " rule=" + chosen.getRule() + " targets=" + targetIds(chosen)
                        + " step=" + game.getTurnStepType() + " turn=" + game.getTurnNum());
                rememberFailure(chosen, game);
                try { logPaymentFailure(chosen, game); }
                catch (RuntimeException diagnosticFailure) { LOG.debug("fourth-seat payment-context unavailable"); }
            }
            LOG.debug("fourth-seat pass reason=" + decision.reason + " candidates=" + actions.size()
                    + " elapsedNanos=" + (System.nanoTime() - start));
            pass(game);
            return false;
        } catch (RuntimeException failure) {
            LOG.warn("fourth-seat priority failed; passing: " + failure.getClass().getSimpleName());
            pass(game);
            return false;
        } finally {
            callbackManaCache = null;
            capturePlayableMana = false;
            capturedPlayableMana = null;
            game.pauseTimer(getTurnControlledBy());
        }
    }

    private void logPaymentFailure(ActivatedAbility chosen, Game game) {
        StringBuilder producers = new StringBuilder(); int count = 0;
        for (Permanent p : game.getBattlefield().getAllActivePermanents(getId())) {
            if (p.isFaceDown(game) || p.getAbilities(game).getActivatedManaAbilities(Zone.BATTLEFIELD).isEmpty()) continue;
            if (count++ >= 32) break;
            producers.append(p.getName()).append(':').append(p.getId()).append(":tapped=").append(p.isTapped()).append(';');
        }
        Object x = chosen.getCostsTagMap() == null ? null : chosen.getCostsTagMap().get("X");
        LOG.debug("fourth-seat payment-context source=" + chosen.getSourceId() + " cost=" + chosen.getManaCostsToPay()
                + " X=" + x + " pool=" + getManaPool().getMana() + " conditional=" + getManaPool().getConditionalMana().size()
                + " publicProducers=" + producers);
    }

    private static ActionKind actionKind(Ability ability) {
        if (ability instanceof EquipAbility) return ActionKind.EQUIP;
        if (ability.getAbilityType() == AbilityType.SPELL) return ActionKind.SPELL;
        if (ability.getAbilityType() == AbilityType.PLAY_LAND) return ActionKind.LAND;
        if (ability.getAbilityType().isActivatedAbility()) return ActionKind.ACTIVATION;
        return ActionKind.SPECIAL;
    }

    private static List<Mode> selectedModes(Ability ability) {
        List<Mode> modes = new ArrayList<Mode>();
        for (UUID id : ability.getModes().getSelectedModes()) {
            Mode mode = ability.getModes().get(id);
            if (mode != null) modes.add(mode);
        }
        if (modes.isEmpty() && ability.getModes().getMode() != null) modes.add(ability.getModes().getMode());
        return modes;
    }

    private static Set<String> effectOutcomes(Ability ability) {
        Set<String> outcomes = new HashSet<String>();
        for (Mode mode : selectedModes(ability)) for (Effect effect : mode.getEffects())
            if (effect.getOutcome() != null) outcomes.add(effect.getOutcome().name());
        return outcomes;
    }

    private static String effectText(Ability ability) {
        StringBuilder result = new StringBuilder();
        for (Mode mode : selectedModes(ability)) for (Effect effect : mode.getEffects()) {
            String text = effect.getText(mode);
            if (text != null) result.append(text).append(' ');
        }
        return result.toString().trim();
    }

    private static List<String> costTargetIds(Ability ability) {
        List<String> ids = new ArrayList<String>();
        for (Cost cost : ability.getCosts()) for (Target target : cost.getTargets())
            for (UUID id : target.getTargets()) ids.add(id.toString());
        return ids;
    }

    private static List<String> sacrifices(Ability ability) {
        List<String> ids = new ArrayList<String>();
        for (Cost cost : ability.getCosts()) {
            if (cost instanceof SacrificeSourceCost) ids.add(ability.getSourceId().toString());
            else if (cost instanceof SacrificeCost) for (Target target : cost.getTargets())
                for (UUID id : target.getTargets()) ids.add(id.toString());
        }
        return ids;
    }

    private static boolean redundantKamahlAnimation(Ability ability, Game game) {
        MageObject source = game.getObject(ability.getSourceId());
        if (source == null || !"Kamahl, Heart of Krosa".equals(source.getName())
                || !ability.getEffects().stream().anyMatch(effect -> effect instanceof BecomesCreatureTargetEffect)) return false;
        Permanent land = game.getPermanent(ability.getFirstTarget());
        if (land != null && land.isCreature(game) && land.hasAbility(VigilanceAbility.getInstance(), game)
                && land.hasAbility(IndestructibleAbility.getInstance(), game) && land.hasAbility(HasteAbility.getInstance(), game)) return true;
        for (StackObject object : game.getStack()) {
            Ability pending = object.getStackAbility();
            if (pending != null && Objects.equals(pending.getSourceId(), ability.getSourceId())
                    && pending.getRule().equals(ability.getRule()) && targetIds(pending).equals(targetIds(ability))) return true;
        }
        return false;
    }

    private ComputerPlayerFourthSeat restoredPlayer(Game game) {
        Player player = game.getPlayer(getId());
        return player != null && player.getRealPlayer() instanceof ComputerPlayerFourthSeat
                ? (ComputerPlayerFourthSeat) player.getRealPlayer() : null;
    }

    private void rememberFailure(Ability ability, Game game) {
        String key = guardKey(ability);
        if (failedActions.size() < 256) failedActions.add(key);
        Player restored = game.getPlayer(getId());
        if (restored != null && restored.getRealPlayer() instanceof ComputerPlayerFourthSeat) {
            ComputerPlayerFourthSeat current = (ComputerPlayerFourthSeat) restored.getRealPlayer();
            current.actionTurn = actionTurn;
            current.actionStep = actionStep;
            if (current.failedActions.size() < 256) current.failedActions.add(key);
        }
    }

    private static int urgency(Ability ability, Observation observation) {
        String text = ability.getRule().toLowerCase(Locale.ROOT);
        if (!observation.stack.isEmpty() && (text.contains("counter target") || text.contains("hexproof") || text.contains("indestructible"))) return 300;
        if (ability instanceof PlayLandAbility) return 200;
        if (text.contains("destroy target") || text.contains("exile target")) return 100;
        return 0;
    }

    private static Map<String, Set<String>> targetOutcomes(Ability ability) {
        Map<String, Set<String>> result = new HashMap<String, Set<String>>();
        for (Mode mode : selectedModes(ability)) for (Target target : mode.getTargets()) for (UUID id : target.getTargets()) {
            Set<String> values = result.computeIfAbsent(id.toString(), key -> new HashSet<String>());
            for (Effect effect : mode.getEffects()) if (effect.getOutcome() != null) values.add(effect.getOutcome().name());
        }
        return result;
    }

    private static double targetPriority(Ability ability, Observation observation) {
        double result = 0;
        for (Mode mode : selectedModes(ability)) {
            boolean harmful = mode.getEffects().stream().anyMatch(e -> e.getOutcome() != null && !e.getOutcome().isGood());
            for (Target target : mode.getTargets()) for (UUID targetId : target.getTargets()) {
                String id = targetId.toString();
                for (CardView stack : observation.stack) if (stack.id.equals(id)) result += stack.controllerId.equals(observation.selfId) ? -1000 : 100 + stack.manaValue;
                for (Seat seat : observation.seats) {
                    boolean self = seat.id.equals(observation.selfId);
                    if (seat.id.equals(id)) result += self == harmful ? -100 : 10 + Math.max(0, 40 - seat.life);
                    for (CardView card : seat.battlefield) if (card.id.equals(id)) {
                        result += (self == harmful ? -1 : 1) * (card.manaValue + Math.max(0, card.power) + 1);
                        if (self && !harmful) {
                            if (card.attacking && effectOutcomes(ability).contains("BoostCreature")) result += 20;
                            for (CardView spell : observation.stack) if (!spell.controllerId.equals(observation.selfId) && spell.targetIds.contains(id)) result += 100;
                        }
                    }
                }
            }
        }
        for (String id : sacrifices(ability)) for (CardView card : observation.self().battlefield)
            if (card.id.equals(id)) result -= DeckPlan.forCommander(observation.commander).cardValue(card);
        return result;
    }

    private static String guardKey(Ability ability) { return ability.getSourceId() + ":" + ability.getId() + ":" + ability.getRule(); }
    private static String actionKey(Ability ability) { return guardKey(ability) + ":costs=" + costTargetIds(ability); }

    private static List<String> targetIds(Ability ability) {
        List<String> ids = new ArrayList<String>();
        for (Mode mode : selectedModes(ability)) for (Target target : mode.getTargets())
            for (UUID id : target.getTargets()) ids.add(id.toString());
        return ids;
    }

    private List<CardView> ownHand(Game game) {
        List<CardView> cards = new ArrayList<CardView>();
        for (Card card : getHand().getCards(game)) cards.add(view(card, game));
        return cards;
    }

    @Override public ManaOptions getManaAvailable(Game game) {
        ManaOptions available = super.getManaAvailable(game);
        if (capturePlayableMana) capturedPlayableMana = available.copy();
        return available;
    }

    public Observation observe(Game game) { return observe(game, null); }

    private Observation observe(Game game, ManaOptions alreadyEnumeratedMana) {
        observationBuildCount++;
        List<Seat> seats = new ArrayList<Seat>();
        for (Player player : game.getPlayers().values()) {
            if (player.hasLeft()) continue;
            List<CardView> battlefield = new ArrayList<CardView>();
            for (Permanent permanent : game.getBattlefield().getAllActivePermanents(player.getId())) battlefield.add(view(permanent, game));
            seats.add(new Seat(player.getId().toString(), player.getLife(), player.getHand().size(), battlefield));
        }
        List<CardView> stack = new ArrayList<CardView>();
        for (StackObject object : game.getStack()) {
            Ability ability = object.getStackAbility();
            boolean hidden = object instanceof Card && ((Card) object).isFaceDown(game);
            stack.add(new CardView(object.getId().toString(), hidden ? "Face-down spell" : object.getName(), hidden ? 0 : object.getManaValue(),
                    !hidden && object.isLand(game), hidden || object.isCreature(game), hidden ? 0 : object.getPower().getValue(), hidden ? 0 : object.getToughness().getValue(),
                    false, hidden || ability == null ? "" : ability.getRule(), object.getControllerId().toString(),
                    ability == null ? Collections.<String>emptyList() : targetIds(ability), false, publicSubtypes(object, game, hidden)));
        }
        List<ManaView> manaOptions = new ArrayList<ManaView>();
        for (Mana mana : alreadyEnumeratedMana == null ? getManaAvailable(game) : alreadyEnumeratedMana) {
            if (manaOptions.size() >= 16) break;
            manaOptions.add(new ManaView(mana.getWhite(), mana.getBlue(), mana.getBlack(), mana.getRed(),
                    mana.getGreen(), mana.getColorless(), mana.getGeneric(), mana.getAny()));
        }
        boolean combatFirst = combatBeforeDevelopment(game);
        boolean saturated = developmentSaturated(game, credibleScuteArmy(game));
        int reserveFunding = reserveGrowthFunding(game);
        int[] scutes = scuteCounts(game);
        boolean scuteReserve = scutes[1] >= 32 || (game.isActivePlayer(getId()) && reserveObservedTurn == game.getTurnNum()
                && reserveAttackCommittedTurn == game.getTurnNum() && scutes[0] >= 32);
        boolean ashaya = false;
        for (Permanent p : game.getBattlefield().getAllActivePermanents(getId()))
            if (!p.isFaceDown(game) && game.getCard(p.getId()) instanceof mage.cards.a.AshayaSoulOfTheWild) ashaya = true;
        return new Observation(getId().toString(), commanderName(game), game.getTurnStepType().name(),
                game.isActivePlayer(getId()), game.getTurnNum(), ownHand(game), seats, stack, manaOptions, combatFirst, saturated, reserveFunding > 0, reserveFunding == 2, reserveFunding > 0 && scuteReserve && ashaya);
    }

    // A public, conservative attack projection, not a lethal proof or game rollout.
    private boolean combatBeforeDevelopment(Game game) {
        String step = game.getTurnStepType().name();
        return game.isActivePlayer(getId()) && ("UPKEEP".equals(step) || "DRAW".equals(step) || "PRECOMBAT_MAIN".equals(step))
                && game.getStack().isEmpty() && credibleScuteArmy(game);
    }

    private boolean credibleScuteArmy(Game game) {
        if (!"Aesi, Tyrant of Gyre Strait".equals(commanderName(game))) return false;
        List<Permanent> own = game.getBattlefield().getAllActivePermanents(getId());
        int scutes = 0;
        for (Permanent permanent : own) if (!permanent.isFaceDown(game)
                && "Scute Swarm".equals(permanent.getName())) scutes++;
        if (scutes < 16) return false;
        for (UUID opponentId : game.getOpponents(getId(), true)) {
            Player opponent = game.getPlayer(opponentId);
            if (opponent == null || opponent.hasLost() || opponent.hasLeft()) continue;
            int attackers = 0, largest = 0, blockers = 0;
            long power = 0;
            for (Permanent permanent : own) if (permanent.isCreature(game) && permanent.canAttack(opponentId, game)) {
                attackers++;
                int value = Math.max(0, permanent.getPower().getValue());
                power += value; largest = Math.max(largest, value);
            }
            if (attackers < 16) continue;
            for (Permanent permanent : game.getBattlefield().getAllActivePermanents(opponentId))
                if (permanent.isCreature(game) && !permanent.isTapped()) blockers++;
            if (power - (long) blockers * largest >= opponent.getLife()) return true;
        }
        return false;
    }

    private CardView view(Card card, Game game) {
        boolean hidden = card.isFaceDown(game);
        return new CardView(card.getId().toString(), hidden ? "Face-down card" : card.getName(),
                hidden ? 0 : card.getManaValue(), card.isLand(game), card.isCreature(game),
                card.getPower().getValue(), card.getToughness().getValue(), card instanceof Permanent && ((Permanent) card).isTapped(),
                hidden ? "" : String.join(" ", card.getRules(game)),
                (card instanceof Permanent ? ((Permanent) card).getControllerId() : card.getOwnerId()).toString(),
                Collections.<String>emptyList(), card instanceof Permanent && ((Permanent) card).isAttacking(), publicSubtypes(card, game, hidden), card instanceof Permanent && ((Permanent) card).hasSummoningSickness());
    }

    private static Set<String> publicSubtypes(MageObject card, Game game, boolean hidden) {
        Set<String> result = new HashSet<String>();
        if (hidden) return result;
        if (card.hasSubtype(SubType.ANGEL, game)) result.add("Angel");
        if (card.hasSubtype(SubType.GOBLIN, game)) result.add("Goblin");
        if (card.hasSubtype(SubType.ELF, game)) result.add("Elf");
        if (card.hasSubtype(SubType.DINOSAUR, game)) result.add("Dinosaur");
        if (card.hasSubtype(SubType.HUMAN, game)) result.add("Human");
        return result;
    }

    @Override public boolean choose(Outcome outcome, Cards cards, TargetCard target, Ability source, Game game) {
        if (isCelestialBeholdPayment(outcome, target, source, game) && target.getZone() == Zone.HAND) {
            UUID controller = target.getAffectedAbilityControllerId(getId());
            for (UUID id : new TreeSet<UUID>(cards)) {
                if (target.getTargets().size() >= target.getMaxNumberOfTargets()) break;
                if (!target.getTargets().contains(id) && target.canTarget(controller, id, source, cards, game)) target.add(id, game);
            }
            return target.isChosen(game);
        }
        CommanderPolicy.ChoiceKind kind = target.getZone() == Zone.LIBRARY ? CommanderPolicy.ChoiceKind.TUTOR
                : outcome == Outcome.Discard && target.getZone() == Zone.HAND ? CommanderPolicy.ChoiceKind.DISCARD : null;
        if (kind == null || (kind == CommanderPolicy.ChoiceKind.TUTOR && !target.isNotTarget())) return super.choose(outcome, cards, target, source, game);
        UUID controllerId = target.getAffectedAbilityControllerId(getId());
        List<CardView> candidates = new ArrayList<CardView>();
        for (UUID id : cards) {
            Card card = game.getCard(id);
            if (card == null || !getId().equals(card.getOwnerId())) return super.choose(outcome, cards, target, source, game);
            if (target.canTarget(controllerId, id, source, cards, game)) candidates.add(view(card, game));
        }
        Observation observation = observe(game);
        while (!target.isChoiceCompleted(controllerId, source, game, cards) && !candidates.isEmpty()) {
            String selected = new CommanderPolicy().chooseCard(observation, candidates, kind);
            if (selected == null) break;
            UUID id = UUID.fromString(selected);
            candidates.removeIf(card -> card.id.equals(selected));
            if (target.canTarget(controllerId, id, source, cards, game)) {
                if (target.isNotTarget()) target.add(id, game); else target.addTarget(id, source, game);
            }
        }
        return target.isChosen(game);
    }

    @Override public boolean chooseTarget(Outcome outcome, Cards cards, TargetCard target, Ability source, Game game) {
        return choose(outcome, cards, target, source, game);
    }

    @Override public int announceX(int min, int max, String message, Game game, Ability source, boolean isManaPay) {
        if (isManaPay && source instanceof ActivatedAbility)
            return FourthSeatVariableManaPolicy.chooseX(min, max, source, game, this, manaAvailableFor((ActivatedAbility) source, game));
        return super.announceX(min, max, message, game, source, false);
    }

    @Override public boolean playMana(Ability ability, ManaCost unpaid, String promptText, Game game) {
        unpaid = prioritizeRestrictedPips(unpaid);
        ManaCost previous = currentUnpaid;
        UUID previousReserved = reservedTapSource;
        Set<UUID> previousTaps = pendingTapReservations;
        pendingTapReservations = reservedTapIdsForPlanning();
        currentUnpaid = unpaid.copy();
        if (ability.getCosts().stream().anyMatch(cost -> cost instanceof TapSourceCost))
            reservedTapSource = ability.getSourceId();
        try {
            // Fund positive-net input-cost mana engines before spending their input
            // lands on the outer spell. Never recursively prefund another engine.
            if (!ability.isManaAbility() && !prefundingMana
                    && !unpaid.getMana().enough(getManaPool().getMana())
                    && !FourthSeatManaDependencyPolicy.ordinaryPaymentSuffices(ability, unpaid, game, this)) {
                Game paymentCalculation = game.createSimulationForPlayableCalc();
                for (Permanent permanent : getAvailableManaProducersWithCost(game)) {
                    for (ActivatedManaAbilityImpl producer : permanent.getAbilities(game)
                            .getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, getId(), game)) {
                        if (producer.getManaCosts().isEmpty() || !producer.canActivate(getId(), game).canActivate()) continue;
                        boolean useful = false;
                        for (Mana output : producer.getNetMana(paymentCalculation)) {
                            if (output.count() <= producer.getManaCosts().manaValue() || !unpaid.testPay(output)) continue;
                            if (output instanceof ConditionalMana && !((ConditionalMana) output)
                                    .apply(ability, paymentCalculation, producer.getSourceId(), unpaid)) continue;
                            useful = true;
                        }
                        if (!useful || !canPayMinimumManaCost(producer, manaAvailableFor(producer, game), game)) continue;
                        prefundingMana = true;
                        try { if (activateAbility(producer, game)) return true; }
                        finally {
                            prefundingMana = false;
                            ComputerPlayerFourthSeat restored = restoredPlayer(game);
                            if (restored != null) restored.prefundingMana = false;
                        }
                    }
                }
            }
            if (super.playMana(ability, unpaid, promptText, game)) return true;
            // Native payment filters on a producer's base output. A colorless
            // land can still pay a colored pip through a real mana trigger,
            // especially after an ordinary Treasure was consumed earlier in
            // this same payment. Reuse the bounded native-trigger certificate
            // for the exact remaining cost; do not count the Treasure again.
            if (ability instanceof ActivatedAbility && !ability.isManaAbility()) {
                ActivatedAbility remaining = ((ActivatedAbility) ability).copy();
                remaining.clearManaCostsToPay(); remaining.addManaCostsToPay(unpaid.copy());
                FourthSeatManaDependencyPolicy.ActionPlan plan = FourthSeatManaDependencyPolicy.nextAction(remaining, game, this, null);
                if (plan != null && ("TRIGGERED_MANA".equals(plan.reason) || "TWO_TRIGGERED_SOURCES".equals(plan.reason))
                        && plan.ability instanceof ActivatedManaAbilityImpl && pureTapMana((ActivatedManaAbilityImpl) plan.ability)
                        && plan.ability.canActivate(getId(), game).canActivate()) return activateAbility(plan.ability, game);
            }
            return false;
        }
        finally {
            currentUnpaid = previous; reservedTapSource = previousReserved;
            pendingTapReservations = previousTaps;
            ComputerPlayerFourthSeat restored = restoredPlayer(game);
            if (restored != null && restored != this) {
                restored.currentUnpaid = previous == null ? null : previous.copy();
                restored.reservedTapSource = previousReserved;
                restored.pendingTapReservations = new HashSet<UUID>(previousTaps);
            }
        }
    }

    /** The native chooser funds the last unpaid component. Announced generic X
     * can be appended after colored pips, so reorder only a disposable demand
     * copy. The engine's real cost and assignment order are untouched.
     */
    private static ManaCost prioritizeRestrictedPips(ManaCost unpaid) {
        if (!(unpaid instanceof ManaCosts)) return unpaid;
        ManaCosts<ManaCost> costs = ((ManaCosts<ManaCost>) unpaid).copy();
        if (costs.isEmpty() || !(costs.get(costs.size()-1) instanceof mage.abilities.costs.mana.GenericManaCost)) return unpaid;
        int colored = -1, colorless = -1;
        for (int i=0;i<costs.size();i++) {
            if (costs.get(i) instanceof mage.abilities.costs.mana.ColoredManaCost) colored=i;
            if (costs.get(i) instanceof mage.abilities.costs.mana.ColorlessManaCost) colorless=i;
        }
        int next = colorless >= 0 ? colorless : colored;
        if (next >= 0) costs.add(costs.remove(next));
        return costs;
    }

    Set<UUID> reservedTapIdsForPlanning() {
        Set<UUID> result = new HashSet<UUID>();
        if (pendingTapReservations != null) result.addAll(pendingTapReservations);
        if (reservedTapSource != null) result.add(reservedTapSource);
        return result;
    }

    /** Only incomplete, non-target own-grave exile choices are reset on a copy. */
    static Ability normalizePartialGraveExileCosts(Ability original) {
        Ability copy = original.copy();
        for (Cost cost : copy.getCosts()) {
            if (!(cost instanceof mage.abilities.costs.common.ExileFromGraveCost)) continue;
            for (Target target : cost.getTargets()) {
                if (target instanceof mage.target.common.TargetCardInYourGraveyard && target.isNotTarget()
                        && !target.getTargets().isEmpty()
                        && target.getTargets().size() < target.getMinNumberOfTargets()) target.clearChosen();
            }
        }
        return copy;
    }

    @Override public List<MageObject> getAvailableManaProducers(Game game) {
        List<MageObject> result = new ArrayList<MageObject>(super.getAvailableManaProducers(game));
        result.removeIf(object -> (reservedTapSource != null && reservedTapSource.equals(object.getId()))
                || (pendingTapReservations != null && pendingTapReservations.contains(object.getId())));
        return result;
    }

    @Override public List<Permanent> getAvailableManaProducersWithCost(Game game) {
        List<Permanent> result = new ArrayList<Permanent>(super.getAvailableManaProducersWithCost(game));
        result.removeIf(object -> (reservedTapSource != null && reservedTapSource.equals(object.getId()))
                || (pendingTapReservations != null && pendingTapReservations.contains(object.getId())));
        return result;
    }

    @Override public List<Integer> getMultiAmountWithIndividualConstraints(Outcome outcome, List<MultiAmountMessage> messages,
            int totalMin, int totalMax, MultiAmountType type, Game game) {
        if (type != MultiAmountType.MANA || currentUnpaid == null || messages.isEmpty())
            return super.getMultiAmountWithIndividualConstraints(outcome, messages, totalMin, totalMax, type, game);
        Mana needed = currentUnpaid.getMana();
        Map<String, Integer> demand = new HashMap<String, Integer>();
        demand.put("W", needed.getWhite()); demand.put("U", needed.getBlue()); demand.put("B", needed.getBlack());
        demand.put("R", needed.getRed()); demand.put("G", needed.getGreen()); demand.put("C", needed.getColorless());
        List<Integer> result = new ArrayList<Integer>();
        int remaining = totalMax;
        for (MultiAmountMessage message : messages) {
            if (!demand.containsKey(message.message))
                return super.getMultiAmountWithIndividualConstraints(outcome, messages, totalMin, totalMax, type, game);
            result.add(message.min); remaining -= message.min;
        }
        if (remaining < 0) return super.getMultiAmountWithIndividualConstraints(outcome, messages, totalMin, totalMax, type, game);
        for (int i = 0; i < messages.size(); i++) {
            int add = Math.min(remaining, Math.min(messages.get(i).max - result.get(i),
                    Math.max(0, demand.get(messages.get(i).message) - result.get(i))));
            result.set(i, result.get(i) + add); remaining -= add;
        }
        for (int i = 0; i < messages.size() && remaining > 0; i++) {
            int add = Math.min(remaining, messages.get(i).max - result.get(i));
            result.set(i, result.get(i) + add); remaining -= add;
        }
        if (remaining != 0) return super.getMultiAmountWithIndividualConstraints(outcome, messages, totalMin, totalMax, type, game);
        return result;
    }

    private static void setCreatureTypeChoice(Choice choice, String type) {
        if (choice.getKeyChoices().containsKey(type)) choice.setChoiceByKey(type);
        else choice.setChoice(type);
    }

    private boolean isCelestialReunionActivation(Ability source, Game game) {
        return source != null && activatingAbility != null
                && Objects.equals(source.getSourceId(), activatingAbility.getSourceId())
                && getId().equals(source.getControllerId())
                && game.getCard(source.getSourceId()) instanceof mage.cards.c.CelestialReunion;
    }

    private boolean isCelestialBeholdPayment(Outcome outcome, Target target, Ability source, Game game) {
        return outcome == Outcome.Neutral && target.isNotTarget() && isCelestialReunionActivation(source, game)
                && source.getCostsTagMap() != null && source.getCostsTagMap().containsKey("beholdActivation");
    }

    private String celestialBeholdType(Set<String> offered, Game game) {
        List<Card> own = new ArrayList<Card>(getHand().getCards(game));
        own.addAll(game.getBattlefield().getAllActivePermanents(getId()));
        String best = null; int bestCount = 1;
        for (SubType type : SubType.getCreatureTypes()) {
            String name = type.toString();
            if (offered != null && !offered.contains(name)) continue;
            int count = 0;
            for (Card card : own) if (!card.isFaceDown(game) && card.isCreature(game) && card.hasSubtype(type, game)) count++;
            if (count > bestCount || count == bestCount && best != null && name.compareTo(best) < 0) {
                best = name; bestCount = count;
            }
        }
        return best;
    }

    @Override protected boolean chooseCreatureType(Outcome outcome, Choice choice, Game game) {
        Set<String> offered = new TreeSet<String>(choice.getChoices());
        offered.addAll(choice.getKeyChoices().keySet());
        if (activatingAbility != null && isCelestialReunionActivation(activatingAbility, game)) {
            String type = celestialBeholdType(offered, game);
            if (type == null) return false;
            setCreatureTypeChoice(choice, type); return true;
        }
        String commander = commanderName(game);
        String preferred = commander.startsWith("Giada,") ? "Angel" : commander.startsWith("Krenko,") ? "Goblin"
                : commander.startsWith("Lathril,") ? "Elf" : commander.startsWith("Gishath,") ? "Dinosaur"
                : commander.startsWith("Adeline,") ? "Human" : null;
        if (outcome != Outcome.Detriment && preferred != null && offered.contains(preferred)) {
            setCreatureTypeChoice(choice, preferred); return true;
        }
        Map<String, Integer> counts = new TreeMap<String, Integer>();
        List<Card> known = new ArrayList<Card>();
        if (outcome != Outcome.Detriment) known.addAll(getHand().getCards(game));
        for (Permanent permanent : game.getBattlefield().getAllActivePermanents()) {
            boolean own = getId().equals(permanent.getControllerId());
            if (own != (outcome == Outcome.Detriment)) known.add(permanent);
        }
        for (Card card : known) {
            if (card.isFaceDown(game) || !card.isCreature(game)) continue;
            for (SubType subtype : card.getSubtype(game)) {
                String type = subtype.toString();
                if (offered.contains(type)) counts.put(type, counts.getOrDefault(type, 0) + 1);
            }
        }
        String selected = null; int best = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) if (entry.getValue() > best) {
            selected = entry.getKey(); best = entry.getValue();
        }
        if (selected == null && !offered.isEmpty()) selected = new TreeSet<String>(offered).first();
        if (selected == null) return false;
        setCreatureTypeChoice(choice, selected); return true;
    }

    @Override public boolean choose(Outcome outcome, Target target, Ability source, Game game, Map<String, Serializable> options) {
        if (isCelestialBeholdPayment(outcome, target, source, game) && target.getZone() == Zone.BATTLEFIELD) {
            UUID controller = target.getAffectedAbilityControllerId(getId());
            for (Permanent permanent : game.getBattlefield().getAllActivePermanents(getId())) {
                if (target.getTargets().size() >= target.getMaxNumberOfTargets()) break;
                if (!target.getTargets().contains(permanent.getId()) && target.canTarget(controller, permanent.getId(), source, game))
                    target.add(permanent.getId(), game);
            }
            return target.isChosen(game);
        }
        if (outcome == Outcome.Discard && target instanceof TargetCard && target.getZone() == Zone.HAND)
            return choose(outcome, getHand(), (TargetCard) target, source, game);
        if (outcome != Outcome.Sacrifice || target.getZone() != Zone.BATTLEFIELD || !target.isNotTarget())
            return super.choose(outcome, target, source, game, options);
        UUID controllerId = target.getAffectedAbilityControllerId(getId());
        List<CardView> candidates = new ArrayList<CardView>();
        for (Permanent permanent : game.getBattlefield().getAllActivePermanents(getId())) {
            if (target.canTarget(controllerId, permanent.getId(), source, game)) candidates.add(view(permanent, game));
        }
        Observation observation = observe(game);
        while (!target.isChoiceCompleted(controllerId, source, game, null) && !candidates.isEmpty()) {
            String selected = new CommanderPolicy().chooseCard(observation, candidates, CommanderPolicy.ChoiceKind.SACRIFICE);
            if (selected == null) break;
            UUID id = UUID.fromString(selected);
            candidates.removeIf(card -> card.id.equals(selected));
            if (target.canTarget(controllerId, id, source, game)) target.add(id, game);
        }
        return target.isChosen(game);
    }

    @Override public void selectAttackers(Game game, UUID attackingPlayerId) {
        FourthSeatCombatAdapter.selectAttackers(this, game, attackingPlayerId);
    }

    @Override public void selectBlockers(Ability source, Game game, UUID defendingPlayerId) {
        FourthSeatCombatAdapter.selectBlockers(this, source, game, defendingPlayerId);
    }
}
