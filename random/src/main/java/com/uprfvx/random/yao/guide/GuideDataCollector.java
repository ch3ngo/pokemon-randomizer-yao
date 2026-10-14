package com.uprfvx.random.yao.guide;

/*----------------------------------------------------------------------------*/
/*--  GuideDataCollector.java - snapshots a ROM's data for the HTML guide.  --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.gamedata.basestats.BaseStats;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the data the guide shows from a loaded ROM into plain maps and lists, so it can be written as JSON.
 * Used twice, once on a freshly opened copy of the original ROM and once on the randomized one, so the page can
 * show what changed.
 *
 * <p>Every section is collected on its own: if a game does not expose one (or reading it fails), that
 * section is left out and a message is added to the {@code errors} list, and the rest of the guide is still
 * produced. Names are resolved by {@link GuideNames}: both an English and a Spanish name are written for every
 * Pokemon, move, ability and item, whatever the ROM's language is (added 12/10/2026).
 */
public final class GuideDataCollector {

    private GuideDataCollector() {
    }

    public static Map<String, Object> collect(RomHandler rom) {
        return collect(rom, true);
    }

    /**
     * Modified for YAO (12/10/2026): {@code useTimeOfDay} must be the same value the randomization was run with
     * ({@code Settings.isUseTimeBasedEncounters()}) for the wild section of the randomized game. Gen 5 re-reads
     * the wild data from the (already saved) ROM and tags it by its vanilla layout; when time based encounters
     * were off, the saved game has the same encounters in all four seasons, so reading it as four seasons found
     * more areas than the layout expects and the whole wild section failed. If the given way of reading fails,
     * the other one is tried, so the section is only left out when neither works.
     */
    public static Map<String, Object> collect(RomHandler rom, boolean useTimeOfDay) {
        return collect(rom, useTimeOfDay, new GuideNames.Resolver(rom));
    }

    /** @param names counts what could not be named, the caller can read it afterwards */
    public static Map<String, Object> collect(RomHandler rom, boolean useTimeOfDay, GuideNames.Resolver names) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        data.put("errors", errors);
        Map<Integer, Item> usedItems = new LinkedHashMap<>();
        try {
            for (Item i : rom.getItems()) {
                if (i != null) {
                    usedItems.put(i.getId(), i);
                }
            }
        } catch (RuntimeException e) {
            errors.add("Section \"items\" could not be read: " + e);
        }

