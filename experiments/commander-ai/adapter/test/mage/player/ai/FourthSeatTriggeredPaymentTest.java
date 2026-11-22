package mage.player.ai;
import mage.Mana;import mage.cards.Card;import mage.constants.*;import mage.game.permanent.Permanent;
import static mage.player.ai.FourthSeatRealGameTest.check;import static mage.player.ai.RecoveryChecks.*;
/** Mixed sacrifice mana and triggered land mana during an actual spell payment. */
public final class FourthSeatTriggeredPaymentTest {
 public static void main(String[]args)throws Exception {
  for(String mode:new String[]{"one-treasure","floating-green","no-monarch","no-treasure","tapped-land","removed-behemoth"})run("Skullspore mixed native payment "+mode,()->{
   FourthSeatRealGameTest.Fixture f=fixture("g.GishathSunsAvatar","Gishath, Sun's Avatar");f.game.getCard(f.bot.getCommandersIds().iterator().next()).moveToZone(Zone.EXILED,null,f.game,false);
   Permanent regal=f.board("r.RegalBehemoth","Regal Behemoth");f.board("g.Gigantosaurus","Gigantosaurus");
   if(!mode.equals("no-monarch"))f.game.getState().setMonarchId(f.bot.getId());
   Permanent land=f.board("u.UnclaimedTerritory","Unclaimed Territory");f.game.getState().setValue(land.getId()+"_type",SubType.DINOSAUR);
   if(mode.equals("tapped-land"))land.setTapped(true);
   if(mode.equals("removed-behemoth"))regal.moveToZone(Zone.GRAVEYARD,null,f.game,false);
   Card card=f.hand("t.TheSkullsporeNexus","The Skullspore Nexus");
   if(!mode.equals("no-treasure")&&!mode.equals("floating-green"))check(new mage.game.permanent.token.TreasureToken().putOntoBattlefield(1,f.game,regal.getAbilities().get(0),f.bot.getId()),"Native Treasure missing");
   if(mode.equals("floating-green"))f.bot.getManaPool().addMana(Mana.GreenMana(1),f.game,card.getSpellAbility());
   f.bot.getManaPool().addMana(Mana.WhiteMana(1),f.game,card.getSpellAbility());f.bot.getManaPool().addMana(Mana.RedMana(1),f.game,card.getSpellAbility());
   boolean legal=mode.equals("one-treasure")||mode.equals("floating-green");
   if(!legal){reject(f,card);check(f.game.rollbackCount==0,"Unavailable mixed payment rolled back");return;}
   cast(f,card,3);check(f.game.rollbackCount==0,"Actual mixed payment hid failed activation");
   check(f.game.getPermanent(land.getId()).isTapped(),"Colorless land was not used for its native bonus");
   check(f.bot.getManaPool().getMana().getGreen()==0&&f.bot.getManaPool().getMana().getWhite()==1&&f.bot.getManaPool().getMana().getRed()==1,"Actual GG cost changed unrelated floating colors");
   check(f.game.getBattlefield().getAllActivePermanents(f.bot.getId()).stream().noneMatch(p->p.hasSubtype(SubType.TREASURE,f.game)),"Native Treasure cost not paid");
   drain(f,10);check(f.game.getPermanent(card.getId())!=null,"Native Skullspore failed resolution");
  });
  finish("TRIGGERED_PAYMENT");
 }
}
