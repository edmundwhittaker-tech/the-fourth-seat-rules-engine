package fourthseat.ai;
import java.util.*;
import fourthseat.ai.PolicyModel.*;
public final class ActivationPolicyTest {
 static int checks;
 static CardView card(String id,String name,boolean creature,boolean sick,boolean tapped){return new CardView(id,name,0,true,creature,2,2,tapped,"","self",Collections.<String>emptyList(),false,Collections.<String>emptySet(),sick);}
 static Observation o(String step,boolean active,CardView...board){return new Observation("self","Ghalta, Primal Hunger",step,active,5,Collections.<CardView>emptyList(),Arrays.asList(new Seat("self",40,0,Arrays.asList(board)),new Seat("foe",40,0,Collections.<CardView>emptyList())));}
 static Action a(String name,String effect,String source,String...targets){return new Action("a",name,"",1,false,false,true,Arrays.asList(targets),ActionKind.ACTIVATION,new HashSet<String>(Arrays.asList(effect)),source);}
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  CardView land=card("land","Lair of the Hydra",false,false,false),sick=card("land","Lair of the Hydra",false,true,false);
  Action animate=a("Lair of the Hydra","BecomeCreature","land");
  check(ActivationPolicy.score(o("PRECOMBAT_MAIN",true,land),animate)>0,"Useful ready land animation");
  check(ActivationPolicy.score(o("PRECOMBAT_MAIN",true,sick),animate)<0,"Do not animate summoning sick Lair");
  check(ActivationPolicy.score(o("POSTCOMBAT_MAIN",true,land),animate)<0,"No pointless postcombat animation");
  check(ActivationPolicy.score(o("BEGIN_COMBAT",false,land),animate)<0,"No unrelated opponent turn animation");
  check(ActivationPolicy.score(o("PRECOMBAT_MAIN",true,card("land","Lair of the Hydra",true,false,false)),animate)<0,"No repeat animation");
  check(ActivationPolicy.score(o("PRECOMBAT_MAIN",true,card("land","Lair of the Hydra",false,false,true)),animate)<0,"Do not animate tapped attacker");
  check(ActivationPolicy.score(o("PRECOMBAT_MAIN",true,sick),a("Kamahl, Heart of Krosa","BecomeCreature","kamahl","land"))>0,"Kamahl grants haste");
  CardView creature=card("body","Bear",true,false,false);
  check(ActivationPolicy.score(o("PRECOMBAT_MAIN",true,creature),a("Bristly Bill, Spine Sower","BoostCreature","bill"))>0,"Pump with combat purpose");
  check(ActivationPolicy.score(o("UPKEEP",true,creature),a("Bristly Bill, Spine Sower","BoostCreature","bill"))<0,"No pointless upkeep pumping loop");
  check(ActivationPolicy.score(o("PRECOMBAT_MAIN",true),a("Unknown","Benefit","x"))<0,"Unsupported ability does not inherit casting value");
  check(ActivationPolicy.score(o("PRECOMBAT_MAIN",true),a("Evolving Wilds","PutLandInPlay","land"))>0,"Fetch land has real development value");
  check(ActivationPolicy.score(o("PRECOMBAT_MAIN",true),new Action("cast","Apex Devastator","",10,false,false,true,Collections.<String>emptyList()))==null,"Spell uses deck plan separately");
  check(ActivationPolicy.score(o("PRECOMBAT_MAIN",true),a("Lathril, Blade of the Elves","Damage","lathril"))>0,"Pinned engine drain uses Damage outcome");
  System.out.println("ActivationPolicyTest: "+checks+" checks passed");
 }
}
