package mage.player.ai;

import mage.abilities.Ability;
import mage.cards.Card;
import mage.constants.*;
import mage.counters.CounterType;
import mage.game.permanent.Permanent;
import mage.game.permanent.PermanentCard;
import static mage.player.ai.FourthSeatRealGameTest.check;
import static mage.player.ai.RecoveryChecks.*;

/** Regressions from full-game activity audits, using native cards and choices. */
public final class FourthSeatActivityTest {
    static FourthSeatRealGameTest.Fixture giant(boolean ready, int lands, boolean blue) throws Exception {
        FourthSeatRealGameTest.Fixture f=fixture("a.AesiTyrantOfGyreStrait","Aesi, Tyrant of Gyre Strait");
        Card card=f.card("m.MossbornHydra","Mossborn Hydra");
        PermanentCard hydra=new PermanentCard(card,f.bot.getId(),f.game);
        f.game.addPermanent(hydra,0);f.game.getState().setZone(card.getId(),Zone.BATTLEFIELD);
        if(ready)hydra.removeSummoningSickness();
        hydra.addCounters(CounterType.P1P1.createInstance(512),hydra.getAbilities().get(0),f.game);
        for(int i=0;i<lands;i++) f.board(blue&&i<2?"basiclands.Island":"basiclands.Forest",blue&&i<2?"Island":"Forest");
        f.hand("c.CurseOfTheSwine","Curse of the Swine");
        f.hand("c.CollectiveResistance","Collective Resistance");
        f.hand("r.RoilingRegrowth","Roiling Regrowth");
        f.hand("basiclands.Forest","Forest");f.hand("basiclands.Forest","Forest");
        f.game.applyEffects();return f;
    }
    public static void main(String[] args)throws Exception {
        for(boolean ready:new boolean[]{false,true}) run("Large trample threat: readiness="+ready,()->{
            FourthSeatRealGameTest.Fixture f=giant(ready,8,true);
            check(flag(f,"combatBeforeDevelopment")==ready,"Future power confused with attack readiness");
            if(ready){Card ashaya=f.hand("a.AshayaSoulOfTheWild","Ashaya, Soul of the Wild");
                f.bot.priority(f.game);check(f.bot.getHand().contains(ashaya.getId()),"Expanded growth before ready combat");
                f.combat();f.bot.selectAttackers(f.game,f.bot.getId());
                check(!f.game.getCombat().getAttackers().isEmpty(),"Huge ready trample creature failed to attack");}
        });
        for(String mode:new String[]{"funded","short","wrong-colour","removed"}) run("Complex hand growth reserve: "+mode,()->{
            FourthSeatRealGameTest.Fixture f=giant(false,mode.equals("short")?11:12,!mode.equals("wrong-colour"));
            if(mode.equals("removed")) for(Permanent p:f.game.getBattlefield().getAllActivePermanents(f.bot.getId()))
                if(p.getName().equals("Mossborn Hydra"))p.moveToZone(Zone.GRAVEYARD,null,f.game,false);
            check(flag(f,"reserveGrowthSaturated")==mode.equals("funded"),"Wrong resource/threat reserve boundary");
        });
        for(boolean saturated:new boolean[]{false,true}) run("Native Burgeoning optional land with saturated="+saturated,()->{
            FourthSeatRealGameTest.Fixture f=giant(false,saturated?12:4,true);
            Permanent burgeoning=f.board("b.Burgeoning","Burgeoning");
            Ability trigger=burgeoning.getAbilities(f.game).stream().filter(a->a instanceof mage.abilities.TriggeredAbility).findFirst().get();
            int before=f.bot.getHand().size();
            trigger.getEffects().get(0).apply(f.game,trigger);
            check(f.bot.getHand().size()==before-(saturated?0:1),"Wrong real optional land choice");
        });
        run("Reserve keeps native instant protection available",()->{
            FourthSeatRealGameTest.Fixture f=giant(false,12,true);
            Card protect=f.hand("h.HeroicIntervention","Heroic Intervention");
            Permanent hydra=f.game.getBattlefield().getAllActivePermanents(f.bot.getId()).stream().filter(p->p.getName().equals("Mossborn Hydra")).findFirst().get();
            Card murder=f.enemyHand("m.Murder","Murder");murder.getSpellAbility().getTargets().get(0).addTarget(hydra.getId(),murder.getSpellAbility(),f.game);f.castEnemy(murder);
            cast(f,protect,1);drain(f,20);check(f.game.getPermanent(hydra.getId())!=null,"Growth restraint blocked protection");
        });
        run("Eliminated seat skips queued priority callback",()->{
            FourthSeatRealGameTest.Fixture f=giant(false,12,true);f.bot.leave();
            check(!f.bot.priority(f.game)&&f.bot.observationBuildCount==0,"Eliminated player continued strategic work");
        });
        for(int count:new int[]{32,127,128}) run("Huge funded Scute army can stop ramp with a short hand: "+count,()->{
            FourthSeatRealGameTest.Fixture f=FourthSeatRecoveredGrowthTest.army(count,false,false,true,8,2);
            check(flag(f,"reserveGrowthSaturated")== (count>=128),"Wrong short-hand growth boundary");
        });
        finish("ACTIVITY");
    }
}
