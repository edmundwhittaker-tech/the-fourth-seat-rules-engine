package fourthseat.ai;

import java.util.*;
import fourthseat.ai.PolicyModel.*;

/** Effect-outcome gate for non-cast actions. Card value is not the value of every ability on that card. */
public final class ActivationPolicy {
    private ActivationPolicy() {}
    /** null means a spell/land should use its deck casting plan; other actions receive a separate score. */
    public static Double score(Observation o,Action a) {
        if(a.kind==ActionKind.SPELL||a.kind==ActionKind.LAND) return null;
        CardView source=null;Map<String,CardView> own=new HashMap<String,CardView>();
        for(CardView c:o.self().battlefield){own.put(c.id,c);if(c.id.equals(a.sourceId))source=c;}
        String step=o.step.toUpperCase(Locale.ROOT);
        boolean beforeAttack=o.active&&(step.equals("PRECOMBAT_MAIN")||step.equals("BEGIN_COMBAT"));
        boolean duringBlocks=step.equals("DECLARE_BLOCKERS");
        int incoming=0;for(Seat s:o.seats)if(!s.id.equals(o.selfId))for(CardView c:s.battlefield)if(c.attacking)incoming+=Math.max(0,c.power);
        boolean defense=!o.active&&step.equals("DECLARE_ATTACKERS")&&incoming>=o.self().life;
        if(a.kind==ActionKind.EQUIP) {
            double best=-1000;
            for(String id:a.targetIds){CardView c=own.get(id);if(c!=null&&c.creature)best=Math.max(best,3+Math.max(0,c.power)*.5+(c.name.equals(o.commander)?6:0));}
            return best;
        }
        if(a.outcomes.contains("BecomeCreature")) {
            if(!beforeAttack&&!defense)return -1000.0;
            List<CardView> targets=new ArrayList<CardView>();
            if(a.targetIds.isEmpty()&&source!=null)targets.add(source);
            for(String id:a.targetIds)if(own.containsKey(id))targets.add(own.get(id));
            for(CardView c:targets) {
                if(!c.land||c.creature||c.tapped)continue;
                // Kamahl explicitly grants haste; generic animation does not bypass summoning sickness.
                if(beforeAttack&&c.summoningSick&&!a.sourceName.equals("Kamahl, Heart of Krosa"))continue;
                return defense?18.0:7.0;
            }
            return -1000.0;
        }
        if(a.outcomes.contains("BoostCreature")) {
            if(!beforeAttack&&!duringBlocks)return -1000.0;
            boolean useful=false;
            for(CardView c:own.values())if(c.creature&&(!c.tapped||c.attacking)&&(!c.summoningSick||c.attacking))useful=true;
            return useful?9.0:-1000.0;
        }
        if(a.outcomes.contains("PutLandInPlay"))return 15.0;
        if(a.outcomes.contains("PutCreatureInPlay")) {
            int goblins=0;for(CardView c:own.values())if(c.subtypes.contains("Goblin"))goblins++;
            return a.sourceName.equals("Krenko, Mob Boss")?18.0+goblins:12.0;
        }
        if(a.outcomes.contains("DrawCard"))return o.hand.size()<6?10.0:2.0;
        if(a.outcomes.contains("Untap")) {
            for(String id:a.targetIds){CardView c=own.get(id);if(c!=null&&c.tapped)return 9.0;}
            return -1000.0;
        }
        if(a.sourceName.equals("Lathril, Blade of the Elves")&&(a.outcomes.contains("LoseLife")||a.outcomes.contains("Damage")))return 50.0;
        if(a.sourceName.equals("Goblin Bombardment")&&a.outcomes.contains("Damage")) {
            int fodder=0;for(CardView c:own.values())if(c.creature&&!c.name.equals(o.commander))fodder++;
            for(Seat s:o.seats)if(!s.id.equals(o.selfId)&&a.targetIds.contains(s.id)&&s.life>0&&fodder>=s.life)return 40.0;
            return -1000.0;
        }
        if(a.outcomes.contains("Protect")||a.outcomes.contains("Regenerate")||a.outcomes.contains("PreventDamage")) {
            for(CardView spell:o.stack)if(!spell.controllerId.equals(o.selfId))for(String id:spell.targetIds)if(own.containsKey(id))return 15.0;
            return -1000.0;
        }
        // Unsupported activated semantics are a deliberate pass, not an invitation to spend all mana.
        return -1000.0;
    }
}
