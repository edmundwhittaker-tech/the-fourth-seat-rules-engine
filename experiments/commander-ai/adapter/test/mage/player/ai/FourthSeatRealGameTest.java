package mage.player.ai;

import fourthseat.ai.PolicyModel.*;
import mage.Mana;
import mage.cards.Card;
import mage.cards.CardSetInfo;
import mage.cards.decks.Deck;
import mage.constants.*;
import mage.game.*;
import mage.game.match.MatchType;
import mage.game.mulligan.MulliganType;
import mage.game.permanent.PermanentCard;
import mage.game.turn.*;
import java.util.*;

/** Real engine smoke/regression tests, not policy-model fixtures. Run with assertions enabled. */
public final class FourthSeatRealGameTest {
    private static int passed, failed;
    private static final Trace TRACE=new Trace();
    static final class Trace extends org.apache.log4j.AppenderSkeleton { int failures; protected void append(org.apache.log4j.spi.LoggingEvent e){ if(e.getRenderedMessage().contains("fourth-seat activation-failed"))failures++; } public void close(){} public boolean requiresLayout(){return false;} }
    private static final String[][] COMMANDERS = {
        {"g.GiadaFontOfHope", "Giada, Font of Hope"},
        {"k.KrenkoMobBoss", "Krenko, Mob Boss"},
        {"l.LathrilBladeOfTheElves", "Lathril, Blade of the Elves"},
        {"g.GhaltaPrimalHunger", "Ghalta, Primal Hunger"},
        {"g.GoreclawTerrorOfQalSisma", "Goreclaw, Terror of Qal Sisma"},
        {"t.TorbranThaneOfRedFell", "Torbran, Thane of Red Fell"},
        {"t.TalrandSkySummoner", "Talrand, Sky Summoner"},
        {"a.AesiTyrantOfGyreStrait", "Aesi, Tyrant of Gyre Strait"},
        {"g.GishathSunsAvatar", "Gishath, Sun's Avatar"},
        {"a.AdelineResplendentCathar", "Adeline, Resplendent Cathar"}
    };
    static final class FixtureGame extends GameCommanderImpl {
        FixtureGame() { super(MultiplayerAttackOption.MULTIPLE, RangeOfInfluence.ALL,
            MulliganType.GAME_DEFAULT.getMulligan(0), 0, 40, 7); setGameOptions(new GameOptions()); }
        FixtureGame(FixtureGame other) { super(other); }
        public FixtureGame copy() { return new FixtureGame(this); }
        long nextPlayableDelayMillis; int delayedPlayableCalculations;
        @Override public mage.game.Game createSimulationForPlayableCalc() { if(nextPlayableDelayMillis>0){long delay=nextPlayableDelayMillis;nextPlayableDelayMillis=0;delayedPlayableCalculations++;try{Thread.sleep(delay);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}return super.createSimulationForPlayableCalc(); }
        int rollbackCount;
        void resolveWithLoopCheck() { super.resolve(); }
        void leavePlayer(UUID id) { super.leave(id); }
        @Override public GameState restoreState(int bookmark, String context) { GameState restored=super.restoreState(bookmark,context); if(restored!=null) rollbackCount++; return restored; }
        void initializeRollbackOrder() { playerList = state.getPlayerList(state.getActivePlayerId()); }
        public int getNumPlayers() { return 4; }
        public MatchType getGameType() { return new FixtureType(); }
    }
    static final class FixtureType extends MatchType {
        FixtureType() { name = "Fourth Seat integration fixture"; minPlayers = maxPlayers = 4; }
        public MatchType copy() { return new FixtureType(); }
    }
    static final class Fixture {
        final FixtureGame game = new FixtureGame();
        final ComputerPlayerFourthSeat bot = new ComputerPlayerFourthSeat("Under test", RangeOfInfluence.ALL);
        final ComputerPlayer[] foes = new ComputerPlayer[3];
        Fixture() {
            game.addPlayer(bot, new Deck()); bot.init(game); bot.setTurnControlledBy(bot.getId());
            for (int i=0;i<foes.length;i++) { foes[i]=new ComputerPlayer("Opponent "+i,RangeOfInfluence.ALL); game.addPlayer(foes[i],new Deck()); foes[i].init(game); }
            bot.updateRange(game); for(ComputerPlayer foe:foes) foe.updateRange(game);
            game.getState().setTurnNum(4); game.getState().setActivePlayerId(bot.getId()); game.getState().setPriorityPlayerId(bot.getId());
            PreCombatMainPhase phase = new PreCombatMainPhase(); phase.setStep(new PreCombatMainStep()); game.getTurn().setPhase(phase);
        }
        // Identity-only battlefield fixtures still need real CommanderInfoWatcher registration
        // before engine state-based actions. Command-zone fixtures use full initCommander below.
        void identifyCommander(UUID id) { bot.getCommandersIds().add(id); mage.watchers.common.CommanderInfoWatcher watcher=game.initCommanderWatcher(game.getCard(id),true); game.getState().addWatcher(watcher); watcher.addCardInfoToCommander(game); }
        Card card(String type, String name) throws Exception {
            Card c=(Card)Class.forName("mage.cards."+type).getConstructor(UUID.class,CardSetInfo.class)
                .newInstance(bot.getId(),new CardSetInfo(name,"TST","1",Rarity.COMMON));
            game.loadCards(Collections.singleton(c),bot.getId()); return c;
        }
        Card hand(String type,String name) throws Exception { Card c=card(type,name); bot.getHand().add(c); game.getState().setZone(c.getId(),Zone.HAND); return c; }
        PermanentCard board(String type,String name) throws Exception {
            Card c=card(type,name); PermanentCard p=new PermanentCard(c,bot.getId(),game);
            game.addPermanent(p,0); game.getState().setZone(c.getId(),Zone.BATTLEFIELD); p.removeSummoningSickness(); game.applyEffects(); return p;
        }
        PermanentCard enemyBoard(int foe,String type,String name) throws Exception {
            Card c=card(type,name); c.setOwnerId(foes[foe].getId()); c.getAbilities().setControllerId(foes[foe].getId());
            PermanentCard p=new PermanentCard(c,foes[foe].getId(),game); game.addPermanent(p,0); game.getState().setZone(c.getId(),Zone.BATTLEFIELD); p.removeSummoningSickness(); game.applyEffects(); return p;
        }
        Card enemyHand(String type,String name) throws Exception { Card c=card(type,name); c.setOwnerId(foes[0].getId()); c.getAbilities().setControllerId(foes[0].getId()); foes[0].getHand().add(c); game.getState().setZone(c.getId(),Zone.HAND); return c; }
        void castEnemy(Card c) { game.getState().setActivePlayerId(foes[0].getId()); foes[0].getManaPool().addMana(new Mana(20,20,20,20,20,0,0,20),game,c.getSpellAbility()); check(foes[0].activateAbility(c.getSpellAbility(),game),"Fixture enemy spell failed: "+c.getName()); }
        void combat() { CombatPhase phase=new CombatPhase(); phase.setStep(new DeclareAttackersStep()); game.getTurn().setPhase(phase); game.getCombat().setAttacker(bot.getId()); game.getCombat().setDefenders(game); }
        void mana() { mana(bot.getHand().getCards(game).iterator().next()); }
        void mana(Card source) { bot.getManaPool().addMana(new Mana(20,20,20,20,20,0,0,20),game,source.getSpellAbility()); }
        Card command(String type,String name) throws Exception { Card c=hand(type,name); bot.getCommandersIds().add(c.getId()); game.initCommander(c,bot); game.applyEffects(); return c; }
    }
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    static void test(String name, Checked test) { try { test.run(); passed++; System.out.println("PASS "+name); } catch(Throwable e) { failed++; System.err.println("FAIL "+name+": "+e); e.printStackTrace(); } }
    interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        org.apache.log4j.Logger aiLog=org.apache.log4j.Logger.getLogger(ComputerPlayerFourthSeat.class); aiLog.setLevel(org.apache.log4j.Level.DEBUG); aiLog.addAppender(TRACE);
        for (final String[] commander:COMMANDERS) {
            test(commander[1]+" actual own-hand spell extraction and paid cast",()->{
                Fixture f=new Fixture(); Card c=f.hand(commander[0],commander[1]); f.identifyCommander(c.getId()); f.mana();
                check(f.bot.observe(f.game).commander.equals(commander[1]),"Commander identity missing");
                check(f.bot.observe(f.game).hand.size()==1,"Own hand missing");
                check(f.bot.priority(f.game),"AI passed despite funded commander");
                check(!f.bot.getHand().contains(c.getId()),"Chosen spell remained in hand");
                check(f.game.getStack().size()==1,"Cast not on actual engine stack");
                check(f.bot.getManaPool().getMana().count()<120,"No real mana payment");
            });
            test(commander[1]+" actual command-zone cast",()->{
                Fixture f=new Fixture(); Card c=f.command(commander[0],commander[1]); f.mana(c);
                check(f.game.getState().getZone(c.getId())==Zone.COMMAND,"Fixture commander not in command zone");
                check(f.bot.priority(f.game),"AI passed funded command-zone commander");
                check(f.game.getState().getZone(c.getId())==Zone.STACK && f.game.getStack().size()==1,"Command-zone cast not on stack");
                check(f.bot.getManaPool().getMana().count()<120,"Command-zone cost unpaid");
            });
            test(commander[1]+" unaffordable cast excluded",()->{
                Fixture f=new Fixture(); Card c=f.hand(commander[0],commander[1]); f.identifyCommander(c.getId());
                check(!f.bot.priority(f.game),"AI activated an unfunded spell");
                check(f.bot.getHand().contains(c.getId()) && f.game.getStack().isEmpty(),"Illegal state mutation");
            });
        }
        test("Commander tax is enforced on an actual second command-zone cast",()->{
            Fixture f=new Fixture(); Card commander=f.command("g.GiadaFontOfHope","Giada, Font of Hope");
            f.bot.getManaPool().addMana(Mana.WhiteMana(2),f.game,commander.getSpellAbility());
            check(f.bot.priority(f.game),"First command-zone cast failed"); f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(f.game.getPermanent(commander.getId())!=null,"Giada did not resolve");
            check(commander.moveToZone(Zone.COMMAND,null,f.game,true),"Commander return fixture failed"); f.game.applyEffects(); f.bot.getManaPool().init(); f.bot.resetPassed();
            f.bot.getManaPool().addMana(Mana.WhiteMana(2),f.game,commander.getSpellAbility());
            check(!f.bot.priority(f.game),"Second cast ignored two-mana commander tax");
            check(f.game.getState().getZone(commander.getId())==Zone.COMMAND,"Unaffordable commander left zone");
            f.bot.getManaPool().addMana(Mana.ColorlessMana(2),f.game,commander.getSpellAbility()); f.bot.resetPassed();
            check(f.bot.priority(f.game),"Funded taxed commander failed to cast"); check(f.bot.getManaPool().getMana().count()==0,"Commander tax was not actually paid");
        });
        test("Krenko real legal tap activation",()->{
            Fixture f=new Fixture(); PermanentCard krenko=f.board("k.KrenkoMobBoss","Krenko, Mob Boss"); f.identifyCommander(krenko.getId());
            check(f.bot.priority(f.game),"Krenko failed to activate"); check(krenko.isTapped(),"Krenko tap cost unpaid"); check(f.game.getStack().size()==1,"Krenko ability not stacked");
        });
        test("Krenko casts a token maker before multiplying goblins",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("k.KrenkoMobBoss","Krenko, Mob Boss"); f.identifyCommander(commander.getId());
            Card maker=f.hand("k.KrenkosCommand","Krenko's Command"); f.mana();
            check(f.bot.priority(f.game),"Krenko development passed"); check(!f.bot.getHand().contains(maker.getId()) && !commander.isTapped(),"Krenko multiplied before casting payable goblin maker");
        });
        test("Krenko allows own token spell to resolve before multiplication",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("k.KrenkoMobBoss","Krenko, Mob Boss"); f.identifyCommander(commander.getId());
            Card maker=f.hand("k.KrenkosCommand","Krenko's Command"); f.mana();
            check(f.bot.priority(f.game) && !commander.isTapped(),"Krenko did not start with token spell");
            check(!f.bot.priority(f.game),"Krenko activated while its token maker was still on stack");
            f.game.getStack().resolve(f.game); f.game.applyEffects(); f.bot.resetPassed();
            check(f.game.getBattlefield().getAllActivePermanents(f.bot.getId()).size()==3,"Krenko's Command did not create real tokens");
            check(f.bot.priority(f.game),"Krenko failed to multiply after token maker resolved"); check(commander.isTapped(),"Krenko activation cost unpaid after resolution");
            f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(f.game.getBattlefield().getAllActivePermanents(f.bot.getId()).size()==6,"Actual Krenko resolution failed to double the three goblins");
        });
        test("Krenko tapped cannot activate",()->{
            Fixture f=new Fixture(); PermanentCard krenko=f.board("k.KrenkoMobBoss","Krenko, Mob Boss"); f.identifyCommander(krenko.getId()); krenko.setTapped(true);
            check(!f.bot.priority(f.game),"Tapped Krenko acted"); check(f.game.getStack().isEmpty(),"Illegal Krenko activation");
        });
        test("Lathril ten untapped elves permits actual drain activation",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.identifyCommander(commander.getId());
            for(int i=0;i<10;i++) f.board("l.LlanowarElves","Llanowar Elves");
            check(f.bot.priority(f.game),"Lathril did not choose legal ten-elf activation");
            check(commander.isTapped(),"Lathril cost unpaid"); check(f.game.getStack().size()==1,"Drain not on real stack");
            int tapped=0; for(mage.game.permanent.Permanent p:f.game.getBattlefield().getAllActivePermanents(f.bot.getId())) if(p.isTapped()) tapped++;
            check(tapped==11,"Ten other elf tap cost was not fully paid");
            f.game.getStack().resolve(f.game); check(f.bot.getLife()==50,"Lathril life gain incorrect"); for(ComputerPlayer foe:f.foes) check(foe.getLife()==30,"Lathril failed to drain an opponent");
        });
        test("Lathril fewer than ten other elves cannot drain",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.identifyCommander(commander.getId());
            for(int i=0;i<9;i++) f.board("l.LlanowarElves","Llanowar Elves");
            check(!f.bot.priority(f.game),"Lathril selected unavailable drain"); check(f.game.getStack().isEmpty(),"Illegal drain stacked");
        });
        test("Talrand does not counter an empty stack",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.identifyCommander(commander.getId());
            Card counter=f.hand("c.Counterspell","Counterspell"); f.mana();
            check(!f.bot.priority(f.game),"Counterspell acted on empty stack"); check(f.bot.getHand().contains(counter.getId()),"Counterspell wasted");
        });
        test("Talrand counters a threat while at two life",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.identifyCommander(commander.getId());
            Card counter=f.hand("c.Counterspell","Counterspell"); f.mana(); f.bot.initLife(2);
            Card enemy=f.card("g.GrizzlyBears","Grizzly Bears"); enemy.setOwnerId(f.foes[0].getId()); enemy.getAbilities().setControllerId(f.foes[0].getId());
            f.foes[0].getHand().add(enemy); f.game.getState().setZone(enemy.getId(),Zone.HAND);
            f.game.getState().setActivePlayerId(f.foes[0].getId());
            f.foes[0].getManaPool().addMana(Mana.GreenMana(2),f.game,enemy.getSpellAbility());
            check(f.foes[0].activateAbility(enemy.getSpellAbility(),f.game),"Fixture opponent spell failed to cast");
            check(f.game.getStack().size()==1,"Fixture must have a real enemy spell on stack");
            check(f.bot.priority(f.game),"Talrand failed legal counter response");
            check(!f.bot.getHand().contains(counter.getId()),"Counterspell not cast");
            check(f.game.getStack().size()>=2,"Counter response absent from stack");
        });
        test("Talrand preserves counter for a harmless opposing Bear",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.identifyCommander(commander.getId());
            Card counter=f.hand("c.Counterspell","Counterspell"); f.mana();
            Card enemy=f.card("g.GrizzlyBears","Grizzly Bears"); enemy.setOwnerId(f.foes[0].getId()); enemy.getAbilities().setControllerId(f.foes[0].getId());
            f.foes[0].getHand().add(enemy); f.game.getState().setZone(enemy.getId(),Zone.HAND);
            f.game.getState().setActivePlayerId(f.foes[0].getId());
            f.foes[0].getManaPool().addMana(Mana.GreenMana(2),f.game,enemy.getSpellAbility());
            check(f.foes[0].activateAbility(enemy.getSpellAbility(),f.game),"Fixture opponent spell failed to cast");
            check(f.game.getStack().size()==1,"Fixture must have a real enemy spell on stack");
            check(!f.bot.priority(f.game),"Talrand wasted Counterspell on a low-threat Bear");
            check(f.bot.getHand().contains(counter.getId()),"Counterspell consumed on harmless spell");
            check(f.game.getStack().size()==1,"Unexpected response to harmless spell");
        });
        test("Talrand never counters its own spell",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.identifyCommander(commander.getId());
            Card counter=f.hand("c.Counterspell","Counterspell"); Card opt=f.hand("o.Opt","Opt"); f.mana();
            check(f.bot.activateAbility(opt.getSpellAbility(),f.game),"Own Opt fixture failed");
            check(!f.bot.priority(f.game),"Talrand countered own spell"); check(f.bot.getHand().contains(counter.getId()),"Own counter was consumed");
        });
        test("Talrand reserves UU rather than main-phase cantrip",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.identifyCommander(commander.getId());
            Card opt=f.hand("o.Opt","Opt"); Card counter=f.hand("c.Counterspell","Counterspell");
            f.bot.getManaPool().addMana(Mana.BlueMana(2),f.game,opt.getSpellAbility());
            check(!f.bot.priority(f.game),"Talrand spent reserved counter mana in own main"); check(f.bot.getHand().contains(opt.getId()),"Main-phase cantrip consumed");
        });
        test("Talrand uses cantrip at opponent end step",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.identifyCommander(commander.getId());
            Card opt=f.hand("o.Opt","Opt"); f.hand("c.Counterspell","Counterspell"); f.bot.getManaPool().addMana(Mana.BlueMana(2),f.game,opt.getSpellAbility());
            EndPhase phase=new EndPhase(); phase.setStep(new EndStep()); f.game.getTurn().setPhase(phase); f.game.getState().setActivePlayerId(f.foes[2].getId());
            check(f.bot.priority(f.game),"Talrand failed end-step cantrip"); check(!f.bot.getHand().contains(opt.getId()),"End-step Opt not cast");
        });
        test("Ghalta actual power discount makes two-green cast legal",()->{
            Fixture f=new Fixture(); f.board("g.Gigantosaurus","Gigantosaurus"); Card commander=f.command("g.GhaltaPrimalHunger","Ghalta, Primal Hunger");
            f.bot.getManaPool().addMana(Mana.GreenMana(2),f.game,commander.getSpellAbility());
            check(f.bot.priority(f.game),"Ghalta discount was not used"); check(f.game.getStack().size()==1,"Discounted Ghalta not stacked"); check(f.bot.getManaPool().getMana().count()==0,"Ghalta GG payment incorrect");
        });
        test("Lathril combat preserves a live ten-elf drain",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.identifyCommander(commander.getId());
            for(int i=0;i<10;i++) f.board("l.LlanowarElves","Llanowar Elves");
            CombatPhase phase=new CombatPhase(); phase.setStep(new DeclareAttackersStep()); f.game.getTurn().setPhase(phase);
            f.game.getCombat().setAttacker(f.bot.getId()); f.game.getCombat().setDefenders(f.game);
            f.bot.selectAttackers(f.game,f.bot.getId());
            int untapped=0; for(mage.game.permanent.Permanent p:f.game.getBattlefield().getAllActivePermanents(f.bot.getId())) if(!p.isTapped()) untapped++;
            check(untapped>=11,"Combat consumed Lathril's live ten-elf activation");
        });
        test("Ghalta combat chooses available lethal opponent",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GhaltaPrimalHunger","Ghalta, Primal Hunger"); f.identifyCommander(commander.getId());
            f.foes[1].initLife(10);
            CombatPhase phase=new CombatPhase(); phase.setStep(new DeclareAttackersStep()); f.game.getTurn().setPhase(phase);
            f.game.getCombat().setAttacker(f.bot.getId()); f.game.getCombat().setDefenders(f.game);
            f.bot.selectAttackers(f.game,f.bot.getId());
            check(f.game.getCombat().getAttackers().contains(commander.getId()),"Ghalta failed profitable attack");
            check(f.foes[1].getId().equals(f.game.getCombat().getDefendingPlayerId(commander.getId(),f.game)),"Ghalta ignored available lethal seat");
        });
        test("Giada is established before a follow-up Angel",()->{
            Fixture f=new Fixture(); Card giada=f.hand("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(giada.getId());
            Card angel=f.hand("b.BaneslayerAngel","Baneslayer Angel"); f.mana();
            check(f.bot.priority(f.game),"Giada sequence passed"); check(!f.bot.getHand().contains(giada.getId()) && f.bot.getHand().contains(angel.getId()),"Angel cast before Giada's counter engine");
        });
        test("Giada preserves untargeted protection",()->{
            Fixture f=new Fixture(); PermanentCard giada=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(giada.getId());
            Card protection=f.hand("f.FlawlessManeuver","Flawless Maneuver"); f.mana();
            check(!f.bot.priority(f.game),"Protection wasted without threat"); check(f.bot.getHand().contains(protection.getId()),"Flawless Maneuver consumed on empty stack");
        });
        test("Goreclaw develops rather than casting a postcombat finisher",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GoreclawTerrorOfQalSisma","Goreclaw, Terror of Qal Sisma"); f.identifyCommander(commander.getId());
            Card finisher=f.hand("o.OverwhelmingStampede","Overwhelming Stampede"); Card body=f.hand("q.QuestingBeast","Questing Beast"); f.mana();
            PostCombatMainPhase phase=new PostCombatMainPhase(); phase.setStep(new PostCombatMainStep()); f.game.getTurn().setPhase(phase);
            check(f.bot.priority(f.game),"Goreclaw passed development"); check(f.bot.getHand().contains(finisher.getId()) && !f.bot.getHand().contains(body.getId()),"Temporary pump wasted after combat");
        });
        test("Torbran amplified Bolt selects a lethal opponent",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TorbranThaneOfRedFell","Torbran, Thane of Red Fell"); f.identifyCommander(commander.getId());
            Card bolt=f.hand("l.LightningBolt","Lightning Bolt"); f.mana(); f.foes[2].initLife(5);
            check(f.bot.priority(f.game),"Torbran ignored lethal five-damage Bolt");
            boolean targeted=false; for(mage.game.stack.StackObject spell:f.game.getStack()) for(mage.target.Target target:spell.getStackAbility().getTargets()) if(target.getTargets().contains(f.foes[2].getId())) targeted=true;
            check(targeted,"Bolt failed to choose lethal opponent");
        });
        test("Aesi precedes land when already funded",()->{
            Fixture f=new Fixture(); Card commander=f.hand("a.AesiTyrantOfGyreStrait","Aesi, Tyrant of Gyre Strait"); f.identifyCommander(commander.getId());
            Card land=f.hand("basiclands.Forest","Forest"); f.mana();
            check(f.bot.priority(f.game),"Aesi sequence passed"); check(!f.bot.getHand().contains(commander.getId()) && f.bot.getHand().contains(land.getId()),"Land played before already-payable Aesi, losing landfall draw");
        });
        test("Aesi still plays land when commander is not payable",()->{
            Fixture f=new Fixture(); Card commander=f.hand("a.AesiTyrantOfGyreStrait","Aesi, Tyrant of Gyre Strait"); f.identifyCommander(commander.getId()); Card land=f.hand("basiclands.Forest","Forest");
            check(f.bot.priority(f.game),"Aesi stalled land development"); check(f.bot.getHand().contains(commander.getId()) && !f.bot.getHand().contains(land.getId()),"Unfunded Aesi prevented legal land drop");
        });
        test("Torbran does not assume five damage without commander on board",()->{
            Fixture f=new Fixture(); Card commander=f.card("t.TorbranThaneOfRedFell","Torbran, Thane of Red Fell"); f.identifyCommander(commander.getId()); f.game.getState().setZone(commander.getId(),Zone.GRAVEYARD); f.bot.getGraveyard().add(commander);
            Card bolt=f.hand("l.LightningBolt","Lightning Bolt"); f.mana(); f.foes[2].initLife(5);
            check(!f.bot.priority(f.game),"Missing Torbran incorrectly amplified Bolt"); check(f.bot.getHand().contains(bolt.getId()),"Bolt spent on nonlethal low-value face target");
        });
        test("Gishath attacks the unblocked opponent",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GishathSunsAvatar","Gishath, Sun's Avatar"); f.identifyCommander(commander.getId());
            f.enemyBoard(0,"g.Gigantosaurus","Gigantosaurus"); f.enemyBoard(2,"g.Gigantosaurus","Gigantosaurus"); f.combat();
            f.bot.selectAttackers(f.game,f.bot.getId());
            check(f.foes[1].getId().equals(f.game.getCombat().getDefendingPlayerId(commander.getId(),f.game)),"Gishath missed unblocked damage-trigger destination");
        });
        test("Adeline can trigger while staying back",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("a.AdelineResplendentCathar","Adeline, Resplendent Cathar"); f.identifyCommander(commander.getId());
            PermanentCard attacker=f.board("s.SuntailHawk","Suntail Hawk");
            for(int i=0;i<3;i++) f.enemyBoard(i,"g.Gigantosaurus","Gigantosaurus"); f.combat();
            f.bot.selectAttackers(f.game,f.bot.getId());
            check(!f.game.getCombat().getAttackers().contains(commander.getId()),"Adeline suicided into 10/10 blockers");
            check(f.game.getCombat().getAttackers().contains(attacker.getId()),"Other creature failed to attack for Adeline's trigger");
        });
        test("Kamahl animates a land once rather than repeatedly paying for no change",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GoreclawTerrorOfQalSisma","Goreclaw, Terror of Qal Sisma"); f.identifyCommander(commander.getId());
            PermanentCard kamahl=f.board("k.KamahlHeartOfKrosa","Kamahl, Heart of Krosa"); PermanentCard forest=f.board("basiclands.Forest","Forest"); f.mana(f.game.getCard(kamahl.getId()));
            check(f.bot.priority(f.game),"Kamahl legal land animation failed"); f.game.getStack().resolve(f.game); f.game.applyEffects(); f.bot.resetPassed();
            check(f.game.getPermanent(forest.getId()).isCreature(f.game),"Kamahl activation did not animate real land");
            check(!f.bot.priority(f.game),"Kamahl repeatedly animates already-animated only land");
        });
        test("Gwenna restricted mana pays Kamahl creature activation",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GoreclawTerrorOfQalSisma","Goreclaw, Terror of Qal Sisma"); f.identifyCommander(commander.getId());
            f.board("k.KamahlHeartOfKrosa","Kamahl, Heart of Krosa"); PermanentCard gwenna=f.board("g.GwennaEyesOfGaea","Gwenna, Eyes of Gaea"); PermanentCard forest=f.board("m.MazeOfIth","Maze of Ith");
            check(f.bot.priority(f.game),"Gwenna's valid creature-activation mana failed Kamahl payment");
            check(f.game.getPermanent(gwenna.getId()).isTapped(),"Gwenna's mana source cost was not paid");
            f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(f.game.getPermanent(forest.getId()).isCreature(f.game),"Kamahl failed to animate land with Gwenna mana");
        });
        test("Gwenna supplies both green pips for a real double-green creature",()->{
            Fixture f=new Fixture(); f.command("g.GhaltaPrimalHunger","Ghalta, Primal Hunger"); PermanentCard gwenna=f.board("g.GwennaEyesOfGaea","Gwenna, Eyes of Gaea");
            Card beast=f.hand("q.QuestingBeast","Questing Beast"); f.bot.getManaPool().addMana(Mana.ColorlessMana(2),f.game,beast.getSpellAbility());
            check(f.bot.priority(f.game),"Gwenna failed GG allocation for Questing Beast"); check(!f.bot.getHand().contains(beast.getId()),"Double-green creature not cast");
            check(f.game.getPermanent(gwenna.getId()).isTapped(),"Gwenna not tapped for GG"); check(f.bot.getManaPool().getMana().count()==0,"2GG payment incorrect");
        });
        test("Swiftfoot Boots does not re-equip its existing wearer",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(commander.getId());
            PermanentCard boots=f.board("s.SwiftfootBoots","Swiftfoot Boots"); f.mana(f.game.getCard(boots.getId()));
            check(f.bot.priority(f.game),"Boots failed initial equip"); f.game.getStack().resolve(f.game); f.game.applyEffects(); f.bot.resetPassed();
            check(!f.bot.priority(f.game),"Boots repeatedly equips same sole creature");
        });
        test("Giada names Angel when actual Banner resolves",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(commander.getId());
            Card banner=f.hand("v.VanquishersBanner","Vanquisher's Banner"); f.mana();
            check(f.bot.priority(f.game),"Banner cast failed"); f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(mage.abilities.effects.common.ChooseCreatureTypeEffect.getChosenCreatureType(banner.getId(),f.game)==SubType.ANGEL,"Giada chose wrong Banner creature type");
        });
        test("Boros Charm chooses indestructible and survives an actual Wrath",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GishathSunsAvatar","Gishath, Sun's Avatar"); f.identifyCommander(commander.getId());
            Card charm=f.hand("b.BorosCharm","Boros Charm"); f.mana(); Card wrath=f.enemyHand("w.WrathOfGod","Wrath of God"); f.castEnemy(wrath);
            check(f.bot.priority(f.game),"Boros Charm failed to respond to Wrath"); check(!f.bot.getHand().contains(charm.getId()),"Boros Charm not cast");
            f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(f.game.getPermanent(commander.getId()).hasAbility(mage.abilities.keyword.IndestructibleAbility.getInstance(),f.game),"Boros Charm chose damage/double strike instead of indestructible");
            f.game.getStack().resolve(f.game); f.game.applyEffects(); check(f.game.getPermanent(commander.getId())!=null,"Commander died despite protective Charm response");
        });
        test("Tyvar's Stand protects the threatened Elf, not another own creature",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.identifyCommander(commander.getId()); f.board("l.LlanowarElves","Llanowar Elves");
            Card stand=f.hand("t.TyvarsStand","Tyvar's Stand"); f.mana(); Card removal=f.enemyHand("s.SwordsToPlowshares","Swords to Plowshares"); removal.getSpellAbility().getTargets().get(0).addTarget(commander.getId(),removal.getSpellAbility(),f.game); f.castEnemy(removal);
            check(f.bot.priority(f.game),"Tyvar's Stand failed to respond"); boolean correct=false; for(mage.game.stack.StackObject spell:f.game.getStack()) if(spell.getName().equals("Tyvar's Stand")) for(mage.target.Target target:spell.getStackAbility().getTargets()) if(target.getTargets().contains(commander.getId()))correct=true;
            check(correct,"Tyvar's Stand chose an unthreatened Elf"); f.game.getStack().resolve(f.game); f.game.applyEffects(); f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(f.game.getPermanent(commander.getId())!=null,"Threatened commander was not saved");
        });
        test("Get a Leg Up buffs own attacker rather than enemy Giada",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.identifyCommander(commander.getId()); f.board("l.LlanowarElves","Llanowar Elves"); f.enemyBoard(0,"g.GiadaFontOfHope","Giada, Font of Hope");
            Card buff=f.hand("g.GetALegUp","Get a Leg Up"); f.mana(); f.combat(); f.bot.declareAttacker(commander.getId(),f.foes[1].getId(),f.game,false); f.game.getPhase().setStep(new DeclareBlockersStep());
            check(f.bot.priority(f.game),"Combat pump not cast"); boolean correct=false; for(mage.game.stack.StackObject spell:f.game.getStack()) if(spell.getName().equals("Get a Leg Up")) for(mage.target.Target target:spell.getStackAbility().getTargets()) if(target.getTargets().contains(commander.getId()))correct=true;
            String actual=""; for(mage.game.stack.StackObject spell:f.game.getStack()) for(mage.target.Target t:spell.getStackAbility().getTargets()) for(UUID id:t.getTargets()) actual+=f.game.getObject(id).getName()+" "; check(correct,"Get a Leg Up did not choose own attacking Elf; actual targets="+actual);
        });
        test("Get a Leg Up is not wasted during upkeep",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.identifyCommander(commander.getId()); f.board("l.LlanowarElves","Llanowar Elves");
            Card buff=f.hand("g.GetALegUp","Get a Leg Up"); f.mana(); BeginningPhase phase=new BeginningPhase(); phase.setStep(new UpkeepStep()); f.game.getTurn().setPhase(phase);
            check(!f.bot.priority(f.game),"Temporary pump wasted on upkeep"); check(f.bot.getHand().contains(buff.getId()),"Upkeep consumed Get a Leg Up");
        });
        test("Goblin Grenade sacrifices expendable Goblin, not Krenko",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("k.KrenkoMobBoss","Krenko, Mob Boss"); f.identifyCommander(commander.getId()); commander.setTapped(true); PermanentCard fodder=f.board("g.GoblinArsonist","Goblin Arsonist");
            Card grenade=f.hand("g.GoblinGrenade","Goblin Grenade"); f.mana(); f.foes[1].initLife(5);
            check(f.bot.priority(f.game),"Lethal Goblin Grenade not cast"); check(!f.bot.getHand().contains(grenade.getId()),"Grenade remained in hand");
            check(f.game.getPermanent(commander.getId())!=null && f.game.getPermanent(fodder.getId())==null,"Grenade sacrificed Krenko instead of expendable Goblin");
        });
        for(final int targets:new int[]{1,2}) test("By Force pays affordable X for "+targets+" actual artifact targets",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TorbranThaneOfRedFell","Torbran, Thane of Red Fell"); f.identifyCommander(commander.getId());
            PermanentCard first=f.enemyBoard(0,"t.TheGreatHenge","The Great Henge"); PermanentCard second=targets==2?f.enemyBoard(1,"p.PortalToPhyrexia","Portal to Phyrexia"):null;
            Card spell=f.hand("b.ByForce","By Force"); f.bot.getManaPool().addMana(Mana.RedMana(targets+1),f.game,spell.getSpellAbility());
            check(f.bot.priority(f.game),"By Force could not pay valid X="+targets); check(!f.bot.getHand().contains(spell.getId()),"By Force did not leave hand");
            f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(f.game.getPermanent(first.getId())==null,"By Force missed actual first artifact; mana left="+f.bot.getManaPool().getMana().count()); if(second!=null)check(f.game.getPermanent(second.getId())==null,"By Force did not destroy second affordable target");
        });
        test("Entreat the Angels pays XX plus WWW and produces two Angels",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(commander.getId());
            Card spell=f.hand("e.EntreatTheAngels","Entreat the Angels"); f.bot.getManaPool().addMana(Mana.WhiteMana(7),f.game,spell.getSpellAbility());
            check(f.bot.priority(f.game),"Entreat failed affordable X=2 with seven mana"); check(!f.bot.getHand().contains(spell.getId()),"Entreat remained in hand");
            f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(f.game.getBattlefield().getAllActivePermanents(f.bot.getId()).size()==3,"Entreat did not make exactly two real Angels; permanents="+f.game.getBattlefield().getAllActivePermanents(f.bot.getId()).size()+" mana left="+f.bot.getManaPool().getMana().count()); check(f.bot.getManaPool().getMana().count()==0,"Entreat XX payment incorrect");
        });
        test("Castle Embereth reserves its own tap while other lands pay",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TorbranThaneOfRedFell","Torbran, Thane of Red Fell"); f.identifyCommander(commander.getId()); PermanentCard castle=f.board("c.CastleEmbereth","Castle Embereth");
            for(int i=0;i<3;i++) f.board("basiclands.Mountain","Mountain");
            check(f.bot.priority(f.game),"Castle failed with three other mana sources"); check(f.game.getPermanent(castle.getId()).isTapped(),"Castle source tap not paid"); f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(f.game.getPermanent(commander.getId()).getPower().getValue()==3,"Castle pump did not resolve");
        });
        test("Myriad Landscape pays from other lands and resolves real search",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("a.AesiTyrantOfGyreStrait","Aesi, Tyrant of Gyre Strait"); f.identifyCommander(commander.getId()); PermanentCard myriad=f.board("m.MyriadLandscape","Myriad Landscape");
            for(int i=0;i<2;i++){ f.board("basiclands.Forest","Forest"); Card land=f.card("basiclands.Island","Island"); f.bot.getLibrary().putOnTop(land,f.game); }
            check(f.bot.priority(f.game),"Myriad failed with two other mana sources"); check(f.game.getPermanent(myriad.getId())==null,"Myriad sacrifice cost not paid"); f.game.getStack().resolve(f.game); f.game.applyEffects();
            int islands=0; for(mage.game.permanent.Permanent p:f.game.getBattlefield().getAllActivePermanents(f.bot.getId())) if(p.getName().equals("Island")){islands++;check(p.isTapped(),"Myriad land should enter tapped");}
            check(islands==2,"Myriad did not fetch two actual same-type basics");
        });
        test("Entreat chooses X one with five white mana",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(commander.getId()); Card spell=f.hand("e.EntreatTheAngels","Entreat the Angels"); f.bot.getManaPool().addMana(Mana.WhiteMana(5),f.game,spell.getSpellAbility());
            check(f.bot.priority(f.game),"Entreat X1 cast failed"); f.game.getStack().resolve(f.game); f.game.applyEffects(); check(f.game.getBattlefield().getAllActivePermanents(f.bot.getId()).size()==2,"Five-mana Entreat did not make one Angel");
        });
        test("Entreat cannot substitute colorless for WWW",()->{
            Fixture f=new Fixture(); Card spell=f.hand("e.EntreatTheAngels","Entreat the Angels"); f.bot.getManaPool().addMana(Mana.ColorlessMana(7),f.game,spell.getSpellAbility());
            check(!f.bot.priority(f.game),"Colorless paid required WWW"); check(f.bot.getHand().contains(spell.getId()),"Unaffordable Entreat left hand");
        });
        test("Entreat uses a multi-mana producer without double counting",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(commander.getId());
            f.board("s.SolRing","Sol Ring"); for(int i=0;i<3;i++)f.board("basiclands.Plains","Plains"); Card spell=f.hand("e.EntreatTheAngels","Entreat the Angels");
            check(f.bot.priority(f.game),"Sol Ring and three Plains failed Entreat"); f.game.getStack().resolve(f.game); f.game.applyEffects(); int angels=0;for(mage.game.permanent.Permanent p:f.game.getBattlefield().getAllActivePermanents(f.bot.getId()))if(p.isCreature(f.game)&&p.hasSubtype(SubType.ANGEL,f.game))angels++;
            check(angels==2,"Sol Ring+WWW should produce one token, not overcount available mana");
        });
        test("Flare of Fortitude can pay normal mana to preserve its creatures",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(commander.getId()); PermanentCard angel=f.board("b.BaneslayerAngel","Baneslayer Angel");
            Card flare=f.hand("f.FlareOfFortitude","Flare of Fortitude"); f.bot.getManaPool().addMana(Mana.WhiteMana(4),f.game,flare.getSpellAbility()); Card wrath=f.enemyHand("w.WrathOfGod","Wrath of God"); f.castEnemy(wrath);
            check(f.bot.priority(f.game),"Flare failed affordable response to Wrath");
            check(f.game.getPermanent(commander.getId())!=null && f.game.getPermanent(angel.getId())!=null,"Flare sacrificed valuable creature despite normal mana being available");
            f.game.getStack().resolve(f.game); f.game.applyEffects(); f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(f.game.getPermanent(commander.getId())!=null && f.game.getPermanent(angel.getId())!=null,"Flare failed to preserve creatures from Wrath");
        });
        test("Myriad with only one other land is rejected without failed activation",()->{
            Fixture f=new Fixture(); PermanentCard myriad=f.board("m.MyriadLandscape","Myriad Landscape"); f.board("basiclands.Plains","Plains");
            int before=TRACE.failures; check(!f.bot.priority(f.game),"Underfunded Myriad acted"); check(TRACE.failures==before,"Underfunded Myriad was attempted then failed");
            check(f.game.getPermanent(myriad.getId())!=null && !f.game.getPermanent(myriad.getId()).isTapped(),"Underfunded Myriad mutated its source");
        });
        for(final String[] spec:new String[][]{{"a.AdelineResplendentCathar","Adeline, Resplendent Cathar","White"},{"t.TalrandSkySummoner","Talrand, Sky Summoner","Blue"}}) test(spec[1]+" chooses correct Heraldic Banner color",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board(spec[0],spec[1]); f.identifyCommander(commander.getId()); Card banner=f.hand("h.HeraldicBanner","Heraldic Banner"); f.mana();
            check(f.bot.priority(f.game),"Heraldic Banner not cast"); f.game.getStack().resolve(f.game); f.game.applyEffects();
            mage.ObjectColor color=(mage.ObjectColor)f.game.getState().getValue(banner.getId()+"_color"); check(color!=null && (spec[2].equals("White")?color.isWhite():color.isBlue()),"Heraldic Banner chose wrong color: "+color);
        });
        test("Flare without mana sacrifices expendable white creature",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(commander.getId()); PermanentCard angel=f.board("b.BaneslayerAngel","Baneslayer Angel"); PermanentCard fodder=f.board("t.ThrabenInspector","Thraben Inspector");
            Card flare=f.hand("f.FlareOfFortitude","Flare of Fortitude"); f.castEnemy(f.enemyHand("w.WrathOfGod","Wrath of God")); int before=TRACE.failures;
            check(f.bot.priority(f.game),"Flare did not choose payable alternate sacrifice"); check(TRACE.failures==before,"Flare chose an unaffordable cost option");
            check(f.game.getPermanent(fodder.getId())==null && f.game.getPermanent(commander.getId())!=null && f.game.getPermanent(angel.getId())!=null,"Flare did not sacrifice expendable white creature");
            f.game.getStack().resolve(f.game); f.game.applyEffects(); f.game.getStack().resolve(f.game); f.game.applyEffects(); check(f.game.getPermanent(commander.getId())!=null && f.game.getPermanent(angel.getId())!=null,"Alternate Flare failed to preserve valuable creatures");
        });
        test("Giada restricted mana does not fund a non-Angel artifact",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(commander.getId()); Card ring=f.hand("s.SolRing","Sol Ring"); int before=TRACE.failures;
            check(!f.bot.priority(f.game),"Giada incorrectly paid Sol Ring"); check(TRACE.failures==before,"Illegal Giada-funded artifact was attempted"); check(f.bot.getHand().contains(ring.getId()),"Non-Angel artifact left hand");
        });
        test("Giada restricted white mana pays an actual Angel spell",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.identifyCommander(commander.getId()); Card angel=f.hand("i.InspiringOverseer","Inspiring Overseer"); f.bot.getManaPool().addMana(Mana.ColorlessMana(2),f.game,angel.getSpellAbility());
            check(f.bot.priority(f.game),"Giada did not pay valid Angel cost"); check(!f.bot.getHand().contains(angel.getId()) && f.game.getPermanent(commander.getId()).isTapped(),"Angel payment did not use Giada");
        });
        test("Selvala dependent mana preserves activation green to cast commander",()->{
            Fixture f=new Fixture(); Card commander=f.command("g.GoreclawTerrorOfQalSisma","Goreclaw, Terror of Qal Sisma"); PermanentCard selvala=f.board("s.SelvalaHeartOfTheWilds","Selvala, Heart of the Wilds"); f.board("g.Gigantosaurus","Gigantosaurus"); PermanentCard forest=f.board("basiclands.Forest","Forest"); int before=TRACE.failures;
            check(f.bot.priority(f.game),"Selvala dependent mana failed commander cast"); check(TRACE.failures==before,"Selvala payment attempted an impossible order");
            if(f.game.getState().getZone(commander.getId())!=Zone.STACK) RecoveryChecks.cast(f,commander,3); check(f.bot.getManaPool().getMana().count()==6,"Selvala ten output must pay four commander mana"); check(f.game.getState().getZone(commander.getId())==Zone.STACK,"Commander not cast with dependent mana"); check(f.game.getPermanent(selvala.getId()).isTapped()&&f.game.getPermanent(forest.getId()).isTapped(),"Selvala or green activation source not paid");
        });
        test("Golgari Charm regenerates Lathril through actual Murder",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.identifyCommander(commander.getId()); Card charm=f.hand("g.GolgariCharm","Golgari Charm"); f.mana(); Card murder=f.enemyHand("m.Murder","Murder"); murder.getSpellAbility().getTargets().get(0).addTarget(commander.getId(),murder.getSpellAbility(),f.game); f.castEnemy(murder);
            check(f.bot.priority(f.game),"Golgari Charm did not answer regenerable destruction"); f.game.getStack().resolve(f.game); f.game.applyEffects(); f.game.getStack().resolve(f.game); f.game.applyEffects();
            check(f.game.getPermanent(commander.getId())!=null && f.game.getPermanent(commander.getId()).isTapped(),"Golgari Charm did not select functional regeneration mode");
        });
        test("Golgari Charm does not pretend regeneration stops exile",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.identifyCommander(commander.getId()); Card charm=f.hand("g.GolgariCharm","Golgari Charm"); f.mana(); Card removal=f.enemyHand("s.SwordsToPlowshares","Swords to Plowshares"); removal.getSpellAbility().getTargets().get(0).addTarget(commander.getId(),removal.getSpellAbility(),f.game); f.castEnemy(removal);
            check(!f.bot.priority(f.game),"Golgari Charm wasted regeneration against exile"); check(f.bot.getHand().contains(charm.getId()),"Golgari Charm consumed despite no useful mode");
        });
        for(final int mana:new int[]{2,4}) test("Tender Wildguide chooses affordable Offspring with "+mana+" mana",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GoreclawTerrorOfQalSisma","Goreclaw, Terror of Qal Sisma"); f.identifyCommander(commander.getId()); Card tender=f.hand("t.TenderWildguide","Tender Wildguide"); f.bot.getManaPool().addMana(Mana.GreenMana(mana),f.game,tender.getSpellAbility()); int before=TRACE.failures;
            check(f.bot.priority(f.game),"Tender failed cast with "+mana+" mana"); check(TRACE.failures==before,"Tender chose unaffordable optional cost"); f.game.getStack().resolve(f.game); f.game.applyEffects(); f.game.checkStateAndTriggered();
            for(int i=0;i<5 && !f.game.getStack().isEmpty();i++){f.game.getStack().resolve(f.game); f.game.applyEffects(); f.game.checkStateAndTriggered();}
            int copies=0,small=0; for(mage.game.permanent.Permanent p:f.game.getBattlefield().getAllActivePermanents(f.bot.getId()))if(p.getName().equals("Tender Wildguide")){copies++;if(p.getPower().getValue()==1&&p.getToughness().getValue()==1)small++;}
            check(copies==(mana==4?2:1),"Tender wrong Offspring result for available mana"); check(small==(mana==4?1:0),"Offspring token is not exactly one 1/1 when paid");
        });
        for(final int mana:new int[]{1,2}) test("Smuggler's Surprise pays only an affordable Spree mode with "+mana+" mana",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GoreclawTerrorOfQalSisma","Goreclaw, Terror of Qal Sisma"); f.identifyCommander(commander.getId()); Card surprise=f.hand("s.SmugglersSurprise","Smuggler's Surprise"); f.bot.getManaPool().addMana(Mana.GreenMana(mana),f.game,surprise.getSpellAbility());
            Card murder=f.enemyHand("m.Murder","Murder"); murder.getSpellAbility().getTargets().get(0).addTarget(commander.getId(),murder.getSpellAbility(),f.game); f.castEnemy(murder); int before=TRACE.failures;
            if(mana==1){check(!f.bot.priority(f.game),"Spree ignored selected-mode surcharge");check(f.bot.getHand().contains(surprise.getId()),"Unfunded Spree left hand");}
            else{check(f.bot.priority(f.game),"Affordable protection Spree failed"); check(f.bot.getManaPool().getMana().count()==0,"Spree did not pay exactly baseG plus surcharge1");f.game.getStack().resolve(f.game);f.game.applyEffects();f.game.getStack().resolve(f.game);f.game.applyEffects();check(f.game.getPermanent(commander.getId())!=null,"Spree did not choose protective affordable mode");}
            check(TRACE.failures==before,"Spree attempted an unaffordable cost combination");
        });
        for(final boolean enough:new boolean[]{false,true}) test("Selvala dependent commander-tax recast "+(enough?"succeeds when funded":"is rejected when short"),()->{
            Fixture f=new Fixture(); Card commander=f.command("g.GoreclawTerrorOfQalSisma","Goreclaw, Terror of Qal Sisma"); f.bot.getManaPool().addMana(Mana.GreenMana(4),f.game,commander.getSpellAbility()); check(f.bot.priority(f.game),"Initial commander cast failed"); f.game.getStack().resolve(f.game); f.game.applyEffects(); check(commander.moveToZone(Zone.COMMAND,null,f.game,true),"Tax fixture return failed"); f.game.applyEffects(); f.bot.getManaPool().init(); f.bot.resetPassed();
            PermanentCard selvala=f.board("s.SelvalaHeartOfTheWilds","Selvala, Heart of the Wilds"); f.board(enough?"g.Gigantosaurus":"r.RumblingBaloth",enough?"Gigantosaurus":"Rumbling Baloth"); PermanentCard forest=f.board("basiclands.Forest","Forest"); int before=TRACE.failures;
            boolean acted=f.bot.priority(f.game); check(acted==enough,"Incorrect Selvala tax affordability result"); check(TRACE.failures==before,"Taxed Selvala candidate failed after admission");
            if(enough){if(f.game.getState().getZone(commander.getId())!=Zone.STACK) RecoveryChecks.cast(f,commander,3);check(f.game.getState().getZone(commander.getId())==Zone.STACK,"Taxed commander not stacked"); check(f.bot.getManaPool().getMana().count()==4,"Selvala ten-mana output must pay all six taxed mana");}
            else{check(f.game.getState().getZone(commander.getId())==Zone.COMMAND,"Unaffordable taxed commander moved");check(!f.game.getPermanent(selvala.getId()).isTapped()&&!f.game.getPermanent(forest.getId()).isTapped(),"Rejected dependent mana should leave sources unused");}
        });
        test("Failed real spell payment restores transient context on rollback player",()->{
            Fixture f=new Fixture(); f.game.initializeRollbackOrder(); Card spell=f.hand("g.GrizzlyBears","Grizzly Bears");
            check(!f.bot.activateAbility(spell.getSpellAbility(),f.game),"Unfunded raw cast unexpectedly succeeded");
            mage.players.Player current=f.game.getPlayer(f.bot.getId());
            check(f.game.rollbackCount==1,"Fixture did not exercise an actual completed engine rollback");
            // This pinned fork restores players in place (GameState.restore), rather than replacing them.
            check(current.getHand().contains(spell.getId()) && f.game.getStack().isEmpty(),"Failed cast did not restore actual hand and stack");
            for(String name:new String[]{"activatingAbility","currentUnpaid","reservedTapSource","prefundingMana"}) {
                java.lang.reflect.Field field=ComputerPlayerFourthSeat.class.getDeclaredField(name); field.setAccessible(true);
                Object value=field.get(current);
                check(name.equals("prefundingMana")?Boolean.FALSE.equals(value):value==null,"Stale rollback context: "+name+"="+value);
            }
        });
        for(final int lands:new int[]{1,2}) test("Nykthos paid activation with "+lands+" other mana sources",()->{
            Fixture f=new Fixture(); f.game.initializeRollbackOrder(); PermanentCard nyk=f.board("n.NykthosShrineToNyx","Nykthos, Shrine to Nyx");
            f.board("a.ArchangelOfTithes","Archangel of Tithes"); f.board("b.BaneslayerAngel","Baneslayer Angel");
            for(int i=0;i<lands;i++)f.board("basiclands.Plains","Plains");
            Card spell=f.hand("a.ArchangelOfThune","Archangel of Thune");int before=TRACE.failures;
            boolean acted=f.bot.priority(f.game);check(acted==(lands==2),"Incorrect Nykthos input affordability");
            check(TRACE.failures==before,"Nykthos attempted an unpayable or self-tapping activation");
            if(lands==2&&f.game.getState().getZone(spell.getId())!=Zone.STACK) RecoveryChecks.cast(f,spell,4); check(f.game.getState().getZone(spell.getId())==(lands==2?Zone.STACK:Zone.HAND),"Wrong actual spell zone"); if(lands==2)check(f.bot.getManaPool().getMana().count()==0,"Nykthos five output must pay five spell mana");
            if(lands==2)check(f.game.getPermanent(nyk.getId()).isTapped(),"Nykthos output not paid");
        });
        test("Nykthos chooses productive devotion for generic artifact",()->{
            Fixture f=new Fixture(); f.game.initializeRollbackOrder(); f.board("n.NykthosShrineToNyx","Nykthos, Shrine to Nyx");f.board("a.ArchangelOfTithes","Archangel of Tithes");f.board("b.BaneslayerAngel","Baneslayer Angel");
            f.board("basiclands.Plains","Plains");f.board("basiclands.Plains","Plains"); Card spell=f.hand("c.CoatOfArms","Coat of Arms"); int before=TRACE.failures;
            check(f.bot.priority(f.game),"Generic artifact failed despite five white devotion"); check(TRACE.failures==before,"Generic Nykthos selected unproductive color or self tap");if(f.game.getState().getZone(spell.getId())!=Zone.STACK) RecoveryChecks.cast(f,spell,4);check(f.game.getState().getZone(spell.getId())==Zone.STACK,"Artifact not stacked");check(f.bot.getManaPool().getMana().count()==0,"Generic Nykthos five-mana cost unpaid");
        });
        for(final boolean stocked:new boolean[]{false,true}) test("Aesi optional landfall draw with "+(stocked?"nonempty":"empty")+" library",()->{
            Fixture f=new Fixture();PermanentCard aesi=f.board("a.AesiTyrantOfGyreStrait","Aesi, Tyrant of Gyre Strait"); f.identifyCommander(aesi.getId());
            if(stocked){Card top=f.card("g.GrizzlyBears","Grizzly Bears");f.bot.getLibrary().putOnTop(top,f.game);}
            Card land=f.hand("basiclands.Forest","Forest");check(f.bot.priority(f.game),"Land not played");f.game.checkStateAndTriggered();check(!f.game.getStack().isEmpty(),"Actual landfall trigger absent");
            f.game.getStack().resolve(f.game); f.game.checkStateAndTriggered();check(!f.bot.hasLost(),"Voluntary empty-library draw lost game"); check(f.bot.getHand().size()==(stocked?1:0),"Optional draw decision incorrect");
        });
        for(final String condition:new String[]{"ready","tapped","sick"}) test("Aesi Scute combat-development observation with "+condition,()->{
            boolean ready=condition.equals("ready");
            Fixture f=new Fixture();PermanentCard aesi=f.board("a.AesiTyrantOfGyreStrait","Aesi, Tyrant of Gyre Strait");f.identifyCommander(aesi.getId());aesi.setTapped(true);
            for(int i=0;i<16;i++){Card card=f.card("s.ScuteSwarm","Scute Swarm");PermanentCard scute=new PermanentCard(card,f.bot.getId(),f.game);f.game.addPermanent(scute,0);f.game.getState().setZone(card.getId(),Zone.BATTLEFIELD);if(!condition.equals("sick"))scute.removeSummoningSickness();if(condition.equals("tapped"))scute.setTapped(true);}
            f.foes[0].setLife(16,f.game,null);
            java.lang.reflect.Field flag=Observation.class.getField("combatBeforeDevelopment");check(flag.getBoolean(f.bot.observe(f.game))==ready,"Combat-ready public projection incorrect");
        });
        test("Empty-hand no-action priority skips observation construction",()->{
            Fixture f=new Fixture();java.lang.reflect.Field count=ComputerPlayerFourthSeat.class.getDeclaredField("observationBuildCount");count.setAccessible(true);long before=count.getLong(f.bot);
            check(!f.bot.priority(f.game),"No-action player did not pass");check(count.getLong(f.bot)==before,"Fast pass built an unnecessary observation");
        });
        for(final int count:new int[]{1,2}) test("Curse of the Swine caps X at "+count+" opponent creatures",()->{
            Fixture f=new Fixture(); f.game.initializeRollbackOrder(); PermanentCard own=f.board("g.GrizzlyBears","Grizzly Bears");List<UUID> victims=new ArrayList<>();for(int i=0;i<count;i++)victims.add(f.enemyBoard(0,"r.RumblingBaloth","Rumbling Baloth").getId());
            Card spell=f.hand("c.CurseOfTheSwine","Curse of the Swine");f.bot.getManaPool().addMana(Mana.BlueMana(8),f.game,spell.getSpellAbility());int before=TRACE.failures;
            check(f.bot.priority(f.game),"Curse failed despite sufficient mana");check(TRACE.failures==before,"Curse attempted more X targets than legal creatures");check(f.bot.getManaPool().getMana().count()==6-count,"Curse paid incorrect XUU");f.game.getStack().resolve(f.game);f.game.applyEffects();
            check(f.game.getPermanent(own.getId())!=null,"Curse filled X with own creature");for(UUID id:victims)check(f.game.getState().getZone(id)==Zone.EXILED,"Curse victim not exiled");int boars=0;for(mage.game.permanent.Permanent p:f.game.getBattlefield().getAllActivePermanents(f.foes[0].getId()))if(p.hasSubtype(SubType.BOAR,f.game))boars++;check(boars==count,"Curse Boar tokens missing");
        });
        for(final boolean sick:new boolean[]{false,true}) for(final int bodies:new int[]{1,2}) test("Collective Effort actual distinct Escalate taps with "+bodies+" bodies sick="+sick,()->{
            Fixture f=new Fixture();f.game.initializeRollbackOrder();List<UUID> bodyIds=new ArrayList<>();for(int i=0;i<bodies;i++){Card body=f.card("g.GrizzlyBears","Grizzly Bears");PermanentCard p=new PermanentCard(body,f.bot.getId(),f.game);f.game.addPermanent(p,0);f.game.getState().setZone(p.getId(),Zone.BATTLEFIELD);if(!sick)p.removeSummoningSickness();bodyIds.add(p.getId());}
            for(int i=0;i<3;i++)f.board("basiclands.Plains","Plains");PermanentCard victim=f.enemyBoard(0,"r.RumblingBaloth","Rumbling Baloth");PermanentCard enchantment=f.enemyBoard(0,"g.GloriousAnthem","Glorious Anthem");Card card=f.hand("c.CollectiveEffort","Collective Effort");
            mage.abilities.SpellAbility spell=card.getSpellAbility().copy();List<mage.abilities.Mode> modes=new ArrayList<>(spell.getModes().values());spell.getModes().clearSelectedModes();
            modes.get(0).getTargets().get(0).addTarget(victim.getId(),spell,f.game);modes.get(1).getTargets().get(0).addTarget(enchantment.getId(),spell,f.game);modes.get(2).getTargets().get(0).addTarget(f.bot.getId(),spell,f.game);
            for(int i=0;i<bodies+1;i++)spell.getModes().addSelectedMode(modes.get(i).getId());spell.getModes().setPreselected(true);
            check(f.bot.activateAbility(spell,f.game),"Affordable Escalate cast failed");int tapped=0;for(UUID id:bodyIds)if(f.game.getPermanent(id).isTapped())tapped++;check(tapped==bodies,"Escalate did not tap distinct bodies exactly once");check(f.game.getStack().getFirst().getStackAbility().getModes().getSelectedModes().size()==bodies+1,"Selected mode count changed");
        });
        test("Collective Effort does not reuse required mana dork for Escalate",()->{
            Fixture f=new Fixture();f.game.initializeRollbackOrder();PermanentCard bird=f.board("b.BirdsOfParadise","Birds of Paradise");f.board("basiclands.Plains","Plains");f.board("basiclands.Plains","Plains");f.enemyBoard(0,"r.RumblingBaloth","Rumbling Baloth");f.enemyBoard(0,"g.GloriousAnthem","Glorious Anthem");Card card=f.hand("c.CollectiveEffort","Collective Effort");int before=TRACE.failures;
            check(f.bot.priority(f.game),"Affordable single-mode spell not cast");check(TRACE.failures==before,"Mana dork was double committed to Escalate");check(f.game.getState().getZone(card.getId())==Zone.STACK,"Collective Effort not stacked");check(f.game.getStack().getFirst().getStackAbility().getModes().getSelectedModes().size()==1,"Mana dork incorrectly available for additional mode");check(f.game.getPermanent(bird.getId()).isTapped(),"Mana dork did not fund normal spell");
        });
        for(final boolean tower:new boolean[]{true,false}) test("Growth Spiral GU paid by "+(tower?"Command Tower and Forest":"Simic Growth Chamber"),()->{
            Fixture f=new Fixture();f.game.initializeRollbackOrder();f.command("a.AesiTyrantOfGyreStrait","Aesi, Tyrant of Gyre Strait");
            if(tower){f.board("c.CommandTower","Command Tower");f.board("basiclands.Forest","Forest");}else f.board("s.SimicGrowthChamber","Simic Growth Chamber");
            Card spell=f.hand("g.GrowthSpiral","Growth Spiral");int before=TRACE.failures;
            check(f.bot.priority(f.game),"Growth Spiral failed actual GU payment");check(TRACE.failures==before,"Dual-color payment attempted wrong colors");check(f.game.getState().getZone(spell.getId())==Zone.STACK,"Growth Spiral not on stack");
        });
        for(final String[] commander:new String[][]{{"g.GhaltaPrimalHunger","Ghalta, Primal Hunger"},{"g.GishathSunsAvatar","Gishath, Sun's Avatar"}}) for(final boolean small:new boolean[]{true,false}) test(commander[1]+" trample combat versus "+(small?"one small blocker":"two lethal blockers"),()->{
            Fixture f=new Fixture();PermanentCard attacker=f.board(commander[0],commander[1]);f.identifyCommander(attacker.getId());for(int seat=0;seat<3;seat++){f.enemyBoard(seat,small?"g.GrizzlyBears":"g.Gigantosaurus",small?"Grizzly Bears":"Gigantosaurus");if(!small)f.enemyBoard(seat,"g.Gigantosaurus","Gigantosaurus");}f.combat();f.bot.selectAttackers(f.game,f.bot.getId());
            check(f.game.getCombat().getAttackers().contains(attacker.getId())==small,"Trample attack decision ignored meaningful overflow or lethal blockers");
        });
        for(final int blockers:new int[]{1,2}) test("Lathril menace versus "+blockers+" large blockers per seat",()->{
            Fixture f=new Fixture();PermanentCard attacker=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves");f.identifyCommander(attacker.getId());for(int seat=0;seat<3;seat++)for(int i=0;i<blockers;i++)f.enemyBoard(seat,"g.Gigantosaurus","Gigantosaurus");f.combat();f.bot.selectAttackers(f.game,f.bot.getId());
            check(f.game.getCombat().getAttackers().contains(attacker.getId())==(blockers==1),"Menace ignored minimum two legal blockers");
        });
        test("Collective Effort one body admits affordable modes without retry",()->{
            Fixture f=new Fixture();f.game.initializeRollbackOrder();f.board("g.GrizzlyBears","Grizzly Bears");for(int i=0;i<3;i++)f.board("basiclands.Plains","Plains");f.enemyBoard(0,"r.RumblingBaloth","Rumbling Baloth");f.enemyBoard(0,"g.GloriousAnthem","Glorious Anthem");Card spell=f.hand("c.CollectiveEffort","Collective Effort");int before=TRACE.failures;
            check(f.bot.priority(f.game),"Affordable Collective Effort was not cast");check(TRACE.failures==before,"Impossible three-mode Escalate was attempted");check(f.game.getState().getZone(spell.getId())==Zone.STACK,"Collective Effort not on stack");check(f.game.getStack().getFirst().getStackAbility().getModes().getSelectedModes().size()<=2,"One body paid two distinct taps");
        });
        test("Observation uses actual subtype and changeling rules",()->{
            Fixture f=new Fixture(); PermanentCard elf=f.board("l.LlanowarElves","Llanowar Elves"); PermanentCard bear=f.board("g.GrizzlyBears","Grizzly Bears"); PermanentCard changeling=f.board("c.ChangelingOutcast","Changeling Outcast");
            Observation o=f.bot.observe(f.game); int checked=0;
            for(fourthseat.ai.PolicyModel.Seat seat:o.seats) if(seat.id.equals(o.selfId)) for(CardView card:seat.battlefield) {
                if(card.id.equals(elf.getId().toString())) { check(card.subtypes.contains("Elf"),"Real Elf subtype absent"); checked++; }
                if(card.id.equals(bear.getId().toString())) { check(!card.subtypes.contains("Elf"),"Non-Elf counted as Elf"); checked++; }
                if(card.id.equals(changeling.getId().toString())) { check(card.subtypes.contains("Elf") && card.subtypes.contains("Goblin"),"Changeling tribe rules absent"); checked++; }
            }
            check(checked==3,"Observation subtype fixtures missing");
        });
        test("Observation excludes opponent hand identity",()->{
            Fixture f=new Fixture(); Card secret=f.card("c.Counterspell","Counterspell"); secret.setOwnerId(f.foes[0].getId()); f.foes[0].getHand().add(secret); f.game.getState().setZone(secret.getId(),Zone.HAND);
            Observation o=f.bot.observe(f.game); check(o.hand.isEmpty(),"Opponent card leaked into own hand");
            boolean seen=false; for(fourthseat.ai.PolicyModel.Seat s:o.seats) if(s.id.equals(f.foes[0].getId().toString())) { seen=true; check(s.handCount==1,"Public hand count missing"); check(s.battlefield.isEmpty(),"Secret card leaked onto public board"); }
            check(seen,"Opponent observation absent");
        });
        System.out.println("RESULT: "+passed+" passed, "+failed+" failed real-engine checks");
        if(failed>0) throw new AssertionError(failed+" real-engine checks failed");
    }
}
