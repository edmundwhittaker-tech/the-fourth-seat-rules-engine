package mage.player.ai;

import mage.abilities.Ability;
import mage.abilities.effects.ReplacementEffectImpl;
import mage.abilities.effects.common.DoubleCountersSourceEffect;
import mage.abilities.common.delayed.AtTheBeginOfNextUpkeepDelayedTriggeredAbility;
import mage.abilities.effects.common.GainLifeEffect;
import mage.constants.*;
import mage.counters.*;
import mage.game.*;
import mage.game.mulligan.MulliganType;
import mage.game.events.GameEvent;
import mage.game.permanent.Permanent;
import mage.watchers.Watcher;
import java.lang.reflect.Field;
import java.util.*;
import static mage.player.ai.FourthSeatRealGameTest.check;
import static mage.player.ai.RecoveryChecks.*;

/** Exercises the actual CardImpl path, including observers which must disable batching. */
public final class FourthSeatCounterBatchTest {
    static FourthSeatRealGameTest.Fixture fixture() throws Exception {
        return RecoveryChecks.fixture("a.AesiTyrantOfGyreStrait", "Aesi, Tyrant of Gyre Strait");
    }
    static Permanent hydra(FourthSeatRealGameTest.Fixture f) throws Exception {
        return f.board("m.MossbornHydra", "Mossborn Hydra");
    }
    static void add(FourthSeatRealGameTest.Fixture f, Permanent p, int count) {
        check(p.addCounters(CounterType.P1P1.createInstance(count),p.getAbilities().get(0),f.game),"Native counter addition failed");
    }
    public static final class CounterObserver extends Watcher {
        final List<Integer> seen=new ArrayList<>(); int groupAmount;
        public CounterObserver(){super(WatcherScope.GAME);}
        public void watch(GameEvent event,Game game){
            if(event.getType()==GameEvent.EventType.COUNTER_ADDED)
                seen.add(game.getPermanent(event.getTargetId()).getCounters(game).getCount(CounterType.P1P1));
            if(event.getType()==GameEvent.EventType.COUNTERS_ADDED)groupAmount+=event.getAmount();
        }
    }
    public static final class DenyAlternateCounter extends ReplacementEffectImpl {
        static int visited;
        DenyAlternateCounter(){super(Duration.EndOfGame,Outcome.Neutral);}
        DenyAlternateCounter(DenyAlternateCounter other){super(other);}
        public DenyAlternateCounter copy(){return new DenyAlternateCounter(this);}
        public boolean checksEventType(GameEvent event,Game game){return event.getType()==GameEvent.EventType.ADD_COUNTER;}
        public boolean applies(GameEvent event,Ability source,Game game){return true;}
        public boolean replaceEvent(GameEvent event,Ability source,Game game){return (++visited%2)==0;}
    }
    public static void main(String[] args)throws Exception {
        run("Native landfall board doubles ten million counters without individual observers",()->{
            FourthSeatRealGameTest.Fixture f=fixture();Permanent p=hydra(f);
            for(String[] card:new String[][]{{"a.AncientGreenwarden","Ancient Greenwarden"},{"a.AshayaSoulOfTheWild","Ashaya, Soul of the Wild"},{"r.RetreatToKazandu","Retreat to Kazandu"},{"s.ScuteSwarm","Scute Swarm"},{"r.RampagingBaloths","Rampaging Baloths"}})f.board(card[0],card[1]);
            check(f.game.getState().canBatchIndividualCounterEvents(f.game),"Representative board not proven safe");
            long start=System.nanoTime();add(f,p,10000000);
            check(new DoubleCountersSourceEffect(CounterType.P1P1).apply(f.game,p.getAbilities().get(0)),"Native doubling failed");
            check(p.getCounters(f.game).getCount(CounterType.P1P1)==20000000,"Counter total was capped or changed");
            long millis=(System.nanoTime()-start)/1000000;
            System.out.println("COUNTER_BATCH native 10000000 -> 20000000 elapsedMs="+millis);
            check(millis<5000,"Representative doubling exceeded five seconds");
        });
        for(final String card:new String[]{"doubling","scales"})run("Native group replacement preserved: "+card,()->{
            FourthSeatRealGameTest.Fixture f=fixture();Permanent p=hydra(f);
            f.board(card.equals("doubling")?"d.DoublingSeason":"h.HardenedScales",card.equals("doubling")?"Doubling Season":"Hardened Scales");
            check(f.game.getState().canBatchIndividualCounterEvents(f.game),"Group-only replacement incorrectly disables proof");
            add(f,p,100);check(p.getCounters(f.game).getCount(CounterType.P1P1)==(card.equals("doubling")?200:101),"Group replacement was bypassed");
        });
        run("Native aggregate Simic Ascendancy trigger preserved",()->{
            FourthSeatRealGameTest.Fixture f=fixture();Permanent p=hydra(f);Permanent ascendancy=f.board("s.SimicAscendancy","Simic Ascendancy");
            check(f.game.getState().canBatchIndividualCounterEvents(f.game),"Aggregate-only trigger incorrectly disables proof");
            add(f,p,100);f.game.checkStateAndTriggered();check(f.game.getStack().size()==1,"Expected exactly one aggregate trigger");
            drain(f,5);check(ascendancy.getCounters(f.game).getCount(CounterType.GROWTH)==100,"Aggregate trigger lost actual amount");
        });
        run("Unknown observer receives all individual counters in order",()->{
            FourthSeatRealGameTest.Fixture f=fixture();Permanent p=hydra(f);CounterObserver observer=new CounterObserver();f.game.getState().addWatcher(observer);
            check(!f.game.getState().canBatchIndividualCounterEvents(f.game),"Unknown watcher incorrectly trusted");
            add(f,p,100);check(observer.seen.size()==100&&observer.groupAmount==100,"Native individual or group events omitted");
            for(int n=0;n<100;n++)check(observer.seen.get(n)==n+1,"Native callback ordering changed");
        });
        run("Unknown per-counter replacement retains native rejections and aggregate amount",()->{
            FourthSeatRealGameTest.Fixture f=fixture();Permanent p=hydra(f);DenyAlternateCounter.visited=0;
            f.game.addEffect(new DenyAlternateCounter(),p.getAbilities().get(0));
            CounterObserver observer=new CounterObserver();f.game.getState().addWatcher(observer);
            check(!f.game.getState().canBatchIndividualCounterEvents(f.game),"Unknown replacement incorrectly trusted");
            check(!p.addCounters(CounterType.P1P1.createInstance(100),p.getAbilities().get(0),f.game),"Native partial rejection return changed");
            check(DenyAlternateCounter.visited==100&&p.getCounters(f.game).getCount(CounterType.P1P1)==50,"Per-counter replacement bypassed");
            check(observer.seen.size()==50&&observer.groupAmount==50,"Rejected counters counted as added");
        });
        run("Native Solemnity prohibition preserves zero counters",()->{
            FourthSeatRealGameTest.Fixture f=fixture();Permanent p=hydra(f);f.board("s.Solemnity","Solemnity");
            check(!p.addCounters(CounterType.P1P1.createInstance(100),p.getAbilities().get(0),f.game),"Prohibited addition accepted");
            check(p.getCounters(f.game).getCount(CounterType.P1P1)==0,"Prohibition bypassed");
        });
        run("Native delayed triggers disable event batching",()->{
            FourthSeatRealGameTest.Fixture f=fixture();Permanent p=hydra(f);
            f.game.addDelayedTriggeredAbility(new AtTheBeginOfNextUpkeepDelayedTriggeredAbility(new GainLifeEffect(1)),p.getAbilities().get(0));
            check(!f.game.getState().canBatchIndividualCounterEvents(f.game),"Delayed callback incorrectly trusted");add(f,p,100);
            check(p.getCounters(f.game).getCount(CounterType.P1P1)==100,"Native delayed fallback changed total");
        });
        run("Enabled trigger integrity checks disable event batching",()->{
            FourthSeatRealGameTest.Fixture f=fixture();Permanent p=hydra(f);Object triggers=f.game.getState().getTriggers();
            Field flag=triggers.getClass().getDeclaredField("enableIntegrityChecks");flag.setAccessible(true);flag.setBoolean(triggers,true);
            check(!f.game.getState().canBatchIndividualCounterEvents(f.game),"Integrity checking bypassed");flag.setBoolean(triggers,false);add(f,p,100);
        });
        run("Nullable final selector keeps native callback path",()->{
            mage.abilities.effects.EntersBattlefieldEffect effect=new mage.abilities.effects.EntersBattlefieldEffect(new GainLifeEffect(1),null,"",true,false,null);
            check(!CounterEventBatching.ignores(effect,"checksEventType"),"Nullable switch selector was incorrectly trusted");
            check(CounterEventBatching.ignores(new mage.abilities.effects.EntersBattlefieldEffect(new GainLifeEffect(1)),"checksEventType"),"Valid selector unnecessarily rejected");
        });
        run("Native maximum counter bound remains enforced",()->{
            FourthSeatRealGameTest.Fixture f=fixture();Permanent p=hydra(f);Ability source=p.getAbilities().get(0);
            check(p.addCounters(CounterType.P1P1.createInstance(1000),f.bot.getId(),source,f.game,null,true,100),"Native bounded addition failed");
            check(p.getCounters(f.game).getCount(CounterType.P1P1)==100,"Native maximum was bypassed");
        });
        run("Custom raw event dispatch disables event batching",()->{
            Game game=new GameCommanderImpl(MultiplayerAttackOption.MULTIPLE,RangeOfInfluence.ALL,MulliganType.GAME_DEFAULT.getMulligan(0),0,40,7){
                @Override public void fireEvent(GameEvent event){super.fireEvent(event);}
                public Game copy(){throw new UnsupportedOperationException("Dispatch test only");}
                public int getNumPlayers(){return 4;}
                public mage.game.match.MatchType getGameType(){return new FourthSeatRealGameTest.FixtureType();}
            };
            check(!CounterEventBatching.standardDispatch(game),"Custom dispatch incorrectly trusted");
        });
        finish("COUNTER_BATCH");
    }
}
