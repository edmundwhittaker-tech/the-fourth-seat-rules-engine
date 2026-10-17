package fourthseat.ai;

import java.util.*;
import fourthseat.ai.CombatPolicy.*;

/** Deterministic zero-dependency regression suite. Run with java fourthseat.ai.CombatPolicyTest. */
public final class CombatPolicyTest {
    private static int checks;
    private static Set<String> set(String... s) {return new HashSet<String>(Arrays.asList(s));}
    private static Attacker a(String id,String name,int p,int t,boolean vigilance,String... legal) {return new Attacker(id,name,p,t,3,vigilance,set(legal));}
    private static Defender d(String id,int life,int crackback,Blocker... blockers) {return new Defender(id,life,Arrays.asList(blockers),crackback);}
    private static void check(boolean x,String message) {checks++;if(!x) throw new AssertionError(message);}
    private static Plan plan(int life,List<Attacker> a,Defender... d) {return CombatPolicy.plan("",life,a,Arrays.asList(d));}
    public static void main(String[] args) {
        Attacker soldier=a("s","Soldier",4,4,false,"A","B","C");
        List<Attacker> one=Arrays.asList(soldier);
        Defender defended=d("A",40,0,new Blocker("wall",8,8,set("s")));
        Defender vulnerable=d("B",4,0), other=d("C",40,0);
        Plan p=plan(40,one,defended,vulnerable,other);
        check("B".equals(p.attacks.get("s")),"Prefer visible lethal over blocked seat");
        List<Defender> seats=Arrays.asList(defended,vulnerable,other);
        for(int i=0;i<3;i++) for(int j=0;j<3;j++) if(i!=j) {
            int k=3-i-j;Plan perm=plan(40,one,seats.get(i),seats.get(j),seats.get(k));
            check(perm.attacks.equals(p.attacks),"Every seat permutation is invariant");
        }
        check(plan(5,one,d("A",40,9)).attacks.isEmpty(),"Preserve blocker against lethal exposure");
        check(plan(5,one,d("A",4,9)).attacks.size()==1,"Visible elimination removes that seat's counterattack");
        check(plan(5,one,d("A",4,0),d("B",40,9)).attacks.isEmpty(),"Do not chase lethal into another seat's lethal crackback");
        check(plan(40,one,defended).attacks.isEmpty(),"Do not throw valuable body into known lethal blocker");
        check(plan(40,Arrays.asList(a("x","Flyer",3,3,false,"A")),d("A",40,0,new Blocker("ground",5,5,set()))).attacks.size()==1,"Respect supplied evasion legality, not assumed ground block");
        check(plan(40,Arrays.asList(a("x","Ground",3,3,false,"A")),d("A",40,0,new Blocker("flying",5,5,set("x")))).attacks.isEmpty(),"Do not infer attacker unblockability from a name");
        check(plan(40,Arrays.asList(a("x","X",8,8,false,"A")),d("B",1,0)).attacks.isEmpty(),"Never invent legal defender");
        // No human identity field exists. Seat IDs are deterministic tie breakers, not player type.
        Plan ties=plan(40,one,d("C",40,0),d("B",40,0),d("A",40,0));
        check("A".equals(ties.attacks.get("s")),"Equal seats resolve by stable identifier only");
        Plan adeline=plan(40,Arrays.asList(a("ad","Adeline, Resplendent Cathar",3,4,true,"A")),d("A",40,0));
        Plan generic=plan(40,Arrays.asList(a("ad","Generic",3,4,true,"A")),d("A",40,0));
        check(adeline.score>generic.score,"Adeline attack trigger valued");
        Plan lathril=plan(40,Arrays.asList(a("l","Lathril, Blade of the Elves",2,3,false,"A")),d("A",40,0));
        check(lathril.score>2,"Lathril connection token trigger valued");
        Plan blockedLathril=plan(40,Arrays.asList(a("l","Lathril, Blade of the Elves",2,3,false,"A")),d("A",40,0,new Blocker("wall",0,9,set("l"))));
        check(blockedLathril.attacks.isEmpty(),"No connection bonus through known blocker");
        Plan gishath=plan(40,Arrays.asList(a("g","Gishath, Sun's Avatar",7,6,true,"A")),d("A",40,0));
        check(gishath.score>7,"Gishath hit payoff valued without reading library");
        Plan goreclaw=plan(40,Arrays.asList(a("g","Goreclaw, Terror of Qal Sisma",4,3,false,"A")),d("A",40,0));
        check(goreclaw.score>4,"Goreclaw attack trigger valued without invented trample damage");
        Attacker krenko=new Attacker("k","Krenko, Mob Boss",3,3,9,false,set("A"),8);
        check(plan(40,Arrays.asList(krenko),d("A",40,0)).attacks.isEmpty(),"Krenko retains usable goblin-production tap");
        check(plan(40,Arrays.asList(krenko),d("A",3,0)).attacks.size()==1,"Krenko may trade tap opportunity for projected lethal");
        Attacker vigilantKrenko=new Attacker("k","Krenko, Mob Boss",3,3,9,true,set("A"),8);
        check(plan(40,Arrays.asList(vigilantKrenko),d("A",40,0)).attacks.size()==1,"Vigilance preserves Krenko tap opportunity");
        List<Attacker> elves=new ArrayList<Attacker>();
        elves.add(new Attacker("l","Lathril, Blade of the Elves",2,3,9,false,set("A"),1000));
        for(int i=0;i<10;i++) elves.add(new Attacker("elf"+i,"Elf",1,1,7,false,set("A"),1000));
        check(plan(40,elves,d("A",10,0)).attacks.isEmpty(),"Lathril and ten elves retain viable drain activation");
        elves.add(new Attacker("extra","Elf",1,1,1,false,set("A"),0));
        Plan spare=plan(40,elves,d("A",40,0));
        check(spare.attacks.size()==1&&spare.attacks.containsKey("extra"),"Only surplus elf attacks beyond reserved ten");
        Attacker giada=new Attacker("j","Giada, Font of Hope",2,2,8,true,set("A"),3);
        check(plan(40,Arrays.asList(giada),d("A",40,0)).attacks.size()==1,"Giada vigilance safely retains mana tap");
        check(plan(40,Arrays.asList(giada),d("A",40,0,new Blocker("enemy",2,2,set("j")))).attacks.isEmpty(),"Giada does not trade mana engine for no damage");
        Attacker tramplingGhalta=new Attacker("gh","Ghalta, Primal Hunger",12,12,10,false,set("A"),0,"trample",true,1,false);
        check(plan(40,Arrays.asList(tramplingGhalta),d("A",40,0,new Blocker("bear",2,2,set("gh")))).attacks.size()==1,"Ghalta tramples over a chump");
        check(plan(40,Arrays.asList(tramplingGhalta),d("A",40,0,new Blocker("g1",10,10,set("gh")),new Blocker("g2",10,10,set("gh")))).attacks.isEmpty(),"Ghalta does not assume damage through two giant blockers");
        Defender bearA=d("A",40,2,new Blocker("bearA",2,2,set("gh")));
        Defender bearB=d("B",40,2,new Blocker("bearB",2,2,set("gh")));
        Defender bearC=d("C",40,2,new Blocker("bearC",2,2,set("gh")));
        check(plan(40,Arrays.asList(tramplingGhalta),bearA,bearB,bearC).attacks.size()==1,"40-life Ghalta accepts safe six-power total crackback for ten damage");
        check(plan(5,Arrays.asList(tramplingGhalta),bearA,bearB,bearC).attacks.isEmpty(),"Low-life Ghalta preserves defense against aggregate lethal crackback");
        check(plan(5,Arrays.asList(tramplingGhalta),bearA).attacks.size()==1,"Single nonlethal foe differs from all-seat lethal risk");
        Attacker tramplingGishath=new Attacker("gs","Gishath, Sun's Avatar",7,6,10,true,set("A"),0,"trample",true,1,false);
        Plan tramplingHit=plan(40,Arrays.asList(tramplingGishath),d("A",40,0,new Blocker("bear",2,2,set("gs"))));
        check(tramplingHit.attacks.size()==1&&tramplingHit.score>5,"Gishath trample connection includes real hit payoff");
        check(plan(40,Arrays.asList(tramplingGishath),d("A",40,0,new Blocker("g1",10,10,set("gs")),new Blocker("g2",10,10,set("gs")))).attacks.isEmpty(),"Gishath avoids lethal wall with no projected connection");
        Attacker menace=new Attacker("lm","Lathril, Blade of the Elves",2,3,6,false,set("A"),0,"menace",false,2,false);
        check(plan(40,Arrays.asList(menace),d("A",40,0,new Blocker("giant",10,10,set("lm")))).attacks.size()==1,"One legal blocker cannot block Lathril menace");
        check(plan(40,Arrays.asList(menace),d("A",40,0,new Blocker("giant",10,10,set("lm"),100))).attacks.size()==1,"One multi-blocking creature is not two distinct menace blockers");
        check(plan(40,Arrays.asList(menace),d("A",40,0,new Blocker("g1",10,10,set("lm")),new Blocker("g2",10,10,set("lm")))).attacks.isEmpty(),"Two legal blockers can contain Lathril");
        Defender giantA=d("A",40,10,new Blocker("ga",10,10,set("lm")));
        Defender giantB=d("B",40,10,new Blocker("gb",10,10,set("lm")));
        Defender giantC=d("C",40,10,new Blocker("gc",10,10,set("lm")));
        check(plan(40,Arrays.asList(menace),giantA,giantB,giantC).attacks.size()==1,"40-life Lathril can accept a safe menace hit despite marginal lost reserve");
        check(plan(20,Arrays.asList(menace),giantA,giantB,giantC).attacks.isEmpty(),"Low-life Lathril does not worsen lethal all-seat exposure");
        check(plan(31,Arrays.asList(menace),giantA,giantB,giantC).attacks.isEmpty(),"Near-lethal exposure still carries more weight than a large cushion");
        Attacker goreTrigger=new Attacker("gc","Goreclaw, Terror of Qal Sisma",4,3,3,false,set("A"),0,"trigger",false,1,true);
        Attacker beast=new Attacker("beast","Beast",6,6,4,false,set("A"));
        Plan goreHit=plan(40,Arrays.asList(goreTrigger,beast),d("A",40,0,new Blocker("block",3,3,set("gc","beast"))));
        check(goreHit.attacks.size()==2&&goreHit.score>8,"Attacking Goreclaw projects own boost and granted trample");
        Attacker goreHome=new Attacker("gc","Goreclaw, Terror of Qal Sisma",4,3,3,false,set(),0,"trigger",false,1,true);
        check(plan(40,Arrays.asList(goreHome,beast),d("A",40,0,new Blocker("block",1,1,set("beast")))).score<2,"Goreclaw staying home grants no invented trample, only bounded attrition");
        check(!CombatPolicy.samePublicCombatGroup(tramplingGhalta,new Attacker("other","Ghalta, Primal Hunger",12,12,10,false,set("A"),0,"trample",false,1,false),Collections.<Defender>emptyList()),"Trample difference prevents grouping");
        check(!CombatPolicy.samePublicCombatGroup(menace,new Attacker("other","Lathril, Blade of the Elves",2,3,6,false,set("A"),0,"menace",false,1,false),Collections.<Defender>emptyList()),"Minimum blocker difference prevents grouping");
        List<Attacker> swarm=new ArrayList<Attacker>();Set<String> swarmIds=new HashSet<String>();
        for(int i=0;i<12;i++) {String id="t"+i;swarm.add(new Attacker(id,"Token",1,1,0.1,false,set("A")));swarmIds.add(id);}
        Plan wide=plan(40,swarm,d("A",8,0,new Blocker("wall",1,8,swarmIds)));
        check(wide.attacks.size()>=9,"Swarm seed breaks through blocker instead of considering only solo attacks");
        Collections.reverse(swarm);check(plan(40,swarm,d("A",8,0,new Blocker("wall",1,8,swarmIds))).attacks.equals(wide.attacks),"Attacker order invariant");
        List<Attacker> large=new ArrayList<Attacker>();for(int i=0;i<200;i++) large.add(a("n"+i,"Token",1,1,false,"A","B","C"));
        Plan bounded=plan(40,large,d("A",40,0),d("B",40,0),d("C",40,0));
        check(bounded.attacks.size()>CombatPolicy.MAX_ATTACKERS,"Equivalent surplus is not stranded by optimization cap");
        check(bounded.evaluations<=CombatPolicy.MAX_EVALUATIONS,"Decision evaluation budget");
        List<Attacker> scutes=new ArrayList<Attacker>();Set<String> scuteIds=new HashSet<String>();
        for(int i=0;i<500;i++) {String id=String.format("scute%04d",i);scuteIds.add(id);scutes.add(new Attacker(id,"Scute Swarm",1,1,1.3,false,set("A"),0,"Scute public rules"));}
        Plan scutePlan=plan(40,scutes,d("A",40,0,new Blocker("wall",4,4,scuteIds)));
        check(scutePlan.attacks.size()>32,"500 realistic-value Scutes attack past one blocker");
        check(scutePlan.evaluations<=CombatPolicy.MAX_EVALUATIONS,"Overflow preserves evaluation bound");
        List<Blocker> manyWalls=new ArrayList<Blocker>();for(int i=0;i<40;i++)manyWalls.add(new Blocker("wall"+i,4,4,scuteIds));
        Plan overwhelm=CombatPolicy.plan("",40,scutes,Arrays.asList(new Defender("A",40,manyWalls,0)));
        check(overwhelm.attacks.size()>32,"Bulk seed finds army attack even when all sampled attackers can be blocked");
        Collections.reverse(scutes);
        check(plan(40,scutes,d("A",40,0,new Blocker("wall",4,4,scuteIds))).attacks.equals(scutePlan.attacks),"Overflow invariant to creature order");
        check(plan(40,scutes,d("A",40,0,new Blocker("multi",4,4,scuteIds,500))).attacks.isEmpty(),"Actual multi-block capacity can contain swarm");
        // These sort after the sampled 32. They must not borrow an eligible token's plan.
        scutes.add(new Attacker("zzTapped","Scute Swarm",1,0,1.3,false,set(),0,"Scute public rules"));
        scutes.add(new Attacker("zzSick","Scute Swarm",1,1,1.3,false,set(),0,"Scute public rules"));
        scutes.add(new Attacker("zzOtherSeat","Scute Swarm",1,1,1.3,false,set("B"),0,"Scute public rules"));
        scutes.add(new Attacker("zzTapEngine","Scute Swarm",1,1,1.3,false,set("A"),1000,"Scute public rules"));
        scutes.add(new Attacker("zzAttached","Scute Swarm",1,1,1.3,false,set("A"),0,"individual:zzAttached"));
        scutes.add(new Attacker("zzCounter","Scute Swarm",1,1,1.3,false,set("A"),0,"individual:zzCounter"));
        scutes.add(new Attacker("zzDifferentRules","Scute Swarm",1,1,1.3,false,set("A"),0,"Different public rules"));
        scutes.add(new Attacker("zzDifferentBlockEdge","Scute Swarm",1,1,1.3,false,set("A"),0,"Scute public rules"));
        Plan restricted=plan(40,scutes,d("A",40,0,new Blocker("wall",4,4,scuteIds)),d("B",40,0));
        for(String id:Arrays.asList("zzTapped","zzSick","zzTapEngine"))
            check(!restricted.attacks.containsKey(id),"Overflow does not inherit eligibility/features: "+id);
        check(!"A".equals(restricted.attacks.get("zzOtherSeat")),"Whole-army seed still honors each creature's legal seat");
        Attacker normal=scutes.get(0);
        for(Attacker modified:scutes) if(modified.id.startsWith("zz"))
            check(!CombatPolicy.samePublicCombatGroup(normal,modified,Arrays.asList(d("A",40,0,new Blocker("wall",4,4,scuteIds)))),"Differing public feature/block edge cannot join equivalent overflow: "+modified.id);
        List<Attacker> distinctArmy=new ArrayList<Attacker>();
        for(int i=0;i<70;i++)distinctArmy.add(new Attacker("angel"+i,"Angel",4,4,5,true,set("A"),0,"individual:angel"+i));
        check(plan(40,distinctArmy,d("A",100,0)).attacks.size()>32,"Individually legal modified army can use separate whole-army seed");
        // Sending every equivalent token would expose lethal crackback; rescoring must
        // preserve a useful reserve rather than blindly expanding the sample.
        Plan reserve=plan(5,scutes,d("A",1000,100));
        check(reserve.attacks.size()<500,"Overflow is rescored against lost blocking reserve");
        Attacker capped=new Attacker("dino","Gishath, Sun's Avatar",7,6,10,true,set("A"),0,"temple",true,1,false,1);
        Attacker uncapped=new Attacker("dino","Gishath, Sun's Avatar",7,6,10,true,set("A"),0,"temple",true,1,false);
        Defender twoCentaurs=d("A",40,6,new Blocker("c1",3,3,set("dino")),new Blocker("c2",3,3,set("dino")));
        check(plan(40,Arrays.asList(capped),twoCentaurs).attacks.size()==1,"Known per-source prevention enables surviving Gishath connection");
        check(plan(40,Arrays.asList(uncapped),twoCentaurs).attacks.isEmpty(),"No prevention cannot invent surviving Gishath");
        List<Blocker> sixSources=new ArrayList<Blocker>();for(int i=0;i<6;i++)sixSources.add(new Blocker("src"+i,3,3,set("dino")));
        check(plan(40,Arrays.asList(capped),new Defender("A",40,sixSources,18)).attacks.isEmpty(),"Six sources still kill capped six toughness");
        check(!CombatPolicy.samePublicCombatGroup(capped,uncapped,Arrays.asList(twoCentaurs)),"Prevention enters grouping fingerprint");
        Attacker safeGiant=new Attacker("giant","Giant",7,8,8,true,set("A"));
        check(plan(40,Arrays.asList(safeGiant),d("A",40,0,new Blocker("wall",0,7,set("giant")))).attacks.size()==1,"Safe nonconnecting attack values attrition");
        check(plan(40,Arrays.asList(safeGiant),d("A",40,0,new Blocker("wall",0,8,set("giant")))).attacks.isEmpty(),"Unkillable wall supplies no casualty");
        check(plan(40,Arrays.asList(safeGiant),d("A",40,0,new Blocker("wall",0,7,set("giant"),1,false))).attacks.isEmpty(),"Unsupported blocker supplies no casualty");
        check(plan(40,Arrays.asList(safeGiant),d("A",40,0,new Blocker("lethal",8,7,set("giant")))).attacks.isEmpty(),"Unsafe trade receives no safe attrition bonus");
        Attacker riskyGiant=new Attacker("giant","Giant",7,8,8,false,set("A"));
        check(plan(7,Arrays.asList(riskyGiant),d("A",40,10,new Blocker("wall",0,7,set("giant")))).attacks.isEmpty(),"Attrition cannot override newly lethal exposure");
        List<Attacker> seedArmy=new ArrayList<Attacker>();Set<String> seedIds=new HashSet<String>();
        for(int i=0;i<100;i++){String id=String.format("g%03d",i);seedIds.add(id);seedArmy.add(new Attacker(id,"Goblin",2,1,1.6,false,set("A"),0,"same"));}
        List<Blocker> seedBlockers=new ArrayList<Blocker>();for(int i=0;i<32;i++)seedBlockers.add(new Blocker("b"+i,1,2,seedIds));
        List<Defender> seedSeats=Arrays.asList(new Defender("A",40,seedBlockers,32),d("B",40,25));
        Plan intermediate=CombatPolicy.plan("",10,seedArmy,seedSeats);
        check(intermediate.attacks.size()>32&&intermediate.attacks.size()<100&&intermediate.score>0,"Cross blocked-sample valley without all-in exposure");
        check(intermediate.evaluations<=CombatPolicy.MAX_EVALUATIONS,"Intermediate budget");
        Collections.reverse(seedArmy);Collections.reverse(seedSeats);
        check(CombatPolicy.plan("",10,seedArmy,seedSeats).attacks.equals(intermediate.attacks),"Intermediate permutation invariant");
        seedArmy.add(new Attacker("held","Goblin",2,1,1.6,false,set("A"),1000,"same"));
        seedArmy.add(new Attacker("sick","Goblin",2,1,1.6,false,set(),0,"same"));
        Plan seedMixed=CombatPolicy.plan("",10,seedArmy,seedSeats);
        check(!seedMixed.attacks.containsKey("held")&&!seedMixed.attacks.containsKey("sick"),"No reserved or illegal expansion");
        check(CombatPolicy.plan("",10,seedArmy,Arrays.asList(seedSeats.get(1),d("B",40,100))).attacks.isEmpty(),"No intermediate lethal waiver");
        List<Attacker> freshHaste=new ArrayList<Attacker>();
        for(int i=0;i<100;i++){String id=String.format("g%03d",i);freshHaste.add(new Attacker(id,"Goblin",2,1,1.6,false,set("A"),0,"individual:"+id));}
        Plan freshPlan=CombatPolicy.plan("",10,freshHaste,seedSeats);
        check(freshPlan.attacks.size()>32&&freshPlan.attacks.size()<100,"Distinct legal fresh-haste bodies use intermediate army seed");
        check(freshPlan.evaluations<=CombatPolicy.MAX_EVALUATIONS,"Distinct intermediate budget");
        List<Attacker> sickNoHaste=new ArrayList<Attacker>();
        for(Attacker a:freshHaste)sickNoHaste.add(new Attacker(a.id,a.name,a.power,a.toughness,a.value,a.vigilance,set(),0,a.publicCombatKey));
        check(CombatPolicy.plan("",10,sickNoHaste,seedSeats).attacks.isEmpty(),"Sick bodies without legal haste edges stay home");
        freshHaste.add(new Attacker("uniqueReserved","Goblin",2,1,1.6,false,set("A"),1000,"individual:reserved"));
        Plan reservedFresh=CombatPolicy.plan("",10,freshHaste,seedSeats);
        check(!reservedFresh.attacks.containsKey("uniqueReserved"),"Intermediate army excludes reserved engine");
        Collections.reverse(freshHaste);Collections.reverse(seedSeats);
        check(CombatPolicy.plan("",10,freshHaste,seedSeats).attacks.equals(reservedFresh.attacks),"Distinct input and seat permutation invariant");
        boolean immutable=false;try {bounded.attacks.clear();}catch(UnsupportedOperationException e){immutable=true;}
        check(immutable,"Immutable result");
        System.out.println("CombatPolicyTest: "+checks+" checks passed");
    }
}
