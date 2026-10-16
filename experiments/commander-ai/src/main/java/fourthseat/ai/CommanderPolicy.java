package fourthseat.ai;

import java.util.*;
import fourthseat.ai.PolicyModel.*;

/** Deterministic, observation-only policy. It ranks legal candidates; it cannot manufacture actions. */
public final class CommanderPolicy {
    public enum ChoiceKind { TUTOR, DISCARD, SACRIFICE }
    public static final int MAX_CANDIDATES = 256;
    public interface Clock { long nanoTime(); }
    private final Clock clock;
    public CommanderPolicy() { this(new Clock(){ public long nanoTime(){return System.nanoTime();} }); }
    public CommanderPolicy(Clock clock) {this.clock=Objects.requireNonNull(clock);}

    public Decision choose(Observation observation,List<Action> legalActions,long budgetNanos) {
        Objects.requireNonNull(observation); Objects.requireNonNull(legalActions);
        if(legalActions.isEmpty()) return new Decision(null,"NO_LEGAL_CANDIDATES",0);
        if(budgetNanos<=0) {
            // Only a legal land drop has a context-independent safe development fallback here.
            Action land=null;
            for(Action a:legalActions) if(a.land&&(land==null||a.id.compareTo(land.id)<0)) land=a;
            return new Decision(land==null?null:land.id,land==null?"BUDGET_SAFE_PASS":"BUDGET_LEGAL_LAND_FALLBACK",land==null?0:1);
        }
        long start=clock.nanoTime();
        DeckPlan plan=DeckPlan.forCommander(observation.commander);
        Action best=null; double bestScore=0; int evaluated=0; String termination="COMPLETE";
        // Caller controls stable candidate IDs; sort makes equal-score decisions independent of input order.
        List<Action> bounded=new ArrayList<Action>(legalActions);
        Collections.sort(bounded,new Comparator<Action>(){public int compare(Action a,Action b){
            int priorityA=candidateClass(a,plan,observation), priorityB=candidateClass(b,plan,observation);
            return priorityA==priorityB?a.id.compareTo(b.id):Integer.compare(priorityB,priorityA);
        }});
        if(bounded.size()>MAX_CANDIDATES) bounded=new ArrayList<Action>(bounded.subList(0,MAX_CANDIDATES));
        if(legalActions.size()>MAX_CANDIDATES) termination="CANDIDATE_CAP";
        for(Action a:bounded) {
            if(clock.nanoTime()-start>=budgetNanos) {termination="BUDGET_EXHAUSTED";break;}
            if(a.mana) continue; // Engine mana payment chooses producers; never repeatedly float mana as a policy action.
            Double activation=ActivationPolicy.score(observation,a);
            double value=(activation==null?plan.actionValue(a,observation):activation)+targetAdjustment(observation,a);
            if(a.kind==ActionKind.SPELL && a.outcomes.contains("BoostCreature") && plan.role(a.sourceName)!=DeckPlan.Role.PROTECTION) {
                String step=observation.step.toUpperCase(Locale.ROOT).replace(' ','_');
                boolean relevant=(observation.active&&(step.equals("PRECOMBAT_MAIN")||step.equals("BEGIN_COMBAT")||step.equals("DECLARE_BLOCKERS")))||(!observation.active&&step.equals("DECLARE_BLOCKERS"));
                if(!relevant)value=-1000;
            }
            for(String sacrifice:a.sacrifices)for(CardView c:observation.self().battlefield)
                if(c.id.equals(sacrifice))value-=plan.cardValue(c)*0.6;
            // Do not spend Aesi's land drop before an already-affordable commander resolves.
            if(a.land && observation.commander.equals("Aesi, Tyrant of Gyre Strait")) {
                for(Action option:legalActions) if(option.sourceName.equals(observation.commander) && !option.mana && !option.land) {
                    boolean alreadyPresent=false;
                    for(CardView c:observation.self().battlefield) if(c.name.equals(observation.commander)) alreadyPresent=true;
                    if(!alreadyPresent) value-=25;
                    break;
                }
            }
            // With no incoming stack threat, build the Goblin count before committing Krenko's tap.
            if(observation.commander.equals("Krenko, Mob Boss") && a.sourceName.equals(observation.commander) && observation.stack.isEmpty()) {
                boolean krenkoOnBoard=false;
                for(CardView c:observation.self().battlefield) if(c.name.equals(observation.commander)) krenkoOnBoard=true;
                if(krenkoOnBoard) for(Action option:legalActions) {
                    boolean addsGoblins=option.sourceName.equals("Krenko's Command");
                    for(CardView c:observation.hand) if(c.name.equals(option.sourceName)&&c.creature&&c.subtypes.contains("Goblin")) addsGoblins=true;
                    if(addsGoblins&&!option.id.equals(a.id)) {value-=40;break;}
                }
            }
            if(observation.commander.equals("Krenko, Mob Boss") && a.sourceName.equals(observation.commander)) {
                boolean makerPending=false, threatened=false;
                for(CardView spell:observation.stack) {
                    if(spell.controllerId.equals(observation.selfId) && (spell.name.equals("Krenko's Command")||spell.subtypes.contains("Goblin"))) makerPending=true;
                    if(!spell.controllerId.equals(observation.selfId)) for(CardView c:observation.self().battlefield)
                        if(c.name.equals(observation.commander)&&spell.targetIds.contains(c.id)) threatened=true;
                }
                if(makerPending&&!threatened) value=-1000;
            }
            evaluated++;
            if(Double.isFinite(value) && value>bestScore) { best=a;bestScore=value; }
        }
        if(best==null) return new Decision(null,evaluated==0?"BUDGET_BEFORE_EVALUATION":"DELIBERATE_PASS:"+termination,0);
        return new Decision(best.id,"PLAN:"+plan.name()+":"+termination+":EVALUATED="+evaluated,bestScore);
    }

