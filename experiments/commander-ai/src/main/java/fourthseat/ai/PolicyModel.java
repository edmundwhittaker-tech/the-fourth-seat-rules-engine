package fourthseat.ai;

import java.util.*;

/** Immutable policy boundary. Deliberately has no engine/server references or opponent private cards. */
public final class PolicyModel {
    private PolicyModel() {}
    private static <T> List<T> frozen(List<T> x) { return Collections.unmodifiableList(new ArrayList<T>(x)); }
    public static final class CardView {
        public final String id,name,text,controllerId;
        public final List<String> targetIds;
        public final Set<String> subtypes;
        public final int manaValue,power,toughness;
        public final boolean land,creature,tapped,attacking,summoningSick;
        public CardView(String id,String name,int manaValue,boolean land,boolean creature,int power,int toughness,boolean tapped,String text) {
            this(id,name,manaValue,land,creature,power,toughness,tapped,text,"",Collections.<String>emptyList());
        }
        public CardView(String id,String name,int manaValue,boolean land,boolean creature,int power,int toughness,boolean tapped,String text,String controllerId,List<String> targetIds) {
            this(id,name,manaValue,land,creature,power,toughness,tapped,text,controllerId,targetIds,false);
        }
        public CardView(String id,String name,int manaValue,boolean land,boolean creature,int power,int toughness,boolean tapped,String text,String controllerId,List<String> targetIds,boolean attacking) {
            this(id,name,manaValue,land,creature,power,toughness,tapped,text,controllerId,targetIds,attacking,Collections.<String>emptySet());
        }
        public CardView(String id,String name,int manaValue,boolean land,boolean creature,int power,int toughness,boolean tapped,String text,String controllerId,List<String> targetIds,boolean attacking,Set<String> subtypes) {
            this(id,name,manaValue,land,creature,power,toughness,tapped,text,controllerId,targetIds,attacking,subtypes,false);
        }
        public CardView(String id,String name,int manaValue,boolean land,boolean creature,int power,int toughness,boolean tapped,String text,String controllerId,List<String> targetIds,boolean attacking,Set<String> subtypes,boolean summoningSick) {
            this.summoningSick=summoningSick;
            this.subtypes=Collections.unmodifiableSet(new HashSet<String>(subtypes));
            this.attacking=attacking;
            this.controllerId=controllerId==null?"":controllerId;this.targetIds=frozen(targetIds);
            this.id=Objects.requireNonNull(id); this.name=Objects.requireNonNull(name); this.manaValue=Math.max(0,manaValue); this.land=land;this.creature=creature;this.power=power;this.toughness=toughness;this.tapped=tapped;this.text=text==null?"":text;
        }
    }
    public static final class Seat {
        public final String id; public final int life,handCount; public final List<CardView> battlefield;
        public Seat(String id,int life,int handCount,List<CardView> battlefield) { this.id=Objects.requireNonNull(id);this.life=life;this.handCount=Math.max(0,handCount);this.battlefield=frozen(battlefield); }
    }
    /** Engine-provided production option; a hint, not permission to pay a cost (conditional mana exists). */
    public static final class ManaView {
        public final int white,blue,black,red,green,colorless,generic,any;
        public ManaView(int white,int blue,int black,int red,int green,int colorless,int generic,int any) {
            this.white=white;this.blue=blue;this.black=black;this.red=red;this.green=green;this.colorless=colorless;this.generic=generic;this.any=any;
        }
        public int total() { return white+blue+black+red+green+colorless+generic+any; }
    }
    public static final class Observation {
        public final String selfId,commander,step;public final boolean active;public final int turn;public final List<CardView> hand,stack;public final List<Seat> seats;public final List<ManaView> manaOptions;
        public Observation(String selfId,String commander,String step,boolean active,int turn,List<CardView> hand,List<Seat> seats) {
            this(selfId,commander,step,active,turn,hand,seats,Collections.<CardView>emptyList());
        }
        public Observation(String selfId,String commander,String step,boolean active,int turn,List<CardView> hand,List<Seat> seats,List<CardView> stack) {
            this(selfId,commander,step,active,turn,hand,seats,stack,Collections.<ManaView>emptyList());
        }
        public Observation(String selfId,String commander,String step,boolean active,int turn,List<CardView> hand,List<Seat> seats,List<CardView> stack,List<ManaView> manaOptions) {
            this.manaOptions=frozen(manaOptions);this.stack=frozen(stack);this.selfId=Objects.requireNonNull(selfId);this.commander=commander==null?"":commander;this.step=Objects.requireNonNull(step);this.active=active;this.turn=turn;this.hand=frozen(hand);this.seats=frozen(seats);
        }
        public Seat self() { for(Seat s:seats) if(s.id.equals(selfId)) return s; throw new IllegalArgumentException("Self seat missing"); }
    }
    public enum ActionKind { SPELL, LAND, ACTIVATION, EQUIP, SPECIAL }
    public static final class Action {
        public final String id,sourceName,text,sourceId,effectText;public final ActionKind kind;public final Set<String> outcomes;public final int manaValue;public final boolean land,mana,usesStack;public final List<String> targetIds,sacrifices;
        public Action(String id,String sourceName,String text,int manaValue,boolean land,boolean mana,boolean usesStack,List<String> targetIds) {
            this(id,sourceName,text,manaValue,land,mana,usesStack,targetIds,land?ActionKind.LAND:ActionKind.SPELL,Collections.<String>emptySet(),"");
        }
        public Action(String id,String sourceName,String text,int manaValue,boolean land,boolean mana,boolean usesStack,List<String> targetIds,ActionKind kind,Set<String> outcomes,String sourceId) {
            this(id,sourceName,text,manaValue,land,mana,usesStack,targetIds,kind,outcomes,sourceId,text);
        }
        public Action(String id,String sourceName,String text,int manaValue,boolean land,boolean mana,boolean usesStack,List<String> targetIds,ActionKind kind,Set<String> outcomes,String sourceId,String effectText) {
            this(id,sourceName,text,manaValue,land,mana,usesStack,targetIds,kind,outcomes,sourceId,effectText,Collections.<String>emptyList());
        }
        public Action(String id,String sourceName,String text,int manaValue,boolean land,boolean mana,boolean usesStack,List<String> targetIds,ActionKind kind,Set<String> outcomes,String sourceId,String effectText,List<String> sacrifices) {
            this.sacrifices=frozen(sacrifices);
            this.effectText=effectText==null?"":effectText;
            this.kind=Objects.requireNonNull(kind);this.outcomes=Collections.unmodifiableSet(new HashSet<String>(outcomes));this.sourceId=sourceId;
            this.id=Objects.requireNonNull(id);this.sourceName=sourceName==null?"":sourceName;this.text=text==null?"":text;this.manaValue=Math.max(0,manaValue);this.land=land;this.mana=mana;this.usesStack=usesStack;this.targetIds=frozen(targetIds);
        }
    }
    public static final class Decision {
        /** null actionId denotes deliberate pass, never a made-up engine ability. */
        public final String actionId,reason;public final double score;
        public Decision(String actionId,String reason,double score) {this.actionId=actionId;this.reason=reason;this.score=score;}
    }
}
