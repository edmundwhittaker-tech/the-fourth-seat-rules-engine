package mage.player.ai;

import fourthseat.ai.CommanderPolicy;
import fourthseat.ai.DeckPlan;
import fourthseat.ai.PolicyModel.*;
import mage.MageObject;
import mage.Mana;
import mage.abilities.Ability;
import mage.abilities.ActivatedAbility;
import mage.abilities.PlayLandAbility;
import mage.cards.Card;
import mage.choices.Choice;
import org.apache.log4j.Logger;
import mage.cards.Cards;
import mage.constants.CommanderCardType;
import mage.constants.Outcome;
import mage.constants.RangeOfInfluence;
import mage.constants.Zone;
import mage.constants.SubType;
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
    }

    @Override public ComputerPlayerFourthSeat copy() { return new ComputerPlayerFourthSeat(this); }

    @Override public void init(Game game) {
        super.init(game);
        mulligans = 0;
        actionTurn = -1;
        actionStep = "";
        costlessActions.clear();
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
                actionTurn = game.getTurnNum();
                actionStep = step;
            }
            Observation observation = observe(game);
            List<Action> actions = new ArrayList<Action>();
            Map<String, ActivatedAbility> engineActions = new LinkedHashMap<String, ActivatedAbility>();
            List<ActivatedAbility> playable = getPlayable(game, true);
            Collections.sort(playable, Comparator.comparingInt((ActivatedAbility ability) -> -urgency(ability, observation))
                    .thenComparing(ComputerPlayerFourthSeat::actionKey));
            for (ActivatedAbility base : playable) {
                if (actions.size() >= CANDIDATE_LIMIT || (!actions.isEmpty() && System.nanoTime() - start >= BUDGET_NANOS)) break;
                if (base.isManaAbility() || costlessActions.contains(actionKey(base))) continue;
                List<Ability> options = getPlayableOptions(base.copy(), game);
                if (options.isEmpty()) options = Collections.<Ability>singletonList(base);
                options = new ArrayList<Ability>(options);
                Collections.sort(options, Comparator.comparingDouble((Ability ability) -> -targetPriority(ability, observation))
                        .thenComparing(ability -> actionKey(ability) + ":" + targetIds(ability)));
                int sourceOptions = 0;
                for (Ability option : options) {
                    if (actions.size() >= CANDIDATE_LIMIT || sourceOptions >= 8) break;
                    if (!(option instanceof ActivatedAbility)) continue;
                    String id = actionKey(option) + ":" + targetIds(option) + ":" + option.getModes().getSelectedModes();
                    if (engineActions.containsKey(id)) continue;
                    MageObject source = game.getObject(option.getSourceId());
                    String name = source == null ? "" : source.getName();
                    actions.add(new Action(id, name, option.getRule(), option.getManaCosts().manaValue(),
                            option instanceof PlayLandAbility, option.isManaAbility(), option.isUsesStack(), targetIds(option)));
                    engineActions.put(id, (ActivatedAbility) option);
                    sourceOptions++;
                }
            }
            Decision decision = new CommanderPolicy().choose(observation, actions,
                    Math.max(5_000_000L, BUDGET_NANOS - (System.nanoTime() - start)));
            ActivatedAbility chosen = decision.actionId == null ? null : engineActions.get(decision.actionId);
            if (chosen != null) {
                if (chosen.getManaCosts().manaValue() == 0 && chosen.getCosts().isEmpty()) costlessActions.add(actionKey(chosen));
                boolean activated = activateAbility(chosen, game);
                LOG.debug("fourth-seat decision=" + decision.reason + " candidates=" + actions.size()
                        + " elapsedNanos=" + (System.nanoTime() - start) + " activated=" + activated);
                if (activated) return true;
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
            game.pauseTimer(getTurnControlledBy());
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
        String text = ability.getRule().toLowerCase(Locale.ROOT);
        boolean harmful = text.contains("destroy") || text.contains("exile") || text.contains("damage") || text.contains("counter target");
        double result = 0;
        for (String id : targetIds(ability)) {
            for (CardView stack : observation.stack) if (stack.id.equals(id))
                result += stack.controllerId.equals(observation.selfId) ? -1000 : 100 + stack.manaValue;
            for (Seat seat : observation.seats) {
                boolean self = seat.id.equals(observation.selfId);
                if (seat.id.equals(id)) result += self == harmful ? -100 : 10 + Math.max(0, 40 - seat.life);
                for (CardView card : seat.battlefield) if (card.id.equals(id))
                    result += (self == harmful ? -1 : 1) * (card.manaValue + Math.max(0, card.power) + 1);
            }
        }
        return result;
    }

    private static String actionKey(Ability ability) { return ability.getSourceId() + ":" + ability.getId() + ":" + ability.getRule(); }

    private static List<String> targetIds(Ability ability) {
        List<String> ids = new ArrayList<String>();
        for (Target target : ability.getTargets()) for (UUID id : target.getTargets()) ids.add(id.toString());
        return ids;
    }

    private List<CardView> ownHand(Game game) {
        List<CardView> cards = new ArrayList<CardView>();
        for (Card card : getHand().getCards(game)) cards.add(view(card, game));
        return cards;
    }

    public Observation observe(Game game) {
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
        for (Mana mana : getManaAvailable(game)) {
            if (manaOptions.size() >= 16) break;
            manaOptions.add(new ManaView(mana.getWhite(), mana.getBlue(), mana.getBlack(), mana.getRed(),
                    mana.getGreen(), mana.getColorless(), mana.getGeneric(), mana.getAny()));
        }
        return new Observation(getId().toString(), commanderName(game), String.valueOf(game.getTurnStepType()),
                game.isActivePlayer(getId()), game.getTurnNum(), ownHand(game), seats, stack, manaOptions);
    }

    private CardView view(Card card, Game game) {
        boolean hidden = card.isFaceDown(game);
        return new CardView(card.getId().toString(), hidden ? "Face-down card" : card.getName(),
                hidden ? 0 : card.getManaValue(), card.isLand(game), card.isCreature(game),
                card.getPower().getValue(), card.getToughness().getValue(), card instanceof Permanent && ((Permanent) card).isTapped(),
                hidden ? "" : String.join(" ", card.getRules(game)),
                (card instanceof Permanent ? ((Permanent) card).getControllerId() : card.getOwnerId()).toString(),
                Collections.<String>emptyList(), card instanceof Permanent && ((Permanent) card).isAttacking(), publicSubtypes(card, game, hidden));
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

    @Override protected boolean chooseCreatureType(Outcome outcome, Choice choice, Game game) {
        for (Card card : getHand().getCards(game)) {
            if (card.isCreature(game) && !card.getSubtype(game).isEmpty()) {
                String type = card.getSubtype(game).get(0).toString();
                if (choice.getChoices().contains(type)) { choice.setChoice(type); return true; }
            }
        }
        return false;
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