    private static int candidateClass(Action a,DeckPlan p,Observation o) {
        DeckPlan.Role role=p.role(a.sourceName);
        if(!o.stack.isEmpty()&&(role==DeckPlan.Role.COUNTER||role==DeckPlan.Role.PROTECTION)) return 4;
        if(a.land) return 3;
        if(role==DeckPlan.Role.REMOVAL) return 2;
        return 1;
    }

    /** All opponents contribute without treating them as an omniscient coalition. */
    public double evaluate(Observation o) {
        DeckPlan plan=DeckPlan.forCommander(o.commander);
        double own=seatValue(o.self(),plan,true), worst=0, sum=0; int opponents=0;
        for(Seat s:o.seats) if(!s.id.equals(o.selfId)) {double v=seatValue(s,plan,false); worst=Math.max(worst,v);sum+=v;opponents++;}
        return own-(opponents==0?0:0.65*worst+0.35*sum/opponents);
    }
    private double seatValue(Seat s,DeckPlan plan,boolean own) {
        double score=Math.min(40,s.life)*0.25+s.handCount*1.2;
        if(s.life<=0) return -10000;
        for(CardView c:s.battlefield) score+=own?plan.cardValue(c):publicValue(c);
        return score;
    }
    public double publicValue(CardView c) {
        if(c.land) return 2;
        double score=1+c.manaValue*0.8;
        if(c.creature) score+=Math.max(0,c.power)*0.7+Math.max(0,c.toughness)*0.25;
        String t=c.text.toLowerCase(Locale.ROOT);
        if(t.contains("draw")||t.contains("whenever")) score+=2;
        if(t.contains("you win the game")||t.contains("extra turn")) score+=12;
        return score;
    }
    private double targetAdjustment(Observation o,Action a) {
        boolean beneficial=a.outcomes.contains("DrawCard")||a.outcomes.contains("BoostCreature")||a.outcomes.contains("AddAbility")||a.outcomes.contains("Protect")||a.outcomes.contains("Untap")||a.outcomes.contains("GainLife");
        boolean harmful=a.outcomes.contains("Damage")||a.outcomes.contains("DestroyPermanent")||a.outcomes.contains("Exile")||a.outcomes.contains("LoseLife")||a.outcomes.contains("Removal")||a.outcomes.contains("UnboostCreature");
        if(beneficial&&!harmful) {
            for(Seat seat:o.seats) if(!seat.id.equals(o.selfId)) {
                if(a.targetIds.contains(seat.id)) return -1000;
                for(CardView c:seat.battlefield) if(a.targetIds.contains(c.id)) return -1000;
            }
        }
        if(harmful&&!beneficial) {
            if(a.targetIds.contains(o.selfId))return -1000;
            for(CardView c:o.self().battlefield)if(a.targetIds.contains(c.id))return -1000;
        }
        String text=a.text.toLowerCase(Locale.ROOT);
        DeckPlan.Role role=DeckPlan.forCommander(o.commander).role(a.sourceName);
        // Intent comes from audited frozen-card roles, never mere rules-text matches (blink is not removal).
        boolean hostile=(role==DeckPlan.Role.COUNTER||role==DeckPlan.Role.REMOVAL)&&!beneficial;
        if(!hostile || a.targetIds.isEmpty()) {
            double utility=0;
            if(a.outcomes.contains("BoostCreature")) for(CardView c:o.self().battlefield) if(a.targetIds.contains(c.id)) {
                // A temporary pump must help the current combat, not an idle high-value engine.
                if(c.attacking) utility+=20;
                else if(o.active && (o.step.equals("DECLARE_BLOCKERS")||o.step.equals("DECLARE_ATTACKERS"))) utility-=100;
                else if(!c.tapped&&!c.summoningSick) utility+=2;
            }
            return utility;
        }
        double adjustment=0;
        for(String target:a.targetIds) {
            for(CardView spell:o.stack) if(spell.id.equals(target)) {
                if(spell.controllerId.equals(o.selfId)) return -1000;
                adjustment+=publicValue(spell)*0.7;
                if(spell.targetIds.contains(o.selfId)) adjustment+=8;
                for(CardView own:o.self().battlefield) if(spell.targetIds.contains(own.id)) adjustment+=publicValue(own);
            }
            for(Seat seat:o.seats) {
                if(seat.id.equals(target)) adjustment+=seat.id.equals(o.selfId)?-1000:Math.max(0,12-seat.life)*0.3;
                for(CardView c:seat.battlefield) if(c.id.equals(target)) {
                    if(seat.id.equals(o.selfId)) return -1000;
                    adjustment+=publicValue(c)*0.75;
                    if(c.creature&&c.power>=o.self().life) adjustment+=12;
                }
            }
        }
        return adjustment;
    }
    /** Operates on engine-provided eligible cards only. No arbitrary tutor/discard identifiers. */
    public String chooseCard(Observation o,List<CardView> eligible,ChoiceKind kind) {
        if(eligible.isEmpty()) return null;
        DeckPlan plan=DeckPlan.forCommander(o.commander);
        CardView best=null;double bestScore=-Double.MAX_VALUE;
        int lands=0;for(CardView c:o.hand) if(c.land) lands++;
        for(CardView c:eligible) {
            double value=plan.cardValue(c);
            if(c.land) value+=lands<3?6:-Math.max(0,lands-4)*2;
            if(kind!=ChoiceKind.TUTOR) value=-value;
            if(best==null||value>bestScore||value==bestScore&&c.id.compareTo(best.id)<0) {best=c;bestScore=value;}
        }
        return best.id;
    }
}
