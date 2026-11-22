package mage.player.ai;

import mage.cards.Card;
import mage.constants.*;
import mage.game.permanent.Permanent;
import static mage.player.ai.FourthSeatRealGameTest.check;
import static mage.player.ai.RecoveryChecks.*;

/** Native {B/G} payment must not tap a flexible land for unusable {C}. */
public final class FourthSeatHybridPaymentTest {
    public static void main(String[] args)throws Exception {
        for(String variant:new String[]{"forests","realms","realms-druid","cavern","wrong-type","short"})
            run("Hybrid Elf native payment: "+variant,()->{
                FourthSeatRealGameTest.Fixture f=fixture("l.LathrilBladeOfTheElves","Lathril, Blade of the Elves");
                f.board("basiclands.Forest","Forest");
                if(!variant.equals("short"))f.board(variant.equals("realms-druid")?"d.DevotedDruid":"basiclands.Forest",variant.equals("realms-druid")?"Devoted Druid":"Forest");
                Permanent land=f.board(variant.equals("forests")?"basiclands.Forest":variant.equals("cavern")?"c.CavernOfSouls":"e.EclipsedRealms",variant.equals("forests")?"Forest":variant.equals("cavern")?"Cavern of Souls":"Eclipsed Realms");
                f.game.getState().setValue(land.getId()+"_type",variant.equals("wrong-type")?SubType.GOBLIN:SubType.ELF);
                Card elf=f.hand("e.EclipsedElf","Eclipsed Elf");
                boolean funded=!variant.equals("wrong-type")&&!variant.equals("short");
                check(f.bot.priority(f.game)==funded,"Wrong payment decision");
                check(f.game.getState().getZone(elf.getId())==(funded?Zone.STACK:Zone.HAND),"Wrong native spell zone");
                check(f.game.rollbackCount==0,"Failed cast was hidden by rollback");
                if(funded){check(f.bot.getManaPool().getMana().count()==0,"Unused wrong-colour mana");
                    drain(f,10);check(f.game.getPermanent(elf.getId())!=null,"Native Elf resolution failed");}
            });
        finish("HYBRID_PAYMENT");
    }
}
