package fourthseat.ai;

import java.util.*;

/** Pure, bounded multiplayer attack heuristic. Engine supplies legality, never private cards.
 * Damage is a conservative vanilla-combat projection, NOT a combat rules simulator:
 * no trample, first strike, prevention, replacement effects or commander-damage ledger.
 * Such effects cannot be inferred from names/text. The adapter must validate the final declaration.
 */
public final class CombatPolicy {
    private CombatPolicy() {}
    public static final int MAX_ATTACKERS=32, MAX_DEFENDERS=8, MAX_EVALUATIONS=2048;
    public static final class Attacker {
        public final String id,name; public final int power,toughness; public final double value;
        public final boolean vigilance; public final Set<String> legalDefenders; public final double tapOpportunityCost;
        public Attacker(String id,String name,int power,int toughness,double value,boolean vigilance,Set<String> legalDefenders) {
            this(id,name,power,toughness,value,vigilance,legalDefenders,0);
        }
        /** Opportunity cost supplied from public, currently usable tap abilities. */
        public Attacker(String id,String name,int power,int toughness,double value,boolean vigilance,Set<String> legalDefenders,double tapOpportunityCost) {
            this.tapOpportunityCost=Math.max(0,tapOpportunityCost);
            this.id=Objects.requireNonNull(id);this.name=name==null?"":name;this.power=Math.max(0,power);this.toughness=Math.max(0,toughness);
            this.value=Math.max(0,value);this.vigilance=vigilance;this.legalDefenders=freeze(legalDefenders);
        }
    }
    public static final class Blocker {
        public final String id; public final int power,toughness;public final Set<String> blockableAttackerIds;
        public Blocker(String id,int power,int toughness,Set<String> blockableAttackerIds) {
            this.id=Objects.requireNonNull(id);this.power=Math.max(0,power);this.toughness=Math.max(0,toughness);this.blockableAttackerIds=freeze(blockableAttackerIds);
        }
    }
    public static final class Defender {
        public final String id;public final int life,visibleCrackbackPower; public final List<Blocker> blockers;
        public Defender(String id,int life,List<Blocker> blockers,int visibleCrackbackPower) {
            this.id=Objects.requireNonNull(id);this.life=life;this.blockers=Collections.unmodifiableList(new ArrayList<Blocker>(blockers));
            this.visibleCrackbackPower=Math.max(0,visibleCrackbackPower);
        }
    }
    public static final class Plan {
        public final Map<String,String> attacks;public final double score;public final int evaluations;public final String reason;
        private Plan(Map<String,String> attacks,double score,int evaluations) {
            this.attacks=Collections.unmodifiableMap(new LinkedHashMap<String,String>(attacks));this.score=score;this.evaluations=evaluations;
            this.reason=attacks.isEmpty()?"Preserve blockers: no positive visible-board attack found":"Bounded all-seat attack allocation; projected damage is not guaranteed lethal";
        }
    }
    private static Set<String> freeze(Set<String> x) {return Collections.unmodifiableSet(new HashSet<String>(x));}
    /** Includes ALL opponent seats in risk, including seats no attacker can legally attack.
     * Attackers beyond the 32 strongest legal candidates are kept home. At most eight seats
     * can be attack destinations; additional seats still contribute to visible crackback risk.
     * No human/AI marker, hidden hand, deck order, engine state, or randomness is accepted.
     */
    public static Plan plan(String commander,int selfLife,List<Attacker> input,List<Defender> opponents) {
        List<Attacker> all=new ArrayList<Attacker>(input); Set<String> unique=new HashSet<String>();
        for(Attacker a:all) if(!unique.add(a.id)) throw new IllegalArgumentException("Duplicate attacker ID");
        Collections.sort(all,Comparator.comparingInt((Attacker a)->a.power).reversed().thenComparing(a->a.id));
        List<Attacker> candidates=new ArrayList<Attacker>();
        for(Attacker a:all) if(!a.legalDefenders.isEmpty()&&candidates.size()<MAX_ATTACKERS) candidates.add(a);
        List<Defender> seats=new ArrayList<Defender>(opponents);Collections.sort(seats,Comparator.comparing(d->d.id));
        unique.clear();for(Defender d:seats) if(!unique.add(d.id)) throw new IllegalArgumentException("Duplicate defender ID");
        List<Defender> destinations=seats.subList(0,Math.min(MAX_DEFENDERS,seats.size()));
        Map<String,String> best=new TreeMap<String,String>();double bestScore=0;int evaluations=1;
        // Full-team seeds allow a token army to break through even when no solo attack is good.
        for(Defender d:destinations) {
            Map<String,String> seed=new TreeMap<String,String>();for(Attacker a:candidates) if(a.legalDefenders.contains(d.id)) seed.put(a.id,d.id);
            double s=score(commander,selfLife,all,seats,seed);evaluations++;
            if(s>bestScore+1e-9) {best=seed;bestScore=s;}
        }
        // Strict improvement only, stable ID ties; bounded independent of any wall-clock clock.
        for(int round=0;round<6 && evaluations<MAX_EVALUATIONS;round++) {
            Map<String,String> next=best;double nextScore=bestScore;
            search: for(Attacker a:candidates) {
                for(int i=-1;i<destinations.size();i++) {
                    String target=i<0?null:destinations.get(i).id;
                    if(target!=null&&!a.legalDefenders.contains(target)) continue;
                    if(Objects.equals(best.get(a.id),target)) continue;
                    if(evaluations>=MAX_EVALUATIONS) break search;
                    Map<String,String> proposal=new TreeMap<String,String>(best);
                    if(target==null) proposal.remove(a.id);else proposal.put(a.id,target);
                    double s=score(commander,selfLife,all,seats,proposal);evaluations++;
                    if(s>nextScore+1e-9) {next=proposal;nextScore=s;}
                }
            }
            if(next==best) break;best=next;bestScore=nextScore;
        }
        return new Plan(best,bestScore,evaluations);
    }
    private static double score(String commander,int selfLife,List<Attacker> all,List<Defender> seats,Map<String,String> attacks) {
        if(attacks.isEmpty()) return 0;
        double value=0,remainingDefense=0,baselineDefense=0,enemyPower=0,baselineEnemy=0;
        boolean adeline=false,goreclaw=false;int bigAttackers=0,liveSeats=0;
        for(Attacker a:all) {
            baselineDefense+=a.toughness;
            if(!attacks.containsKey(a.id)||a.vigilance) remainingDefense+=a.toughness;
            if(attacks.containsKey(a.id)) {
                if(a.name.startsWith("Adeline,")) adeline=true;
                if(a.name.startsWith("Goreclaw,")) goreclaw=true;
                if(a.power>=4) bigAttackers++;
                value-=0.15; // Avoid gratuitous attacks, even vigilance attacks.
                if(!a.vigilance) value-=a.tapOpportunityCost;
            }
        }
        // Adeline triggers when ANY creature attacks, provided she is on the board.
        if(!adeline) for(Attacker a:all) if(a.name.startsWith("Adeline,")) adeline=true;
        for(Defender d:seats) {
            if(d.life<=0) continue;liveSeats++;baselineEnemy+=d.visibleCrackbackPower;
            List<Attacker> sent=new ArrayList<Attacker>();double power=0,suppression=0;
            for(Attacker a:all) if(d.id.equals(attacks.get(a.id))) {sent.add(a);power+=a.power;}
            // Every blocker suppresses its largest legal attacker. Duplicate suppression
            // deliberately overestimates defense, giving a lower damage estimate for swarms.
            for(Blocker b:d.blockers) {int max=0;for(Attacker a:sent) if(b.blockableAttackerIds.contains(a.id)) max=Math.max(max,a.power);suppression+=max;}
            double damage=Math.max(0,power-suppression);value+=damage;
            boolean projectedLethal=damage>=d.life;
            if(projectedLethal) value+=35;else enemyPower+=d.visibleCrackbackPower;
            for(Attacker a:sent) {
                int possibleBlockPower=0;boolean blockable=false;
                for(Blocker b:d.blockers) if(b.blockableAttackerIds.contains(a.id)) {blockable=true;possibleBlockPower+=b.power;}
                // Double-block possibility is charged without assuming opponents will trade badly.
                if(blockable&&possibleBlockPower>=a.toughness) value-=Math.max(a.value*1.7,a.tapOpportunityCost);
                if(!blockable&&a.power>0) {
                    if(a.name.startsWith("Lathril,")) value+=Math.min(12,a.power*1.8);
                    if(a.name.startsWith("Gishath,")) value+=Math.min(18,a.power*2.0);
                }
            }
        }
        if(adeline) value+=Math.min(6,liveSeats*1.5); // Trigger value, not invented damage.
        if(goreclaw) value+=Math.min(6,bigAttackers*0.7); // No assumption trample connects.
        double baselineExposure=Math.max(0,baselineEnemy-baselineDefense);
        double exposure=Math.max(0,enemyPower-remainingDefense);
        value-=2.5*Math.max(0,exposure-baselineExposure);
        if(enemyPower>0 && exposure>=selfLife && exposure>baselineExposure) value-=10000;
        return value;
    }
}
