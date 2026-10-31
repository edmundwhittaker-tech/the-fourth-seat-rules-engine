package mage.player.ai;

import java.util.*;
import mage.abilities.Ability;
import mage.abilities.ActivatedAbility;
import mage.abilities.Mode;
import mage.abilities.costs.Cost;
import mage.abilities.costs.common.TapTargetCost;
import mage.abilities.costs.common.TapSourceCost;
import mage.constants.Zone;
import mage.game.permanent.Permanent;
import mage.target.Target;
import mage.abilities.costs.mana.ManaCost;
import mage.abilities.keyword.OffspringAbility;
import mage.cards.Card;
import mage.game.Game;

/** Preparation of exact engine costs on disposable copies only. Does not dispatch an
 * ability, pay costs, infer costs from card text, or mutate the live spell.
 */
public final class FourthSeatAdditionalCostPolicy {
    private FourthSeatAdditionalCostPolicy() {}

    /** For pre-activation candidates only: mirrors AbilityImpl's selected-mode cost step.
     * Includes Spree's selected costs before affordability scoring. Pass the original
     * unprepared candidate once, not a copy whose selected mode costs were already applied.
     * The returned copy is for affordability checks only, never dispatch it: the engine
     * applies these costs itself during actual activation.
     */
    public static ActivatedAbility prepareModeCost(ActivatedAbility candidate) {
        ActivatedAbility prepared=candidate.copy();
        for(UUID id:prepared.getModes().getSelectedModes()) {
            Mode mode=prepared.getModes().get(id);
            if(mode==null) continue;
            Cost cost=mode.getCost();
            if(cost instanceof ManaCost) prepared.addManaCostsToPay((ManaCost)cost.copy());
            else if(cost!=null) prepared.getCosts().add(cost.copy());
        }
        return prepared;
    }

    public static final class PreparedCosts {
        public final ActivatedAbility ability;
        public final Set<UUID> reservedTapIds;
        public final boolean distinctTapCostsPayable;
        private PreparedCosts(ActivatedAbility ability,Set<UUID> ids,boolean payable) {
            this.ability=ability;this.reservedTapIds=Collections.unmodifiableSet(new LinkedHashSet<UUID>(ids));
            this.distinctTapCostsPayable=payable;
        }
    }

    /** Complete selected-mode cost preparation, including typed continuous effects such
     * as Escalate. Invoke once on an unprepared candidate and disposable legality snapshot.
     * Does not pay or tap anything. Other cost types still require the engine's canPay.
     * The caller must reserve returned IDs during BOTH mana feasibility and actual mana
     * payment, since engine mana payment happens before TapTargetCost payment.
     * Matching is bounded to 64 required taps and 256 legal candidate bodies per cost.
     * Exceeding those bounds can conservatively reject a payable unusual combination.
     */
    public static PreparedCosts prepareSelectedCosts(ActivatedAbility candidate,Game snapshot) {
        ActivatedAbility prepared=prepareModeCost(candidate);
        prepared.adjustX(snapshot);
        snapshot.getContinuousEffects().costModification(prepared,snapshot);
        return reserveTapCosts(prepared,snapshot);
    }

    /** Sephara's native alternative has a simultaneous mana/tap requirement.
     * Unwrap and copy its public typed costs; do not activate the shared native
     * AlternativeCost objects during forecasting. The engine applies the chosen
     * alternative itself during the live cast.
     */
    public static PreparedCosts prepareSepharaAlternative(ActivatedAbility candidate,Game snapshot) {
        Card card=snapshot.getCard(candidate.getSourceId());
        if(candidate.getAbilityType()!=mage.constants.AbilityType.SPELL
                || !(card instanceof mage.cards.s.SepharaSkysBlade))return null;
        for(Ability ability:card.getAbilities(snapshot))
            if(ability.getClass()==mage.abilities.costs.AlternativeCostSourceAbility.class) {
                mage.abilities.costs.AlternativeCostSourceAbility alternative=(mage.abilities.costs.AlternativeCostSourceAbility)ability;
                if(snapshot.getState().getZone(candidate.getSourceId())!=Zone.STACK && candidate instanceof mage.abilities.SpellAbility) {
                    Set<mage.MageIdentifier> allowed=((mage.abilities.SpellAbility)candidate).spellCanBeActivatedNow(candidate.getControllerId(),snapshot);
                    if(!allowed.contains(mage.MageIdentifier.Default)&&!allowed.contains(alternative.getIdentifier()))continue;
                }
                if(alternative.getDynamicCost()!=null||!alternative.isAvailable(candidate,snapshot)
                        ||!alternative.canActivateAlternativeCostsNow(candidate,snapshot))continue;
                ActivatedAbility copy=candidate.copy();copy.clearManaCostsToPay();
                for(Cost wrapper:alternative.getCosts()) {
                    if(!(wrapper instanceof mage.abilities.costs.AlternativeCost))return null;
                    Cost cost=((mage.abilities.costs.AlternativeCost)wrapper).getCost().copy();
                    if(cost instanceof ManaCost)copy.addManaCostsToPay((ManaCost)cost);else copy.addCost(cost);
                }
                return prepareSelectedCosts(copy,snapshot);
            }
        return null;
    }

