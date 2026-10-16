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
    static final class FixtureGame extends GameImpl {
        FixtureGame() { super(MultiplayerAttackOption.MULTIPLE, RangeOfInfluence.ALL,
            MulliganType.GAME_DEFAULT.getMulligan(0), 0, 40, 7); setGameOptions(new GameOptions()); }
        FixtureGame(FixtureGame other) { super(other); }
        public FixtureGame copy() { return new FixtureGame(this); }
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
        void combat() { CombatPhase phase=new CombatPhase(); phase.setStep(new DeclareAttackersStep()); game.getTurn().setPhase(phase); game.getCombat().setAttacker(bot.getId()); game.getCombat().setDefenders(game); }
        void mana() { Card source=bot.getHand().getCards(game).iterator().next(); bot.getManaPool().addMana(new Mana(20,20,20,20,20,0,0,20),game,source.getSpellAbility()); }
    }
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    static void test(String name, Checked test) { try { test.run(); passed++; System.out.println("PASS "+name); } catch(Throwable e) { failed++; System.err.println("FAIL "+name+": "+e); e.printStackTrace(); } }
    interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        for (final String[] commander:COMMANDERS) {
            test(commander[1]+" actual own-hand spell extraction and paid cast",()->{
                Fixture f=new Fixture(); Card c=f.hand(commander[0],commander[1]); f.bot.getCommandersIds().add(c.getId()); f.mana();
                check(f.bot.observe(f.game).commander.equals(commander[1]),"Commander identity missing");
                check(f.bot.observe(f.game).hand.size()==1,"Own hand missing");
                check(f.bot.priority(f.game),"AI passed despite funded commander");
                check(!f.bot.getHand().contains(c.getId()),"Chosen spell remained in hand");
                check(f.game.getStack().size()==1,"Cast not on actual engine stack");
                check(f.bot.getManaPool().getMana().count()<120,"No real mana payment");
            });
            test(commander[1]+" unaffordable cast excluded",()->{
                Fixture f=new Fixture(); Card c=f.hand(commander[0],commander[1]); f.bot.getCommandersIds().add(c.getId());
                check(!f.bot.priority(f.game),"AI activated an unfunded spell");
                check(f.bot.getHand().contains(c.getId()) && f.game.getStack().isEmpty(),"Illegal state mutation");
            });
        }
        test("Krenko real legal tap activation",()->{
            Fixture f=new Fixture(); PermanentCard krenko=f.board("k.KrenkoMobBoss","Krenko, Mob Boss"); f.bot.getCommandersIds().add(krenko.getId());
            check(f.bot.priority(f.game),"Krenko failed to activate"); check(krenko.isTapped(),"Krenko tap cost unpaid"); check(f.game.getStack().size()==1,"Krenko ability not stacked");
        });
        test("Krenko casts a token maker before multiplying goblins",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("k.KrenkoMobBoss","Krenko, Mob Boss"); f.bot.getCommandersIds().add(commander.getId());
            Card maker=f.hand("k.KrenkosCommand","Krenko's Command"); f.mana();
            check(f.bot.priority(f.game),"Krenko development passed"); check(!f.bot.getHand().contains(maker.getId()) && !commander.isTapped(),"Krenko multiplied before casting payable goblin maker");
        });
        test("Krenko allows own token spell to resolve before multiplication",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("k.KrenkoMobBoss","Krenko, Mob Boss"); f.bot.getCommandersIds().add(commander.getId());
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
            Fixture f=new Fixture(); PermanentCard krenko=f.board("k.KrenkoMobBoss","Krenko, Mob Boss"); f.bot.getCommandersIds().add(krenko.getId()); krenko.setTapped(true);
            check(!f.bot.priority(f.game),"Tapped Krenko acted"); check(f.game.getStack().isEmpty(),"Illegal Krenko activation");
        });
        test("Lathril ten untapped elves permits actual drain activation",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.bot.getCommandersIds().add(commander.getId());
            for(int i=0;i<10;i++) f.board("l.LlanowarElves","Llanowar Elves");
            check(f.bot.priority(f.game),"Lathril did not choose legal ten-elf activation");
            check(commander.isTapped(),"Lathril cost unpaid"); check(f.game.getStack().size()==1,"Drain not on real stack");
            int tapped=0; for(mage.game.permanent.Permanent p:f.game.getBattlefield().getAllActivePermanents(f.bot.getId())) if(p.isTapped()) tapped++;
            check(tapped==11,"Ten other elf tap cost was not fully paid");
        });
        test("Lathril fewer than ten other elves cannot drain",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.bot.getCommandersIds().add(commander.getId());
            for(int i=0;i<9;i++) f.board("l.LlanowarElves","Llanowar Elves");
            check(!f.bot.priority(f.game),"Lathril selected unavailable drain"); check(f.game.getStack().isEmpty(),"Illegal drain stacked");
        });
        test("Talrand does not counter an empty stack",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.bot.getCommandersIds().add(commander.getId());
            Card counter=f.hand("c.Counterspell","Counterspell"); f.mana();
            check(!f.bot.priority(f.game),"Counterspell acted on empty stack"); check(f.bot.getHand().contains(counter.getId()),"Counterspell wasted");
        });
        test("Talrand real opponent spell can be countered",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.bot.getCommandersIds().add(commander.getId());
            Card counter=f.hand("c.Counterspell","Counterspell"); f.mana();
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
        test("Talrand never counters its own spell",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.bot.getCommandersIds().add(commander.getId());
            Card counter=f.hand("c.Counterspell","Counterspell"); Card opt=f.hand("o.Opt","Opt"); f.mana();
            check(f.bot.activateAbility(opt.getSpellAbility(),f.game),"Own Opt fixture failed");
            check(!f.bot.priority(f.game),"Talrand countered own spell"); check(f.bot.getHand().contains(counter.getId()),"Own counter was consumed");
        });
        test("Talrand reserves UU rather than main-phase cantrip",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.bot.getCommandersIds().add(commander.getId());
            Card opt=f.hand("o.Opt","Opt"); Card counter=f.hand("c.Counterspell","Counterspell");
            f.bot.getManaPool().addMana(Mana.BlueMana(2),f.game,opt.getSpellAbility());
            check(!f.bot.priority(f.game),"Talrand spent reserved counter mana in own main"); check(f.bot.getHand().contains(opt.getId()),"Main-phase cantrip consumed");
        });
        test("Talrand uses cantrip at opponent end step",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TalrandSkySummoner","Talrand, Sky Summoner"); f.bot.getCommandersIds().add(commander.getId());
            Card opt=f.hand("o.Opt","Opt"); f.hand("c.Counterspell","Counterspell"); f.bot.getManaPool().addMana(Mana.BlueMana(2),f.game,opt.getSpellAbility());
            EndPhase phase=new EndPhase(); phase.setStep(new EndStep()); f.game.getTurn().setPhase(phase); f.game.getState().setActivePlayerId(f.foes[2].getId());
            check(f.bot.priority(f.game),"Talrand failed end-step cantrip"); check(!f.bot.getHand().contains(opt.getId()),"End-step Opt not cast");
        });
        test("Ghalta actual power discount makes two-green cast legal",()->{
            Fixture f=new Fixture(); f.board("g.Gigantosaurus","Gigantosaurus"); Card commander=f.hand("g.GhaltaPrimalHunger","Ghalta, Primal Hunger"); f.bot.getCommandersIds().add(commander.getId());
            f.bot.getManaPool().addMana(Mana.GreenMana(2),f.game,commander.getSpellAbility());
            check(f.bot.priority(f.game),"Ghalta discount was not used"); check(f.game.getStack().size()==1,"Discounted Ghalta not stacked"); check(f.bot.getManaPool().getMana().count()==0,"Ghalta GG payment incorrect");
        });
        test("Lathril combat preserves a live ten-elf drain",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves"); f.bot.getCommandersIds().add(commander.getId());
            for(int i=0;i<10;i++) f.board("l.LlanowarElves","Llanowar Elves");
            CombatPhase phase=new CombatPhase(); phase.setStep(new DeclareAttackersStep()); f.game.getTurn().setPhase(phase);
            f.game.getCombat().setAttacker(f.bot.getId()); f.game.getCombat().setDefenders(f.game);
            f.bot.selectAttackers(f.game,f.bot.getId());
            int untapped=0; for(mage.game.permanent.Permanent p:f.game.getBattlefield().getAllActivePermanents(f.bot.getId())) if(!p.isTapped()) untapped++;
            check(untapped>=11,"Combat consumed Lathril's live ten-elf activation");
        });
        test("Ghalta combat chooses available lethal opponent",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GhaltaPrimalHunger","Ghalta, Primal Hunger"); f.bot.getCommandersIds().add(commander.getId());
            f.foes[1].initLife(10);
            CombatPhase phase=new CombatPhase(); phase.setStep(new DeclareAttackersStep()); f.game.getTurn().setPhase(phase);
            f.game.getCombat().setAttacker(f.bot.getId()); f.game.getCombat().setDefenders(f.game);
            f.bot.selectAttackers(f.game,f.bot.getId());
            check(f.game.getCombat().getAttackers().contains(commander.getId()),"Ghalta failed profitable attack");
            check(f.foes[1].getId().equals(f.game.getCombat().getDefendingPlayerId(commander.getId(),f.game)),"Ghalta ignored available lethal seat");
        });
        test("Giada is established before a follow-up Angel",()->{
            Fixture f=new Fixture(); Card giada=f.hand("g.GiadaFontOfHope","Giada, Font of Hope"); f.bot.getCommandersIds().add(giada.getId());
            Card angel=f.hand("b.BaneslayerAngel","Baneslayer Angel"); f.mana();
            check(f.bot.priority(f.game),"Giada sequence passed"); check(!f.bot.getHand().contains(giada.getId()) && f.bot.getHand().contains(angel.getId()),"Angel cast before Giada's counter engine");
        });
        test("Giada preserves untargeted protection",()->{
            Fixture f=new Fixture(); PermanentCard giada=f.board("g.GiadaFontOfHope","Giada, Font of Hope"); f.bot.getCommandersIds().add(giada.getId());
            Card protection=f.hand("f.FlawlessManeuver","Flawless Maneuver"); f.mana();
            check(!f.bot.priority(f.game),"Protection wasted without threat"); check(f.bot.getHand().contains(protection.getId()),"Flawless Maneuver consumed on empty stack");
        });
        test("Goreclaw develops rather than casting a postcombat finisher",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GoreclawTerrorOfQalSisma","Goreclaw, Terror of Qal Sisma"); f.bot.getCommandersIds().add(commander.getId());
            Card finisher=f.hand("o.OverwhelmingStampede","Overwhelming Stampede"); Card body=f.hand("q.QuestingBeast","Questing Beast"); f.mana();
            PostCombatMainPhase phase=new PostCombatMainPhase(); phase.setStep(new PostCombatMainStep()); f.game.getTurn().setPhase(phase);
            check(f.bot.priority(f.game),"Goreclaw passed development"); check(f.bot.getHand().contains(finisher.getId()) && !f.bot.getHand().contains(body.getId()),"Temporary pump wasted after combat");
        });
        test("Torbran amplified Bolt selects a lethal opponent",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("t.TorbranThaneOfRedFell","Torbran, Thane of Red Fell"); f.bot.getCommandersIds().add(commander.getId());
            Card bolt=f.hand("l.LightningBolt","Lightning Bolt"); f.mana(); f.foes[2].initLife(5);
            check(f.bot.priority(f.game),"Torbran ignored lethal five-damage Bolt");
            boolean targeted=false; for(mage.game.stack.StackObject spell:f.game.getStack()) for(mage.target.Target target:spell.getStackAbility().getTargets()) if(target.getTargets().contains(f.foes[2].getId())) targeted=true;
            check(targeted,"Bolt failed to choose lethal opponent");
        });
        test("Aesi precedes land when already funded",()->{
            Fixture f=new Fixture(); Card commander=f.hand("a.AesiTyrantOfGyreStrait","Aesi, Tyrant of Gyre Strait"); f.bot.getCommandersIds().add(commander.getId());
            Card land=f.hand("basiclands.Forest","Forest"); f.mana();
            check(f.bot.priority(f.game),"Aesi sequence passed"); check(!f.bot.getHand().contains(commander.getId()) && f.bot.getHand().contains(land.getId()),"Land played before already-payable Aesi, losing landfall draw");
        });
        test("Aesi still plays land when commander is not payable",()->{
            Fixture f=new Fixture(); Card commander=f.hand("a.AesiTyrantOfGyreStrait","Aesi, Tyrant of Gyre Strait"); f.bot.getCommandersIds().add(commander.getId()); Card land=f.hand("basiclands.Forest","Forest");
            check(f.bot.priority(f.game),"Aesi stalled land development"); check(f.bot.getHand().contains(commander.getId()) && !f.bot.getHand().contains(land.getId()),"Unfunded Aesi prevented legal land drop");
        });
        test("Torbran does not assume five damage without commander on board",()->{
            Fixture f=new Fixture(); Card commander=f.card("t.TorbranThaneOfRedFell","Torbran, Thane of Red Fell"); f.bot.getCommandersIds().add(commander.getId()); f.game.getState().setZone(commander.getId(),Zone.GRAVEYARD); f.bot.getGraveyard().add(commander);
            Card bolt=f.hand("l.LightningBolt","Lightning Bolt"); f.mana(); f.foes[2].initLife(5);
            check(!f.bot.priority(f.game),"Missing Torbran incorrectly amplified Bolt"); check(f.bot.getHand().contains(bolt.getId()),"Bolt spent on nonlethal low-value face target");
        });
        test("Gishath attacks the unblocked opponent",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("g.GishathSunsAvatar","Gishath, Sun's Avatar"); f.bot.getCommandersIds().add(commander.getId());
            f.enemyBoard(0,"g.Gigantosaurus","Gigantosaurus"); f.enemyBoard(2,"g.Gigantosaurus","Gigantosaurus"); f.combat();
            f.bot.selectAttackers(f.game,f.bot.getId());
            check(f.foes[1].getId().equals(f.game.getCombat().getDefendingPlayerId(commander.getId(),f.game)),"Gishath missed unblocked damage-trigger destination");
        });
        test("Adeline can trigger while staying back",()->{
            Fixture f=new Fixture(); PermanentCard commander=f.board("a.AdelineResplendentCathar","Adeline, Resplendent Cathar"); f.bot.getCommandersIds().add(commander.getId());
            PermanentCard attacker=f.board("s.SuntailHawk","Suntail Hawk");
            for(int i=0;i<3;i++) f.enemyBoard(i,"g.Gigantosaurus","Gigantosaurus"); f.combat();
            f.bot.selectAttackers(f.game,f.bot.getId());
            check(!f.game.getCombat().getAttackers().contains(commander.getId()),"Adeline suicided into 10/10 blockers");
            check(f.game.getCombat().getAttackers().contains(attacker.getId()),"Other creature failed to attack for Adeline's trigger");
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
