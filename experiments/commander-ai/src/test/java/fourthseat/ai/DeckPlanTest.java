package fourthseat.ai;

import java.util.*;
import fourthseat.ai.PolicyModel.*;

/** Run with -ea. Ten plan pairs plus targeted sequencing/stack/mulligan regressions. */
public final class DeckPlanTest {
    private static final String[] COMMANDERS = {"Giada, Font of Hope", "Krenko, Mob Boss", "Lathril, Blade of the Elves", "Ghalta, Primal Hunger", "Goreclaw, Terror of Qal Sisma", "Torbran, Thane of Red Fell", "Talrand, Sky Summoner", "Aesi, Tyrant of Gyre Strait", "Gishath, Sun's Avatar", "Adeline, Resplendent Cathar"};
    private static final String[] RAMP = {"Sol Ring", "Sol Ring", "Llanowar Elves", "Llanowar Elves", "Sol Ring", "Sol Ring", "Sol Ring", "Nature's Lore", "Nature's Lore", "Sol Ring"};
    private static final String[] ANSWER = {"Teferi's Protection", "Bolt Bend", "Tyvar's Stand", "Collective Resistance", "Heroic Intervention", "Deflecting Swat", "Counterspell", "Counterspell", "Heroic Intervention", "Teferi's Protection"};
    private static int checks;
    private static void check(boolean value, String message) {checks++; if(!value) throw new AssertionError(message);}
    private static CardView card(String id,String n,int mv,boolean land,boolean creature,int power) { Set<String> types = new HashSet<String>();
        if (n.equals("Youthful Valkyrie") || n.equals(COMMANDERS[0])) types.add("Angel");
        if (n.equals(COMMANDERS[1])) types.add("Goblin");
        if (n.equals(COMMANDERS[2]) || n.equals("Llanowar Elves")) types.add("Elf");
        if (n.equals(COMMANDERS[8]) || n.equals("Pugnacious Hammerskull") || n.equals("Quartzwood Crasher")) types.add("Dinosaur");
        return new CardView(id,n,mv,land,creature,power,power,false,"","self",Collections.<String>emptyList(),false,types); }
    private static Action action(String id,String n,int mv,String... targets) {return new Action(id,n,"Cast "+n,mv,false,false,true,Arrays.asList(targets));}
    private static Observation observation(String cmd,List<CardView> hand,List<CardView> board,List<CardView> stack) {
        return new Observation("self",cmd,"PRECOMBAT_MAIN",true,4,hand,Arrays.asList(new Seat("self",40,hand.size(),board),new Seat("enemy",40,3,Collections.<CardView>emptyList())),stack);
    }
    private static CommanderPolicy policy() {return new CommanderPolicy(new CommanderPolicy.Clock() {public long nanoTime(){return 0;}});}
    private static List<CardView> empty() {return Collections.emptyList();}
    public static void main(String[] args) {
        for(int i=0;i<COMMANDERS.length;i++) {
            String cmd=COMMANDERS[i]; DeckPlan plan=DeckPlan.forCommander(cmd);
            check(plan.knownCardCount()>60,"Frozen profile populated: "+cmd);
            check(plan.role(RAMP[i])==DeckPlan.Role.RAMP,"Exact ramp role: "+cmd);
            List<CardView> board=Arrays.asList(card("l1","Forest",0,true,false,0),card("l2","Forest",0,true,false,0),card("key",cmd,4,false,true,3));
            List<CardView> hand=Arrays.asList(card("r",RAMP[i],1,false,RAMP[i].contains("Elves"),1),card("a",ANSWER[i],3,false,false,0));
            Observation o=observation(cmd,hand,board,empty());
            Decision d=policy().choose(o,Arrays.asList(action("ramp",RAMP[i],1),action("answer",ANSWER[i],3)),1000000);
            check("ramp".equals(d.actionId),"Develop rather than waste answer: "+cmd);
            check(policy().choose(o,Arrays.asList(action("answer",ANSWER[i],3)),1000000).actionId==null,"Pass rather than unneeded interaction: "+cmd);
            CardView enemySpell=new CardView("spell","Public targeted spell",5,false,false,0,0,false,"destroy target permanent","enemy",Arrays.asList("key"));
            o=observation(cmd,hand,board,Arrays.asList(enemySpell));
            d=policy().choose(o,Arrays.asList(action("answer",ANSWER[i],3,(i==2 || i==3)?"key":"spell")),1000000);
            check("answer".equals(d.actionId),"Respond to public threat: "+cmd);
            CardView ownSpell=new CardView("spell","Own spell",5,false,false,0,0,false,"draw","self",Arrays.asList("l1"));
            o=observation(cmd,hand,board,Arrays.asList(ownSpell));
            check(policy().choose(o,Arrays.asList(action("answer",ANSWER[i],3,(i==2 || i==3)?"key":"spell")),1000000).actionId==null,"Do not answer own spell: "+cmd);
            o=observation(cmd,empty(),board,empty());
            check("commander".equals(policy().choose(o,Arrays.asList(action("commander",cmd,4)),1000000).actionId),"Commander development: "+cmd);
            System.out.println("PASS "+cmd+": ramp, preserve answer, respond, own-stack veto, commander");
        }
        String[] synergy = {"Youthful Valkyrie", "Impact Tremors", "Vitalize", "Pugnacious Hammerskull", "Questing Beast", "Mechanized Warfare", "Ponder", "Nature's Lore", "Gishath, Sun's Avatar", "Anointed Procession"};
        for (int i=0;i<COMMANDERS.length;i++) {
            String cmd = COMMANDERS[i], n = synergy[i];
            CardView source = card("source",n,2,false,i==0 || i==3 || i==4 || i==8,6);
            List<CardView> hand = Arrays.asList(source,card("land","Forest",0,true,false,0));
            List<CardView> withCommander = new ArrayList<CardView>();
            List<CardView> withoutCommander = new ArrayList<CardView>();
            withCommander.add(card("cmd",cmd,4,false,true,3));
            withoutCommander.add(card("other","Creature token",0,false,true,3));
            if (i==2) for (int j=0;j<10;j++) {
                withCommander.add(new CardView("elf"+j,"Elf token",0,false,true,1,1,true,"","self",Collections.<String>emptyList(),false,Collections.singleton("Elf")));
                withoutCommander.add(new CardView("elf"+j,"Elf token",0,false,true,1,1,true,"","self",Collections.<String>emptyList(),false,Collections.singleton("Elf")));
            }
            Action candidate=action("synergy",n,i==8?8:2);
            Observation with = observation(cmd,hand,withCommander,empty());
            Observation without = observation(cmd,hand,withoutCommander,empty());
            if (i==8) {
                Decision choice=policy().choose(without,Arrays.asList(candidate,action("ramp","Nature's Lore",2)),1000000);
                check("synergy".equals(choice.actionId),"Legal Gishath takes priority over more ramp");
            } else {
                double a=policy().choose(with,Arrays.asList(candidate),1000000).score;
                double b=policy().choose(without,Arrays.asList(candidate),1000000).score;
                check(i==3 ? b>a : a>b,"Commander-specific synergy/counterfactual: "+cmd+" via "+n);
            }
        }
        String adeline=COMMANDERS[9]; DeckPlan p=DeckPlan.forCommander(adeline);
        List<CardView> army=Arrays.asList(card("c1",adeline,3,false,true,4),card("c2","Human",0,false,true,1),card("c3","Human",0,false,true,1));
        Action will=action("will","Akroma's Will",4);
        check(p.actionValue(will,observation(adeline,empty(),army,empty()))>0,"Combat payoff with army");
        check(p.actionValue(will,observation(adeline,empty(),empty(),empty()))<0,"No empty-board combat payoff");
        String aesi=COMMANDERS[7]; p=DeckPlan.forCommander(aesi);
        CardView land=card("land","Forest",0,true,false,0);
        List<CardView> engine=Arrays.asList(card("aesi",aesi,6,false,true,5));
        Action ramp=action("ramp","Nature's Lore",2);
        check(p.actionValue(ramp,observation(aesi,Arrays.asList(land),engine,empty()))>p.actionValue(ramp,observation(aesi,Arrays.asList(land),empty(),empty())),"Aesi rewards land ramp with engine");
        p=DeckPlan.forCommander(COMMANDERS[8]);
        List<CardView> keep=Arrays.asList(card("g","Forest",0,true,false,0),card("r","Mountain",0,true,false,0),card("w","Plains",0,true,false,0),card("ramp","Nature's Lore",2,false,false,0),card("dino","Zetalpa, Primal Dawn",8,false,true,4));
        check(p.keep(keep,0),"Naya early green and curve keep");
        check(!p.keep(Arrays.asList(card("l1","Mountain",0,true,false,0),card("l2","Mountain",0,true,false,0),card("dino","Zetalpa, Primal Dawn",8,false,true,4)),0),"Reject missing green and no early curve");
        check(DeckPlan.forCommander(COMMANDERS[0]).role("Counterspell")==DeckPlan.Role.BODY,"No role leakage outside exact deck");
        String talrand = COMMANDERS[6];
        List<CardView> reserveHand=Arrays.asList(card("counter","Counterspell",2,false,false,0),card("mystic","Murmuring Mystic",4,false,true,1));
        Observation base=observation(talrand,reserveHand,Arrays.asList(card("talrand",talrand,4,false,true,2)),empty());
        Observation tight=new Observation(base.selfId,base.commander,base.step,base.active,base.turn,base.hand,base.seats,base.stack,Arrays.asList(new ManaView(0,2,0,0,0,2,0,0)));
        Observation enough=new Observation(base.selfId,base.commander,base.step,base.active,base.turn,base.hand,base.seats,base.stack,Arrays.asList(new ManaView(0,4,0,0,0,2,0,0)));
        Action mystic=action("mystic","Murmuring Mystic",4);
        check(policy().choose(tight,Arrays.asList(mystic),1000000).actionId==null,"Talrand preserves counter budget rather than taps out");
        check("mystic".equals(policy().choose(enough,Arrays.asList(mystic),1000000).actionId),"Talrand develops when hint leaves counter budget");
        check("mystic".equals(policy().choose(base,Arrays.asList(mystic),1000000).actionId),"No invented mana budget when hints absent");
        List<CardView> protectedArmy=Arrays.asList(card("own","Creature token",0,false,true,3));
        CardView wrath=new CardView("wrath","Wrath of God",4,false,false,0,0,false,"destroy all creatures","enemy",Collections.<String>emptyList());
        CardView farewell=new CardView("farewell","Farewell",6,false,false,0,0,false,"exile all creatures","enemy",Collections.<String>emptyList());
        Observation wrathObs=observation(COMMANDERS[0],empty(),protectedArmy,Arrays.asList(wrath));
        check("protect".equals(policy().choose(wrathObs,Arrays.asList(action("protect","Flawless Maneuver",3)),1000000).actionId),"Indestructibility responds to untargeted destroy wipe");
        Observation exileObs=observation(COMMANDERS[0],empty(),protectedArmy,Arrays.asList(farewell));
        check(policy().choose(exileObs,Arrays.asList(action("protect","Flawless Maneuver",3)),1000000).actionId==null,"Indestructibility is not exile protection");
        check("phase".equals(policy().choose(exileObs,Arrays.asList(action("phase","Teferi's Protection",3)),1000000).actionId),"Phasing responds to untargeted exile wipe");
        Observation redWrath=observation(COMMANDERS[1],empty(),protectedArmy,Arrays.asList(wrath));
        check(policy().choose(redWrath,Arrays.asList(action("bend","Bolt Bend",4)),1000000).actionId==null,"Do not redirect untargeted wipe");
        List<CardView> attackers=new ArrayList<CardView>(), idle=new ArrayList<CardView>();
        for(int i=0;i<3;i++) {
            attackers.add(new CardView("attacker"+i,"Creature token",0,false,true,3,3,true,"","enemy",Collections.<String>emptyList(),true));
            idle.add(card("idle"+i,"Creature token",0,false,true,3));
        }
        Observation attacked=new Observation("self",talrand,"DECLARE_BLOCKERS",false,5,empty(),Arrays.asList(new Seat("self",20,1,empty()),new Seat("enemy",30,2,attackers)));
        Observation beforeAttack=new Observation("self",talrand,"BEGIN_COMBAT",false,5,empty(),Arrays.asList(new Seat("self",20,1,empty()),new Seat("enemy",30,2,idle)));
        check("bounce".equals(policy().choose(attacked,Arrays.asList(action("bounce","Aetherize",4)),1000000).actionId),"Aetherize responds to actual public attackers");
        check(policy().choose(beforeAttack,Arrays.asList(action("bounce","Aetherize",4)),1000000).actionId==null,"Do not Aetherize idle creatures at begin combat");
        String torbran=COMMANDERS[5];
        List<CardView> torbranBoard=Arrays.asList(card("torbran",torbran,4,false,true,2));
        List<CardView> smallEnemy=Arrays.asList(card("small","Creature token",0,false,true,1));
        Action boltFace=action("face","Lightning Bolt",1,"enemy"), boltSmall=action("small","Lightning Bolt",1,"small"), boltSelf=action("self","Lightning Bolt",1,"self");
        Observation boltLethal=new Observation("self",torbran,"PRECOMBAT_MAIN",true,6,empty(),Arrays.asList(new Seat("self",30,1,torbranBoard),new Seat("enemy",5,2,smallEnemy)));
        check("face".equals(policy().choose(boltLethal,Arrays.asList(boltSmall,boltSelf,boltFace),1000000).actionId),"Torbran Bolt uses known 3+2 to prioritize lethal player over small creature and self");
        Observation boltNotLethal=new Observation("self",torbran,"PRECOMBAT_MAIN",true,6,empty(),Arrays.asList(new Seat("self",30,1,torbranBoard),new Seat("enemy",6,2,smallEnemy)));
        check(policy().choose(boltNotLethal,Arrays.asList(boltSmall,boltSelf,boltFace),1000000).actionId==null,"Preserve Bolt when no lethal player or worthwhile creature exists");
        Observation noTorbran=new Observation("self",torbran,"PRECOMBAT_MAIN",true,6,empty(),Arrays.asList(new Seat("self",30,1,empty()),new Seat("enemy",5,2,smallEnemy)));
        check(policy().choose(noTorbran,Arrays.asList(boltFace),1000000).actionId==null,"No invented Torbran bonus without commander");
        Observation naturalBolt=new Observation("self",torbran,"PRECOMBAT_MAIN",true,6,empty(),Arrays.asList(new Seat("self",30,1,empty()),new Seat("enemy",3,2,smallEnemy)));
        check("face".equals(policy().choose(naturalBolt,Arrays.asList(boltFace),1000000).actionId),"Unmodified Bolt recognizes three-life opponent");
        CardView angel=card("angel","Youthful Valkyrie",2,false,true,1);
        CardView untyped=new CardView("angel","Youthful Valkyrie",2,false,true,1,1,false,"");
        List<CardView> giadaBoard=Arrays.asList(card("giada",COMMANDERS[0],2,false,true,2));
        Action angelCast=action("angel","Youthful Valkyrie",2);
        check(policy().choose(observation(COMMANDERS[0],Arrays.asList(angel),giadaBoard,empty()),Arrays.asList(angelCast),1000000).score
            > policy().choose(observation(COMMANDERS[0],Arrays.asList(untyped),giadaBoard,empty()),Arrays.asList(angelCast),1000000).score,"Giada bonus requires actual Angel subtype, not matching card name");
        List<CardView> goblins=new ArrayList<CardView>(), elves=new ArrayList<CardView>();
        for(int i=0;i<4;i++) {
            goblins.add(new CardView("g"+i,"Token",0,false,true,1,1,true,"","self",Collections.<String>emptyList(),false,Collections.singleton("Goblin")));
            elves.add(new CardView("e"+i,"Token",0,false,true,1,1,true,"","self",Collections.<String>emptyList(),false,Collections.singleton("Elf")));
        }
        Action impact=action("impact","Impact Tremors",2);
        check(policy().choose(observation(COMMANDERS[1],empty(),goblins,empty()),Arrays.asList(impact),1000000).score
            > policy().choose(observation(COMMANDERS[1],empty(),elves,empty()),Arrays.asList(impact),1000000).score,"Krenko tribal payoff counts Goblins, not all creatures");
        List<CardView> wrongArmy=new ArrayList<CardView>(); wrongArmy.add(card("lathril",COMMANDERS[2],4,false,true,2));
        for(int i=0;i<10;i++) wrongArmy.add(new CardView("g"+i,"Token",0,false,true,1,1,true,"","self",Collections.<String>emptyList(),false,Collections.singleton("Goblin")));
        check(policy().choose(observation(COMMANDERS[2],empty(),wrongArmy,empty()),Arrays.asList(action("untap","Vitalize",1)),1000000).actionId==null,"Lathril does not count ten Goblins as ten Elves");
        List<CardView> eightLands=new ArrayList<CardView>(); for(int i=0;i<8;i++) eightLands.add(card("land"+i,"Forest",0,true,false,0));
        CardView dinosaur=card("dino","Quartzwood Crasher",5,false,true,6), nonDinosaur=new CardView("dino","Quartzwood Crasher",5,false,true,6,6,false,"");
        Action dinoCast=action("dino","Quartzwood Crasher",5);
        check(policy().choose(observation(COMMANDERS[8],Arrays.asList(dinosaur),eightLands,empty()),Arrays.asList(dinoCast),1000000).score
            > policy().choose(observation(COMMANDERS[8],Arrays.asList(nonDinosaur),eightLands,empty()),Arrays.asList(dinoCast),1000000).score,"Gishath late Dinosaur bonus requires public subtype");
        CardView bears=new CardView("bait","Grizzly Bears",2,false,true,2,2,false,"","enemy",Collections.<String>emptyList());
        Observation harmlessBears=new Observation("self",talrand,"PRECOMBAT_MAIN",false,5,empty(),Arrays.asList(new Seat("self",30,2,empty()),new Seat("enemy",30,2,empty())),Arrays.asList(bears));
        Action counterBears=action("counter","Counterspell",2,"bait");
        check(policy().choose(harmlessBears,Arrays.asList(counterBears),1000000).actionId==null,"Preserve counter against harmless small creature bait");
        Observation lethalBears=new Observation("self",talrand,"PRECOMBAT_MAIN",false,5,empty(),Arrays.asList(new Seat("self",2,2,empty()),new Seat("enemy",30,2,empty())),Arrays.asList(bears));
        check("counter".equals(policy().choose(lethalBears,Arrays.asList(counterBears),1000000).actionId),"Small creature becomes meaningful at low own life");
        CardView study=new CardView("engine","Rhystic Study",3,false,false,0,0,false,"draw a card","enemy",Collections.<String>emptyList());
        check("counter".equals(policy().choose(observation(talrand,empty(),empty(),Arrays.asList(study)),Arrays.asList(action("counter","Counterspell",2,"engine")),1000000).actionId),"Counter known high-impact cheap engine");
        CardView removal=new CardView("removal","Swords to Plowshares",1,false,false,0,0,false,"exile target creature","enemy",Arrays.asList("talrand"));
        check("counter".equals(policy().choose(observation(talrand,empty(),Arrays.asList(card("talrand",talrand,4,false,true,2)),Arrays.asList(removal)),Arrays.asList(action("counter","Counterspell",2,"removal")),1000000).actionId),"Protect own commander from cheap targeted removal");
        Action borosDamage=new Action("damage","Boros Charm","Choose one",2,false,false,true,Arrays.asList("enemy"),ActionKind.SPELL,Collections.singleton("Damage"),"boros","Boros Charm deals 4 damage to target player");
        Action borosDouble=new Action("double","Boros Charm","Choose one",2,false,false,true,Arrays.asList("own"),ActionKind.SPELL,Collections.singleton("AddAbility"),"boros","Target creature gains double strike until end of turn");
        Action borosSave=new Action("save","Boros Charm","Choose one",2,false,false,true,Collections.<String>emptyList(),ActionKind.SPELL,Collections.singleton("AddAbility"),"boros","Permanents you control gain indestructible until end of turn");
        Observation borosWrath=observation(COMMANDERS[8],empty(),protectedArmy,Arrays.asList(wrath));
        check("save".equals(policy().choose(borosWrath,Arrays.asList(borosDamage,borosDouble,borosSave),1000000).actionId),"Boros Charm selects indestructible, not damage or double strike, against Wrath");
        check(policy().choose(borosWrath,Arrays.asList(borosDamage,borosDouble),1000000).actionId==null,"Wrong Boros modes do not receive protection score");
        List<CardView> twoOwn=Arrays.asList(card("key",COMMANDERS[2],4,false,true,2),card("other","Creature token",0,false,true,1));
        CardView keyRemoval=new CardView("remove","Swords to Plowshares",1,false,false,0,0,false,"exile target creature","enemy",Arrays.asList("key"));
        Observation tyvarRemoval=observation(COMMANDERS[2],empty(),twoOwn,Arrays.asList(keyRemoval));
        Action rightTyvar=new Action("right","Tyvar's Stand","Target creature",1,false,false,true,Arrays.asList("key"),ActionKind.SPELL,Collections.singleton("AddAbility"),"tyvar","Target creature gains hexproof and indestructible until end of turn");
        Action wrongTyvar=new Action("wrong","Tyvar's Stand","Target creature",1,false,false,true,Arrays.asList("other"),ActionKind.SPELL,Collections.singleton("AddAbility"),"tyvar","Target creature gains hexproof and indestructible until end of turn");
        check("right".equals(policy().choose(tyvarRemoval,Arrays.asList(wrongTyvar,rightTyvar),1000000).actionId),"Targeted protection chooses actually threatened own creature");
        check(policy().choose(tyvarRemoval,Arrays.asList(wrongTyvar),1000000).actionId==null,"Do not protect unrelated own creature");
        Action charmDraw=new Action("draw","Archmage's Charm","Choose one",3,false,false,true,Arrays.asList("self"),ActionKind.SPELL,Collections.singleton("DrawCard"),"charm","Target player draws two cards");
        check("draw".equals(policy().choose(observation(talrand,empty(),empty(),empty()),Arrays.asList(charmDraw),1000000).actionId),"Archmage Charm draw mode works with an empty stack");
        List<CardView> fiveGoblins=new ArrayList<CardView>(goblins); fiveGoblins.add(card("krenko",COMMANDERS[1],4,false,true,3));
        Observation goblinLethal=new Observation("self",COMMANDERS[1],"PRECOMBAT_MAIN",true,6,empty(),Arrays.asList(new Seat("self",30,2,fiveGoblins),new Seat("enemy",5,2,smallEnemy)));
        Observation goblinNotLethal=new Observation("self",COMMANDERS[1],"PRECOMBAT_MAIN",true,6,empty(),Arrays.asList(new Seat("self",30,2,fiveGoblins),new Seat("enemy",6,2,smallEnemy)));
        for(String burn:Arrays.asList("Goblin Grenade","Goblin War Strike","Massive Raid")) {
            Action finisher=action("finisher",burn,1,"enemy");
            check("finisher".equals(policy().choose(goblinLethal,Arrays.asList(finisher),1000000).actionId),"Known Goblin burn estimates lethal: "+burn);
            check(policy().choose(goblinNotLethal,Arrays.asList(finisher),1000000).actionId==null,"Preserve nonlethal Goblin burn: "+burn);
        }
        Action golgariShrink=new Action("shrink","Golgari Charm","Choose one",2,false,false,true,Collections.<String>emptyList(),ActionKind.SPELL,Collections.singleton("UnboostCreature"),"charm","All creatures get -1/-1 until end of turn");
        Action golgariDestroy=new Action("destroy","Golgari Charm","Choose one",2,false,false,true,Collections.<String>emptyList(),ActionKind.SPELL,Collections.singleton("DestroyPermanent"),"charm","Destroy target enchantment");
        Action golgariRegen=new Action("regen","Golgari Charm","Choose one",2,false,false,true,Collections.<String>emptyList(),ActionKind.SPELL,Collections.singleton("Regenerate"),"charm","Regenerate each creature you control");
        CardView destroyLathril=new CardView("destroy","Beast Within",3,false,false,0,0,false,"Destroy target permanent","enemy",Arrays.asList("key"));
        Observation lathrilThreat=observation(COMMANDERS[2],empty(),twoOwn,Arrays.asList(destroyLathril));
        check("regen".equals(policy().choose(lathrilThreat,Arrays.asList(golgariShrink,golgariDestroy,golgariRegen),1000000).actionId),"Golgari Charm chooses regeneration, not shrink or destroy mode, to save Lathril");
        check(policy().choose(lathrilThreat,Arrays.asList(golgariShrink,golgariDestroy),1000000).actionId==null,"Golgari wrong modes cannot inherit protection score");
        check(policy().choose(tyvarRemoval,Arrays.asList(golgariRegen),1000000).actionId==null,"Golgari regeneration does not stop Swords exile");
        check(policy().choose(observation(COMMANDERS[2],empty(),twoOwn,Arrays.asList(wrath)),Arrays.asList(golgariRegen),1000000).actionId==null,"Golgari regeneration does not stop Wrath of God");
        System.out.println("DeckPlanTest: "+checks+" checks passed");
    }
}
