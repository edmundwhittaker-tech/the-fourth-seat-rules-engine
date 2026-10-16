package fourthseat.ai;
import java.util.*;
import fourthseat.ai.PolicyModel.*;

public final class CommanderPolicyTest {
 static CardView card(String id,String name,int cost,boolean land,int power){return new CardView(id,name,cost,land,!land,power,2,false,"");}
 static Seat seat(String id,int life,CardView...board){return new Seat(id,life,4,Arrays.asList(board));}
 static Observation obs(List<Seat> seats){return new Observation("self","Giada, Font of Hope","PRECOMBAT_MAIN",true,4,Collections.<CardView>emptyList(),seats);}
 static Action action(String id,String name,String text,String...targets){return new Action(id,name,text,1,false,false,true,Arrays.asList(targets));}
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  CommanderPolicy p=new CommanderPolicy();
  Seat self=seat("self",10), low=seat("a",40,card("small","Grizzly Bears",2,false,2)), dangerous=seat("b",40,card("huge","Ghalta, Primal Hunger",12,false,12)), empty=seat("c",40);
  Observation o=obs(Arrays.asList(self,low,dangerous,empty));
  Action a=action("a","Swords to Plowshares","Exile target creature.","small"), b=action("b","Swords to Plowshares","Exile target creature.","huge");
  check("b".equals(p.choose(o,Arrays.asList(a,b),1000000000L).actionId),"Choose threat beyond first opponent");
  Observation permuted=obs(Arrays.asList(empty,dangerous,self,low));
  check(p.evaluate(o)==p.evaluate(permuted),"Evaluation invariant to seats");
  check("b".equals(p.choose(permuted,Arrays.asList(b,a),1000000000L).actionId),"Target invariant to seats/order");
  check(p.choose(o,Arrays.asList(a,b),0).actionId==null,"Expired budget safe pass");
  check("NO_LEGAL_CANDIDATES".equals(p.choose(o,Collections.<Action>emptyList(),100).reason),"Empty reason");
  CommanderPolicy timed=new CommanderPolicy(new CommanderPolicy.Clock(){long n;public long nanoTime(){return n++;}});
  Decision limited=timed.choose(o,Arrays.asList(a,b),2);
  check("a".equals(limited.actionId)&&limited.reason.contains("BUDGET_EXHAUSTED"),"Best completed candidate retained");
  Seat selfThreat=seat("self",10,card("mine","Giada, Font of Hope",2,false,2));
  check(p.choose(obs(Arrays.asList(selfThreat,low)),Arrays.asList(action("x","Swords to Plowshares","Exile target creature.","mine")),1000000000L).actionId==null,"No hostile own removal");
  List<CardView> input=new ArrayList<CardView>();input.add(card("x","Plains",0,true,0));
  Seat frozen=new Seat("immutable",40,0,input);input.clear();check(frozen.battlefield.size()==1,"Copy mutable caller input");
  boolean immutable=false;try{frozen.battlefield.clear();}catch(UnsupportedOperationException e){immutable=true;}check(immutable,"Immutable view");
  String choice=p.chooseCard(o,Arrays.asList(card("land","Plains",0,true,0),card("angel","Avacyn, Angel of Hope",8,false,8)),CommanderPolicy.ChoiceKind.TUTOR);
  check(choice!=null&&Arrays.asList("land","angel").contains(choice),"Typed choice eligible only");
  check(p.chooseCard(o,Collections.<CardView>emptyList(),CommanderPolicy.ChoiceKind.SACRIFICE)==null,"Empty mandatory set does not invent ID");
  Action mana=new Action("mana","Plains","Add W",0,false,true,false,Collections.<String>emptyList());
  check(p.choose(o,Arrays.asList(mana),1000000000L).actionId==null,"No repeated float mana");
  List<Action> many=new ArrayList<Action>();
  for(int i=0;i<300;i++) many.add(action("filler-"+i,"Unknown",""));
  Action late=new Action("zz-land","Plains","",0,true,false,false,Collections.<String>emptyList());many.add(late);
  check("zz-land".equals(p.choose(o,many,1000000000L).actionId),"Strategic candidate beyond raw cap preserved");
  Collections.reverse(many);
  check("zz-land".equals(p.choose(o,many,1000000000L).actionId),"Capped choice stable under reordered options");
  check("zz-land".equals(p.choose(o,many,0).actionId),"Legal land fallback at expired budget");
  Set<String> boost=new HashSet<String>(Arrays.asList("BoostCreature","AddAbility"));
  Action ownBuff=new Action("own-buff","Get a Leg Up","",1,false,false,true,Arrays.asList("mine"),ActionKind.SPELL,boost,"buff");
  Action enemyBuff=new Action("enemy-buff","Get a Leg Up","",1,false,false,true,Arrays.asList("small"),ActionKind.SPELL,boost,"buff");
  Observation buffBoard=obs(Arrays.asList(selfThreat,low));
  check("own-buff".equals(p.choose(buffBoard,Arrays.asList(enemyBuff,ownBuff),1000000000L).actionId),"Beneficial effect cannot target opponent");
  Observation upkeep=new Observation("self","Giada, Font of Hope","UPKEEP",true,4,Collections.<CardView>emptyList(),buffBoard.seats);
  check(p.choose(upkeep,Arrays.asList(ownBuff),1000000000L).actionId==null,"Hold temporary pump until relevant combat");
  check(p.choose(buffBoard,Arrays.asList(enemyBuff),1000000000L).actionId==null,"No forced wrong-target buff fallback");
  CardView attacker=new CardView("attacker","Lathril, Blade of the Elves",4,false,true,2,3,true,"","self",Collections.<String>emptyList(),true);
  Observation combat=new Observation("self","Lathril, Blade of the Elves","DECLARE_BLOCKERS",true,4,Collections.<CardView>emptyList(),Arrays.asList(seat("self",40,attacker,card("idle","Llanowar Elves",1,false,1)),low));
  Action pumpAttacker=new Action("z-attacker","Get a Leg Up","",1,false,false,true,Arrays.asList("attacker"),ActionKind.SPELL,boost,"buff");
  Action pumpIdle=new Action("a-idle","Get a Leg Up","",1,false,false,true,Arrays.asList("idle"),ActionKind.SPELL,boost,"buff");
  check("z-attacker".equals(p.choose(combat,Arrays.asList(pumpIdle,pumpAttacker),1000000000L).actionId),"Pump current attacker instead of idle owned engine");
  check(p.choose(combat,Arrays.asList(pumpIdle),1000000000L).actionId==null,"Do not waste combat pump on idle own creature");
  System.out.println("CommanderPolicyTest: 21 assertions passed");
 }
}
