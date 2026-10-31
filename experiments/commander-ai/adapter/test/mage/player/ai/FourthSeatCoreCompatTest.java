package mage.player.ai;
import mage.ApprovingObject;
import mage.MageObjectReference;
import mage.abilities.Ability;
import mage.cards.Card;
import mage.constants.Zone;
import static mage.player.ai.FourthSeatRealGameTest.check;
import static mage.player.ai.RecoveryChecks.*;
/** Actual resolved permission lifetime, not a replacement permission manufactured by the AI. */
public final class FourthSeatCoreCompatTest {
 static final class Mission {
  final FourthSeatRealGameTest.Fixture f=fixture("g.GishathSunsAvatar","Gishath, Sun's Avatar");
  final Card spell,mission; final Ability resolvedSource;
  Mission()throws Exception {
   spell=f.card("c.CongregationAtDawn","Congregation at Dawn");grave(f,spell);
   // Only basic lands can be surveilled, leaving exactly one instant/sorcery choice.
   library(f,8);mission=f.enemyHand("m.MissionBriefing","Mission Briefing");
   f.game.getState().setValue("PlayFromNotOwnHandZone"+mission.getId(),Boolean.TRUE);
   check(f.bot.cast(mission.getSpellAbility(),f.game,true,null),"Stolen Mission actual cast failed");
   f.game.getState().setValue("PlayFromNotOwnHandZone"+mission.getId(),null);
   resolvedSource=f.game.getStack().peek().getStackAbility().copy();
   check(resolvedSource.getControllerId().equals(f.bot.getId()),"Mission did not use survivor controller");
   resolve(f);check(f.game.getState().getZone(mission.getId())==Zone.GRAVEYARD,"Mission did not resolve into owner's graveyard");
   f.mana(spell);check(fund(f,spell.getSpellAbility()),"Actual Mission resolution did not grant permission");
  }
  void ownerLeaves(){f.game.leavePlayer(f.foes[0].getId());check(f.foes[0].hasLeft()&&f.game.getObject(mission.getId())==null,"Owner departure failed to remove Mission");check(!f.game.hasEnded(),"Fixture ended instead of multiplayer departure");}
  void castAndExile()throws Exception{cast(f,spell,2);drain(f,12);check(f.game.getState().getZone(spell.getId())==Zone.EXILED,"Mission replacement did not exile resolved grave spell");}
 }
 static void noErrors(RecoveryChecks.Case body)throws Exception {
  final int[] errors={0};org.apache.log4j.Logger root=org.apache.log4j.Logger.getRootLogger();
  org.apache.log4j.AppenderSkeleton capture=new org.apache.log4j.AppenderSkeleton(){protected void append(org.apache.log4j.spi.LoggingEvent e){if(e.getLevel().isGreaterOrEqual(org.apache.log4j.Level.ERROR))errors[0]++;}public void close(){}public boolean requiresLayout(){return false;}};
  root.addAppender(capture);try{body.run();check(errors[0]==0,"Engine reported missing-source/LKI ERROR during valid permission lifecycle");}finally{root.removeAppender(capture);}
 }
 public static void main(String[]args)throws Exception {
  run("Live stolen Mission permission casts and exiles",()->noErrors(()->{Mission m=new Mission();m.castAndExile();}));
  run("Stolen Mission permission survives actual owner departure",()->noErrors(()->{Mission m=new Mission();m.ownerLeaves();check(fund(m.f,m.spell.getSpellAbility()),"Owner departure incorrectly revoked survivor's EOT permission");m.castAndExile();}));
  for(boolean leave:new boolean[]{false,true})run("Mission permission expires at cleanup ownerLeft="+leave,()->noErrors(()->{Mission m=new Mission();if(leave)m.ownerLeaves();m.f.game.getContinuousEffects().removeEndOfTurnEffects(m.f.game);m.f.game.applyEffects();check(m.f.bot.getPlayable(m.f.game,true).stream().noneMatch(a->m.spell.getId().equals(a.getSourceId())),"Expired Mission permission still offers grave cast");reject(m.f,m.spell);check(m.f.game.getState().getZone(m.spell.getId())==Zone.GRAVEYARD,"Expired permission moved card");}));
  run("Live approval identity preserves current object reference",()->noErrors(()->{Mission m=new Mission();ApprovingObject approval=new ApprovingObject(m.resolvedSource,m.f.game);check(approval.getApprovingMageObjectReference().equals(new MageObjectReference(m.mission.getId(),m.f.game)),"Live approval identity changed");}));
  run("Resolved approval identity remains identical after owner departure",()->noErrors(()->{Mission m=new Mission();ApprovingObject before=new ApprovingObject(m.resolvedSource,m.f.game);m.ownerLeaves();ApprovingObject after=new ApprovingObject(m.resolvedSource,m.f.game);check(before.getApprovingMageObjectReference().equals(after.getApprovingMageObjectReference()),"Elimination changed resolved permission object incarnation");check(after.getApprovingAbility()==m.resolvedSource,"Approval replaced the retained source ability");}));
  run("Once-each-turn watcher retains used identity across source disappearance",()->noErrors(()->{Mission m=new Mission();Ability source=m.resolvedSource.copy();source.setIdentifier(mage.MageIdentifier.OnceEachTurnCastWatcher);ApprovingObject before=new ApprovingObject(source,m.f.game);mage.watchers.common.OnceEachTurnCastWatcher watcher=new mage.watchers.common.OnceEachTurnCastWatcher();watcher.watch(new mage.game.events.GameEvent(mage.game.events.GameEvent.EventType.SPELL_CAST,m.spell.getId(),m.spell.getSpellAbility(),m.f.bot.getId(),before),m.f.game);check(watcher.isAbilityUsed(m.f.bot.getId(),before.getApprovingMageObjectReference()),"Watcher did not register actual approval identity");m.ownerLeaves();ApprovingObject after=new ApprovingObject(source,m.f.game);check(watcher.isAbilityUsed(m.f.bot.getId(),after.getApprovingMageObjectReference()),"Missing source reset once-per-turn identity");check(!watcher.isAbilityUsed(m.f.foes[1].getId(),after.getApprovingMageObjectReference()),"Usage leaked between players");watcher.reset();check(!watcher.isAbilityUsed(m.f.bot.getId(),after.getApprovingMageObjectReference()),"Watcher failed next-turn reset");}));
  run("Unknown uncaptured source retains legacy diagnostic",()->{FourthSeatRealGameTest.Fixture f=new FourthSeatRealGameTest.Fixture();Ability unknown=new mage.abilities.common.SimpleActivatedAbility(mage.constants.Zone.BATTLEFIELD,new mage.abilities.effects.common.DrawCardSourceControllerEffect(1),new mage.abilities.costs.mana.ManaCostsImpl<>("{1}"));unknown.setSourceId(java.util.UUID.randomUUID());unknown.setControllerId(f.bot.getId());check(unknown.getStackMomentSourceZCC()==0,"Unknown fixture accidentally captured an incarnation");final int[] errors={0};org.apache.log4j.Logger logger=org.apache.log4j.Logger.getLogger(MageObjectReference.class);boolean additive=logger.getAdditivity();org.apache.log4j.Level level=logger.getLevel();org.apache.log4j.AppenderSkeleton capture=new org.apache.log4j.AppenderSkeleton(){protected void append(org.apache.log4j.spi.LoggingEvent e){if(e.getLevel().isGreaterOrEqual(org.apache.log4j.Level.ERROR))errors[0]++;}public void close(){}public boolean requiresLayout(){return false;}};logger.setAdditivity(false);logger.setLevel(org.apache.log4j.Level.ERROR);logger.addAppender(capture);try{ApprovingObject malformed=new ApprovingObject(unknown,f.game);check(errors[0]>0,"Malformed source was silently treated as valid retained permission");check(malformed.getApprovingMageObjectReference().getZoneChangeCounter()==0,"Unknown reference legacy sentinel changed");}finally{logger.removeAppender(capture);logger.setAdditivity(additive);logger.setLevel(level);}});
  finish("CORE_COMPAT");
 }
}
