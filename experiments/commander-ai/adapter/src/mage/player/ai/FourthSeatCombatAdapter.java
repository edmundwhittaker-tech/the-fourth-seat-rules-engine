package mage.player.ai;

import fourthseat.ai.CombatPolicy;
import java.util.*;
import mage.abilities.Ability;
import mage.abilities.ActivatedAbility;
import mage.abilities.common.AttacksTriggeredAbility;
import mage.abilities.effects.Effect;
import mage.abilities.effects.common.continuous.BoostControlledEffect;
import mage.abilities.effects.common.continuous.GainAbilityControlledEffect;
import mage.abilities.costs.Cost;
import mage.abilities.costs.common.TapSourceCost;
import mage.constants.SubType;
import mage.abilities.keyword.VigilanceAbility;
import mage.abilities.keyword.TrampleAbility;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.players.Player;

/** Opt-in static public-board combat bridge. No cloned game, rollout, private zones,
 * registrations or defaults. Group requirements/costs remain subject to engine validation.
 * Planeswalker/battle attacks and complex simultaneous combat requirements are not solved.
 */
public final class FourthSeatCombatAdapter {
    private FourthSeatCombatAdapter() {}
    private static List<Permanent> creatures(Game game,UUID playerId) {
        List<Permanent> result=new ArrayList<Permanent>();
        for(Permanent p:game.getBattlefield().getAllActivePermanents(playerId)) if(p.isCreature(game)) result.add(p);
        Collections.sort(result,Comparator.comparing(p->p.getId().toString()));return result;
    }
    private static String publicName(Permanent p,Game game) {return p.isFaceDown(game)?"Face-down creature":p.getName();}
    /** Modified or tap-engine creatures are never grouped with surplus tokens, even
     * when their displayed power/toughness happens to match. Individually sampled
     * modified creatures may still attack if the engine permits it. */
    private static String publicCombatKey(Permanent p,Game game) {
        boolean unique=p.isTapped()||p.hasSummoningSickness()||p.isFaceDown(game)
                ||!p.getAttachments().isEmpty()||!p.getCounters(game).isEmpty()||p.getDamage()>0;
        for(Ability ability:p.getAbilities()) if(ability instanceof ActivatedAbility)
            for(Cost cost:ability.getCosts()) if(cost instanceof TapSourceCost) unique=true;
        return unique?"individual:"+p.getId():String.join(" ",p.getRules(game));
    }
    private static boolean goreclawAttackTrigger(Permanent p,Game game) {
        if(!publicName(p,game).equals("Goreclaw, Terror of Qal Sisma")) return false;
        for(Ability ability:p.getAbilities(game)) if(ability instanceof AttacksTriggeredAbility) {
            boolean boost=false,gain=false;
            for(Effect effect:ability.getEffects()) {boost|=effect instanceof BoostControlledEffect;gain|=effect instanceof GainAbilityControlledEffect;}
            if(boost&&gain) return true;
        }
        return false;
    }
    private static boolean usableTapAbility(Permanent p,Game game) {
        for(Ability a:p.getAbilities()) if(a instanceof ActivatedAbility) {
            boolean tap=false;for(Cost c:a.getCosts()) if(c instanceof TapSourceCost) tap=true;
            if(tap&&((ActivatedAbility)a).canActivate(p.getControllerId(),game).canActivate()) return true;
        }
        return false;
    }
    /** Reserve an actually usable Lathril activation, using real subtype and tap legality.
     * Lathril pays her own tap cost plus ten OTHER untapped Elves. */
    private static Map<UUID,Double> tapCosts(List<Permanent> own,Game game) {
        Map<UUID,Double> result=new HashMap<UUID,Double>();
        int goblins=0;for(Permanent p:own) if(p.hasSubtype(SubType.GOBLIN,game)) goblins++;
        for(Permanent p:own) {
            String name=publicName(p,game);if(!usableTapAbility(p,game)) continue;
            if(name.equals("Krenko, Mob Boss")) result.put(p.getId(),Math.max(6,Math.min(24,goblins*2.0)));
            if(name.equals("Giada, Font of Hope")) result.put(p.getId(),3.0);
            if(name.equals("Lathril, Blade of the Elves")) {
                List<Permanent> elves=new ArrayList<Permanent>();
                for(Permanent elf:own) if(!elf.getId().equals(p.getId())&&!elf.isTapped()&&elf.hasSubtype(SubType.ELF,game)) elves.add(elf);
                Collections.sort(elves,Comparator.comparingDouble(FourthSeatCombatAdapter::value).thenComparing(e->e.getId().toString()));
                if(elves.size()>=10) {
                    result.put(p.getId(),1000.0);
                    for(Permanent elf:elves.subList(0,10)) result.put(elf.getId(),1000.0);
                }
            }
        }
        return result;
    }
    private static double value(Permanent p) {return Math.max(1,p.getManaValue())+Math.max(0,p.getPower().getValue())*0.3;}
    public static void selectAttackers(ComputerPlayerFourthSeat player,Game game,UUID attackingPlayerId) {
        // An effect granting control of another player's combat is not modeled here.
        if(!player.getId().equals(attackingPlayerId)) return;
        List<Permanent> own=creatures(game,attackingPlayerId);
        List<UUID> opponents=new ArrayList<UUID>(game.getOpponents(attackingPlayerId,true));
        Collections.sort(opponents,Comparator.comparing(UUID::toString));
        List<CombatPolicy.Attacker> attackers=new ArrayList<CombatPolicy.Attacker>();
        Map<UUID,Double> tapCosts=tapCosts(own,game);
        for(Permanent p:own) {
            Set<String> legal=new HashSet<String>();
            for(UUID id:opponents) if(p.canAttack(id,game)) legal.add(id.toString());
            attackers.add(new CombatPolicy.Attacker(p.getId().toString(),publicName(p,game),p.getPower().getValue(),
                    p.isTapped()?0:p.getToughness().getValue(),value(p)+(tapCosts.containsKey(p.getId())?6:0),p.hasAbility(VigilanceAbility.getInstance(),game),legal,tapCosts.getOrDefault(p.getId(),0.0),publicCombatKey(p,game),p.hasAbility(TrampleAbility.getInstance(),game),p.getMinBlockedBy(),goreclawAttackTrigger(p,game)));
        }
        List<CombatPolicy.Defender> defenders=new ArrayList<CombatPolicy.Defender>();
        for(UUID id:opponents) {
            Player opponent=game.getPlayer(id);if(opponent==null) continue;
            List<CombatPolicy.Blocker> blockers=new ArrayList<CombatPolicy.Blocker>();int crackback=0;
            for(Permanent p:creatures(game,id)) {
                // Include tapped enemy creatures: they can untap before their next attack.
                crackback+=Math.max(0,p.getPower().getValue());Set<String> canBlock=new HashSet<String>();
                for(Permanent a:own) if(p.canBlock(a.getId(),game)) canBlock.add(a.getId().toString());
                if(!canBlock.isEmpty()) blockers.add(new CombatPolicy.Blocker(p.getId().toString(),p.getPower().getValue(),p.getToughness().getValue(),canBlock,p.getMaxBlocks()));
            }
            defenders.add(new CombatPolicy.Defender(id.toString(),opponent.getLife(),blockers,crackback));
        }
        CombatPolicy.Plan plan=CombatPolicy.plan(player.commanderName(game),player.getLife(),attackers,defenders);
        for(Map.Entry<String,String> e:plan.attacks.entrySet()) {
            UUID attackerId=UUID.fromString(e.getKey()), defenderId=UUID.fromString(e.getValue());Permanent p=game.getPermanent(attackerId);
            if(p!=null&&p.canAttack(defenderId,game)&&game.getCombat().canDefenderBeAttacked(attackerId,defenderId,game)) {
                player.declareAttacker(attackerId,defenderId,game,false);
            }
        }
    }
    /** Bounded greedy blocking, not a combat solver. Honors current legal edges and min/max
     * blocker counts. Does not assume flying creatures can be blocked by ground creatures.
     * First/double strike, deathtouch, protection and damage prevention affect quality;
     * engine validates legality and resolves all actual damage.
     */
    public static void selectBlockers(ComputerPlayerFourthSeat player,Ability source,Game game,UUID defendingPlayerId) {
        if(!player.getId().equals(defendingPlayerId)) return;
        List<Permanent> threats=new ArrayList<Permanent>();int incoming=0;
        for(UUID id:game.getCombat().getAttackers()) {
            if(!defendingPlayerId.equals(game.getCombat().getDefendingPlayerId(id,game))) continue;
            Permanent p=game.getPermanent(id);if(p!=null) {threats.add(p);incoming+=Math.max(0,p.getPower().getValue());}
        }
        Collections.sort(threats,Comparator.comparingInt((Permanent p)->p.getPower().getValue()).reversed().thenComparing(p->p.getId().toString()));
        List<Permanent> available=creatures(game,defendingPlayerId);Set<UUID> used=new HashSet<UUID>(game.getCombat().getBlockers());
        int examined=0;
        for(Permanent attacker:threats) {
            if(examined++>=32) break;
            List<Permanent> legal=new ArrayList<Permanent>();
            for(Permanent b:available) if(!used.contains(b.getId())&&b.canBlock(attacker.getId(),game)
                    &&game.getCombat().findGroup(attacker.getId()).canBlock(b,game)) legal.add(b);
            Collections.sort(legal,Comparator.comparingDouble(FourthSeatCombatAdapter::value).thenComparing(p->p.getId().toString()));
            int required=Math.max(1,attacker.getMinBlockedBy());
            if(required>32||required>attacker.getMaxBlockedBy()||legal.size()<required) continue;
            List<Permanent> selected=new ArrayList<Permanent>(legal.subList(0,required));
            double lostValue=0;int toughness=0,power=0;
            for(Permanent b:selected) {toughness+=Math.max(0,b.getToughness().getValue());power+=Math.max(0,b.getPower().getValue());if(b.getToughness().getValue()<=attacker.getPower().getValue()) lostValue+=value(b);}
            int prevented=attacker.hasAbility(TrampleAbility.getInstance(),game)?Math.min(Math.max(0,attacker.getPower().getValue()),toughness):Math.max(0,attacker.getPower().getValue());
            boolean urgent=incoming>=player.getLife();
            boolean favorable=lostValue==0||(power>=attacker.getToughness().getValue()&&lostValue<=value(attacker))||prevented>lostValue*2;
            if(!urgent&&!favorable) continue;
            int declared=0;
            for(Permanent blocker:selected) if(blocker.canBlock(attacker.getId(),game)) {
                player.declareBlocker(defendingPlayerId,blocker.getId(),attacker.getId(),game,false);
                if(game.getCombat().getBlockers().contains(blocker.getId())) {used.add(blocker.getId());declared++;}
            }
            if(declared==required) incoming=Math.max(0,incoming-prevented);
        }
    }
}
