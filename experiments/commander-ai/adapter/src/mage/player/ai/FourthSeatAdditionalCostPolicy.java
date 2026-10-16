package mage.player.ai;

import java.util.UUID;
import mage.abilities.Ability;
import mage.abilities.ActivatedAbility;
import mage.abilities.Mode;
import mage.abilities.costs.Cost;
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