    /** Can also validate already-adjusted costs, without adding mode/effect costs twice. */
    public static PreparedCosts reserveTapCosts(ActivatedAbility prepared,Game snapshot) {
        Set<UUID> reserved=new LinkedHashSet<UUID>();
        for(Cost cost:prepared.getCosts()) if(!cost.isPaid()&&cost instanceof TapSourceCost)
            reserved.add(prepared.getSourceId());
        List<List<UUID>> slots=new ArrayList<List<UUID>>();
        Map<UUID,Boolean> manaProducer=new HashMap<UUID,Boolean>();
        for(Cost cost:prepared.getCosts()) if(!cost.isPaid()&&cost instanceof TapTargetCost) {
            Target target=((TapTargetCost)cost).getTarget();
            int required=target.getMinNumberOfTargets();
            if(required<0||required>64-slots.size()) return new PreparedCosts(prepared,reserved,false);
            Set<UUID> choices=new HashSet<UUID>(target.possibleTargets(prepared.getControllerId(),prepared,snapshot));
            choices.addAll(target.getTargets());
            List<UUID> legal=new ArrayList<UUID>();
            for(UUID id:choices) {
                Permanent p=snapshot.getPermanent(id);
                if(p==null||p.isTapped()||reserved.contains(id)
                        ||!target.canTarget(prepared.getControllerId(),id,prepared,snapshot)) continue;
                legal.add(id);
                if(!manaProducer.containsKey(id)) manaProducer.put(id,
                        !p.getAbilities(snapshot).getAvailableActivatedManaAbilities(Zone.BATTLEFIELD,prepared.getControllerId(),snapshot).isEmpty());
            }
            Collections.sort(legal,Comparator.comparing((UUID id)->manaProducer.get(id)).thenComparing(UUID::toString));
            if(legal.size()>256) legal=new ArrayList<UUID>(legal.subList(0,256));
            if(legal.size()<required) return new PreparedCosts(prepared,reserved,false);
            for(int i=0;i<required;i++) slots.add(legal);
        }
        Map<UUID,Integer> assigned=new LinkedHashMap<UUID,Integer>();
        for(int i=0;i<slots.size();i++) if(!assignDistinct(i,slots,assigned,new HashSet<UUID>()))
            return new PreparedCosts(prepared,reserved,false);
        List<UUID> ordered=new ArrayList<UUID>(assigned.keySet());Collections.sort(ordered,Comparator.comparing(UUID::toString));
        reserved.addAll(ordered);
        return new PreparedCosts(prepared,reserved,true);
    }

    private static boolean assignDistinct(int slot,List<List<UUID>> slots,Map<UUID,Integer> assigned,Set<UUID> visited) {
        for(UUID id:slots.get(slot)) {
            if(!visited.add(id)) continue;
            Integer previous=assigned.get(id);
            if(previous==null||assignDistinct(previous,slots,assigned,visited)) {assigned.put(id,slot);return true;}
        }
        return false;
    }

    /** Probe Offspring with the engine's actual typed optional-cost object.
     * Caller MUST supply a disposable legality snapshot, with that snapshot's player
     * configured to answer yes to the single Offspring probe. Never pass the live game.
     * The caller owns setting/restoring its transient probe flag in a finally block.
     * Only an OffspringAbility copy is invoked; its protected cost is neither reflected
     * nor parsed. The source card/ability and real game's costs/tags remain unchanged.
     * Returns null if no Offspring ability is present or the engine does not add its cost.
     */
    public static ActivatedAbility prepareOffspringCost(ActivatedAbility source,Game snapshot) {
        Card card=source.getSourceCardIfItStillExists(snapshot);
        if(card==null) return null;
        for(Ability ability:card.getAbilities(snapshot)) if(ability instanceof OffspringAbility) {
            ActivatedAbility prepared=prepareModeCost(source);
            OffspringAbility offspring=((OffspringAbility)ability).copy();
            offspring.addOptionalAdditionalCosts(prepared,snapshot);
            if(prepared.getCostsTagMap()==null
                    ||!prepared.getCostsTagMap().containsKey(OffspringAbility.OFFSPRING_ACTIVATION_VALUE_KEY)) return null;
            return prepared;
        }
        return null;
    }

    /** Typed check used by the caller to scope its optional-cost prompt handling. */
    public static boolean hasOffspring(Ability source,Game game) {
        if(source==null) return false;
        Card card=source.getSourceCardIfItStillExists(game);
        if(card==null) return false;
        for(Ability ability:card.getAbilities(game)) if(ability instanceof OffspringAbility) return true;
        return false;
    }
}
