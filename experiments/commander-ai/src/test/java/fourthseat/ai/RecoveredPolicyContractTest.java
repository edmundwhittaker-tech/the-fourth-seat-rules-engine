package fourthseat.ai;

import java.util.*;
import fourthseat.ai.PolicyModel.*;

/** Fresh recovery regressions, not historical acceptance evidence. */
public final class RecoveredPolicyContractTest {
    private static int checks;
    private static final String AESI="Aesi, Tyrant of Gyre Strait";
    private static CardView card(String id,String name,boolean creature,int power) {return new CardView(id,name,3,false,creature,power,power,false,"");}
    private static CardView pending(String owner) {return new CardView("pending","Nature's Lore",2,false,false,0,0,false,"",owner,Collections.<String>emptyList());}
    private static Observation observation(String commander,String step,boolean active,List<CardView> hand,List<CardView> stack,boolean combat,boolean saturated,boolean reserve,boolean projected,boolean ashaya) {
        return new Observation("self",commander,step,active,8,hand,Arrays.asList(
            new Seat("self",30,hand.size(),Arrays.asList(card("own","Own creature",true,3))),
            new Seat("enemy",30,3,Arrays.asList(card("enemyBig","Big threat",true,8),card("enemySmall","Small threat",true,2)))),
            stack,Collections.<ManaView>emptyList(),combat,saturated,reserve,projected,ashaya);
    }
    private static Action action(String name,boolean land,String... outcomes) {
        return new Action(name,name,"",2,land,false,!land,Collections.<String>emptyList(),land?ActionKind.LAND:ActionKind.SPELL,
            new HashSet<String>(Arrays.asList(outcomes)),name,"",Collections.<String>emptyList());
    }
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    private static CommanderPolicy policy(){return new CommanderPolicy(new CommanderPolicy.Clock(){public long nanoTime(){return 0;}});}
    public static void main(String[] args) {
        List<CardView> empty=Collections.emptyList();
        Action land=action("Forest",true), ramp=action("Nature's Lore",false,"PutLandInPlay"), payoff=action("Scute Swarm",false), protection=action("Heroic Intervention",false,"Protect");
        Observation defaults=observation(AESI,"PRECOMBAT_MAIN",true,empty,empty,false,false,false,false,false);
        check(!defaults.reserveGrowthSaturated&&!defaults.reserveUsesProjectedMana&&!defaults.ashayaLandfallDevelopment,"Unset diagnostic and strategy flags stay false");
        for(boolean active:Arrays.asList(false,true)) for(String step:Arrays.asList("UPKEEP","DRAW","PRECOMBAT_MAIN","POSTCOMBAT_MAIN","END_TURN")) {
            Observation reserve=observation(AESI,step,active,empty,Arrays.asList(pending("enemy")),false,false,true,false,false);
            check(CommanderPolicy.isStrategicallyHeld(reserve,land)&&CommanderPolicy.isStrategicallyHeld(reserve,ramp),"Reserve growth holds development across active-player/phase/stack: "+step+active);
            check(!CommanderPolicy.isStrategicallyHeld(reserve,payoff)&&!CommanderPolicy.isStrategicallyHeld(reserve,protection),"Reserve preserves payoff/protection: "+step+active);
        }
        Observation reserve=observation(AESI,"PRECOMBAT_MAIN",true,empty,empty,false,false,true,false,false);
        check(policy().choose(reserve,Arrays.asList(land),0).actionId==null,"Expired-budget land fallback respects reserve hold");
        check(policy().choose(reserve,Arrays.asList(land,ramp),1000000).actionId==null,"Normal choice shares reserve admission");
        Observation projectedOnly=observation(AESI,"PRECOMBAT_MAIN",true,empty,empty,false,false,false,true,false);
        check(!CommanderPolicy.isStrategicallyHeld(projectedOnly,land),"Projected mana is diagnostic, not a hold itself");
        Observation saturated=observation(AESI,"END_TURN",false,empty,empty,false,true,false,false,false);
        check(CommanderPolicy.isStrategicallyHeld(saturated,ramp),"Diminishing-growth saturation works off-turn");
        Observation saturationWithStack=observation(AESI,"END_TURN",false,empty,Arrays.asList(pending("enemy")),false,true,false,false,false);
        check(!CommanderPolicy.isStrategicallyHeld(saturationWithStack,ramp),"Ordinary saturation differs from reserve across stack");
        for(String step:Arrays.asList("UPKEEP","DRAW","PRECOMBAT_MAIN")) {
            Observation combat=observation(AESI,step,true,empty,empty,true,false,false,false,false);
            check(CommanderPolicy.isStrategicallyHeld(combat,ramp),"Combat-ready projection holds earlier own phase "+step);
        }
        check(!CommanderPolicy.isStrategicallyHeld(observation(AESI,"POSTCOMBAT_MAIN",true,empty,empty,true,false,false,false,false),ramp),"Combat-first ends after combat");
        CardView body=card("Courser of Kruphix","Courser of Kruphix",true,2);
        Observation ashaya=observation(AESI,"END_TURN",false,Arrays.asList(body),Arrays.asList(pending("enemy")),false,false,true,false,true);
        check(CommanderPolicy.isStrategicallyHeld(ashaya,action(body.name,false)),"Ashaya own-hand engine creature counts as reserve landfall growth");
        check(!CommanderPolicy.isStrategicallyHeld(ashaya,payoff),"Ashaya flag does not blanket-disable payoffs");
        Observation ownPending=observation(AESI,"PRECOMBAT_MAIN",true,empty,Arrays.asList(pending("self")),false,false,false,false,false);
        check(CommanderPolicy.isStrategicallyHeld(ownPending,payoff),"Wait for owned proactive stack spell before next payoff");
        check(!CommanderPolicy.isStrategicallyHeld(ownPending,protection),"Owned pending spell does not suppress protective response");
        CardView apex=card("apex","Apex Altisaur",true,10);
        Observation apexPending=observation("Goreclaw, Terror of Qal Sisma","PRECOMBAT_MAIN",true,Arrays.asList(apex),Arrays.asList(pending("self")),false,false,false,false,false);
        check(CommanderPolicy.isStrategicallyHeld(apexPending,action(apex.name,false)),"Untargeted removal creature waits for own pending spell");
        Action targetedRemoval=new Action("answer","Beast Within","",3,false,false,true,Arrays.asList("enemyBig"));
        check(!CommanderPolicy.isStrategicallyHeld(ownPending,targetedRemoval),"Targeted removal stays available during owned pending stack");
        Map<String,Set<String>> scopes=new LinkedHashMap<String,Set<String>>();
        scopes.put("own",new HashSet<String>(Arrays.asList("BoostCreature")));
        scopes.put("enemyBig",new HashSet<String>(Arrays.asList("DestroyPermanent")));
        Action mixed=new Action("mixed","Collective Effort","",3,false,false,true,Arrays.asList("own","enemyBig"),ActionKind.SPELL,
            new HashSet<String>(Arrays.asList("BoostCreature","DestroyPermanent")),"spell","",Collections.<String>emptyList(),scopes);
        scopes.get("own").add("DestroyPermanent");scopes.clear();
        check(mixed.targetOutcomes.get("own").equals(Collections.singleton("BoostCreature")),"Per-target effects deeply copy caller inputs");
        boolean immutable=false;try{mixed.targetOutcomes.get("own").clear();}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"Nested per-target outcomes are immutable");
        Observation adeline=observation("Adeline, Resplendent Cathar","PRECOMBAT_MAIN",true,empty,empty,false,false,false,false,false);
        check("mixed".equals(policy().choose(adeline,Arrays.asList(mixed),1000000).actionId),"Mixed mode can benefit own target and remove opposing target");
        Map<String,Set<String>> wrong=new LinkedHashMap<String,Set<String>>();wrong.put("own",Collections.singleton("DestroyPermanent"));wrong.put("enemyBig",Collections.singleton("BoostCreature"));
        Action bad=new Action("bad","Collective Effort","",3,false,false,true,Arrays.asList("own","enemyBig"),ActionKind.SPELL,
            mixed.outcomes,"spell","",Collections.<String>emptyList(),wrong);
        check(policy().choose(adeline,Arrays.asList(bad),1000000).actionId==null,"Mixed aggregate cannot hide reversed harmful/beneficial target ownership");
        Action detriment=new Action("bad","Unknown","",2,false,false,true,Arrays.asList("own"),ActionKind.SPELL,Collections.singleton("Detriment"),"spell");
        check(policy().choose(adeline,Arrays.asList(detriment),1000000).actionId==null,"Typed Detriment rejects own target");
        System.out.println("RecoveredPolicyContractTest: "+checks+" fresh checks passed");
    }
}
