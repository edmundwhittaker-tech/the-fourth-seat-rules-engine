package mage.player.ai;

import java.util.*;
import mage.abilities.ActivatedAbility;
import mage.abilities.Mode;
import mage.abilities.keyword.OffspringAbility;
import mage.cards.Card;
import mage.game.Game;

/** Real-engine copy/cost contract checks. Uses the shared isolated fixture, no server.
 * Run with the same pinned runtime classpath as FourthSeatRealGameTest.
 */
public final class FourthSeatAdditionalCostContractTest {
    private static int checks;
    private static void check(boolean condition,String reason) {
        checks++;if(!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        FourthSeatRealGameTest.Fixture f=new FourthSeatRealGameTest.Fixture();
        Card spell=f.hand("s.SmugglersSurprise","Smuggler's Surprise");
        List<Mode> modes=new ArrayList<Mode>(spell.getSpellAbility().getModes().values());
        int[] expected={3,6,2};
        check(modes.size()==expected.length,"Pinned Smuggler has three modes");
        for(int i=0;i<modes.size();i++) {
            spell.getSpellAbility().getModes().clearSelectedModes();
            spell.getSpellAbility().getModes().addSelectedMode(modes.get(i).getId());
            ActivatedAbility prepared=FourthSeatAdditionalCostPolicy.prepareModeCost(spell.getSpellAbility());
            check(prepared.getManaCostsToPay().manaValue()==expected[i],"Mode "+i+" includes base and extra cost exactly once");
            check(spell.getSpellAbility().getManaCostsToPay().manaValue()==1,"Mode preparation leaves original mana unchanged");
            check(spell.getSpellAbility().getModes().getSelectedModes().equals(Collections.singletonList(modes.get(i).getId())),"Mode preparation preserves original selection");
            ActivatedAbility again=FourthSeatAdditionalCostPolicy.prepareModeCost(spell.getSpellAbility());
            check(again.getManaCostsToPay().manaValue()==expected[i],"Repeated preparation from original does not accumulate charges");
            prepared.getManaCostsToPay().clear();
            check(spell.getSpellAbility().getManaCostsToPay().manaValue()==1,"Prepared costs are independent of original costs");
        }
        spell.getSpellAbility().getModes().clearSelectedModes();
        for(Mode mode:modes) spell.getSpellAbility().getModes().addSelectedMode(mode.getId());
        check(FourthSeatAdditionalCostPolicy.prepareModeCost(spell.getSpellAbility()).getManaCostsToPay().manaValue()==9,"Three selected modes each charged once plus base");
        check(spell.getSpellAbility().getManaCostsToPay().manaValue()==1,"Multi-mode preparation does not mutate original");

        Card tender=f.hand("t.TenderWildguide","Tender Wildguide");f.mana(tender);
        int livePool=f.bot.getManaPool().getMana().count();
        Game snapshot=f.game.createSimulationForPlayableCalc();
        // Outside an active cast the fixture inherits ComputerPlayer's affirmative
        // optional choice. Only this disposable snapshot receives the engine probe.
        ActivatedAbility offspring=FourthSeatAdditionalCostPolicy.prepareOffspringCost(tender.getSpellAbility(),snapshot);
        check(offspring!=null,"Engine accepted funded Offspring probe");
        check(offspring.getManaCostsToPay().manaValue()==4,"Offspring adds exactly two to base one-green-two-total cost");
        check(tender.getSpellAbility().getManaCostsToPay().manaValue()==2,"Offspring leaves original mana unchanged");
        check(tender.getSpellAbility().getCostsTagMap()==null||!tender.getSpellAbility().getCostsTagMap().containsKey(OffspringAbility.OFFSPRING_ACTIVATION_VALUE_KEY),"Offspring probe does not mark real spell activated");
        check(offspring.getCostsTagMap()!=null&&offspring.getCostsTagMap().containsKey(OffspringAbility.OFFSPRING_ACTIVATION_VALUE_KEY),"Prepared copy records actual engine Offspring selection");
        check(f.bot.getManaPool().getMana().count()==livePool,"Probe never spends real mana");
        check(snapshot.getPlayer(f.bot.getId()).getManaPool().getMana().count()==livePool,"Probe prepares rather than pays snapshot mana");
        ActivatedAbility repeated=FourthSeatAdditionalCostPolicy.prepareOffspringCost(tender.getSpellAbility(),snapshot);
        check(repeated!=null&&repeated.getManaCostsToPay().manaValue()==4,"Repeated Offspring preparation does not double charge");
        check(FourthSeatAdditionalCostPolicy.prepareOffspringCost(spell.getSpellAbility(),snapshot)==null,"Non-Offspring source has no fabricated optional cost");
        System.out.println("FourthSeatAdditionalCostContractTest: "+checks+" checks passed");
    }
}
