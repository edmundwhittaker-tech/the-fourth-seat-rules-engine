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
        List<Attacker> swarm=new ArrayList<Attacker>();Set<String> swarmIds=new HashSet<String>();
        for(int i=0;i<12;i++) {String id="t"+i;swarm.add(new Attacker(id,"Token",1,1,0.1,false,set("A")));swarmIds.add(id);}
        Plan wide=plan(40,swarm,d("A",8,0,new Blocker("wall",1,8,swarmIds)));
        check(wide.attacks.size()>=9,"Swarm seed breaks through blocker instead of considering only solo attacks");
        Collections.reverse(swarm);check(plan(40,swarm,d("A",8,0,new Blocker("wall",1,8,swarmIds))).attacks.equals(wide.attacks),"Attacker order invariant");
        List<Attacker> large=new ArrayList<Attacker>();for(int i=0;i<200;i++) large.add(a("n"+i,"Token",1,1,false,"A","B","C"));
        Plan bounded=plan(40,large,d("A",40,0),d("B",40,0),d("C",40,0));
        check(bounded.attacks.size()<=CombatPolicy.MAX_ATTACKERS,"Large-board attack cap");
        check(bounded.evaluations<=CombatPolicy.MAX_EVALUATIONS,"Decision evaluation budget");
        boolean immutable=false;try {bounded.attacks.clear();}catch(UnsupportedOperationException e){immutable=true;}
        check(immutable,"Immutable result");
        System.out.println("CombatPolicyTest: "+checks+" checks passed");
    }
}
