package mage.player.ai;

import mage.cards.Card;
import mage.constants.*;
import mage.game.permanent.Permanent;
import mage.game.turn.*;
import static mage.player.ai.FourthSeatRealGameTest.check;
import static mage.player.ai.RecoveryChecks.*;

/** Strategy checks prompted by Talrand games that completed without developing. */
public final class FourthSeatSpellTimingTest {
    public static void main(String[] args)throws Exception {
        for(boolean drawStep:new boolean[]{false,true}) run("Save upkeep/draw mana for the commander: "+drawStep,()->{
            FourthSeatRealGameTest.Fixture f=fixture("t.TalrandSkySummoner","Talrand, Sky Summoner");
            for(int i=0;i<4;i++)f.board("basiclands.Island","Island");
            Card draw=f.hand("t.ThinkTwice","Think Twice");
            BeginningPhase beginning=new BeginningPhase();beginning.setStep(drawStep?new DrawStep():new UpkeepStep());f.game.getTurn().setPhase(beginning);
            check(!f.bot.priority(f.game)&&f.game.getStack().isEmpty(),"Spent commander mana on routine pre-main draw");
            PreCombatMainPhase main=new PreCombatMainPhase();main.setStep(new PreCombatMainStep());f.game.getTurn().setPhase(main);f.bot.resetPassed();
            check(f.bot.priority(f.game),"No main-phase play");
            check(f.game.getStack().getLast().getSourceId().equals(f.bot.getCommandersIds().iterator().next()),"Did not prioritize funded Talrand");
            check(f.bot.getHand().contains(draw.getId())&&f.game.rollbackCount==0,"Routine draw or failed payment consumed mana");
            drain(f,10);
        });
        for(boolean threat:new boolean[]{false,true}) run("Upkeep draw remains usable against a threat="+threat,()->{
            FourthSeatRealGameTest.Fixture f=fixture("t.TalrandSkySummoner","Talrand, Sky Summoner");
            Card draw=f.hand("t.ThinkTwice","Think Twice");for(int i=0;i<2;i++)f.board("basiclands.Island","Island");
            if(threat){Card shock=f.enemyHand("s.Shock","Shock");shock.getSpellAbility().getTargets().get(0).addTarget(f.bot.getId(),shock.getSpellAbility(),f.game);f.castEnemy(shock);f.game.getState().setActivePlayerId(f.bot.getId());}
            BeginningPhase phase=new BeginningPhase();phase.setStep(new UpkeepStep());f.game.getTurn().setPhase(phase);
            check(f.bot.priority(f.game)==threat,"Wrong response-to-threat boundary");
            check(f.game.getState().getZone(draw.getId())==(threat?Zone.STACK:Zone.HAND),"Wrong actual draw zone");
        });
        for(String name:new String[]{"Into the Roil","Blink of an Eye"})for(boolean enemy:new boolean[]{false,true})run("Avoid unneeded self-bounce: "+name+" enemy="+enemy,()->{
            FourthSeatRealGameTest.Fixture f=fixture("t.TalrandSkySummoner","Talrand, Sky Summoner");
            f.game.getCard(f.bot.getCommandersIds().iterator().next()).moveToZone(Zone.EXILED,null,f.game,false);
            Permanent own=f.board("s.SolRing","Sol Ring");Permanent target=enemy?f.enemyBoard(0,"s.SolRing","Sol Ring"):null;
            Card bounce=f.hand(name.equals("Into the Roil")?"i.IntoTheRoil":"b.BlinkOfAnEye",name);f.mana();
            check(f.bot.priority(f.game)==enemy,"Cast bounce without a useful hostile target");
            if(enemy){check(f.game.getStack().getLast().getStackAbility().getFirstTarget().equals(target.getId()),"Bounced own permanent instead of opposing threat");drain(f,10);check(f.game.getPermanent(target.getId())==null,"Native bounce failed");}
            check(f.game.getPermanent(own.getId())!=null&&f.game.rollbackCount==0,"Lost own mana rock or rolled back");
        });
        finish("SPELL_TIMING");
    }
}
