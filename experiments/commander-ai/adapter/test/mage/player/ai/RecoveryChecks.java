package mage.player.ai;
import mage.abilities.*;import mage.cards.Card;import mage.constants.*;import mage.game.permanent.Permanent;import java.lang.reflect.*;import java.util.*;
import static mage.player.ai.FourthSeatRealGameTest.check;
/** Shared reconstructed test utilities. No product behavior overrides. */
final class RecoveryChecks {
 interface Case{void run()throws Exception;}static int passed,failed;
 static void run(String name,Case body){try{body.run();passed++;System.out.println("PASS "+name);}catch(Throwable e){failed++;System.out.println("FAIL "+name+": "+e);e.printStackTrace();}}
 static void finish(String suite){System.out.println(suite+" RESULT: "+passed+" passed, "+failed+" failed real-engine checks");if(failed>0)throw new AssertionError(failed+" "+suite+" failures");}
 static FourthSeatRealGameTest.Fixture fixture(String type,String name)throws Exception{FourthSeatRealGameTest.Fixture f=new FourthSeatRealGameTest.Fixture();f.game.initializeRollbackOrder();f.command(type,name);return f;}
 static boolean fund(FourthSeatRealGameTest.Fixture f,ActivatedAbility a)throws Exception{Method m=ComputerPlayerFourthSeat.class.getDeclaredMethod("canFundCandidate",ActivatedAbility.class,mage.game.Game.class);m.setAccessible(true);return (Boolean)m.invoke(f.bot,a,f.game);}
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static boolean flag(FourthSeatRealGameTest.Fixture f,String n)throws Exception{Object o=f.bot.observe(f.game);return o.getClass().getField(n).getBoolean(o);}
 static void resolve(FourthSeatRealGameTest.Fixture f){check(!f.game.getStack().isEmpty(),"Expected real stack object");f.game.resolveWithLoopCheck();f.game.checkStateAndTriggered();f.game.applyEffects();}
 static void drain(FourthSeatRealGameTest.Fixture f,int bound){int i=0;while(!f.game.getStack().isEmpty()&&i++<bound)resolve(f);check(f.game.getStack().isEmpty(),"Bounded stack did not drain");}
 static void grave(FourthSeatRealGameTest.Fixture f,Card c){f.game.getState().setZone(c.getId(),Zone.GRAVEYARD);f.bot.getGraveyard().add(c);}
 static void library(FourthSeatRealGameTest.Fixture f,int n)throws Exception{for(int i=0;i<n;i++)f.bot.getLibrary().putOnTop(f.card("basiclands.Forest","Forest"),f.game);}
 static void cast(FourthSeatRealGameTest.Fixture f,Card c,int callbacks)throws Exception{FourthSeatRealGameTest.Trace trace=new FourthSeatRealGameTest.Trace();org.apache.log4j.Logger l=org.apache.log4j.Logger.getLogger(ComputerPlayerFourthSeat.class);l.setLevel(org.apache.log4j.Level.DEBUG);l.addAppender(trace);try{for(int i=0;i<callbacks&&f.game.getState().getZone(c.getId())!=Zone.STACK;i++){check(f.bot.priority(f.game),"Passed before outer cast "+c.getName());check(trace.failures==0,"Rejected activation during sequence");f.game.checkStateAndTriggered();if(!f.game.getStack().isEmpty()&&f.game.getState().getZone(c.getId())!=Zone.STACK)drain(f,20);}check(f.game.getState().getZone(c.getId())==Zone.STACK,"Outer spell never cast "+c.getName());check(trace.failures==0,"Payment failure hidden by callback retries");}finally{l.removeAppender(trace);}}
 static void reject(FourthSeatRealGameTest.Fixture f,Card c)throws Exception{FourthSeatRealGameTest.Trace t=new FourthSeatRealGameTest.Trace();org.apache.log4j.Logger l=org.apache.log4j.Logger.getLogger(ComputerPlayerFourthSeat.class);l.setLevel(org.apache.log4j.Level.DEBUG);l.addAppender(t);Zone zone=f.game.getState().getZone(c.getId());int rollback=f.game.rollbackCount;try{f.bot.priority(f.game);check(t.failures==0&&f.game.rollbackCount==rollback,"Unavailable action attempted");check(f.game.getState().getZone(c.getId())==zone,"Unavailable spell moved");}finally{l.removeAppender(t);}}
}