        section(data, errors, "moves", () -> moves(rom, names));
        section(data, errors, "species", () -> species(rom, names));
        section(data, errors, "abilities", () -> abilities(rom, names));
        // Modified from FVX 11/10/2026: getTMMoves()/getHMMoves() can return null for a ROM whose
        // TM/HM data could not be located; report an empty section instead of letting section()
        // catch an NPE for what is actually just "nothing to show here".
        section(data, errors, "tms", () -> rom.getTMMoves() == null ? List.of() : new ArrayList<>(rom.getTMMoves()));
        section(data, errors, "hms", () -> rom.getHMMoves() == null ? List.of() : new ArrayList<>(rom.getHMMoves()));
        // Modified 12/10/2026: only games whose handler supports tutors (Sun and Moon read tutor data but cannot change it).
        section(data, errors, "tutors", () -> !rom.hasMoveTutors() || rom.getMoveTutorMoves() == null ? List.of() : new ArrayList<>(rom.getMoveTutorMoves()));
        section(data, errors, "starters", () -> speciesIds(rom.getStarters()));
        section(data, errors, "wild", () -> wild(rom, useTimeOfDay));
        section(data, errors, "statics", () -> statics(rom));
        section(data, errors, "trades", () -> trades(rom));
        section(data, errors, "trainers", () -> trainers(rom, usedItems));
        section(data, errors, "shops", () -> shops(rom, usedItems));
        section(data, errors, "fieldItems", () -> fieldItems(rom, usedItems));
        section(data, errors, "items", () -> items(usedItems, names));
        Map<String, Object> misses = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : names.unmatched().entrySet()) {
            misses.put(e.getKey(), e.getValue().size());
        }
        data.put("nameMisses", misses);
        return data;
    }

    private static void section(Map<String, Object> data, List<String> errors, String name, Supplier<Object> supplier) {
        try {
            Object value = supplier.get();
            if (value != null) {
                data.put(name, value);
            }
        } catch (RuntimeException | StackOverflowError e) {
            errors.add("Section \"" + name + "\" could not be read: " + e);
        }
    }

    // ---- sections ----

    private static List<Object> moves(RomHandler rom, GuideNames.Resolver names) {
        List<Object> out = new ArrayList<>();
        for (Move m : rom.getMoves()) {
            if (m == null) {
                out.add(null);
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.number);
            o.put("name", m.name);
            String[] n = names.move(m.number, m.name);
            o.put("ne", n[0]);
            o.put("ns", n[1]);
            o.put("type", m.type == null ? null : m.type.toString());
            o.put("power", m.power);
            o.put("acc", m.hitratio);
            o.put("pp", m.pp);
            o.put("cat", m.category == null ? null : m.category.toString());
            out.add(o);
        }
        return out;
    }

    /** Every ability the game has, by id; the species list refers to them by id so the page can name them in either language. */
    private static List<Object> abilities(RomHandler rom, GuideNames.Resolver names) {
        List<Object> out = new ArrayList<>();
        if (rom.abilitiesPerSpecies() <= 0) {
            return out;
        }
        for (int id = 1; id <= rom.highestAbilityIndex(); id++) {
            String name = rom.abilityName(id);
            if (name == null || name.isBlank()) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", id);
            o.put("name", name);
            String[] n = names.ability(id, name);
            o.put("ne", n[0]);
            o.put("ns", n[1]);
            out.add(o);
        }
        return out;
    }

    private static List<Object> items(Map<Integer, Item> used, GuideNames.Resolver names) {
        List<Object> out = new ArrayList<>();
        for (Item item : used.values()) {
            String name = item.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", item.getId());
            o.put("name", name);
            String[] n = names.item(item.getId(), name);
            o.put("ne", n[0]);
            o.put("ns", n[1]);
            out.add(o);
        }
        return out;
    }

    private static List<Object> species(RomHandler rom, GuideNames.Resolver names) {
        Map<Integer, List<MoveLearnt>> learnsets = safe(rom::getMovesLearnt);
        Map<Integer, List<Integer>> eggMoves = safe(rom::getEggMoves);
        Map<Species, boolean[]> tmhm = safe(rom::getTMHMCompatibility);
        Map<Species, boolean[]> tutors = rom.hasMoveTutors() ? safe(rom::getMoveTutorCompatibility) : null;
        int tmCount = safeInt(rom::getTMCount);
        int hmCount = safeInt(rom::getHMCount);

        List<Object> out = new ArrayList<>();
        for (Species sp : rom.getSpeciesInclFormes()) {
            if (sp == null) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", sp.getNumber());
            o.put("name", sp.getFullName());
            String[] n = names.species(sp.getBaseNumber(), sp.getName(), sp.getFormeSuffix());
            o.put("ne", n[0]);
            o.put("ns", n[1]);
            List<String> types = new ArrayList<>();
            Type primary = sp.getPrimaryType(false);
            Type secondary = sp.getSecondaryType(false);
            if (primary != null) {
                types.add(primary.toString());
            }
            if (secondary != null) {
                types.add(secondary.toString());
            }
            o.put("types", types);
            BaseStats bs = sp.getBaseStats();
            if (bs != null) {
                o.put("stats", new int[]{bs.getHp(), bs.getAttack(), bs.getDefense(), bs.getSpatk(), bs.getSpdef(), bs.getSpeed()});
            }
            // Modified for YAO (12/10/2026): abilities are written as ids (the "abilities" section has both names),
            // and a game that stores the same ability in two slots (Shedinja's Wonder Guard in Gen 5 and later)
            // lists it once, the way the log does.
            List<Integer> abilities = new ArrayList<>();
            for (int a : new int[]{sp.getAbility1(), sp.getAbility2(), sp.getAbility3()}) {
                if (a > 0 && !abilities.contains(a)) {
                    abilities.add(a);
                }
            }
            o.put("abilities", abilities);

            List<Object> evos = new ArrayList<>();
            for (Evolution e : sp.getEvolutionsFrom()) {
                Map<String, Object> eo = new LinkedHashMap<>();
                eo.put("to", e.getTo().getNumber());
                eo.put("type", e.getType() == null ? null : e.getType().toString());
                eo.put("extra", e.getExtraInfo());
                // Added for YAO (12/10/2026): what the extra value refers to (an item, move or Pokemon id, or a plain
                // number such as the level), so the page can name it in the language it is showing.
                EvolutionType et = e.getType();
                if (et != null && et.usesItem()) {
                    eo.put("xk", "item");
                } else if (et != null && et.usesMove()) {
                    eo.put("xk", "move");
                } else if (et != null && et.usesSpecies()) {
                    eo.put("xk", "species");
                }
                eo.put("x", String.valueOf(e.getExtraInfo()));
                evos.add(eo);
            }
            o.put("evos", evos);

            if (learnsets != null && learnsets.get(sp.getNumber()) != null) {
                List<int[]> learn = new ArrayList<>();
                for (MoveLearnt ml : learnsets.get(sp.getNumber())) {
                    learn.add(new int[]{ml.level, ml.move});
                }
                o.put("learn", learn);
            }
            if (eggMoves != null && eggMoves.get(sp.getNumber()) != null) {
                o.put("egg", new ArrayList<>(eggMoves.get(sp.getNumber())));
            }
            if (tmhm != null && tmhm.get(sp) != null) {
                boolean[] c = tmhm.get(sp);
                List<Integer> tm = new ArrayList<>();
                List<Integer> hm = new ArrayList<>();
                for (int i = 1; i <= tmCount && i < c.length; i++) {
                    if (c[i]) {
                        tm.add(i);
                    }
                }
                for (int i = tmCount + 1; i <= tmCount + hmCount && i < c.length; i++) {
                    if (c[i]) {
                        hm.add(i - tmCount);
                    }
                }
                o.put("tm", tm);
                o.put("hm", hm);
            }
            if (tutors != null && tutors.get(sp) != null) {
                boolean[] c = tutors.get(sp);
                List<Integer> t = new ArrayList<>();
                for (int i = 1; i < c.length; i++) {
                    if (c[i]) {
                        t.add(i);
                    }
                }
                o.put("tut", t);
            }
            out.add(o);
        }
        return out;
    }

    private static List<Integer> speciesIds(List<Species> list) {
        List<Integer> ids = new ArrayList<>();
        for (Species s : list) {
            ids.add(s == null ? 0 : s.getNumber());
        }
        return ids;
    }

    private static List<EncounterArea> sortedEncounters(RomHandler rom, boolean useTimeOfDay) {
        try {
            return rom.getSortedEncounters(useTimeOfDay);
        } catch (RuntimeException first) {
            try {
                return rom.getSortedEncounters(!useTimeOfDay);
            } catch (RuntimeException second) {
                throw first;
            }
        }
    }

    /** Modified for YAO (12/10/2026): one entry per place with one row per Pokemon per table, see {@link GuideWild}. */
    private static List<Object> wild(RomHandler rom, boolean useTimeOfDay) {
        return GuideWild.group(rom, sortedEncounters(rom, useTimeOfDay));
    }

    /**
     * Static, gift and egg Pokemon. No game handler exposes where an event happens (the encounter data has only the
     * species, level and flags), so {@code loc} comes from {@link GuideLocations}: {en, es, known} where known is
     * false for a fallback sentence (modified 13/10/2026, it used to be null for every entry).
     */
    private static List<Object> statics(RomHandler rom) {
        if (!rom.canChangeStaticPokemon()) {
            return null;
        }
        List<Object> out = new ArrayList<>();
        for (StaticEncounter se : rom.getStaticPokemon()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("species", se.getSpecies().getNumber());
            o.put("level", se.getLevel());
            o.put("egg", se.isEgg());
            o.put("loc", null);
            out.add(o);
        }
        return out;
    }

    /**
     * Added for YAO (13/10/2026): fills in {@code loc} of the "statics" and "trades" sections of {@code data} (read
     * from the original game or from the randomized one) with the places of what the ORIGINAL game held at each
     * position, so a randomized game shows where each event is, not where its new species would be. Done after both
     * games were read, so it cannot disturb the order in which they are read. A section that cannot be located keeps
     * {@code loc} null and the page shows its generic text.
     */
    @SuppressWarnings("unchecked")
    public static void applyLocations(Map<String, Object> data, RomHandler original) {
        applyPlaces(data.get("statics"), placesOf(() -> GuideLocations.get().forStatics(original)));
        applyPlaces(data.get("trades"), placesOf(() -> GuideLocations.get().forTrades(original)));
    }

    @SuppressWarnings("unchecked")
    private static void applyPlaces(Object section, List<GuideLocations.Found> places) {
        if (!(section instanceof List<?> rows) || places == null) {
            return;
        }
        for (int i = 0; i < rows.size() && i < places.size(); i++) {
            if (rows.get(i) instanceof Map<?, ?> row) {
                ((Map<String, Object>) row).put("loc", locOf(places, i));
            }
        }
    }

    /** The places for one section, or null when they could not be worked out (the page then shows its generic text). */
    private static List<GuideLocations.Found> placesOf(Supplier<List<GuideLocations.Found>> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Object locOf(List<GuideLocations.Found> places, int index) {
        if (places == null || index >= places.size()) {
            return null;
        }
        GuideLocations.Found f = places.get(index);
        Map<String, Object> loc = new LinkedHashMap<>();
        loc.put("en", f.en);
        loc.put("es", f.es);
        loc.put("known", f.known);
        return loc;
    }

    /**
     * In-game trades. {@code ot} is the trainer name stored with the traded Pokemon; no handler exposes the place,
     * so {@code loc} comes from {@link GuideLocations} like the static Pokemon's (modified 13/10/2026).
     */
    private static List<Object> trades(RomHandler rom) {
        List<InGameTrade> trades = rom.getInGameTrades();
        if (trades == null) {
            return null;
        }
        List<Object> out = new ArrayList<>();
        for (InGameTrade t : trades) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("give", t.getGivenSpecies().getNumber());
            o.put("want", t.getRequestedSpecies() == null ? null : t.getRequestedSpecies().getNumber());
            o.put("nick", t.getNickname());
            o.put("ot", t.getOtName());
            o.put("loc", null);
            out.add(o);
        }
        return out;
    }

    private static final Pattern GYM_TAG = Pattern.compile("^(?:GYM|GIO)(\\d*)");
    private static final Pattern LEAGUE_TAG = Pattern.compile("^(ELITE|KAHUNA|CHAMPION|UBER)(\\d*)");

    private static List<Object> trainers(RomHandler rom, Map<Integer, Item> usedItems) {
        List<Trainer> trainers = rom.getTrainers();
        if (trainers == null) {
            return null;
        }
        int gen = rom.generationOfPokemon();
        int romType = rom.getROMType();
        List<Object> out = new ArrayList<>();
        for (Trainer t : trainers) {
            if (t == null) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", t.getFullDisplayName());
            o.put("tag", t.getTag());
            o.put("boss", t.isBoss());
            // Added for YAO (12/10/2026): the boss tier (same classification the randomizer uses), its number inside
            // the tier, and the highest level, which the page uses to put trainers in game order.
            com.uprfvx.random.yao.TrainerGroups.Group tier = null;
            try {
                tier = com.uprfvx.random.yao.TrainerGroups.tierOf(t, gen, romType);
            } catch (RuntimeException ignored) {
                // a game whose trainers carry no tags simply has no tier
            }
            String tierKey = tier == null ? "" : switch (tier) {
                case GYM_LEADERS -> "gym";
                case LEAGUE -> "league";
                case EVIL_BOSSES -> "evil";
                default -> "boss";
            };
            o.put("tier", tierKey);
            int rank = 0;
            String tag = t.getTag() == null ? "" : t.getTag();
            if (tierKey.equals("gym")) {
                Matcher m = GYM_TAG.matcher(tag);
                rank = m.find() && !m.group(1).isEmpty() ? Integer.parseInt(m.group(1)) : 99;
            } else if (tierKey.equals("league")) {
                Matcher m = LEAGUE_TAG.matcher(tag);
                if (m.find()) {
                    rank = m.group(1).equals("CHAMPION") ? 99 : m.group(2).isEmpty() ? 50 : Integer.parseInt(m.group(2));
                } else {
                    rank = 50;
                }
            }
            o.put("rank", rank);
            o.put("lv", t.getHighestLevel());
            List<Object> party = new ArrayList<>();
            for (TrainerPokemon tp : t.getPokemon()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("species", tp.getSpecies().getNumber());
                p.put("level", tp.getLevel());
                p.put("moves", tp.getMoves());
                p.put("item", itemId(tp.getHeldItem(), usedItems));
                party.add(p);
            }
            o.put("party", party);
            out.add(o);
        }
        return out;
    }

    /** The item's id (or null for none), making sure the "items" section will describe it. */
    private static Integer itemId(Item item, Map<Integer, Item> usedItems) {
        if (item == null) {
            return null;
        }
        usedItems.putIfAbsent(item.getId(), item);
        return item.getId();
    }

    private static List<Object> shops(RomHandler rom, Map<Integer, Item> usedItems) {
        if (!rom.hasShopSupport()) {
            return null;
        }
        List<Object> out = new ArrayList<>();
        for (Shop s : rom.getShops()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", s.getName());
            o.put("main", com.uprfvx.random.randomizers.ItemRandomizer.isRegularMart(s)); // true = regular Poke Mart (changed 12/10/2026, was isMainGame)
            List<Integer> items = new ArrayList<>();
            for (Item i : s.getItems()) {
                items.add(itemId(i, usedItems));
            }
            o.put("items", items);
            out.add(o);
        }
        return out;
    }

    private static List<Object> fieldItems(RomHandler rom, Map<Integer, Item> usedItems) {
        List<Item> items = rom.getFieldItems();
        if (items == null) {
            return null;
        }
        List<Object> out = new ArrayList<>();
        for (Item i : items) {
            out.add(itemId(i, usedItems));
        }
        return out;
    }

    // ---- helpers: optional data that some games lack ----

    private static <T> T safe(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int safeInt(Supplier<Integer> supplier) {
        try {
            Integer v = supplier.get();
            return v == null ? 0 : v;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
