package com.uprfvx.random.yao.guide;

/*----------------------------------------------------------------------------*/
/*--  GuideWild.java - groups wild encounters by place for the HTML guide.  --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.constants.*;
import com.uprfvx.romio.gamedata.Encounter;
import com.uprfvx.romio.gamedata.EncounterArea;
import com.uprfvx.romio.gamedata.EncounterType;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.*;

/**
 * Added for YAO (12/10/2026). The game stores wild Pokemon as many small encounter tables: one per map, per method
 * (grass, surf, each rod...), per time of day or season, and every table repeats a Pokemon once per slot. The guide
 * wants one row per Pokemon per place, so this class:
 *
 * <ul>
 *   <li>groups the tables by place (the program's location tag, which is the same in every language) and the
 *       order of the places is the program's own story order list for that game ({@code locationTagsTraverseOrder});</li>
 *   <li>names each table by method and variant (time of day, season, SOS calls);</li>
 *   <li>merges tables of the same method and variant inside one place (the floors of a cave, the maps of a route);</li>
 *   <li>collapses the slots of a table into one entry per Pokemon with its level range and, where the slot chances of
 *       that table type are known for that generation, the summed chance.</li>
 * </ul>
 *
 * Chances are only written for table types whose slot chances are well established (the 12 slot grass table of
 * Gen 3 to 6, the 5 slot surf table, the Gen 1 and Gen 2 land and water tables, the three Gen 3 rods). For every other
 * table the entry carries its slot count and the chance is null; the page then shows "n of N slots".
 */
public final class GuideWild {

    private GuideWild() {
    }

    private static final double[] STD12 = {20, 20, 10, 10, 10, 10, 5, 5, 4, 4, 1, 1};
    private static final double[] WATER5 = {60, 30, 5, 4, 1};
    private static final double[] GEN1 = percent(new int[]{51, 51, 39, 25, 25, 25, 13, 13, 11, 3}, 256);
    private static final double[] GEN2_LAND = {30, 30, 20, 10, 5, 4, 1};
    private static final double[] GEN2_WATER = {60, 30, 10};
    private static final double[] GEN3_OLD = {70, 30};
    private static final double[] GEN3_GOOD = {60, 20, 20};
    private static final double[] GEN3_SUPER = {40, 40, 15, 4, 1};

    private static final List<String> METHOD_ORDER = List.of("grass", "doubles", "special4", "swarm", "radio", "dexnav",
            "horde1", "horde2", "horde3", "flowery", "rough", "shaking", "surf", "surfspots", "fishing", "oldrod", "goodrod",
            "superrod", "fishspots", "nightfish", "fishswarm", "fishtime", "rocksmash", "headbutt", "interact", "ambush", "bcc",
            "special", "other");
    private static final List<String> VARIANT_ORDER = List.of("", "morning", "day", "night", "tod", "spring", "summer",
            "autumn", "winter");

    private static double[] percent(int[] weights, int total) {
        double[] out = new double[weights.length];
        for (int i = 0; i < weights.length; i++) {
            out[i] = Math.round(weights[i] * 1000.0 / total) / 10.0;
        }
        return out;
    }

    /** The slot chances of a table, or null when they are not known for sure. */
    static double[] slotChances(int gen, String method, int slots) {
        switch (gen) {
            case 1:
                return ("grass".equals(method) || "surf".equals(method)) && slots == GEN1.length ? GEN1 : null;
            case 2:
                if ("grass".equals(method) && slots == GEN2_LAND.length) {
                    return GEN2_LAND;
                }
                return "surf".equals(method) && slots == GEN2_WATER.length ? GEN2_WATER : null;
            case 3:
                if ("grass".equals(method) && slots == STD12.length) {
                    return STD12;
                }
                if (("surf".equals(method) || "rocksmash".equals(method)) && slots == WATER5.length) {
                    return WATER5;
                }
                if ("oldrod".equals(method) && slots == GEN3_OLD.length) {
                    return GEN3_OLD;
                }
                if ("goodrod".equals(method) && slots == GEN3_GOOD.length) {
                    return GEN3_GOOD;
                }
                return "superrod".equals(method) && slots == GEN3_SUPER.length ? GEN3_SUPER : null;
            case 4:
            case 5:
            case 6:
                if ("grass".equals(method) && slots == STD12.length) {
                    return STD12;
                }
                return "surf".equals(method) && slots == WATER5.length ? WATER5 : null;
            default:
                return null;
        }
    }

