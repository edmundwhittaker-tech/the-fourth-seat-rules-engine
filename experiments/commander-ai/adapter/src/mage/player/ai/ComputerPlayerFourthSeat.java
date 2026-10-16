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

    // Engine-only affordability snapshot, never strategic full-state scoring.
    // Restricted outputs are tested BEFORE ManaOptions flattens their conditions.
    private ManaOptions manaAvailableFor(ActivatedAbility ability, Game game) {
        return manaAvailableFor(ability, game, Collections.<UUID>emptySet());
    }

    private ManaOptions manaAvailableFor(ActivatedAbility ability, Game game, Set<UUID> tapReservations) {
        Game calculation = game.createSimulationForPlayableCalc();
        for (UUID id : tapReservations) {
            Permanent reserved = calculation.getPermanent(id);
            if (reserved != null) reserved.setTapped(true);
        }
        Player calculationPlayer = calculation.getPlayer(getId());
        if (ability.getCosts().stream().anyMatch(cost -> cost instanceof TapSourceCost)) {
            Permanent reserved = calculation.getPermanent(ability.getSourceId());
            if (reserved != null) reserved.setTapped(true);
        }
        for (ConditionalMana mana : calculationPlayer.getManaPool().getConditionalMana())
            if (!mana.apply(ability, calculation, mana.getManaProducerId(), ability.getManaCostsToPay())) mana.clear();
        for (Permanent permanent : calculation.getBattlefield().getAllActivePermanents()) {
            List<ActivatedManaAbilityImpl> remove = new ArrayList<ActivatedManaAbilityImpl>();
            for (ActivatedManaAbilityImpl producer : permanent.getAbilities(calculation)
                    .getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, getId(), calculation)) {
                List<Mana> outputs = producer.getNetMana(calculation);
                // Mixed valid/invalid alternatives remain conservatively removed rather
                // than flattening an invalid output into usable mana.
                for (Mana output : outputs) if (output instanceof ConditionalMana
                        && !((ConditionalMana) output).apply(ability, calculation, producer.getSourceId(), ability.getManaCostsToPay())) {
                    remove.add(producer); break;
                }
            }
            for (ActivatedManaAbilityImpl producer : remove) permanent.removeAbility(producer, producer.getSourceId(), calculation);
        }
        return calculationPlayer.getManaAvailable(calculation);
    }

    private boolean canFundCandidate(ActivatedAbility ability, Game game) {
        if (ability instanceof PlayLandAbility) return true;
        Game calculation = game.createSimulationForPlayableCalc();
        return canPlay(ability, manaAvailableFor(ability, calculation), calculation.getObject(ability.getSourceId()), calculation);
    }

    @Override public boolean activateAbility(ActivatedAbility ability, Game game) {
        ActivatedAbility previous = activatingAbility;
        activatingAbility = ability;
        try { return super.activateAbility(ability, game); }
        finally {
            activatingAbility = previous;
            ComputerPlayerFourthSeat restored = restoredPlayer(game);
            if (restored != null && restored != this)
                restored.activatingAbility = previous == null ? null : previous.copy();
        }
    }

    @Override public boolean chooseUse(Outcome outcome, String message, String secondMessage,
            String trueText, String falseText, Ability source, Game game) {
        if (outcome == Outcome.DrawCard && getLibrary().size() == 0) return false;
        if (optionalCostProbe) return true;
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
            // The engine only offers alternatives after canActivateAlternativeCostsNow.
            // Prefer the first available alternative deterministically, never an unfunded
            // normal cast. Actual costs and sacrifice choices are revalidated by XMage.
            for (String key : new TreeSet<String>(choice.getKeyChoices().keySet())) {
                if (!key.equals(normal)) { choice.setChoiceByKey(key); return true; }
            }
            return false;
        }
        if (choice instanceof ChoiceColor && outcome != Outcome.PutManaInPool) {
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
            observationMana = capturedPlayableMana;
            capturedPlayableMana = null;
            // No strategy or timing shortcut: the engine found no non-mana action.
            // Keep priority events/timers above, and never bypass a counter/protection.
            if (playable.stream().noneMatch(ability -> !ability.isManaAbility())) {
                LOG.debug("fourth-seat pass reason=NO_NONMANA_PLAYABLE candidates=0 elapsedNanos="
                        + (System.nanoTime() - start));
                pass(game); return false;
            }
            Observation observation = observe(game, observationMana);
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
                if (!canFundCandidate(base, game)) continue;
                List<Ability> options = getPlayableOptions(base.copy(), game);
                if (options.isEmpty()) options = Collections.<Ability>singletonList(base);
                options = new ArrayList<Ability>(options);
                Collections.sort(options, Comparator.comparingDouble((Ability ability) -> -targetPriority(ability, observation))
                        .thenComparing(ability -> actionKey(ability) + ":" + targetIds(ability)));
                int sourceOptions = 0;
                for (Ability option : options) {
                    if (actions.size() >= CANDIDATE_LIMIT || sourceOptions >= 8) break;
                    if (!(option instanceof ActivatedAbility)) continue;
                    Set<UUID> reservedTaps = Collections.emptySet();
                    if (option.isModal() || selectedModes(option).stream().anyMatch(mode -> mode.getCost() != null)) {
                        Game calculation = game.createSimulationForPlayableCalc();
                        FourthSeatAdditionalCostPolicy.PreparedCosts prepared = FourthSeatAdditionalCostPolicy
                                .prepareSelectedCosts((ActivatedAbility) option, calculation);
                        if (!prepared.distinctTapCostsPayable || !prepared.ability.getCosts().canPay(
                                prepared.ability, prepared.ability, getId(), calculation)) continue;
                        if (!canPayMinimumManaCost(prepared.ability,
                                manaAvailableFor(prepared.ability, calculation, prepared.reservedTapIds), calculation)) continue;
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
                    actions.add(new Action(id, name, option.getRule(), option.getManaCosts().manaValue(),
                            option instanceof PlayLandAbility, option.isManaAbility(), option.isUsesStack(), targetIds(option),
                            actionKind(option), effectOutcomes(option), String.valueOf(option.getSourceId()),
                            effectText(option), sacrifices(option)));
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
                try { activated = activateAbility(chosen, game); }
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
            capturePlayableMana = false;
            capturedPlayableMana = null;
            game.pauseTimer(getTurnControlledBy());
        }
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

    private static double targetPriority(Ability ability, Observation observation) {
        boolean harmful = selectedModes(ability).stream().flatMap(mode -> mode.getEffects().stream())
                .anyMatch(effect -> effect.getOutcome() != null && !effect.getOutcome().isGood());
        double result = 0;
        for (String id : targetIds(ability)) {
            for (CardView stack : observation.stack) if (stack.id.equals(id))
                result += stack.controllerId.equals(observation.selfId) ? -1000 : 100 + stack.manaValue;
            for (Seat seat : observation.seats) {
                boolean self = seat.id.equals(observation.selfId);
                if (seat.id.equals(id)) result += self == harmful ? -100 : 10 + Math.max(0, 40 - seat.life);
                for (CardView card : seat.battlefield) if (card.id.equals(id)) {
                    result += (self == harmful ? -1 : 1) * (card.manaValue + Math.max(0, card.power) + 1);
                    if (self && !harmful && card.attacking && effectOutcomes(ability).contains("BoostCreature")) result += 20;
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
        return new Observation(getId().toString(), commanderName(game), game.getTurnStepType().name(),
                game.isActivePlayer(getId()), game.getTurnNum(), ownHand(game), seats, stack, manaOptions, combatBeforeDevelopment(game));
    }

    // A public, conservative attack projection, not a lethal proof or game rollout.
    private boolean combatBeforeDevelopment(Game game) {
        if (!game.isActivePlayer(getId()) || !"PRECOMBAT_MAIN".equals(game.getTurnStepType().name())
                || !game.getStack().isEmpty() || !"Aesi, Tyrant of Gyre Strait".equals(commanderName(game))) return false;
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
        if (isManaPay) return FourthSeatVariableManaPolicy.chooseX(min, max, source, game, this);
        return super.announceX(min, max, message, game, source, false);
    }

    @Override public boolean playMana(Ability ability, ManaCost unpaid, String promptText, Game game) {
        ManaCost previous = currentUnpaid;
        UUID previousReserved = reservedTapSource;
        currentUnpaid = unpaid.copy();
        if (ability.getCosts().stream().anyMatch(cost -> cost instanceof TapSourceCost))
            reservedTapSource = ability.getSourceId();
        try {
            // Fund positive-net input-cost mana engines before spending their input
            // lands on the outer spell. Never recursively prefund another engine.
            if (!ability.isManaAbility() && !prefundingMana
                    && !unpaid.getMana().enough(getManaPool().getMana())) {
                for (Permanent permanent : getAvailableManaProducersWithCost(game)) {
                    for (ActivatedManaAbilityImpl producer : permanent.getAbilities(game)
                            .getAvailableActivatedManaAbilities(Zone.BATTLEFIELD, getId(), game)) {
                        if (producer.getManaCosts().isEmpty() || !producer.canActivate(getId(), game).canActivate()) continue;
                        boolean useful = false;
                        for (Mana output : producer.getNetMana(game)) {
                            if (output.count() <= producer.getManaCosts().manaValue() || !unpaid.testPay(output)) continue;
                            if (output instanceof ConditionalMana && !((ConditionalMana) output)
                                    .apply(ability, game, producer.getSourceId(), unpaid)) continue;
                            useful = true;
                        }
                        if (!useful || !canPayMinimumManaCost(producer, manaAvailableFor(producer, game), game)) continue;
                        prefundingMana = true;
                        try { return activateAbility(producer, game); }
                        finally {
                            prefundingMana = false;
                            ComputerPlayerFourthSeat restored = restoredPlayer(game);
                            if (restored != null) restored.prefundingMana = false;
                        }
                    }
                }
            }
            return super.playMana(ability, unpaid, promptText, game);
        }
        finally {
            currentUnpaid = previous; reservedTapSource = previousReserved;
            ComputerPlayerFourthSeat restored = restoredPlayer(game);
            if (restored != null && restored != this) {
                restored.currentUnpaid = previous == null ? null : previous.copy();
                restored.reservedTapSource = previousReserved;
            }
        }
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

    @Override protected boolean chooseCreatureType(Outcome outcome, Choice choice, Game game) {
        Set<String> offered = new TreeSet<String>(choice.getChoices());
        offered.addAll(choice.getKeyChoices().keySet());
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
