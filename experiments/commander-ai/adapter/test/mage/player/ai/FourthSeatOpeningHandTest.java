package mage.player.ai;

import mage.cards.Card;
import mage.cards.CardsImpl;
import mage.constants.*;
import mage.filter.FilterCard;
import mage.target.Target;
import mage.target.common.TargetCardInHand;
import java.util.*;
import static mage.player.ai.FourthSeatRealGameTest.check;
import static mage.player.ai.RecoveryChecks.*;

/** Exercises the Target overload used by the pinned LondonMulligan, not a policy-only call. */
public final class FourthSeatOpeningHandTest {
    static Card bottom(FourthSeatRealGameTest.Fixture f) {
        Target target = new TargetCardInHand(new FilterCard("card (1 more) to put on the bottom of your library"));
        check(f.bot.chooseTarget(Outcome.Discard, target, null, f.game), "Opening choice absent");
        check(target.getTargets().size() == 1, "Opening choice must select exactly one legal card");
        Card selected = f.game.getCard(target.getFirstTarget());
        check(f.bot.getHand().contains(selected.getId()), "Selected card is not in own hand");
        check(f.bot.putCardsOnBottomOfLibrary(new CardsImpl(target.getTargets()), f.game, null, true), "Native bottom movement failed");
        check(f.game.getState().getZone(selected.getId()) == Zone.LIBRARY, "Card did not reach library");
        return selected;
    }
    static long lands(FourthSeatRealGameTest.Fixture f) {
        return f.bot.getHand().getCards(f.game).stream().filter(c -> c.isLand(f.game)).count();
    }
    static FourthSeatRealGameTest.Fixture opening() throws Exception {
        FourthSeatRealGameTest.Fixture f = new FourthSeatRealGameTest.Fixture(true);
        f.command("k.KrenkoMobBoss", "Krenko, Mob Boss");
        check(f.game.getTurnNum()==1 && f.game.getTurn().getPhase()==null, "Fixture changed native pregame state");
        return f;
    }
    public static void main(String[] args) throws Exception {
        for (int count : new int[]{2,3}) run("London bottom preserves " + count + " scarce lands across two choices", () -> {
            FourthSeatRealGameTest.Fixture f = opening();
            for (int i=0;i<count;i++) f.hand("basiclands.Mountain", "Mountain");
            f.hand("m.MuxusGoblinGrandee", "Muxus, Goblin Grandee");
            f.hand("d.DeflectingSwat", "Deflecting Swat");
            f.hand("c.ChaosWarp", "Chaos Warp");
            f.hand("g.GoblinChieftain", "Goblin Chieftain");
            if (count==2) f.hand("s.SharedAnimosity", "Shared Animosity");
            check(bottom(f).getName().equals("Muxus, Goblin Grandee"), "Kept six-mana payoff over opening resources");
            check(!bottom(f).isLand(f.game), "Second bottom removed scarce land");
            check(lands(f)==count && f.bot.getHand().size()==5, "Functional five-card hand was damaged");
        });
        run("London bottom trims flood before early ramp and a creature", () -> {
            FourthSeatRealGameTest.Fixture f = opening();
            for(int i=0;i<5;i++) f.hand("basiclands.Mountain", "Mountain");
            Card ring=f.hand("s.SolRing", "Sol Ring"), body=f.hand("g.GoblinChieftain", "Goblin Chieftain");
            check(bottom(f).isLand(f.game) && bottom(f).isLand(f.game), "Excess lands kept over early plays");
            check(lands(f)==3 && f.bot.getHand().contains(ring.getId()) && f.bot.getHand().contains(body.getId()), "Flood correction lost development");
        });
        run("London bottom retains sole red source over colorless excess", () -> {
            FourthSeatRealGameTest.Fixture f = opening();
            Card red=f.hand("basiclands.Mountain", "Mountain");
            for(int i=0;i<3;i++) f.hand("w.Wastes", "Wastes");
            f.hand("g.GoblinChieftain", "Goblin Chieftain");f.hand("s.SolRing", "Sol Ring");f.hand("c.ChaosWarp", "Chaos Warp");
            check(bottom(f).getName().equals("Wastes"), "Bottomed sole colored source");
            check(f.bot.getHand().contains(red.getId()), "Lost red access");
        });
        run("Opening choice honors native card filter", () -> {
            FourthSeatRealGameTest.Fixture f = opening();
            f.hand("basiclands.Mountain", "Mountain");f.hand("m.MuxusGoblinGrandee", "Muxus, Goblin Grandee");
            FilterCard filter=new FilterCard("land card");filter.add(CardType.LAND.getPredicate());
            Target target=new TargetCardInHand(filter);
            check(f.bot.chooseTarget(Outcome.Discard,target,null,f.game), "Filtered mandatory choice failed");
            check(f.game.getCard(target.getFirstTarget()).isLand(f.game), "Opening policy bypassed legal filter");
        });
        run("Opening chooser does not intercept in-game discard", () -> {
            FourthSeatRealGameTest.Fixture f = opening();
            f.game.getState().setTurnNum(4);
            mage.game.turn.PreCombatMainPhase phase=new mage.game.turn.PreCombatMainPhase();
            phase.setStep(new mage.game.turn.PreCombatMainStep());f.game.getTurn().setPhase(phase);
            Card land=f.hand("basiclands.Mountain", "Mountain");f.hand("m.MuxusGoblinGrandee", "Muxus, Goblin Grandee");
            // Existing native discard selection is unchanged outside mulligans.
            check(bottom(f).getId().equals(land.getId()), "Pregame choice leaked into in-game discard");
        });
        finish("Opening hand");
    }
}