    /** What kind of table an area is: a method key and a variant key (time of day, season), see METHOD_ORDER. */
    record Kind(String method, String variant) {
    }

    static Kind classify(EncounterArea area) {
        String name = area.getDisplayName() == null ? "" : area.getDisplayName();
        String lower = name.toLowerCase(Locale.ROOT);
        String variant = "";
        if (area.getSeason() >= 0) {
            variant = new String[]{"spring", "summer", "autumn", "winter"}[Math.min(3, area.getSeason())];
        } else if (lower.contains("(morning)") || lower.contains(" morning grass")) {
            variant = "morning";
        } else if (lower.contains("(day)") || lower.contains(" day grass")) {
            variant = "day";
        } else if (lower.contains("(night)") || lower.contains(" night grass")) {
            variant = "night";
        }
        EncounterType type = area.getEncounterType();
        String method;
        if (lower.contains("doubles grass")) {
            method = "doubles";
        } else if (lower.contains("shaking spots")) {
            method = "shaking";
        } else if (lower.contains("surfing spots") || lower.contains("surf spots")) {
            method = "surfspots";
        } else if (lower.contains("fishing spots")) {
            method = "fishspots";
        } else if (lower.contains("fishing replacement")) {
            method = "nightfish";
        } else if (lower.contains("fishing swarm")) {
            method = "fishswarm";
        } else if (lower.contains("time-specific fishing")) {
            method = "fishtime";
        } else if (lower.contains("radio")) {
            method = "radio";
        } else if (lower.contains("swarm/radar/gba")) {
            method = "special4";
        } else if (lower.contains("swarm")) {
            method = "swarm";
        } else if (lower.contains("old rod")) {
            method = "oldrod";
        } else if (lower.contains("good rod")) {
            method = "goodrod";
        } else if (lower.contains("super rod")) {
            method = "superrod";
        } else if (lower.contains("rock smash")) {
            method = "rocksmash";
        } else if (lower.contains("headbutt")) {
            method = "headbutt";
        } else if (lower.contains("foreign encounter")) {
            method = "dexnav";
        } else if (lower.contains("common horde") && !lower.contains("uncommon")) {
            method = "horde1";
        } else if (lower.contains("uncommon horde")) {
            method = "horde2";
        } else if (lower.contains("rare horde")) {
            method = "horde3";
        } else if (lower.contains("flowers")) {
            method = "flowery";
        } else if (lower.contains("rough terrain") || lower.contains("long grass")) {
            method = "rough";
        } else if (lower.contains("bug catching contest")) {
            method = "bcc";
        } else if (type == EncounterType.WALKING) {
            method = "grass";
        } else if (type == EncounterType.SURFING) {
            method = "surf";
        } else if (type == EncounterType.FISHING) {
            method = "fishing";
        } else if (type == EncounterType.INTERACT) {
            method = "interact";
        } else if (type == EncounterType.AMBUSH) {
            method = "ambush";
        } else if (type == EncounterType.SPECIAL) {
            method = "special";
        } else {
            method = "other";
        }
        return new Kind(method, variant);
    }

    /** The program's story order list of location tags for the loaded game. */
    public static List<String> traverseOrder(RomHandler rom) {
        int romType = rom.getROMType();
        return switch (rom.generationOfPokemon()) {
            case 1 -> Gen1Constants.locationTagsTraverseOrder;
            case 2 -> Gen2Constants.locationTagsTraverseOrder;
            case 3 -> Gen3Constants.getLocationTagsTraverseOrder(romType);
            case 4 -> Gen4Constants.getLocationTagsTraverseOrder(romType);
            case 5 -> Gen5Constants.getLocationTagsTraverseOrder(romType);
            case 6 -> Gen6Constants.getLocationTagsTraverseOrder(romType);
            case 7 -> Gen7Constants.getLocationTagsTraverseOrder(romType);
            default -> List.of();
        };
    }

    // ---- accumulation ----

