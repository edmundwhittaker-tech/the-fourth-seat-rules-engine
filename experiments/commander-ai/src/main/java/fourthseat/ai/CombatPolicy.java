package fourthseat.ai;

import java.util.*;

/** Pure, bounded multiplayer attack heuristic. Engine supplies legality, never private cards.
 * Damage is a conservative vanilla-combat projection, NOT a combat rules simulator:
 * trample, minimum-blocker counts and explicit per-source prevention are modeled,
 * but not first strike, arbitrary prevention,
 * replacement effects or the commander-damage ledger.
 * Such effects cannot be inferred from names/text. The adapter must validate the final declaration.
 */
public final class CombatPolicy {
    private CombatPolicy() {}
    public static final int MAX_ATTACKERS=32, MAX_DEFENDERS=8, MAX_EVALUATIONS=2048;
    public static final class Attacker {
        public final String id,name; public final int power,toughness; public final double value;
        public final boolean vigilance; public final Set<String> legalDefenders; public final double tapOpportunityCost; public final String publicCombatKey; public final boolean trample,goreclawAttackTrigger; public final int minBlockers,incomingDamageCap;
        public Attacker(String id,String name,int power,int toughness,double value,boolean vigilance,Set<String> legalDefenders) {
            this(id,name,power,toughness,value,vigilance,legalDefenders,0);
        }
        /** Opportunity cost supplied from public, currently usable tap abilities. */
        public Attacker(String id,String name,int power,int toughness,double value,boolean vigilance,Set<String> legalDefenders,double tapOpportunityCost) {
            this(id,name,power,toughness,value,vigilance,legalDefenders,tapOpportunityCost,"");
        }
        public Attacker(String id,String name,int power,int toughness,double value,boolean vigilance,Set<String> legalDefenders,double tapOpportunityCost,String publicCombatKey) {
            this(id,name,power,toughness,value,vigilance,legalDefenders,tapOpportunityCost,publicCombatKey,false,1,false);
        }
        public Attacker(String id,String name,int power,int toughness,double value,boolean vigilance,Set<String> legalDefenders,double tapOpportunityCost,String publicCombatKey,boolean trample,int minBlockers,boolean goreclawAttackTrigger) {
            this(id,name,power,toughness,value,vigilance,legalDefenders,tapOpportunityCost,publicCombatKey,trample,minBlockers,goreclawAttackTrigger,Integer.MAX_VALUE);
        }
        /** Explicit known public per-source prevention; MAX_VALUE means no known cap. */
        public Attacker(String id,String name,int power,int toughness,double value,boolean vigilance,Set<String> legalDefenders,double tapOpportunityCost,String publicCombatKey,boolean trample,int minBlockers,boolean goreclawAttackTrigger,int incomingDamageCap) {
            this.incomingDamageCap=Math.max(0,incomingDamageCap);
            this.trample=trample;this.minBlockers=Math.max(1,minBlockers);this.goreclawAttackTrigger=goreclawAttackTrigger;
            this.publicCombatKey=Objects.requireNonNull(publicCombatKey);
            this.tapOpportunityCost=Math.max(0,tapOpportunityCost);
            this.id=Objects.requireNonNull(id);this.name=name==null?"":name;this.power=Math.max(0,power);this.toughness=Math.max(0,toughness);
            this.value=Math.max(0,value);this.vigilance=vigilance;this.legalDefenders=freeze(legalDefenders);
        }
    }
    public static final class Blocker {
        public final String id; public final int power,toughness,maxBlocks;public final Set<String> blockableAttackerIds;public final boolean attritionEligible;
        public Blocker(String id,int power,int toughness,Set<String> blockableAttackerIds) {
            this(id,power,toughness,blockableAttackerIds,1);
        }
        public Blocker(String id,int power,int toughness,Set<String> blockableAttackerIds,int maxBlocks) {
            this(id,power,toughness,blockableAttackerIds,maxBlocks,true);
        }
        public Blocker(String id,int power,int toughness,Set<String> blockableAttackerIds,int maxBlocks,boolean attritionEligible) {
            this.attritionEligible=attritionEligible;
            this.id=Objects.requireNonNull(id);this.power=Math.max(0,power);this.toughness=Math.max(0,toughness);this.blockableAttackerIds=freeze(blockableAttackerIds);this.maxBlocks=Math.max(0,maxBlocks);
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
     * At most 32 attackers are individually optimized. Equivalent surplus may be expanded
     * in bounded batches after risk rescoring. Non-equivalent bodies can participate only
     * through a separately rescored whole-army seed. At most eight seats
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
        Set<String> sampled=new HashSet<String>();for(Attacker a:candidates) sampled.add(a.id);
        Map<List<Object>,List<Attacker>> overflow=new LinkedHashMap<List<Object>,List<Attacker>>();
        for(Attacker a:all) if(!sampled.contains(a.id)&&a.tapOpportunityCost==0&&!a.legalDefenders.isEmpty()) {
            List<Object> key=equivalenceKey(a,seats);
            overflow.computeIfAbsent(key,k->new ArrayList<Attacker>()).add(a);
        }
        Map<String,Map<String,BlockRisk>> risks=blockRisks(all,seats);
        Map<String,String> best=new TreeMap<String,String>();double bestScore=0;int evaluations=1;
        // Full-team seeds allow a token army to break through even when no solo attack is good.
        for(Defender d:destinations) {
            Map<String,String> seed=new TreeMap<String,String>();for(Attacker a:candidates) if(a.legalDefenders.contains(d.id)) seed.put(a.id,d.id);
            double s=score(commander,selfLife,all,seats,risks,seed);evaluations++;
            if(s>bestScore+1e-9) {best=seed;bestScore=s;}
            // A 32-body sample may be fully blocked even though the actual equivalent
            // army overwhelms that defense. One bulk seed per seat prevents that trap.
            Map<String,String> bulk=new TreeMap<String,String>(seed);Set<List<Object>> included=new HashSet<List<Object>>();
            for(Attacker a:candidates) if(seed.containsKey(a.id)&&a.tapOpportunityCost==0) {
                List<Object> key=equivalenceKey(a,seats);if(!included.add(key)) continue;
                List<Attacker> extras=overflow.get(key);
                if(extras!=null) for(Attacker extra:extras) bulk.put(extra.id,d.id);
            }
            if(!bulk.equals(seed)) {
                double bulkScore=score(commander,selfLife,all,seats,risks,bulk);evaluations++;
                if(bulkScore>bestScore+1e-9) {best=bulk;bestScore=bulkScore;}
            }
        }
        // Unconditional intermediate groups bridge fully blocked samples and unsafe all-in attacks.
        // At most five deduplicated counts per sampled group and destination.
        Set<List<Object>> seededGroups=new HashSet<List<Object>>();
        double homeDefense=0,totalThreat=0;
        for(Attacker a:all) homeDefense+=a.toughness;
        for(Defender d:seats) if(d.life>0) totalThreat+=d.visibleCrackbackPower;
        groupSeeds: for(Attacker representative:candidates) {
            if(representative.tapOpportunityCost!=0) continue;
            List<Object> key=equivalenceKey(representative,seats);
            if(!overflow.containsKey(key)||!seededGroups.add(key)) continue;
            List<Attacker> group=new ArrayList<Attacker>(overflow.get(key));
            for(Attacker a:candidates) if(a.tapOpportunityCost==0&&key.equals(equivalenceKey(a,seats))) group.add(a);
            Collections.sort(group,Comparator.comparing(a->a.id));
            for(Defender d:destinations) {
                if(!representative.legalDefenders.contains(d.id)) continue;
                long slots=0;for(Blocker b:d.blockers) if(b.blockableAttackerIds.contains(representative.id)) slots+=b.maxBlocks;
                long lethal=slots+(representative.power>0?((long)Math.max(0,d.life)+representative.power-1)/representative.power:1);
                int globalReserve=group.size(),killReserve=group.size();
                if(!representative.vigilance&&representative.toughness>0) {
                    globalReserve=(int)Math.max(0,Math.min(group.size(),Math.floor((homeDefense-Math.max(0,totalThreat-selfLife+1))/representative.toughness)));
                    killReserve=(int)Math.max(0,Math.min(group.size(),Math.floor((homeDefense-Math.max(0,totalThreat-d.visibleCrackbackPower-selfLife+1))/representative.toughness)));
                }
                Set<Integer> counts=new LinkedHashSet<Integer>(Arrays.asList(group.size()/2,(int)((long)group.size()*3/4),(int)Math.min(group.size(),lethal),globalReserve,killReserve));
                for(int count:counts) {
                    if(count<=0||count>=group.size()) continue;
                    if(evaluations>=MAX_EVALUATIONS) break groupSeeds;
                    Map<String,String> proposal=new TreeMap<String,String>();
                    for(int i=0;i<count;i++) proposal.put(group.get(i).id,d.id);
                    double value=score(commander,selfLife,all,seats,risks,proposal);evaluations++;
                    if(value>bestScore+1e-9) {best=proposal;bestScore=value;}
                }
            }
        }
        // Separate whole-army baseline, not an equivalence assumption. Every body
        // has its own legal edge, value and risk. This permits modified/counter-bearing
        // armies beyond the sample without pretending those bodies are interchangeable.
        for(Defender d:destinations) {
            Map<String,String> army=new TreeMap<String,String>();
            for(Attacker a:all) if(a.tapOpportunityCost==0&&a.legalDefenders.contains(d.id)) army.put(a.id,d.id);
            if(evaluations>=MAX_EVALUATIONS) break;
            double armyScore=score(commander,selfLife,all,seats,risks,army);evaluations++;
            if(armyScore>bestScore+1e-9) {best=army;bestScore=armyScore;}
            // Modified/fresh-haste bodies remain distinct; each prefix has legal edges
            // and receives full risk rescoring. No exhaustive mixed-army search claim.
            List<Attacker> legalArmy=new ArrayList<Attacker>();
            for(Attacker a:all) if(army.containsKey(a.id)) legalArmy.add(a);
            double lostDefense=0;
            double globalAllowance=homeDefense-Math.max(0,totalThreat-selfLife+1);
            double killAllowance=homeDefense-Math.max(0,totalThreat-d.visibleCrackbackPower-selfLife+1);
            int globalCount=0,killCount=0;
            for(int i=0;i<legalArmy.size();i++) {
                Attacker a=legalArmy.get(i);if(!a.vigilance) lostDefense+=a.toughness;
                if(lostDefense<=globalAllowance) globalCount=i+1;
                if(lostDefense<=killAllowance) killCount=i+1;
            }
            Set<Integer> prefixes=new LinkedHashSet<Integer>(Arrays.asList(legalArmy.size()/2,
                    (int)((long)legalArmy.size()*3/4),globalCount,killCount));
            for(int count:prefixes) {
                if(count<=0||count>=legalArmy.size()) continue;
                if(evaluations>=MAX_EVALUATIONS) break;
                Map<String,String> proposal=new TreeMap<String,String>();
                for(int i=0;i<count;i++) proposal.put(legalArmy.get(i).id,d.id);
                double value=score(commander,selfLife,all,seats,risks,proposal);evaluations++;
                if(value>bestScore+1e-9) {best=proposal;bestScore=value;}
            }
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
                    double s=score(commander,selfLife,all,seats,risks,proposal);evaluations++;
                    if(s>nextScore+1e-9) {next=proposal;nextScore=s;}
                }
            }
            if(next==best) break;best=next;bestScore=nextScore;
        }
        // Expand only surplus equivalent to a selected representative. Equality includes
        // public features AND every supplied blocker relation, not merely creature name.
        // Reserved tap engines never enter overflow, even if they have vigilance.
        Set<List<Object>> expanded=new HashSet<List<Object>>();
        for(Attacker representative:candidates) {
            String target=best.get(representative.id);
            if(target==null||representative.tapOpportunityCost!=0) continue;
            List<Object> key=equivalenceKey(representative,seats);
            if(!expanded.add(key)) continue;
            List<Attacker> extras=overflow.get(key);if(extras==null||extras.isEmpty()) continue;
            // Four options per equivalence group: all, half, or a smaller batch sufficient
            // to add roughly one opposing life total. Every option includes crackback risk.
            int life=0;for(Defender d:seats) if(d.id.equals(target)) life=Math.max(0,d.life);
            int lethalBatch=representative.power==0?1:(int)Math.min(extras.size(),((long)life+representative.power-1)/representative.power);
            int[] batches={extras.size(),Math.max(1,extras.size()/2),Math.max(1,lethalBatch),1};
            Map<String,String> expandedBest=best;double expandedScore=bestScore;
            for(int count:batches) {
                if(evaluations>=MAX_EVALUATIONS) break;
                Map<String,String> proposal=new TreeMap<String,String>(best);
                for(int i=0;i<Math.min(count,extras.size());i++) proposal.put(extras.get(i).id,target);
                double s=score(commander,selfLife,all,seats,risks,proposal);evaluations++;
                if(s>expandedScore+1e-9) {expandedBest=proposal;expandedScore=s;}
            }
            best=expandedBest;bestScore=expandedScore;
        }
        return new Plan(best,bestScore,evaluations);
    }
    static boolean samePublicCombatGroup(Attacker a,Attacker b,List<Defender> seats) {
        return equivalenceKey(a,seats).equals(equivalenceKey(b,seats));
    }
    private static final class BlockRisk {
        final boolean blockable;final long power,toughness;final int count;
        BlockRisk(boolean blockable,long power,long toughness,int count) {this.blockable=blockable;this.power=power;this.toughness=toughness;this.count=count;}
    }
    private static Map<String,Map<String,BlockRisk>> blockRisks(List<Attacker> all,List<Defender> seats) {
        Map<String,Map<String,BlockRisk>> result=new HashMap<String,Map<String,BlockRisk>>();
        for(Defender d:seats) {
            Map<String,BlockRisk> byAttacker=new HashMap<String,BlockRisk>();
            for(Attacker a:all) {
                long power=0,toughness=0;int count=0;boolean blockable=false;
                for(Blocker b:d.blockers) if(b.maxBlocks>0&&b.blockableAttackerIds.contains(a.id)) {blockable=true;power+=Math.min(b.power,a.incomingDamageCap);toughness+=b.toughness;count++;}
                byAttacker.put(a.id,new BlockRisk(blockable,power,toughness,count));
            }
            result.put(d.id,byAttacker);
        }
        return result;
    }
    private static List<Object> equivalenceKey(Attacker a,List<Defender> seats) {
        BitSet blockedBy=new BitSet();int index=0;
        for(Defender d:seats) for(Blocker b:d.blockers) {
            if(b.blockableAttackerIds.contains(a.id)) blockedBy.set(index);index++;
        }
        return Arrays.<Object>asList(a.name,a.power,a.toughness,a.value,a.vigilance,
                new TreeSet<String>(a.legalDefenders),a.tapOpportunityCost,a.publicCombatKey,a.trample,a.minBlockers,a.goreclawAttackTrigger,a.incomingDamageCap,blockedBy);
    }
    private static boolean hasTrample(Attacker a,boolean goreclawBoost) {return a.trample||(goreclawBoost&&a.power>=4);}
    private static int effectivePower(Attacker a,boolean goreclawBoost) {return a.power+(goreclawBoost&&a.power>=4&&a.power<Integer.MAX_VALUE?1:0);}
    private static double score(String commander,int selfLife,List<Attacker> all,List<Defender> seats,Map<String,Map<String,BlockRisk>> risks,Map<String,String> attacks) {
        if(attacks.isEmpty()) return 0;
        double value=0,remainingDefense=0,baselineDefense=0,enemyPower=0,baselineEnemy=0;
        boolean adeline=false,goreclaw=false,goreclawBoost=false;int bigAttackers=0,liveSeats=0;
        for(Attacker a:all) if(a.goreclawAttackTrigger&&attacks.containsKey(a.id)) goreclawBoost=true;
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
            List<Attacker> sent=new ArrayList<Attacker>();double power=0,suppression=0;long blockCapacity=0;boolean hasTrample=false,hasNormal=false;
            for(Attacker a:all) if(d.id.equals(attacks.get(a.id))) {sent.add(a);power+=effectivePower(a,goreclawBoost);if(hasTrample(a,goreclawBoost))hasTrample=true;else hasNormal=true;}
            // Give defenders a pessimistic best suppression per blocking slot. Trample
            // requires assigning lethal toughness, not losing the whole attacker power.
            // Sent is power-sorted, so first legal match in each keyword category is best.
            for(Blocker b:d.blockers) {
                int maxTrample=-1,maxNormal=-1;
                for(Attacker a:sent) {
                    if(risks.get(d.id).get(a.id).count<a.minBlockers||!b.blockableAttackerIds.contains(a.id)) continue;
                    if(hasTrample(a,goreclawBoost)) {if(maxTrample<0)maxTrample=effectivePower(a,goreclawBoost);}
                    else if(maxNormal<0)maxNormal=effectivePower(a,goreclawBoost);
                    if((!hasTrample||maxTrample>=0)&&(!hasNormal||maxNormal>=0))break;
                }
                int suppressed=Math.max(Math.max(0,maxNormal),Math.min(Math.max(0,maxTrample),b.toughness));
                suppression+=(double)suppressed*b.maxBlocks;blockCapacity+=b.maxBlocks;
            }
            double damage=Math.max(0,power-suppression);value+=damage;
            boolean projectedLethal=damage>=d.life;
            if(projectedLethal) value+=35;else enemyPower+=d.visibleCrackbackPower;
            List<Double> possibleLosses=new ArrayList<Double>();double attritionPressure=0;
            for(Attacker a:sent) {
                BlockRisk risk=risks.get(d.id).get(a.id);
                long possibleBlockPower=risk.power;boolean blockable=risk.blockable&&risk.count>=a.minBlockers;
                // Double-block possibility is charged without assuming opponents will trade badly.
                if(blockable&&possibleBlockPower>=a.toughness+(goreclawBoost&&a.power>=4?1:0)) possibleLosses.add(Math.max(a.value*1.7,a.tapOpportunityCost));
                // At most one cheap casualty-pressure credit per seat, never per attacker.
                // Unsupported or individually unkillable blockers can chump safely.
                if(blockable&&possibleBlockPower<a.toughness+(goreclawBoost&&a.power>=4?1:0)) {
                    double cheapest=Double.POSITIVE_INFINITY;boolean safe=true;
                    for(Blocker b:d.blockers) if(b.maxBlocks>0&&b.blockableAttackerIds.contains(a.id)) {
                        if(!b.attritionEligible||b.toughness>effectivePower(a,goreclawBoost)) {safe=false;break;}
                        cheapest=Math.min(cheapest,Math.min(3,0.5+0.25*b.power+0.25*b.toughness));
                    }
                    if(safe&&cheapest<Double.POSITIVE_INFINITY) attritionPressure=Math.max(attritionPressure,Math.min(effectivePower(a,goreclawBoost),cheapest));
                }
                long connection=!blockable?effectivePower(a,goreclawBoost)
                        :hasTrample(a,goreclawBoost)?Math.max(0,effectivePower(a,goreclawBoost)-risk.toughness):0;
                if(connection>0) {
                    if(a.name.startsWith("Lathril,")) value+=Math.min(12,connection*1.8);
                    if(a.name.startsWith("Gishath,")) value+=Math.min(18,connection*2.0);
                }
            }
            // Do not stack casualty pressure atop already credited player damage.
            value+=Math.max(0,attritionPressure-damage);
            // A blocker cannot kill the entire swarm one creature at a time. Charge the
            // worst exposed losses, up to actual total blocking capacity. Double blocks
            // still overestimate casualties here, deliberately conservative.
            Collections.sort(possibleLosses,Collections.reverseOrder());
            for(int i=0;i<possibleLosses.size()&&i<blockCapacity;i++) value-=possibleLosses.get(i);
        }
        if(adeline) value+=Math.min(6,liveSeats*1.5); // Trigger value, not invented damage.
        if(goreclaw) value+=Math.min(6,bigAttackers*0.7); // No assumption trample connects.
        double baselineExposure=Math.max(0,baselineEnemy-baselineDefense);
        double exposure=Math.max(0,enemyPower-remainingDefense);
        // The same counterattack is less costly with a large life cushion. A fixed
        // 2.5x penalty previously vetoed safe 40-life attacks and stalled lifegain decks.
        // All opponents still contribute; newly lethal exposure retains its hard veto.
        double riskWeight=0.5+1.5*Math.min(1.0,exposure/Math.max(1,selfLife));
        value-=riskWeight*Math.max(0,exposure-baselineExposure);
        if(enemyPower>0 && exposure>=selfLife && exposure>baselineExposure) value-=10000;
        return value;
    }
}
