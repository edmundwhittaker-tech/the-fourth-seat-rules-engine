package mage.player.ai;
import mage.cards.Card;import mage.constants.*;
import static mage.player.ai.FourthSeatRealGameTest.check;import static mage.player.ai.RecoveryChecks.*;
/** The established-army resource proof must understand Crop Rotation's typed cost. */
public final class FourthSeatCropRotationReserveTest {
 public static void main(String[]args)throws Exception {
  for(String mode:new String[]{"current","projected","pending-stack","short-army","short-hand","missing-blue","short-mana","variable-hand","other-commander","protection"})run("Crop Rotation established army resource proof "+mode,()->{
   FourthSeatRealGameTest.Fixture f=FourthSeatRecoveredGrowthTest.army(mode.equals("short-army")?31:32,false,mode.equals("projected"),!mode.equals("missing-blue"),mode.equals("short-mana")?2:8,mode.equals("short-hand")?3:5);
   Card crop=f.hand("c.CropRotation","Crop Rotation");
   if(mode.equals("variable-hand"))f.hand("c.CurseOfTheSwine","Curse of the Swine");
   if(mode.equals("other-commander")){f.bot.getCommandersIds().clear();f.command("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves");}
   if(mode.equals("pending-stack")){Card shock=f.enemyHand("s.Shock","Shock");shock.getSpellAbility().getTargets().get(0).addTarget(f.foes[1].getId(),shock.getSpellAbility(),f.game);f.castEnemy(shock);}
   boolean reserve=mode.equals("current")||mode.equals("projected")||mode.equals("pending-stack")||mode.equals("protection");
   check(FourthSeatRecoveredGrowthTest.reserve(f)==reserve,"Typed land-sacrifice cost changed the wrong resource boundary");
   if(!reserve)return;
   if(mode.equals("protection")){
    Card protect=f.hand("h.HeroicIntervention","Heroic Intervention");java.util.UUID commander=f.bot.getCommandersIds().iterator().next();Card murder=f.enemyHand("m.Murder","Murder");murder.getSpellAbility().getTargets().get(0).addTarget(commander,murder.getSpellAbility(),f.game);f.castEnemy(murder);cast(f,protect,1);drain(f,10);check(f.game.getPermanent(commander)!=null,"Growth reserve suppressed actual protection");
   }else{
    library(f,5);int stack=f.game.getStack().size();check(!f.bot.priority(f.game),"Redundant land search expanded an established army");
    check(f.bot.getHand().contains(crop.getId())&&f.game.getStack().size()==stack&&f.game.rollbackCount==0,"Reserve spent or rolled back the land search");
   }
  });
  finish("CROP_ROTATION_RESERVE");
 }
}
