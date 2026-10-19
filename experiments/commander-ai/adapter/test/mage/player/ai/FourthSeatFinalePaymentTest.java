package mage.player.ai;
import mage.cards.Card;import mage.constants.*;import mage.game.permanent.Permanent;
import static mage.player.ai.FourthSeatRealGameTest.check;import static mage.player.ai.RecoveryChecks.*;
public final class FourthSeatFinalePaymentTest {
 public static void main(String[]args)throws Exception {
  for(String mode:new String[]{"all-sources","missing-wastes","missing-cemetery"})run("Finale GG before generic X payment "+mode,()->{
   // Native producer sorting breaks ties by object identity. Exercise fresh
   // source identities so payment cannot rely on one accidental tie order.
   for(int trial=0;trial<(mode.equals("all-sources")?12:1);trial++){
    FourthSeatRealGameTest.Fixture f=fixture("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves");f.game.getCard(f.bot.getCommandersIds().iterator().next()).moveToZone(Zone.EXILED,null,f.game,false);
    for(String[] c:new String[][]{{"l.LlanowarWastes","Llanowar Wastes"},{"u.UnclaimedTerritory","Unclaimed Territory"},{"b.BojukaBog","Bojuka Bog"},{"w.WoodlandCemetery","Woodland Cemetery"}}){
     if(mode.equals("missing-wastes")&&c[0].startsWith("l.")||mode.equals("missing-cemetery")&&c[0].startsWith("w."))continue;
     Permanent p=f.board(c[0],c[1]);if(c[0].startsWith("u."))f.game.getState().setValue(p.getId()+"_type",SubType.ELF);
    }
    library(f,5);Card goal=f.card("e.ElvishVisionary","Elvish Visionary");f.bot.getLibrary().putOnTop(goal,f.game);
    Card card=f.hand("f.FinaleOfDevastation","Finale of Devastation");
    if(!mode.equals("all-sources")){reject(f,card);check(f.game.rollbackCount==0,"Restricted Territory incorrectly supplied a missing green pip");continue;}
    cast(f,card,1);check(f.game.rollbackCount==0,"Source ordering caused rollback");
    check(Integer.valueOf(2).equals(f.game.getStack().peek().getStackAbility().getCostsTagMap().get("X")),"Exact payable X changed");
    check(f.bot.getManaPool().getMana().count()==0,"Actual four-mana payment was not exact");
    for(Permanent p:f.game.getBattlefield().getAllActivePermanents(f.bot.getId()))check(p.isTapped(),"A required source was skipped");
    drain(f,10);check(f.game.getPermanent(goal.getId())!=null,"Actual X=2 tutor failed to resolve");
   }
  });
  finish("FINALE_PAYMENT");
 }
}
