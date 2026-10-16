package mage.player.ai;

import java.util.*;
import mage.Mana;
import mage.ConditionalMana;
import mage.abilities.Ability;
import mage.abilities.costs.mana.ManaCost;
import mage.abilities.costs.mana.VariableManaCost;
import mage.abilities.effects.Effect;
import mage.abilities.effects.mana.*;
import mage.abilities.mana.ActivatedManaAbilityImpl;
import mage.abilities.mana.ManaOptions;
import mage.constants.Zone;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.players.Player;
import mage.target.Target;
import mage.target.TargetPermanent;
import mage.constants.Outcome;
import mage.target.targetadjustment.XTargetsCountAdjuster;

/** Isolated announcement helper, not an auto-payment implementation. Engine resolves payment.
 * Uses actual unpaid cost alternatives and actual X multiplicity, including miracle costs.
 * ManaOptions already includes floating mana: never add the pool again.
 * Engine availability internally uses its normal playable-calculation snapshot, not AI rollouts.
 * Restricted mana is conservatively subtracted because engine ManaOptions loses its conditions.
 * Exotic mana replacement/reduction/filter costs remain engine-validated, not proved here.
 */
public final class FourthSeatVariableManaPolicy {
    private FourthSeatVariableManaPolicy() {}
    private static final int OPTION_LIMIT=256;
    public static int chooseX(int min,int max,Ability source,Game game,Player player) {
        if(min>=max) return min;
        if(source==null||player==null) return min;
        int instances=0;
        for(ManaCost cost:source.getManaCostsToPay()) if(cost instanceof VariableManaCost) {
            VariableManaCost variable=(VariableManaCost)cost;
            // Colored/filtered X and nonstandard X payments cannot be treated as generic.
            if(variable.getFilter()!=null&&!variable.getFilter().isGeneric()) return min;
            instances+=variable.getXInstancesCount();
        }
        if(instances<=0) return min;
        int cap=Math.min(max,permanentTargetCap(source,game,player,instances));
        if(cap<min) return min; // API requires the engine-declared minimum; it may reject payment.
        ManaOptions available=player.getManaAvailable(game);
        Mana restricted=restrictedManaUpperBound(player,game);
        ManaOptions fixed=source.getManaCostsToPay().getUnpaid().getOptions(false);
        if(fixed.isEmpty()) fixed.add(new Mana());
        int result=min, examined=0;
        for(Mana option:available) {
            if(examined++>=OPTION_LIMIT) break;
            Mana spendable=option.copy();subtractClamped(spendable,restricted);
            int fixedExamined=0;
            for(Mana cost:fixed) {
                if(fixedExamined++>=OPTION_LIMIT) break;
                if(!cost.enough(spendable)) continue;
                long spare=(long)spendable.count()-cost.count();
                int candidate=(int)Math.min((long)cap,Math.max(0L,spare/instances));
                if(candidate<min) continue;
                Mana total=cost.copy();total.add(Mana.GenericMana(candidate*instances));
                // ManaCost.testPay tests whether ANY component can consume mana, not affordability.
                if(total.enough(spendable)) result=Math.max(result,candidate);
            }
        }
        return Math.min(max,Math.max(min,result));
    }
    private static int permanentTargetCap(Ability source,Game game,Player player,int instances) {
        if(!(source.getTargetAdjuster() instanceof XTargetsCountAdjuster)) return Integer.MAX_VALUE;
        // The engine can clear targets while previewing X=0. Recreate the typed blueprint
        // on an ability copy only; never announce X or modify targets on the real spell.
        Ability probe=source.copy();probe.getManaCostsToPay().setX(1,instances);probe.setCostsTag("X",1);
        probe.getTargetAdjuster().adjustTargets(probe,game);
        if(probe.getTargets().size()!=1||!(probe.getTargets().get(0) instanceof TargetPermanent)) return Integer.MAX_VALUE;
        Target target=probe.getTargets().get(0);int count=0;
        Outcome intent=probe.getEffects().getOutcome(probe);
        Set<UUID> opponents=game.getOpponents(player.getId(),true);
        for(UUID id:target.possibleTargets(player.getId(),probe,game)) {
            Permanent permanent=game.getPermanent(id);
            if(permanent==null) continue;
            // Engine outcomes describe benefit/detriment to the TARGET. This supports
            // exact-X creature exile (Curse), artifacts (By Force), and beneficial own
            // permanent effects without selecting our own bodies merely to fill X.
            if(intent==Outcome.Neutral||intent.anyTargetHasSameValue()
                    ||(intent.isGood()?player.getId().equals(permanent.getControllerId())
                    :opponents.contains(permanent.getControllerId()))) count++;
        }
        return count;
    }
    private static Mana restrictedManaUpperBound(Player player,Game game) {
        Mana restricted=new Mana();
        for(ConditionalMana mana:player.getManaPool().getConditionalMana()) restricted.add(mana);
        // Public battlefield only. Only abilities the player can legally activate are counted.
        // Over-subtracting alternative outputs is intentional: safety over maximal X.
        for(Permanent p:game.getBattlefield().getAllActivePermanents()) {
            for(ActivatedManaAbilityImpl ability:p.getAbilities(game).getAvailableActivatedManaAbilities(Zone.BATTLEFIELD,player.getId(),game)) {
                boolean conditional=false;
                for(Effect effect:ability.getEffects()) {
                    if(effect instanceof AddConditionalManaEffect
                            ||effect instanceof AddConditionalManaOfAnyColorEffect
                            ||effect instanceof AddConditionalManaOfTwoDifferentColorsEffect
                            ||effect instanceof AddConditionalManaChosenColorEffect
                            ||effect instanceof AddConditionalColorlessManaEffect) conditional=true;
                }
                Mana maxima=new Mana();
                for(Mana output:ability.getNetMana(game)) if(conditional||output instanceof ConditionalMana) {
                    maximize(maxima,output);
                }
                restricted.add(maxima);
            }
        }
        return restricted;
    }
    private static void maximize(Mana mana,Mana output) {
        // Do not use Mana.get(COLORLESS): this engine method includes generic mana too.
        mana.setWhite(Math.max(mana.getWhite(),output.getWhite()));
        mana.setBlue(Math.max(mana.getBlue(),output.getBlue()));
        mana.setBlack(Math.max(mana.getBlack(),output.getBlack()));
        mana.setRed(Math.max(mana.getRed(),output.getRed()));
        mana.setGreen(Math.max(mana.getGreen(),output.getGreen()));
        mana.setColorless(Math.max(mana.getColorless(),output.getColorless()));
        mana.setGeneric(Math.max(mana.getGeneric(),output.getGeneric()));
        mana.setAny(Math.max(mana.getAny(),output.getAny()));
    }
    private static void subtractClamped(Mana mana,Mana restriction) {
        mana.setWhite(Math.max(0,mana.getWhite()-restriction.getWhite()));
        mana.setBlue(Math.max(0,mana.getBlue()-restriction.getBlue()));
        mana.setBlack(Math.max(0,mana.getBlack()-restriction.getBlack()));
        mana.setRed(Math.max(0,mana.getRed()-restriction.getRed()));
        mana.setGreen(Math.max(0,mana.getGreen()-restriction.getGreen()));
        mana.setColorless(Math.max(0,mana.getColorless()-restriction.getColorless()));
        mana.setGeneric(Math.max(0,mana.getGeneric()-restriction.getGeneric()));
        mana.setAny(Math.max(0,mana.getAny()-restriction.getAny()));
    }
}
