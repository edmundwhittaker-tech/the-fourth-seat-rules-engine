package mage.player.ai;
import mage.Mana;import mage.cards.Card;import mage.constants.*;
import static mage.player.ai.FourthSeatRealGameTest.check;import static mage.player.ai.RecoveryChecks.*;
public final class FourthSeatCelestialReunionTest {
 public static void main(String[] args)throws Exception{
  for(String position:new String[]{"battlefield","hand","mixed","changeling","no-pair","opponent-pair"})run("Celestial Reunion actual behold payment "+position,()->{
   FourthSeatRealGameTest.Fixture f=fixture("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves");f.game.getCard(f.bot.getCommandersIds().iterator().next()).moveToZone(Zone.EXILED,null,f.game,false);
   Card first=position.equals("hand")?f.hand("b.BenevolentBodyguard","Benevolent Bodyguard"):f.board(position.equals("changeling")?"c.ChangelingOutcast":"b.BenevolentBodyguard",position.equals("changeling")?"Changeling Outcast":"Benevolent Bodyguard");
   Card second=position.equals("opponent-pair")?f.enemyBoard(0,"a.AvacynsPilgrim","Avacyn's Pilgrim"):position.equals("no-pair")?f.board("e.ElvishMystic","Elvish Mystic"):position.equals("hand")||position.equals("mixed")?f.hand("a.AvacynsPilgrim","Avacyn's Pilgrim"):f.board("a.AvacynsPilgrim","Avacyn's Pilgrim");
   Zone firstZone=f.game.getState().getZone(first.getId()),secondZone=f.game.getState().getZone(second.getId());
   Card goal=f.card("b.BenevolentBodyguard","Benevolent Bodyguard");library(f,4);f.bot.getLibrary().putOnTop(goal,f.game);Card spell=f.hand("c.CelestialReunion","Celestial Reunion");f.bot.getManaPool().addMana(Mana.GreenMana(4),f.game,spell.getSpellAbility());
   check(f.bot.activateAbility(spell.getSpellAbility(),f.game),"Real Celestial cast rejected");check(f.game.rollbackCount==0&&f.game.getState().getZone(spell.getId())==Zone.STACK,"Cast rollback concealed behold failure");drain(f,30);
   boolean paid=!position.equals("no-pair")&&!position.equals("opponent-pair");check(f.game.getState().getZone(goal.getId())==(paid?Zone.BATTLEFIELD:Zone.HAND),"Behold choice did not match actual own pair and fetched creature type");
   check(f.game.getState().getZone(first.getId())==firstZone&&f.game.getState().getZone(second.getId())==secondZone,"Beholding moved a selected card");check(f.game.rollbackCount==0,"Resolution changed rollback count");
  });
  finish("CELESTIAL_REUNION");
 }
}
