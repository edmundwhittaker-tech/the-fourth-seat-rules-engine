package fourthseat.ai;

import java.util.*;
import fourthseat.ai.PolicyModel.*;

/** Hand-authored, bounded heuristics over the frozen pool, not a rules engine or simulator. */
public final class DeckPlan {
    public enum Role { BODY, RAMP, ENGINE, PAYOFF, REMOVAL, SWEEPER, PROTECTION, COUNTER, SUPPORT }
    private static final Map<String, DeckPlan> PLANS = new LinkedHashMap<String, DeckPlan>();
    private static final Map<String, Role> ROLES = new HashMap<String, Role>();
    private final String commander;
    private final Set<String> cards;
    private DeckPlan(String commander, String[] names) {
        this.commander = commander;
        Set<String> copy = new LinkedHashSet<String>();
        for (String n : names) copy.add(front(n));
        this.cards = Collections.unmodifiableSet(copy);
    }
    private static String front(String name) {
        if (name == null) return "";
        int split = name.indexOf(" // ");
        return split < 0 ? name : name.substring(0, split);
    }
    private static void register(String name, String[] cards) { PLANS.put(name, new DeckPlan(name, cards)); }
    private static void assign(Role role, String names) {
        for (String n : names.split("\\|")) ROLES.put(n, role);
    }
    public static DeckPlan forCommander(String name) {
        DeckPlan p = PLANS.get(name);
        return p != null ? p : new DeckPlan(name == null ? "Unknown" : name, new String[0]);
    }
    public String name() { return commander; }
    public int knownCardCount() { return cards.size(); }
    public Set<String> knownCards() { return cards; }
    public Role role(String name) {
        String n = front(name);
        Role r = cards.contains(n) ? ROLES.get(n) : null;
        return r == null ? Role.BODY : r;
    }
    private boolean is(String prefix) { return commander.startsWith(prefix); }
    public double cardValue(CardView c) {
        if (c.land) return 2.5;
        double v = 2 + Math.min(8, Math.max(0, c.manaValue)) * .45;
        if (c.creature) v += Math.min(12, Math.max(0, c.power)) * .45 + Math.min(12, Math.max(0, c.toughness)) * .15;
        switch (role(c.name)) {
            case RAMP: v += 3.5; break;
            case ENGINE: v += 5; break;
            case PAYOFF: v += 4; break;
            case REMOVAL: case COUNTER: v += 2.5; break;
            case PROTECTION: case SUPPORT: v += 2; break;
            default: break;
        }
        if (front(c.name).equals(commander)) v += 7;
        return v;
    }
    private static Seat self(Observation o) {
        for (Seat s : o.seats) if (s.id.equals(o.selfId)) return s;
        return null;
    }
    private static int countCreatures(List<CardView> cards) {
        int n = 0; for (CardView c : cards) if (c.creature) n++; return n;
    }
    private static boolean has(List<CardView> cards, String name) {
        for (CardView c : cards) if (front(c.name).equals(name)) return true; return false;
    }
    private static boolean combat(Observation o) {
        String s = o.step.toLowerCase(Locale.ROOT);
        return s.contains("combat") || s.contains("attack") || s.contains("block");
    }
    /** Negative means prefer passing. Scores are comparative, not win probabilities. */
    public double actionValue(Action a, Observation o) {
        Seat own = self(o);
        List<CardView> board = own == null ? Collections.<CardView>emptyList() : own.battlefield;
        int creatures = countCreatures(board), lands = 0, handLands = 0, bigHand = 0, power = 0;
        for (CardView c : board) { if (c.land) lands++; if (c.creature) power += Math.max(0,c.power); }
        for (CardView c : o.hand) { if (c.land) handLands++; if (c.creature && c.power >= 4) bigHand++; }
        int tribeCount = creatures;
        String tribe = is("Giada") ? "Angel" : is("Krenko") ? "Goblin" : is("Lathril") ? "Elf" : is("Gishath") ? "Dinosaur" : "";
        if (!tribe.isEmpty()) { tribeCount = 0; for (CardView c : board) if (c.creature && c.subtypes.contains(tribe)) tribeCount++; }
        String n = front(a.sourceName), text = a.text.toLowerCase(Locale.ROOT);
        boolean commanderPresent = has(board, commander);
        if (a.land) return 30 + (is("Aesi") && commanderPresent ? 8 : 0);
        if (a.mana) return -30; // Mana abilities are for the engine's payment callback, not value actions.
        Role r = role(n);
        double score = 5 + Math.min(8,a.manaValue) * .25;
        double targetThreat = 0; boolean enemyTarget = false, selfTarget = false;
        int enemyCreatures = 0, enemyAttackers = 0;
        for (Seat s : o.seats) {
            if (s.id.equals(o.selfId)) {
                if (a.targetIds.contains(s.id)) selfTarget = true;
                for (CardView c : s.battlefield) if (a.targetIds.contains(c.id)) selfTarget = true;
            } else {
                enemyCreatures = Math.max(enemyCreatures,countCreatures(s.battlefield));
                for (CardView c : s.battlefield) if (c.creature && c.attacking) enemyAttackers++;
                if (a.targetIds.contains(s.id)) { enemyTarget = true; targetThreat = Math.max(targetThreat, 2); }
                for (CardView c : s.battlefield) if (a.targetIds.contains(c.id)) {
                    enemyTarget = true; targetThreat = Math.max(targetThreat,cardValue(c));
                }
            }
        }
        if (cards.contains(n) && (n.equals("Lightning Bolt") || n.equals("Goblin Grenade") || n.equals("Goblin War Strike") || n.equals("Massive Raid"))) {
            // Audited named spell bounds only. Never infer burn damage from mana value or generic text.
            int goblins = 0; for (CardView c : board) if (c.creature && c.subtypes.contains("Goblin")) goblins++;
            int damage = n.equals("Lightning Bolt") ? 3 : n.equals("Goblin Grenade") ? 5 : n.equals("Goblin War Strike") ? goblins : creatures;
            if (n.equals("Goblin War Strike") || n.equals("Massive Raid")) {
                for (CardView c : board) if (a.sacrifices.contains(c.id) && c.creature && (n.equals("Massive Raid") || c.subtypes.contains("Goblin"))) damage--;
            }
            if (damage > 0 && has(board,"Torbran, Thane of Red Fell")) damage += 2;
            if (selfTarget || !enemyTarget) return -1000;
            double burnScore = -8;
            for (Seat s : o.seats) if (!s.id.equals(o.selfId)) {
                if (a.targetIds.contains(s.id) && s.life > 0 && s.life <= damage) burnScore = Math.max(burnScore,70);
                for (CardView c : s.battlefield) if (a.targetIds.contains(c.id) && c.creature) {
                    if (c.toughness > damage || c.text.toLowerCase(Locale.ROOT).contains("indestructible")) return -1000;
                    if (cardValue(c) >= 5) burnScore = Math.max(burnScore,3+cardValue(c));
                }
            }
            return burnScore;
        }
        if (r == Role.COUNTER) {
            if (n.equals("Archmage's Charm") && a.outcomes.contains("DrawCard") && !a.outcomes.contains("Counter"))
                return 8 + (o.hand.size() <= 3 ? 4 : 0) + (commanderPresent ? 3 : 0);
            // Adapter supplies public stack controller provenance; never counter our own spell.
            if (o.stack.isEmpty()) return -25;
            boolean stackTarget = false, meaningfulThreat = false;
            for (CardView c : o.stack) if (!c.controllerId.isEmpty() && !c.controllerId.equals(o.selfId) && a.targetIds.contains(c.id)) {
                stackTarget = true;
                if (counterWorthy(c,o,board,own)) meaningfulThreat = true;
            }
            return stackTarget && meaningfulThreat ? 13 : -1000; // Preserve limited answers against small bait spells.
        }
        if (r == Role.PROTECTION) {
            if (is("Lathril") && n.equals("Vitalize")) {
                int untapped = 0; for (CardView c : board) if (c.creature && c.subtypes.contains("Elf") && !c.tapped) untapped++;
                return tribeCount >= 11 && untapped < 10 && commanderPresent ? 15 : -10;
            }
            String selectedEffect = a.effectText.toLowerCase(Locale.ROOT);
            if (n.equals("Golgari Charm")) {
                if (!a.outcomes.contains("Regenerate") || !selectedEffect.contains("regenerate")) return -1000;
                for (CardView spell : o.stack) if (!spell.controllerId.isEmpty() && !spell.controllerId.equals(o.selfId)) {
                    String threatName = front(spell.name), threat = spell.text.toLowerCase(Locale.ROOT);
                    if (threatName.equals("Wrath of God") || threat.contains("exile") || threat.contains("can't be regenerated")
                        || threat.contains("cannot be regenerated") || threat.contains("can’t be regenerated")) continue;
                    boolean destructionOrDamage = threat.contains("destroy") || threat.contains("damage")
                        || threatName.equals("Blasphemous Act") || threatName.equals("Chain Reaction");
                    if (!destructionOrDamage) continue;
                    for (CardView c : board) if (c.creature && spell.targetIds.contains(c.id)) return 18;
                    if (creatures > 0 && (threat.contains("destroy all creatures") || threat.contains("destroy all permanents")
                        || threatName.equals("Blasphemous Act") || threatName.equals("Chain Reaction"))) return 18;
                }
                return -1000; // Regeneration does not stop exile, toughness loss, or cannot-regenerate destruction.
            }
            boolean borosProtection = n.equals("Boros Charm") && a.outcomes.contains("AddAbility") && selectedEffect.contains("indestructible");
            if (n.equals("Boros Charm") && !borosProtection) return -1000;
            boolean targetedProtection = n.equals("Tyvar's Stand") || n.equals("Collective Resistance");
            if (targetedProtection && !a.outcomes.isEmpty() && !selectedEffect.contains("indestructible") && !selectedEffect.contains("hexproof")) return -1000;
            boolean protectsOwnedCreature = false;
            for (CardView c : board) if (c.creature && a.targetIds.contains(c.id)) protectsOwnedCreature = true;
            if (targetedProtection && !protectsOwnedCreature) return -1000;
            boolean threatened = false;
            for (CardView spell : o.stack) if (!spell.controllerId.isEmpty() && !spell.controllerId.equals(o.selfId)) {
                if (spell.targetIds.contains(o.selfId) && !targetedProtection) threatened = true;
                for (CardView c : board) if (spell.targetIds.contains(c.id) && (!targetedProtection || a.targetIds.contains(c.id))) threatened = true;
                String threatName = front(spell.name), threatText = spell.text.toLowerCase(Locale.ROOT);
                boolean destroyArmy = threatName.equals("Wrath of God") || threatName.equals("Blasphemous Act")
                    || threatName.equals("Chain Reaction") || threatText.contains("destroy all creatures") || threatText.contains("destroy all permanents");
                boolean exileArmy = threatName.equals("Farewell") || threatText.contains("exile all creatures") || threatText.contains("exile all permanents");
                boolean indestructible = n.equals("Heroic Intervention") || n.equals("Flawless Maneuver") || n.equals("Flare of Fortitude")
                    || borosProtection || n.equals("Unbreakable Formation") || n.equals("Tyvar's Stand") || n.equals("Collective Resistance");
                if (creatures > 0 && ((destroyArmy && (indestructible || n.equals("Teferi's Protection")))
                    || (exileArmy && n.equals("Teferi's Protection")))) threatened = true;
            }
            return threatened && !board.isEmpty() ? 18 : -25;
        }
        if (r == Role.REMOVAL && !isCreatureSource(o, n)) {
            if (selfTarget || !enemyTarget) return -18;
            return targetThreat >= 5 ? 3 + targetThreat : -3; // Preserve answers for material threats.
        }
        if (r == Role.SWEEPER && !isCreatureSource(o,n)) {
            if (n.equals("Aetherize")) return !o.active && combat(o) && enemyAttackers >= 3 ? 16 : -18;
            return enemyCreatures >= creatures + 3 ? 16 : -12;
        }
        if (text.contains("sacrifice") && has(board,n) && !text.contains("cast")) {
            // No death-trigger/recursive resource search: avoid blind sacrifice activations.
            return enemyTarget && targetThreat >= 6 ? 4 + targetThreat : -8;
        }
        switch (r) {
            case RAMP: score += lands < 5 ? 10 : lands < 8 ? 5 : 0;
                if (is("Gishath") && lands < 8) score += 5;
                if (is("Aesi")) score += commanderPresent ? 6 : 3;
                break;
            case ENGINE: score += 7 + (o.hand.size() <= 3 ? 4 : 0); break;
            case SUPPORT: score += commanderPresent ? 5 : 2; break;
            case PAYOFF:
                score += tribeCount >= 4 ? 9 : tribeCount >= 2 ? 4 : -2;
                if (is("Torbran")) score += commanderPresent ? 8 : 3;
                if (is("Aesi")) score += handLands > 0 ? 6 : 0;
                break;
            default: if (isCreatureSource(o,n)) score += 3; break;
        }
        if (n.equals(commander)) {
            score += 11;
            if (is("Gishath")) score += 8; // Once legal, prioritize the eight-mana commander over more ramp.
            if (is("Ghalta")) score += Math.min(8,power * .5);
            if (is("Goreclaw")) score += bigHand * 2;
            if (is("Krenko") || is("Lathril")) score += Math.min(10,tribeCount * 2);
            if (is("Adeline")) score += Math.min(10,creatures * 2);
            if (is("Torbran")) score += Math.min(8,board.size());
            if (is("Aesi")) score += handLands * 3;
            if (is("Talrand")) score += Math.min(6,o.hand.size());
        }
        if (is("Giada") && isTribeSource(o,n,"Angel") && commanderPresent) score += 5;
        if (is("Ghalta") && isCreatureSource(o,n) && !commanderPresent) score += sourcePower(o,n) * .5;
        if (is("Goreclaw") && sourcePower(o,n) >= 4 && commanderPresent) score += 5;
        if (is("Gishath") && isTribeSource(o,n,"Dinosaur") && lands >= 8) score += 3;
        if (is("Talrand") && !isCreatureSource(o,n) && (r == Role.ENGINE || r == Role.BODY)) {
            score += commanderPresent ? 7 : -2;
            if (!commanderPresent && lands >= 4 && o.hand.size() > 3) score -= 5;
        }
        if (is("Aesi") && r == Role.RAMP && handLands == 0 &&
            (n.equals("Exploration") || n.equals("Burgeoning") || n.equals("Azusa, Lost but Seeking"))) score -= 12;
        if (is("Krenko") && (n.equals("Impact Tremors") || n.equals("Goblin Chieftain") || n.equals("Goblin Warchief"))) score += commanderPresent ? 6 : 2;
        if (is("Adeline") && (n.equals("Anointed Procession") || n.equals("Cathars' Crusade"))) score += commanderPresent ? 7 : 0;
        if (isCombatFinisher(n)) {
            // Avoid casting temporary team pumps after combat or without a meaningful army.
            String step = o.step.toLowerCase(Locale.ROOT);
            boolean attackWindow = o.active && (step.contains("precombat") || step.contains("pre_combat") || step.contains("begin_combat") || step.contains("declare_block"));
            score = attackWindow && creatures >= 3 ? 22 + creatures : -15;
        }
        if ((n.equals("High Tide") || n.equals("Jeska's Will")) && o.hand.size() <= 2) score -= 12;
        if (is("Talrand") && commanderPresent && o.active && o.step.toLowerCase(Locale.ROOT).contains("main")
            && !o.manaOptions.isEmpty() && !n.equals(commander)) {
            CardView reserve = null;
            for (CardView c : o.hand) if (role(c.name) == Role.COUNTER && (reserve == null || c.manaValue < reserve.manaValue)) reserve = c;
            if (reserve != null) {
                int blueNeeded = front(reserve.name).equals("Counterspell") ? 2 : 1;
                boolean room = false;
                for (ManaView m : o.manaOptions) if (m.total() - a.manaValue >= reserve.manaValue && m.blue + m.any >= blueNeeded) room = true;
                if (!room) score = -12;
            }
        }
        return score;
    }
    private boolean counterWorthy(CardView spell, Observation o, List<CardView> board, Seat own) {
        String n = front(spell.name), t = spell.text.toLowerCase(Locale.ROOT);
        if (spell.manaValue >= 5) return true;
        if (PLANS.containsKey(n)) return true; // Known commander identity, not an inferred unknown commander.
        if (t.contains("destroy all creatures") || t.contains("exile all creatures") || t.contains("destroy all permanents")
            || t.contains("exile all permanents") || t.contains("extra turn") || t.contains("you win the game")) return true;
        if (n.equals("Wrath of God") || n.equals("Blasphemous Act") || n.equals("Farewell") || n.equals("Chain Reaction")) return true;
        if (n.equals("Rhystic Study") || n.equals("Sylvan Library") || n.equals("Smothering Tithe")
            || n.equals("Skullclamp") || n.equals("Beast Whisperer") || n.equals("Guardian Project")
            || n.equals("Anointed Procession") || n.equals("Mechanized Warfare")) return true;
        if (spell.creature && own != null && spell.power > 0 && spell.power >= own.life) return true;
        boolean hostile = t.contains("destroy") || t.contains("exile") || t.contains("damage") || t.contains("counter target") || t.contains("lose");
        if (hostile) {
            if (spell.targetIds.contains(o.selfId)) return true;
            for (CardView c : board) if (spell.targetIds.contains(c.id) && (front(c.name).equals(commander) || cardValue(c) >= 7)) return true;
            for (CardView c : o.stack) if (c.controllerId.equals(o.selfId) && spell.targetIds.contains(c.id) && cardValue(c) >= 5) return true;
        }
        Role publicRole = ROLES.get(n);
        if (publicRole == Role.PAYOFF) {
            for (Seat s : o.seats) if (s.id.equals(spell.controllerId) && countCreatures(s.battlefield) >= 3) return true;
        }
        return spell.creature && (spell.power >= 5 || spell.manaValue >= 4 && t.contains("draw"));
    }
    private static boolean isCombatFinisher(String n) {
        return n.equals("Akroma's Will") || n.equals("Overrun") || n.equals("Overwhelming Stampede");
    }
    private static boolean isCreatureSource(Observation o, String n) {
        for (CardView c : o.hand) if (front(c.name).equals(n)) return c.creature;
        for (Seat s : o.seats) if (s.id.equals(o.selfId)) for (CardView c : s.battlefield) if (front(c.name).equals(n)) return c.creature;
        return n.equals(o.commander);
    }
    private static boolean isTribeSource(Observation o, String n, String tribe) {
        for (CardView c : o.hand) if (front(c.name).equals(n)) return c.creature && c.subtypes.contains(tribe);
        for (Seat s : o.seats) if (s.id.equals(o.selfId)) for (CardView c : s.battlefield)
            if (front(c.name).equals(n)) return c.creature && c.subtypes.contains(tribe);
        return false; // Missing subtype data is not permission to infer a tribe from a card name.
    }
    private static int sourcePower(Observation o, String n) {
        for (CardView c : o.hand) if (front(c.name).equals(n)) return Math.max(0,c.power);
        return 0;
    }
    /** Rough keep heuristic only; cannot infer pips, fetch restrictions or MDFC back-face lands. */
    public boolean keep(List<CardView> hand, int mulligans) {
        int lands = 0, cheap = 0, ramp = 0, colors = 0;
        for (CardView c : hand) {
            if (c.land) { lands++; colors |= landColors(c); }
            else { if (c.manaValue <= 3) cheap++; if (role(c.name) == Role.RAMP && c.manaValue <= 3) ramp++; }
        }
        if (lands == 0 || lands == hand.size()) return false;
        if (mulligans >= 2) return lands >= 2 && lands <= 5; // Explicitly stop over-mulliganing functional hands.
        if (lands < 2 || lands > 5 || cheap == 0) return false;
        if ((is("Gishath") || is("Aesi")) && lands == 2 && ramp == 0) return false;
        if ((is("Giada") || is("Adeline")) && (colors & 1) == 0) return false;
        if ((is("Krenko") || is("Torbran")) && (colors & 8) == 0) return false;
        if (is("Talrand") && (colors & 2) == 0) return false;
        if ((is("Ghalta") || is("Goreclaw")) && (colors & 16) == 0) return false;
        if (is("Lathril") && ((colors & 16) == 0 || ((colors & 4) == 0 && ramp == 0))) return false;
        if (is("Aesi") && ((colors & 16) == 0 || ((colors & 2) == 0 && ramp == 0))) return false;
        if (is("Gishath") && ((colors & 16) == 0 || (Integer.bitCount(colors & 25) < 2 && ramp == 0))) return false;
        return true;
    }
    private static int landColors(CardView c) {
        String n = c.name, t = c.text;
        int result = 0;
        if (n.equals("Plains") || t.contains("{W}")) result |= 1;
        if (n.equals("Island") || t.contains("{U}")) result |= 2;
        if (n.equals("Swamp") || t.contains("{B}")) result |= 4;
        if (n.equals("Mountain") || t.contains("{R}")) result |= 8;
        if (n.equals("Forest") || n.equals("Snow-Covered Forest") || t.contains("{G}")) result |= 16;
        if (n.equals("Command Tower") || n.equals("Path of Ancestry") || n.equals("Cavern of Souls") || n.equals("Secluded Courtyard") || n.equals("Unclaimed Territory")) result |= 31;
        if (n.equals("Breeding Pool") || n.equals("Tropical Island") || n.equals("Hinterland Harbor") || n.equals("Rejuvenating Springs") || n.equals("Dreamroot Cascade") || n.equals("Yavimaya Coast") || n.equals("Simic Growth Chamber")) result |= 18;
        if (n.equals("Llanowar Wastes") || n.equals("Woodland Cemetery") || n.equals("Deathcap Glade") || n.equals("Twilight Mire") || n.equals("Necroblossom Snarl") || n.equals("Viridescent Bog")) result |= 20;
        if (n.equals("Stomping Ground") || n.equals("Rootbound Crag") || n.equals("Cinder Glade") || n.equals("Spire Garden")) result |= 24;
        if (n.equals("Temple Garden") || n.equals("Canopy Vista") || n.equals("Sunpetal Grove") || n.equals("Overgrown Farmland") || n.equals("Bountiful Promenade")) result |= 17;
        if (n.equals("Sacred Foundry") || n.equals("Clifftop Retreat") || n.equals("Spectator Seating") || n.equals("Sundown Pass")) result |= 9;
        if (n.equals("Jetmir's Garden")) result |= 25;
        return result;
    }
    static {
        register("Giada, Font of Hope", new String[] {
            "Akroma's Will",
            "Angel of Destiny",
            "Angel of Finality",
            "Angel of Jubilation",
            "Angel of Vitality",
            "Angel of the Ruins",
            "Angelic Accord",
            "Angelic Field Marshal",
            "Angelic Skirmisher",
            "Arcane Signet",
            "Archaeomancer's Map",
            "Archangel of Thune",
            "Archangel of Tithes",
            "Austere Command",
            "Authority of the Consuls",
            "Avacyn, Angel of Hope",
            "Baneslayer Angel",
            "Battle Angels of Tyr",
            "Bishop of Wings",
            "Bonders' Enclave",
            "Bruna, the Fading Light",
            "Cavern of Souls",
            "Court of Grace",
            "Eiganjo, Seat of the Empire",
            "Emeria Shepherd",
            "Emeria, the Sky Ruin",
            "Entreat the Angels",
            "Exemplar of Light",
            "Farewell",
            "Firemane Commando",
            "Flare of Fortitude",
            "Flawless Maneuver",
            "Folk Hero",
            "Generous Gift",
            "Gisela, the Broken Blade",
            "Herald of War",
            "Herald's Horn",
            "Inspiring Overseer",
            "Land Tax",
            "Lightning Greaves",
            "Linvala, Keeper of Silence",
            "Luminarch Ascension",
            "Lyra Dawnbringer",
            "Metropolis Reformer",
            "Minas Tirith",
            "Myriad Landscape",
            "Nykthos, Shrine to Nyx",
            "Oketra's Monument",
            "Path of Ancestry",
            "Path to Exile",
            "Pearl Medallion",
            "Plains",
            "Resplendent Angel",
            "Righteous Valkyrie",
            "Sephara, Sky's Blade",
            "Seraph Sanctuary",
            "Serra the Benevolent",
            "Serra's Emissary",
            "Smothering Tithe",
            "Sol Ring",
            "Starnheim Aspirant",
            "Stroke of Midnight",
            "Sunblast Angel",
            "Swiftfoot Boots",
            "Sword of the Animist",
            "Swords to Plowshares",
            "Teferi's Protection",
            "The Book of Exalted Deeds",
            "Thraben Watcher",
            "Urza's Incubator",
            "Valkyrie Harbinger",
            "Vanquisher's Banner",
            "War Room",
            "Wrath of God",
            "Youthful Valkyrie",
            "Giada, Font of Hope",
        });
        register("Krenko, Mob Boss", new String[] {
            "Amonkhet Raceway",
            "Ancient Tomb",
            "Anger",
            "Battle Cry Goblin",
            "Battle Squadron",
            "Big Score",
            "Blasphemous Act",
            "Bolt Bend",
            "Brash Taunter",
            "Breeches, Eager Pillager",
            "Castle Embereth",
            "Chaos Warp",
            "Conspicuous Snoop",
            "Count on Luck",
            "Deflecting Swat",
            "Demand Answers",
            "Embrace the Unknown",
            "Faithless Looting",
            "Flare of Duplication",
            "General Kreat, the Boltbringer",
            "Glimpse the Impossible",
            "Goblin Bombardment",
            "Goblin Chieftain",
            "Goblin Chirurgeon",
            "Goblin Grenade",
            "Goblin King",
            "Goblin Lackey",
            "Goblin Matron",
            "Goblin Motivator",
            "Goblin Recruiter",
            "Goblin Ringleader",
            "Goblin Sledder",
            "Goblin Trashmaster",
            "Goblin War Strike",
            "Goblin Warchief",
            "Goblin Wizard",
            "Goro-Goro, Disciple of Ryusei",
            "Hexing Squelcher",
            "Hobgoblin Bandit Lord",
            "Howlsquad Heavy",
            "Impact Tremors",
            "Krenko's Command",
            "Krenko, Tin Street Kingpin",
            "Legion Loyalist",
            "Legion Warboss",
            "Lightning Greaves",
            "Mass Hysteria",
            "Massive Raid",
            "Moria Marauder",
            "Mountain",
            "Muxus, Goblin Grandee",
            "Pashalik Mons",
            "Pyroblast",
            "Quest for the Goblin Lord",
            "Redirect Lightning",
            "Return the Favor",
            "Romantic Rendezvous",
            "Rundvelt Hordemaster",
            "Shared Animosity",
            "Skirk Prospector",
            "Skullclamp",
            "Sol Ring",
            "Springleaf Drum",
            "Swiftfoot Boots",
            "Vandalblast",
            "War Room",
            "Warren Instigator",
            "Wild Wasteland",
            "Krenko, Mob Boss",
        });
        register("Lathril, Blade of the Elves", new String[] {
            "Arbor Elf",
            "Beast Whisperer",
            "Beast Within",
            "Bojuka Bog",
            "Cantankerous Keepers",
            "Celestial Reunion",
            "Champions of the Perfect",
            "Collective Resistance",
            "Collective Unconscious",
            "Command Tower",
            "Crippling Fear",
            "Cultist of the Absolute",
            "Deathcap Glade",
            "Demonic Embrace",
            "Devoted Druid",
            "Dwynen, Gilt-Leaf Daen",
            "Eclipsed Elf",
            "Eclipsed Realms",
            "Elven Passage",
            "Elvish Archdruid",
            "Elvish Dreadlord",
            "Elvish Guidance",
            "Elvish Mystic",
            "Elvish Warmaster",
            "Emerald Charm",
            "Exotic Orchard",
            "Festering Thicket",
            "Finale of Devastation",
            "Forest",
            "Fyndhorn Elves",
            "Get a Leg Up",
            "Gloom Ripper",
            "Gnarlroot Trapper",
            "Golgari Charm",
            "Grim Backwoods",
            "Harald Unites the Elves",
            "Heritage Druid",
            "Immaculate Magistrate",
            "Imperious Perfect",
            "Joraga Treespeaker",
            "Kindred Summons",
            "Lightning Greaves",
            "Llanowar Elves",
            "Llanowar Wastes",
            "Might of the Masses",
            "Minions' Murmurs",
            "Morcant's Loyalist",
            "My Precious // Allure of Power",
            "Necroblossom Snarl",
            "Night's Whisper",
            "Overrun",
            "Overwhelming Stampede",
            "Path of Ancestry",
            "Pile On",
            "Plumb the Forbidden",
            "Poison-Tip Archer",
            "Priest of Titania",
            "Realmwalker",
            "Reclamation Sage",
            "Return of the Wildspeaker",
            "Revitalizing Repast // Old-Growth Grove",
            "Rogue's Passage",
            "Secluded Courtyard",
            "Selfless Safewright",
            "Shaman of the Pack",
            "Shamanic Revelation",
            "Skemfar Avenger",
            "Skemfar Elderhall",
            "Skemfar Shadowsage",
            "Stargaze",
            "Swamp",
            "Swiftfoot Boots",
            "Timberwatch Elf",
            "Trystan's Command",
            "Twilight Mire",
            "Tyvar's Stand",
            "Unclaimed Territory",
            "Vernal Fen",
            "Viridescent Bog",
            "Virulent Emissary",
            "Vitalize",
            "Wirewood Channeler",
            "Wirewood Symbiote",
            "Woodland Cemetery",
            "Lathril, Blade of the Elves",
        });
        register("Ghalta, Primal Hunger", new String[] {
            "Agonasaur Rex",
            "Arbor Elf",
            "Badgermole Cub",
            "Beast Whisperer",
            "Beast Within",
            "Boseiju, Who Endures",
            "Bramble Familiar // Fetch Quest",
            "Bridgeworks Battle // Tanglespan Bridgeworks",
            "Bushwhack",
            "Collective Resistance",
            "Cultivate",
            "Disciple of Freyalise // Garden of Freyalise",
            "Drover of the Mighty",
            "Elder Gargaroth",
            "Elvish Mystic",
            "Fanatic of Rhonas",
            "Flare of Cultivation",
            "Forest",
            "Fyndhorn Elves",
            "Garruk's Uprising",
            "Gigantosaurus",
            "Goreclaw, Terror of Qal Sisma",
            "Gwenna, Eyes of Gaea",
            "Herd Heirloom",
            "Hulking Raptor",
            "Ilysian Caryatid",
            "Intrepid Paleontologist",
            "Karametra's Acolyte",
            "Kazandu Mammoth // Kazandu Valley",
            "Kogla, the Titan Ape",
            "Lair of the Hydra",
            "Lightning Greaves",
            "Llanowar Elves",
            "Llanowar Tribe",
            "Lotus Cobra",
            "Majestic Genesis",
            "Misty Rainforest",
            "Mosswort Bridge",
            "Nissa's Pilgrimage",
            "Nullhide Ferox",
            "Nykthos, Shrine to Nyx",
            "Nylea, Keen-Eyed",
            "Outcaster Trailblazer",
            "Overlord of the Hauntwoods",
            "Paradise Druid",
            "Phytotitan",
            "Primal Might",
            "Pugnacious Hammerskull",
            "Questing Beast",
            "Raucous Audience",
            "Reclamation Sage",
            "Regal Imperiosaur",
            "Rhonas the Indomitable",
            "Scrapshooter",
            "Selvala, Heart of the Wilds",
            "Sol Ring",
            "Spinner of Souls",
            "Steel Leaf Champion",
            "Summon: Fenrir",
            "Surrak, Elusive Hunter",
            "Surrak, the Hunt Caller",
            "Swiftfoot Boots",
            "Sylvan Library",
            "Tangled Florahedron // Tangled Vale",
            "Tangleweave Armor",
            "Tender Wildguide",
            "The Great Henge",
            "The Skullspore Nexus",
            "Topiary Stomper",
            "Tribute to the World Tree",
            "Turntimber Symbiosis // Turntimber, Serpentine Wood",
            "Utopia Sprawl",
            "Vaultborn Tyrant",
            "Vorinclex // The Grand Evolution",
            "Wayward Swordtooth",
            "Whisperer of the Wilds",
            "Wild Growth",
            "Wooded Foothills",
            "Yorvo, Lord of Garenbrig",
            "Ghalta, Primal Hunger",
        });
        register("Goreclaw, Terror of Qal Sisma", new String[] {
            "Apex Altisaur",
            "Apex Devastator",
            "Badgermole Cub",
            "Bala Ged Recovery // Bala Ged Sanctuary",
            "Beast Whisperer",
            "Beast Within",
            "Bellowing Tanglewurm",
            "Beorn the Fierce",
            "Bonders' Enclave",
            "Boseiju, Who Endures",
            "Castle Garenbrig",
            "Challenger Troll",
            "Craterhoof Behemoth",
            "Demolition Field",
            "Disciple of Freyalise // Garden of Freyalise",
            "Elder Gargaroth",
            "Emerald Medallion",
            "Evendo, Waking Haven",
            "Fanatic of Rhonas",
            "Frenzied Baloth",
            "Garruk's Packleader",
            "Garruk's Uprising",
            "Guardian Project",
            "Gwenna, Eyes of Gaea",
            "Heartwood Storyteller",
            "Heroic Intervention",
            "Ilysian Caryatid",
            "Intrepid Paleontologist",
            "Kamahl, Heart of Krosa",
            "Keeper of Fables",
            "Kenrith's Transformation",
            "Kodama of the East Tree",
            "Kogla, the Titan Ape",
            "Kona, Rescue Beastie",
            "Manglehorn",
            "Mosswort Bridge",
            "Nature's Lore",
            "Nykthos, Shrine to Nyx",
            "Nylea, Keen-Eyed",
            "Ohran Frostfang",
            "Ojer Kaslem, Deepest Growth // Temple of Cultivation",
            "Outcaster Trailblazer",
            "Overlord of the Hauntwoods",
            "Overwhelming Stampede",
            "Prowling Serpopard",
            "Questing Beast",
            "Radagast of Rhosgobel",
            "Rampaging Baloths",
            "Regal Behemoth",
            "Rhonas the Indomitable",
            "Rhonas's Monument",
            "Rogue's Passage",
            "Sakura-Tribe Elder",
            "Scavenger Grounds",
            "Selvala, Heart of the Wilds",
            "Silverback Elder",
            "Smuggler's Surprise",
            "Snow-Covered Forest",
            "Sol Ring",
            "Soul of the Harvest",
            "Studious First-Year // Rampant Growth",
            "Surrak and Goreclaw",
            "Surrak, the Hunt Caller",
            "Sword of Light and Shadow",
            "Tangled Florahedron // Tangled Vale",
            "Terastodon",
            "The Earth King",
            "The Great Henge",
            "Thunderfoot Baloth",
            "Toski, Bearer of Secrets",
            "Trailtracker Scout",
            "Vaultborn Tyrant",
            "War Room",
            "Wayward Swordtooth",
            "Whisperer of the Wilds",
            "Yeva, Nature's Herald",
            "Zopandrel, Hunger Dominus",
            "Goreclaw, Terror of Qal Sisma",
        });
        register("Torbran, Thane of Red Fell", new String[] {
            "Abrade",
            "Ancient Tomb",
            "Arcane Signet",
            "Arena of Glory",
            "Blasphemous Act",
            "Boltwave",
            "By Force",
            "Cemetery Gatekeeper",
            "Chain Reaction",
            "Chandra's Incinerator",
            "Chaos Warp",
            "City on Fire",
            "Dead // Gone",
            "Defiler of Instinct",
            "Deflecting Swat",
            "Delayed Blast Fireball",
            "Dwarven Mine",
            "Eidolon of the Great Revel",
            "End the Festivities",
            "Faithless Looting",
            "Fiery Confluence",
            "Fiery Emancipation",
            "Forgotten Cave",
            "Fury",
            "Generous Plunderer",
            "Glimpse the Impossible",
            "Goblin Chainwhirler",
            "Impact Resonance",
            "Inferno Titan",
            "Jeska's Will",
            "Lightning Bolt",
            "Lightning Greaves",
            "Liquimetal Torque",
            "Magda, Brazen Outlaw",
            "Magda, the Hoardmaster",
            "Manabarbs",
            "Mechanized Warfare",
            "Mimic Vat",
            "Mines of Moria",
            "Mizzium Mortars",
            "Mountain",
            "Neheb, the Eternal",
            "Nykthos, Shrine to Nyx",
            "Ojer Axonil, Deepest Might // Temple of Power",
            "Pinnacle Monk // Mystic Peak",
            "Price of Progress",
            "Pyrokinesis",
            "Quakebringer",
            "Ragavan, Nimble Pilferer",
            "Rampaging Ferocidon",
            "Ramunap Ruins",
            "Razorkin Needlehead",
            "Red Elemental Blast",
            "Ruby Medallion",
            "Scavenger Grounds",
            "Screaming Nemesis",
            "Scytheclaw Raptor",
            "Sensei's Divining Top",
            "Sin Prodder",
            "Sokenzan, Crucible of Defiance",
            "Sol Ring",
            "Solphim, Mayhem Dominus",
            "Spikefield Hazard // Spikefield Cave",
            "Spinerock Knoll",
            "Stigma Lasher",
            "Sundering Eruption // Volcanic Fissure",
            "Sunspine Lynx",
            "Swiftfoot Boots",
            "Tectonic Hazard",
            "Terror of the Peaks",
            "Valakut Exploration",
            "Vandalblast",
            "Virtue of Courage // Embereth Blaze",
            "Wild Magic Surge",
            "Zoyowa's Justice",
            "Torbran, Thane of Red Fell",
        });
        register("Talrand, Sky Summoner", new String[] {
            "Abjure",
            "Aetherize",
            "An Offer You Can't Refuse",
            "Arcane Denial",
            "Arcane Signet",
            "Archmage Emeritus",
            "Archmage of Runes",
            "Archmage's Charm",
            "Baral, Chief of Compliance",
            "Bident of Thassa",
            "Blink of an Eye",
            "Brainstorm",
            "Case of the Ransacked Lab",
            "Castle Vantress",
            "Chart a Course",
            "Chemister's Insight",
            "Coastal Piracy",
            "Consider",
            "Counterspell",
            "Curse of the Swine",
            "Dig Through Time",
            "Disdainful Stroke",
            "Dispel",
            "Dissolve",
            "Everflowing Chalice",
            "Favorable Winds",
            "Flood of Recollection",
            "Flow of Knowledge",
            "Frantic Search",
            "Gitaxian Probe",
            "Heraldic Banner",
            "Hermes, Overseer of Elpis",
            "High Tide",
            "Hullbreaker Horror",
            "Into the Roil",
            "Island",
            "Metallurgic Summonings",
            "Mission Briefing",
            "Murmuring Mystic",
            "Myriad Landscape",
            "Mystic Sanctuary",
            "Narset, Parter of Veils",
            "Negate",
            "Opt",
            "Ponder",
            "Pongify",
            "Preordain",
            "Quick Study",
            "Rapid Hybridization",
            "Ravenform",
            "Reality Shift",
            "Reconnaissance Mission",
            "Refute",
            "Reliquary Tower",
            "Rewind",
            "Rhystic Study",
            "Riptide Laboratory",
            "Rivendell",
            "Sapphire Medallion",
            "Serum Visions",
            "Sol Ring",
            "Solve the Equation",
            "Think Twice",
            "Thought Vessel",
            "Transcendent Message",
            "Treasure Cruise",
            "Unwind",
            "Wash Away",
            "Wavebreak Hippocamp",
            "Winged Words",
            "Wizard's Retort",
            "Talrand, Sky Summoner",
        });
        register("Aesi, Tyrant of Gyre Strait", new String[] {
            "An Offer You Can't Refuse",
            "Ancient Greenwarden",
            "Arcane Signet",
            "Arixmethes, Slumbering Isle",
            "Ashaya, Soul of the Wild",
            "Azusa, Lost but Seeking",
            "Beast Within",
            "Boseiju, Who Endures",
            "Breeding Pool",
            "Bristly Bill, Spine Sower",
            "Burgeoning",
            "Command Tower",
            "Counterspell",
            "Courser of Kruphix",
            "Crop Rotation",
            "Cultivate",
            "Dreamroot Cascade",
            "Dryad of the Ilysian Grove",
            "Entish Restoration",
            "Evolution Sage",
            "Evolving Wilds",
            "Exploration",
            "Fabled Passage",
            "Farseek",
            "Forest",
            "Garruk's Uprising",
            "Growth Spiral",
            "Harrow",
            "Heroic Intervention",
            "Hinterland Harbor",
            "Icetill Explorer",
            "Island",
            "Khalni Heart Expedition",
            "Kodama's Reach",
            "Lightning Greaves",
            "Lotus Cobra",
            "Misty Rainforest",
            "Mole Man, Moloid Master",
            "Mossborn Hydra",
            "Multani, Yavimaya's Avatar",
            "Murkfiend Liege",
            "Myriad Landscape",
            "Nature's Lore",
            "Oracle of Mul Daya",
            "Planar Engineering",
            "Pongify",
            "Rampaging Baloths",
            "Rampant Growth",
            "Ramunap Excavator",
            "Rapid Hybridization",
            "Rejuvenating Springs",
            "Reliquary Tower",
            "Retreat to Coralhelm",
            "Retreat to Kazandu",
            "Ride the Shoopuf",
            "Roaring Earth",
            "Roiling Regrowth",
            "Sakura-Tribe Elder",
            "Sazh's Chocobo",
            "Scute Swarm",
            "Scythecat Cub",
            "Simic Growth Chamber",
            "Skyshroud Claim",
            "Sol Ring",
            "Springheart Nantuko",
            "Swiftfoot Boots",
            "Tatyova, Benthic Druid",
            "Terramorphic Expanse",
            "Three Visits",
            "Tireless Provisioner",
            "Traveling Chocobo",
            "Tropical Island",
            "Uro, Titan of Nature's Wrath",
            "Yavimaya Coast",
            "Yavimaya, Cradle of Growth",
            "Aesi, Tyrant of Gyre Strait",
        });
        register("Gishath, Sun's Avatar", new String[] {
            "Akroma's Will",
            "Apex Altisaur",
            "Arcane Signet",
            "Arid Mesa",
            "Blasphemous Act",
            "Bonehoard Dracosaur",
            "Boros Charm",
            "Bountiful Promenade",
            "Bronzebeak Foragers",
            "Canopy Vista",
            "Cavern of Souls",
            "Chandra's Ignition",
            "Cinder Glade",
            "Clifftop Retreat",
            "Command Tower",
            "Congregation at Dawn",
            "Contest of Claws",
            "Curious Altisaur",
            "Earthshaker Dreadmaw",
            "Etali, Primal Conqueror // Etali, Primal Sickness",
            "Etali, Primal Storm",
            "Fabled Passage",
            "Farewell",
            "Farseek",
            "Forest",
            "Garruk's Uprising",
            "Generous Gift",
            "Ghalta and Mavren",
            "Ghalta, Primal Hunger",
            "Ghalta, Stampede Tyrant",
            "Guardian Project",
            "Heroic Intervention",
            "Hulking Raptor",
            "Hunting Velociraptor",
            "Invasion of Ikoria // Zilortha, Apex of Ikoria",
            "Jetmir's Garden",
            "Kogla and Yidaro",
            "Monster Manual // Zoological Study",
            "Mountain",
            "Nature's Lore",
            "Overgrown Farmland",
            "Overwhelming Stampede",
            "Pantlaza, Sun-Favored",
            "Path of Ancestry",
            "Path to Exile",
            "Plains",
            "Quartzwood Crasher",
            "Rampaging Brontodon",
            "Rampant Growth",
            "Regal Behemoth",
            "Regisaur Alpha",
            "Rhythm of the Wild",
            "Rogue's Passage",
            "Rootbound Crag",
            "Sacred Foundry",
            "Savage Order",
            "Sawhorn Nemesis",
            "Scion of Calamity",
            "Secluded Courtyard",
            "Sheltering Landscape",
            "Skyshroud Claim",
            "Sol Ring",
            "Spectator Seating",
            "Spire Garden",
            "Stomping Ground",
            "Sundown Pass",
            "Sunfrill Imitator",
            "Sunpetal Grove",
            "Swords to Plowshares",
            "Temple Altisaur",
            "Temple Garden",
            "The Great Henge",
            "The Skullspore Nexus",
            "Thrashing Brontodon",
            "Three Visits",
            "Thunderherd Migration",
            "Topiary Stomper",
            "Trumpeting Carnosaur",
            "Unclaimed Territory",
            "Urza's Incubator",
            "Vaultborn Tyrant",
            "Wayward Swordtooth",
            "Welcome to . . . // Jurassic Park",
            "Windswept Heath",
            "Wooded Foothills",
            "World War Hulk",
            "Wrathful Raptors",
            "Zacama, Primal Calamity",
            "Zetalpa, Primal Dawn",
            "Gishath, Sun's Avatar",
        });
        register("Adeline, Resplendent Cathar", new String[] {
            "Akroma's Will",
            "Anointed Procession",
            "Arcane Signet",
            "Archaeomancer's Map",
            "Austere Command",
            "Bennie Bracks, Zoologist",
            "By Invitation Only",
            "Castle Ardenvale",
            "Cathars' Crusade",
            "Champion of the Parish",
            "Coat of Arms",
            "Collective Effort",
            "Court Street Denizen",
            "Divine Visitation",
            "Dolmen Gate",
            "Elesh Norn, Grand Cenobite",
            "Elspeth, Sun's Champion",
            "Emeria's Call // Emeria, Shattered Skyclave",
            "Emeria, the Sky Ruin",
            "Esper Sentinel",
            "Flawless Maneuver",
            "Frontline Medic",
            "Generous Gift",
            "Ghostly Prison",
            "Goldnight Commander",
            "Grand Abolisher",
            "Guardian of Faith",
            "Halo Fountain",
            "Hanweir Militia Captain // Westvale Cult Leader",
            "Heliod's Intervention",
            "Heraldic Banner",
            "Hero of Bladehold",
            "Hour of Reckoning",
            "Idol of Oblivion",
            "Intrepid Adversary",
            "Keeper of the Accord",
            "Knight of the White Orchid",
            "Land Tax",
            "Lightning Greaves",
            "Loyal Warhound",
            "Luminarch Ascension",
            "Mangara, the Diplomat",
            "Mentor of the Meek",
            "Mirror Entity",
            "Mother of Runes",
            "Nykthos, Shrine to Nyx",
            "Odric, Master Tactician",
            "Oketra's Monument",
            "Paladin Class",
            "Path to Exile",
            "Pearl Medallion",
            "Plains",
            "Prava of the Steel Legion",
            "Reconnaissance",
            "Rogue's Passage",
            "Rumor Gatherer",
            "Selfless Spirit",
            "Smothering Tithe",
            "Sol Ring",
            "Soul Warden",
            "Sun Titan",
            "Swiftfoot Boots",
            "Sword of the Animist",
            "Swords to Plowshares",
            "Teferi's Protection",
            "Thalia's Lieutenant",
            "Thalia, Heretic Cathar",
            "Unbreakable Formation",
            "Victory's Envoy",
            "War Room",
            "Welcoming Vampire",
            "Whispersilk Cloak",
            "Windbrisk Heights",
            "Adeline, Resplendent Cathar",
        });
        assign(Role.COUNTER, "Pyroblast|Red Elemental Blast|Abjure|An Offer You Can't Refuse|Arcane Denial|Archmage's Charm|Counterspell|Disdainful Stroke|Dispel|Dissolve|Negate|Refute|Rewind|Unwind|Wash Away|Wizard's Retort");
        assign(Role.ENGINE, "Battle Angels of Tyr|Exemplar of Light|Firemane Commando|Folk Hero|Inspiring Overseer|Vanquisher's Banner|Conspicuous Snoop|Count on Luck|Big Score|Demand Answers|Embrace the Unknown|Faithless Looting|Glimpse the Impossible|Goblin Matron|Goblin Recruiter|Goblin Ringleader|Moria Marauder|Rundvelt Hordemaster|Skullclamp|Beast Whisperer|Collective Unconscious|Minions' Murmurs|Night's Whisper|Plumb the Forbidden|Realmwalker|Return of the Wildspeaker|Shamanic Revelation|Skemfar Avenger|Garruk's Uprising|Outcaster Trailblazer|Spinner of Souls|Sylvan Library|The Great Henge|Tribute to the World Tree|Vaultborn Tyrant|Garruk's Packleader|Guardian Project|Heartwood Storyteller|Keeper of Fables|Kodama of the East Tree|Ohran Frostfang|Ojer Kaslem, Deepest Growth|Soul of the Harvest|Toski, Bearer of Secrets|Jeska's Will|Sensei's Divining Top|Sin Prodder|Valakut Exploration|Virtue of Courage|Archmage Emeritus|Archmage of Runes|Baral, Chief of Compliance|Bident of Thassa|Brainstorm|Case of the Ransacked Lab|Chart a Course|Chemister's Insight|Coastal Piracy|Consider|Dig Through Time|Flood of Recollection|Flow of Knowledge|Frantic Search|Gitaxian Probe|Mission Briefing|Narset, Parter of Veils|Opt|Ponder|Preordain|Quick Study|Reconnaissance Mission|Rhystic Study|Serum Visions|Solve the Equation|Think Twice|Transcendent Message|Treasure Cruise|Wavebreak Hippocamp|Winged Words|Courser of Kruphix|Ramunap Excavator|Tatyova, Benthic Druid|Ancient Greenwarden|Bonehoard Dracosaur|Curious Altisaur|Earthshaker Dreadmaw|Pantlaza, Sun-Favored|Bennie Bracks, Zoologist|Esper Sentinel|Idol of Oblivion|Mangara, the Diplomat|Mentor of the Meek|Rumor Gatherer|Welcoming Vampire");
        assign(Role.PAYOFF, "Akroma's Will|Angel of Destiny|Angelic Accord|Archangel of Thune|Avacyn, Angel of Hope|Entreat the Angels|Lyra Dawnbringer|Resplendent Angel|Righteous Valkyrie|Serra's Emissary|The Book of Exalted Deeds|Valkyrie Harbinger|Battle Squadron|General Kreat, the Boltbringer|Goblin Bombardment|Goblin Chieftain|Goblin King|Goblin War Strike|Goblin Warchief|Hobgoblin Bandit Lord|Impact Tremors|Massive Raid|Muxus, Goblin Grandee|Pashalik Mons|Quest for the Goblin Lord|Shared Animosity|Finale of Devastation|Immaculate Magistrate|Kindred Summons|Overrun|Overwhelming Stampede|Poison-Tip Archer|Shaman of the Pack|Skemfar Shadowsage|Timberwatch Elf|Ghalta, Primal Hunger|Gigantosaurus|Majestic Genesis|The Skullspore Nexus|Apex Devastator|Craterhoof Behemoth|Kamahl, Heart of Krosa|Rampaging Baloths|Thunderfoot Baloth|Zopandrel, Hunger Dominus|Boltwave|City on Fire|Fiery Emancipation|Mechanized Warfare|Ojer Axonil, Deepest Might|Price of Progress|Solphim, Mayhem Dominus|Terror of the Peaks|Favorable Winds|Hullbreaker Horror|Metallurgic Summonings|Murmuring Mystic|Ashaya, Soul of the Wild|Bristly Bill, Spine Sower|Evolution Sage|Mossborn Hydra|Multani, Yavimaya's Avatar|Retreat to Coralhelm|Retreat to Kazandu|Roaring Earth|Scute Swarm|Scythecat Cub|Springheart Nantuko|Ghalta and Mavren|Ghalta, Stampede Tyrant|Etali, Primal Conqueror|Etali, Primal Storm|Quartzwood Crasher|Sawhorn Nemesis|Zacama, Primal Calamity|Zetalpa, Primal Dawn|Anointed Procession|Cathars' Crusade|Coat of Arms|Divine Visitation|Elesh Norn, Grand Cenobite|Goldnight Commander|Intrepid Adversary|Mirror Entity|Odric, Master Tactician|Thalia's Lieutenant|Victory's Envoy");
        assign(Role.PROTECTION, "Flare of Fortitude|Flawless Maneuver|Teferi's Protection|Bolt Bend|Deflecting Swat|Flare of Duplication|Redirect Lightning|Return the Favor|Collective Resistance|Emerald Charm|Golgari Charm|Tyvar's Stand|Vitalize|Heroic Intervention|Boros Charm|Unbreakable Formation");
        assign(Role.RAMP, "Sol Ring|Arcane Signet|Archaeomancer's Map|Land Tax|Pearl Medallion|Smothering Tithe|Starnheim Aspirant|Urza's Incubator|Oketra's Monument|Herald's Horn|Sword of the Animist|Skirk Prospector|Springleaf Drum|Arbor Elf|Devoted Druid|Elvish Archdruid|Elvish Guidance|Elvish Mystic|Fyndhorn Elves|Gnarlroot Trapper|Heritage Druid|Joraga Treespeaker|Llanowar Elves|Priest of Titania|Wirewood Channeler|Cultivate|Drover of the Mighty|Fanatic of Rhonas|Flare of Cultivation|Gwenna, Eyes of Gaea|Herd Heirloom|Hulking Raptor|Ilysian Caryatid|Intrepid Paleontologist|Karametra's Acolyte|Llanowar Tribe|Lotus Cobra|Nissa's Pilgrimage|Nylea, Keen-Eyed|Overlord of the Hauntwoods|Paradise Druid|Selvala, Heart of the Wilds|Tangled Florahedron|Tender Wildguide|Topiary Stomper|Utopia Sprawl|Wayward Swordtooth|Whisperer of the Wilds|Wild Growth|Emerald Medallion|Nature's Lore|Regal Behemoth|Rhonas's Monument|Sakura-Tribe Elder|Liquimetal Torque|Ruby Medallion|Magda, Brazen Outlaw|Generous Plunderer|Neheb, the Eternal|Ragavan, Nimble Pilferer|Everflowing Chalice|Heraldic Banner|High Tide|Sapphire Medallion|Thought Vessel|Arixmethes, Slumbering Isle|Azusa, Lost but Seeking|Burgeoning|Dryad of the Ilysian Grove|Entish Restoration|Exploration|Farseek|Growth Spiral|Harrow|Khalni Heart Expedition|Kodama's Reach|Oracle of Mul Daya|Rampant Growth|Roiling Regrowth|Skyshroud Claim|Three Visits|Tireless Provisioner|Uro, Titan of Nature's Wrath|Hunting Velociraptor|Thunderherd Migration|Keeper of the Accord|Knight of the White Orchid|Loyal Warhound");
        assign(Role.REMOVAL, "Generous Gift|Path to Exile|Stroke of Midnight|Swords to Plowshares|Chaos Warp|Goblin Grenade|Vandalblast|Beast Within|Pile On|Reclamation Sage|Bushwhack|Primal Might|Scrapshooter|Kenrith's Transformation|Kogla, the Titan Ape|Manglehorn|Terastodon|Abrade|By Force|Dead|Lightning Bolt|Mizzium Mortars|Pyrokinesis|Wild Magic Surge|Zoyowa's Justice|Blink of an Eye|Curse of the Swine|Into the Roil|Pongify|Rapid Hybridization|Ravenform|Reality Shift|Apex Altisaur|Bronzebeak Foragers|Contest of Claws|Kogla and Yidaro|Scion of Calamity|Thrashing Brontodon|Collective Effort|Heliod's Intervention");
        assign(Role.SUPPORT, "Lightning Greaves|Swiftfoot Boots|Anger|Goblin Motivator|Mass Hysteria|Goro-Goro, Disciple of Ryusei|Legion Loyalist|Hexing Squelcher|Wirewood Symbiote|Surrak, the Hunt Caller|Prowling Serpopard|Surrak and Goreclaw|Yeva, Nature's Herald|Regisaur Alpha|Rhythm of the Wild|Dolmen Gate|Frontline Medic|Grand Abolisher|Guardian of Faith|Mother of Runes|Reconnaissance|Selfless Spirit|Whispersilk Cloak");
        assign(Role.SWEEPER, "Austere Command|Farewell|Wrath of God|Blasphemous Act|Crippling Fear|Chain Reaction|Delayed Blast Fireball|End the Festivities|Fiery Confluence|Goblin Chainwhirler|Tectonic Hazard|Aetherize|Chandra's Ignition|By Invitation Only|Hour of Reckoning");
    }
}