    private static final class Mon {
        int species;
        int lo = Integer.MAX_VALUE, hi = 0;
        double pMin = Double.MAX_VALUE, pMax = -1;
        boolean pKnown = true;
        int slots;
    }

    private static final class Table {
        final String method, variant;
        int rate, slots, maps;
        boolean postGame = true;
        final Map<Integer, Mon> mons = new LinkedHashMap<>();

        Table(String method, String variant) {
            this.method = method;
            this.variant = variant;
        }
    }

    private static final class Place {
        String tag, name;
        int firstIndex;
        boolean postGame = true;
        final Map<String, Table> tables = new LinkedHashMap<>();
    }

    private static String stripMethod(String displayName) {
        if (displayName == null) {
            return "";
        }
        return displayName.replaceAll("(?i)\\s+(grass/cave|surfing|surf|old rod|good rod|super rod|rock smash|headbutt|fishing)\\b.*$", "").strip();
    }

    /**
     * @return places in the game's story order, each as a map ready for JSON. {@code useTimeOfDay} is passed on to the
     *         handler by the caller, this method only groups what it is given.
     */
    public static List<Object> group(RomHandler rom, List<EncounterArea> areas) {
        int gen = rom.generationOfPokemon();
        List<String> order = traverseOrder(rom);
        Map<String, Place> places = new LinkedHashMap<>();
        int index = 0;
        for (EncounterArea area : areas) {
            index++;
            String tag = area.getLocationTag();
            String key;
            String name;
            if (tag == null || tag.isBlank()) {
                name = stripMethod(area.getDisplayName());
                key = "~" + name;
                tag = null;
            } else if (tag.equals("UNKNOWN")) {
                // maps the program has no name for: kept apart, one place per map
                key = "UNKNOWN#" + area.getMapIndex();
                name = "UNKNOWN";
            } else {
                key = tag;
                name = tag;
            }
            Place place = places.get(key);
            if (place == null) {
                place = new Place();
                place.tag = tag;
                place.name = name;
                place.firstIndex = index;
                places.put(key, place);
            }
            if (!area.isPostGame()) {
                place.postGame = false;
            }
            addArea(place, area, gen);
        }
        List<Place> sorted = new ArrayList<>(places.values());
        sorted.sort(Comparator.comparingInt((Place p) -> placeOrder(p, order)).thenComparingInt(p -> p.firstIndex));
        List<Object> out = new ArrayList<>();
        for (Place p : sorted) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("n", out.size() + 1);
            o.put("tag", p.tag);
            o.put("name", p.name);
            o.put("ord", placeOrder(p, order));
            o.put("pg", p.postGame);
            List<Table> tables = new ArrayList<>(p.tables.values());
            tables.sort(Comparator.comparingInt((Table t) -> METHOD_ORDER.indexOf(t.method) < 0 ? 999 : METHOD_ORDER.indexOf(t.method))
                    .thenComparingInt(t -> variantRank(t.variant)));
            List<Object> tableList = new ArrayList<>();
            for (Table t : tables) {
                Map<String, Object> to = new LinkedHashMap<>();
                to.put("m", t.method);
                to.put("v", t.variant);
                to.put("rate", t.rate);
                to.put("n", t.slots);
                to.put("maps", t.maps);
                to.put("pg", t.postGame);
                List<Object> mons = new ArrayList<>();
                for (Mon m : t.mons.values()) {
                    List<Object> row = new ArrayList<>();
                    row.add(m.species);
                    row.add(m.lo);
                    row.add(m.hi);
                    row.add(m.pKnown ? round1(m.pMin) : null);
                    row.add(m.pKnown ? round1(m.pMax) : null);
                    row.add(m.slots);
                    mons.add(row);
                }
                to.put("mons", mons);
                tableList.add(to);
            }
            o.put("tables", tableList);
            out.add(o);
        }
        return out;
    }

    private static Double round1(double d) {
        return Math.round(d * 10.0) / 10.0;
    }

    private static int variantRank(String v) {
        if (v.endsWith("+sos")) {
            return 100 + variantRank(v.substring(0, v.length() - 4));
        }
        int i = VARIANT_ORDER.indexOf(v);
        return i < 0 ? 99 : i;
    }

    private static int placeOrder(Place p, List<String> order) {
        int i = p.tag == null ? -1 : order.indexOf(p.tag);
        return i >= 0 ? i : 100000 + p.firstIndex;
    }

    private static void addArea(Place place, EncounterArea area, int gen) {
        Kind kind = classify(area);
        int n = area.size();
        List<Encounter> all = new ArrayList<>(area);
        if (gen == 3 && kind.method().equals("fishing") && n == 10) {
            addSlice(place, area, gen, "oldrod", kind.variant(), all.subList(0, 2));
            addSlice(place, area, gen, "goodrod", kind.variant(), all.subList(2, 5));
            addSlice(place, area, gen, "superrod", kind.variant(), all.subList(5, 10));
        } else if (gen == 4 && kind.method().equals("grass") && n == 16) {
            // Diamond, Pearl and Platinum with time based encounters: 12 normal slots, then 4 slots that replace some
            // of them depending on the time of day
            addSlice(place, area, gen, "grass", kind.variant(), all.subList(0, 12));
            addSlice(place, area, gen, "grass", "tod", all.subList(12, 16));
        } else if (gen == 7) {
            List<Encounter> normal = new ArrayList<>();
            List<Encounter> sos = new ArrayList<>();
            for (Encounter e : all) {
                (e.isSOS() ? sos : normal).add(e);
            }
            addSlice(place, area, gen, kind.method(), kind.variant(), normal);
            if (!sos.isEmpty()) {
                addSlice(place, area, gen, kind.method(), kind.variant() + "+sos", sos);
            }
        } else {
            addSlice(place, area, gen, kind.method(), kind.variant(), all);
        }
    }

    private static void addSlice(Place place, EncounterArea area, int gen, String method, String variant,
                                 List<Encounter> slice) {
        if (slice.isEmpty()) {
            return;
        }
        String key = method + "|" + variant;
        Table table = place.tables.computeIfAbsent(key, k -> new Table(method, variant));
        table.maps++;
        table.rate = Math.max(table.rate, area.getRate());
        table.slots = Math.max(table.slots, slice.size());
        if (!area.isPostGame()) {
            table.postGame = false;
        }
        // the slots of this one source table, collapsed per Pokemon
        double[] chances = variant.contains("sos") ? null : slotChances(gen, method, slice.size());
        Map<Integer, Mon> here = new LinkedHashMap<>();
        for (int i = 0; i < slice.size(); i++) {
            Encounter e = slice.get(i);
            int id = e.getSpecies().getNumber();
            Mon m = here.computeIfAbsent(id, k -> {
                Mon x = new Mon();
                x.species = id;
                x.pMin = 0;
                x.pMax = 0;
                return x;
            });
            m.lo = Math.min(m.lo, e.getLevel());
            m.hi = Math.max(m.hi, Math.max(e.getLevel(), e.getMaxLevel()));
            m.slots++;
            if (chances == null) {
                m.pKnown = false;
            } else {
                m.pMin += chances[i];
                m.pMax += chances[i];
            }
        }
        for (Mon h : here.values()) {
            Mon t = table.mons.get(h.species);
            if (t == null) {
                t = new Mon();
                t.species = h.species;
                table.mons.put(h.species, t);
                t.pKnown = h.pKnown;
            } else {
                t.pKnown = t.pKnown && h.pKnown;
            }
            t.lo = Math.min(t.lo, h.lo);
            t.hi = Math.max(t.hi, h.hi);
            t.slots = Math.max(t.slots, h.slots);
            if (h.pKnown) {
                t.pMin = Math.min(t.pMin, h.pMin);
                t.pMax = Math.max(t.pMax, h.pMax);
            }
        }
    }

    // ---- the same grouping as plain text, for the randomization log ----

    private static final Map<String, String> METHOD_EN = new HashMap<>();
    private static final Map<String, String> VARIANT_EN = Map.of("morning", "Morning", "day", "Day", "night", "Night",
            "tod", "time-of-day slots", "spring", "Spring", "summer", "Summer", "autumn", "Autumn", "winter", "Winter",
            "sos", "SOS calls");

    static {
        String[][] m = {{"grass", "Grass/Cave"}, {"doubles", "Doubles grass"}, {"shaking", "Shaking grass"},
                {"special4", "Swarms, Poke Radar, GBA slots"}, {"swarm", "Swarms"}, {"radio", "Hoenn/Sinnoh sound"},
                {"dexnav", "DexNav foreign encounter"}, {"horde1", "Common horde"}, {"horde2", "Uncommon horde"},
                {"horde3", "Rare horde"}, {"flowery", "Flowers"}, {"rough", "Rough terrain / tall grass"}, {"surf", "Surf"},
                {"surfspots", "Rippling water"}, {"fishing", "Fishing"}, {"oldrod", "Old Rod"}, {"goodrod", "Good Rod"},
                {"superrod", "Super Rod"}, {"fishspots", "Fishing spots"}, {"nightfish", "Night fishing replacement"},
                {"fishswarm", "Fishing swarm"}, {"fishtime", "Time-specific fishing"}, {"rocksmash", "Rock Smash"},
                {"headbutt", "Headbutt"}, {"interact", "Interaction"}, {"ambush", "Ambush"},
                {"bcc", "Bug-Catching Contest"}, {"special", "Special"}, {"other", "Other"}};
        for (String[] e : m) {
            METHOD_EN.put(e[0], e[1]);
        }
    }

    /** "Grass/Cave (Spring)": the English name of a table's method and variant. */
    static String englishLabel(String method, String variant) {
        String base = METHOD_EN.getOrDefault(method, method);
        if (variant == null || variant.isEmpty()) {
            return base;
        }
        boolean sos = variant.endsWith("+sos");
        String v = sos ? variant.substring(0, variant.length() - 4) : variant;
        List<String> parts = new ArrayList<>();
        if (!v.isEmpty()) {
            parts.add(VARIANT_EN.getOrDefault(v, v));
        }
        if (sos) {
            parts.add(VARIANT_EN.get("sos"));
        }
        return base + (parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")");
    }

    /**
     * The wild encounters as text, grouped like the HTML guide: one block per place in the game's story order, one
     * block per table (method and variant) inside it, one line per Pokemon with its level range and chance.
     *
     * @param nameOf the Pokemon name for an id (the ROM's own text)
     */
    @SuppressWarnings("unchecked")
    public static String logText(RomHandler rom, List<EncounterArea> areas, java.util.function.IntFunction<String> nameOf) {
        StringBuilder sb = new StringBuilder();
        GuideNames names = GuideNames.get();
        List<Object> places = group(rom, areas);
        int unknown = 0;
        for (Object po : places) {
            Map<String, Object> p = (Map<String, Object>) po;
            String tag = (String) p.get("tag");
            String title;
            if ("UNKNOWN".equals(tag)) {
                title = "Unnamed area " + (++unknown);
            } else if (tag != null && names.place(tag) != null) {
                title = names.place(tag)[0];
            } else {
                title = String.valueOf(p.get("name"));
            }
            sb.append("Place #").append(p.get("n") == null ? places.indexOf(po) + 1 : p.get("n")).append(": ").append(title);
            if (Boolean.TRUE.equals(p.get("pg"))) {
                sb.append(" (post-game)");
            }
            sb.append(System.lineSeparator());
            for (Object to : (List<Object>) p.get("tables")) {
                Map<String, Object> t = (Map<String, Object>) to;
                sb.append("  ").append(englishLabel((String) t.get("m"), (String) t.get("v")));
                if (Boolean.TRUE.equals(t.get("pg"))) {
                    sb.append(" (post-game)");
                }
                sb.append(System.lineSeparator());
                for (Object mo : (List<Object>) t.get("mons")) {
                    List<Object> m = (List<Object>) mo;
                    int lo = ((Number) m.get(1)).intValue(), hi = ((Number) m.get(2)).intValue();
                    sb.append("    ").append(nameOf.apply(((Number) m.get(0)).intValue())).append(": Lv")
                            .append(lo == hi ? String.valueOf(lo) : lo + "-" + hi).append(", ");
                    Object pmin = m.get(3), pmax = m.get(4);
                    if (pmin instanceof Double a && pmax instanceof Double b) {
                        sb.append(a.equals(b) ? fmt(a) : fmt(a) + "-" + fmt(b)).append('%');
                    } else {
                        sb.append(m.get(5)).append(" of ").append(t.get("n")).append(" slots");
                    }
                    sb.append(System.lineSeparator());
                }
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
