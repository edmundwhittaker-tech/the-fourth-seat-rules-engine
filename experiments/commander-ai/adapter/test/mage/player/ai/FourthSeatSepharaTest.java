package mage.player.ai;
import mage.Mana;import mage.cards.Card;import mage.constants.*;import mage.game.permanent.Permanent;
import static mage.player.ai.FourthSeatRealGameTest.check;import static mage.player.ai.RecoveryChecks.*;

/** Actual native alternative casts; the mana source cannot also pay a tap cost. */
public final class FourthSeatSepharaTest {
 static FourthSeatRealGameTest.Fixture board(int flyers,boolean giada)throws Exception {
  FourthSeatRealGameTest.Fixture f=new FourthSeatRealGameTest.Fixture();f.game.initializeRollbackOrder();
  if(giada){Permanent p=f.board("g.GiadaFontOfHope","Giada, Font of Hope");f.identifyCommander(p.getId());}
  else{Card c=f.card("g.GiadaFontOfHope","Giada, Font of Hope");f.identifyCommander(c.getId());}
  for(int n=giada?1:0;n<flyers;n++)f.board("s.SerraAngel","Serra Angel");
  return f;
 }
 public static void main(String[] args)throws Exception {
  for(final String mode:new String[]{"giada-conflict","external-land","spare-flyer","floating","regular","too-few-flyers","tax-unfunded","tax-funded","granted-free-cast"})run("Sephara actual payment "+mode,()->{
   boolean regular=mode.equals("regular"),few=mode.equals("too-few-flyers");
   FourthSeatRealGameTest.Fixture f=board(regular?0:few?3:mode.equals("spare-flyer")?5:4,!regular);
   Card card=f.hand("s.SepharaSkysBlade","Sephara, Sky's Blade");
   if(mode.equals("granted-free-cast"))f.board("o.Omniscience","Omniscience");
   if(mode.equals("external-land")||mode.startsWith("tax-"))f.board("basiclands.Plains","Plains");
   if(few)f.board("b.BenevolentBodyguard","Benevolent Bodyguard");
   if(mode.startsWith("tax-"))f.board("s.SphereOfResistance","Sphere of Resistance");
   if(mode.equals("floating")||mode.equals("tax-funded"))f.bot.getManaPool().addMana(Mana.WhiteMana(1),f.game,card.getSpellAbility());
   if(regular)f.bot.getManaPool().addMana(new Mana(3,0,0,0,0,0,0,4),f.game,card.getSpellAbility());
   boolean legal=!mode.equals("giada-conflict")&&!few&&!mode.equals("tax-unfunded");
   if(!legal){reject(f,card);check(f.game.rollbackCount==0,"Unpayable alternative attempted and rolled back");return;}
   if(mode.equals("granted-free-cast")) {
    check(f.bot.canPlayCardByAlternateCost(card,new mage.abilities.mana.ManaOptions(),card.getSpellAbility(),f.game),"Native granted alternative was hidden by intrinsic-cost filtering");
    check(f.bot.activateAbility(card.getSpellAbility(),f.game),"Actual granted free cast failed");
    check(f.game.getState().getZone(card.getId())==Zone.STACK,"Granted cast never entered stack");
   } else cast(f,card,1);
   check(f.game.rollbackCount==0,"Alternative payment rolled back");
   check(f.bot.getManaPool().getMana().count()==0,"Actual mana cost was not paid exactly");
   long tapped=f.game.getBattlefield().getAllActivePermanents(f.bot.getId()).stream().filter(Permanent::isTapped).count();
   check(tapped==(regular||mode.equals("granted-free-cast")?0:mode.equals("spare-flyer")||mode.equals("external-land")||mode.startsWith("tax-")?5:4),"Four distinct tap payments and mana payment did not agree");
   drain(f,10);check(f.game.getPermanent(card.getId())!=null,"Actual spell did not resolve");
  });
  finish("SEPHARA");
 }
}
