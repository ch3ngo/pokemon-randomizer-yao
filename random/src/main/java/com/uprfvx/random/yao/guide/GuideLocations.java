package com.uprfvx.random.yao.guide;

/*----------------------------------------------------------------------------*/
/*--  GuideLocations.java - where static, gift and traded Pokemon are found.  --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.InGameTrade;
import com.uprfvx.romio.gamedata.StaticEncounter;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Added for YAO (13/10/2026). No game handler reads where a static, gift or egg Pokemon is given, nor where an
 * in-game trade happens (the encounter data holds the species, level and flags, the trade data holds no map), so the
 * guide used to say "location not available". This class answers from a maintained table
 * ({@code locations.txt}, one section per game family, English and Spanish), matching the ORIGINAL game's own list
 * by species and level. It never matches by position: the Spanish dumps have shorter lists than the English ones, and
 * the order differs between versions, while a species and a level are the same everywhere.
 *
 * <p>Place names come from the guide's own place table ({@link GuideNames#place}) so a place has the same name here as
 * in the wild encounter tables, plus a few extra names of the file's own ({@code P} lines). Where the table has no
 * entry the result is a plain fallback ({@link Found#known} is false) so the page can say it honestly instead of
 * inventing a place.
 */
public final class GuideLocations {

    static final String RESOURCE = "/com/uprfvx/random/yao/guide/locations.txt";
    private static final String SPLIT = " || ";

    /** One answer: English and Spanish text, and whether it came from the table (false for a fallback). */
    public static final class Found {
        public final String en;
        public final String es;
        public final boolean known;

        Found(String en, String es, boolean known) {
            this.en = en;
            this.es = es;
            this.known = known;
        }

        public String text(String lang) {
            return "es".equals(lang) ? es : en;
        }
    }

    /** A data line before its species is looked up. */
    private static final class Entry {
        final int species;
        final int level; // -1 for any
        final int wanted; // trades only: -1 for "any Pokemon"
        final String en;
        final String es;
        final int line;

        Entry(int species, int level, int wanted, String en, String es, int line) {
            this.species = species;
            this.level = level;
            this.wanted = wanted;
            this.en = en;
            this.es = es;
            this.line = line;
        }
    }

    private static final class Family {
        final List<Entry> statics = new ArrayList<>();
        final List<Entry> trades = new ArrayList<>();
    }

    private static GuideLocations instance;

    private final Map<String, Family> families = new LinkedHashMap<>();
    private final Map<String, String[]> extraPlaces = new HashMap<>();
    /** Tags and species names the file uses that could not be resolved; the test requires this to be empty. */
    private final List<String> problems = new ArrayList<>();

    private GuideLocations() throws IOException {
        List<String> lines;
        try (InputStream in = GuideLocations.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("The location table is missing from the program.");
            }
            lines = List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
        }
        Map<String, Integer> speciesByName = speciesByName();
        // places first so the order of lines in the file does not matter
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("P|")) {
                String[] p = line.split("\\|", 4);
                if (p.length == 4) {
                    extraPlaces.put(p[1], new String[]{p[2], p[3]});
                }
            }
        }
        Family current = null;
        int number = 0;
        for (String raw : lines) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("P|")) {
                continue;
            }
            if (line.startsWith("@family ")) {
                current = new Family();
                families.put(line.substring(8).strip(), current);
                continue;
            }
            String[] f = line.split("\\|", 4);
            if (current == null || f.length != 4) {
                problems.add("line " + number + ": cannot read \"" + line + "\"");
                continue;
            }
            String[] text = texts(f[3], number);
            if (f[0].equals("S")) {
                int species = species(f[1], speciesByName, number);
                int level = f[2].equals("*") ? -1 : Integer.parseInt(f[2].strip());
                current.statics.add(new Entry(species, level, -1, text[0], text[1], number));
            } else if (f[0].equals("T")) {
                int wanted = f[1].equals("*") ? -1 : species(f[1], speciesByName, number);
                int given = species(f[2], speciesByName, number);
                current.trades.add(new Entry(given, -1, wanted, text[0], text[1], number));
            } else {
                problems.add("line " + number + ": unknown kind \"" + f[0] + "\"");
            }
        }
    }

    public static synchronized GuideLocations get() {
        if (instance == null) {
            try {
                instance = new GuideLocations();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return instance;
    }

    /** Everything wrong with the data file (unknown place tags, species names that match nothing). */
    public List<String> problems() {
        return problems;
    }

    public boolean hasFamily(String family) {
        return families.containsKey(family);
    }

    public java.util.Set<String> familyKeys() {
        return families.keySet();
    }

    // ---------------------------------------------------------------- reading the file

    private static Map<String, Integer> speciesByName() {
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, String[]> e : GuideNames.get().speciesTable().entrySet()) {
            try {
                out.putIfAbsent(GuideNames.norm(e.getValue()[0]), Integer.parseInt(e.getKey()));
            } catch (NumberFormatException ignored) {
                // a non numeric key is not a species
            }
        }
        return out;
    }

    private int species(String token, Map<String, Integer> byName, int line) {
        String t = token.strip();
        if (t.startsWith("#")) {
            return Integer.parseInt(t.substring(1));
        }
        Integer n = byName.get(GuideNames.norm(t));
        if (n == null) {
            problems.add("line " + line + ": unknown species \"" + t + "\"");
            return -2;
        }
        return n;
    }

    /** Splits "english || spanish" and resolves {TAGS} in each part to that language's name. */
    private String[] texts(String template, int line) {
        String english = template;
        String spanish = template;
        int split = template.indexOf(SPLIT);
        if (split >= 0) {
            english = template.substring(0, split);
            spanish = template.substring(split + SPLIT.length());
        }
        return new String[]{resolve(english, 0, line), resolve(spanish, 1, line)};
    }

    private String resolve(String template, int lang, int line) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i);
                if (end < 0) {
                    problems.add("line " + line + ": unclosed { in \"" + template + "\"");
                    out.append(template.substring(i));
                    break;
                }
                String tag = template.substring(i + 1, end);
                String[] names = extraPlaces.get(tag);
                if (names == null) {
                    names = GuideNames.get().place(tag);
                }
                if (names == null) {
                    problems.add("line " + line + ": unknown place \"" + tag + "\"");
                    out.append(tag);
                } else {
                    out.append(names[lang]);
                }
                i = end + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- game families

    /**
     * The key of the section of the table for a game, from its name, or null for a game without one. Public so the
     * tests can check every game name this program shows.
     */
    public static String familyOf(String romName) {
        if (romName == null) {
            return null;
        }
        String n = romName.toLowerCase(Locale.ROOT).replaceAll("^pok[eé]mon\\s+", "").strip();
        if (n.startsWith("heartgold") || n.startsWith("soulsilver")) {
            return "hgss";
        }
        if (n.startsWith("platinum")) {
            return "platinum";
        }
        if (n.startsWith("diamond") || n.startsWith("pearl")) {
            return "dp";
        }
        if (n.startsWith("black 2") || n.startsWith("white 2")) {
            return "bw2";
        }
        if (n.startsWith("black") || n.startsWith("white")) {
            return "bw";
        }
        if (n.startsWith("fire red") || n.startsWith("firered") || n.startsWith("leaf green") || n.startsWith("leafgreen")) {
            return "frlg";
        }
        if (n.startsWith("emerald")) {
            return "emerald";
        }
        if (n.startsWith("crystal")) {
            return "crystal";
        }
        if (n.startsWith("omega ruby") || n.startsWith("alpha sapphire")) {
            return "oras";
        }
        if (n.startsWith("ultra sun") || n.startsWith("ultra moon")) {
            return "usum";
        }
        if (n.startsWith("sun") || n.startsWith("moon")) {
            return "sm";
        }
        if (n.equals("x") || n.startsWith("x ") || n.equals("y") || n.startsWith("y ")) {
            return "xy";
        }
        return null;
    }

    // ---------------------------------------------------------------- matching

    private static final Found STATIC_FALLBACK = new Found("A gift or fixed encounter (place not listed)",
            "Un regalo o encuentro fijo (lugar no indicado)", false);
    private static final Found EGG_FALLBACK = new Found("An egg given by someone (place not listed)",
            "Un huevo regalado (lugar no indicado)", false);
    private static final Found TRADE_FALLBACK = new Found("An in-game trade (place not listed)",
            "Un intercambio del juego (lugar no indicado)", false);

    /** True when the table has a section for this game. */
    public boolean covers(RomHandler original) {
        return families.containsKey(familyOf(original.getROMName()));
    }

    /**
     * A place for each static, gift and egg Pokemon of the ORIGINAL game, in the order of
     * {@code original.getStaticPokemon()}, or null when the game has no static Pokemon support.
     */
    public List<Found> forStatics(RomHandler original) {
        if (!original.canChangeStaticPokemon()) {
            return null;
        }
        return forStatics(original.getROMName(), original.getStaticPokemon());
    }

    /** The same from the original game's name and its static list (what the randomization log keeps). */
    public List<Found> forStatics(String romName, List<StaticEncounter> list) {
        Family family = families.get(familyOf(romName));
        List<Found> out = new ArrayList<>();
        boolean[] used = family == null ? new boolean[0] : new boolean[family.statics.size()];
        for (StaticEncounter se : list) {
            int idx = family == null ? -1 : matchStatic(family, used, se.getSpecies().getBaseForme().getNumber(), se.getLevel());
            if (idx >= 0) {
                used[idx] = true;
                Entry e = family.statics.get(idx);
                out.add(new Found(e.en, e.es, true));
            } else {
                out.add(se.isEgg() ? EGG_FALLBACK : STATIC_FALLBACK);
            }
        }
        return out;
    }

    private static int matchStatic(Family family, boolean[] used, int species, int level) {
        List<Entry> all = family.statics;
        int sameSpecies = 0;
        for (Entry e : all) {
            if (e.species == species) {
                sameSpecies++;
            }
        }
        // 1. the same level, not yet given to another encounter; 2. a table entry for any level, not yet used;
        // 3. the same level or any level again (the same place can hold several identical encounters)
        for (int pass = 0; pass < 4; pass++) {
            for (int i = 0; i < all.size(); i++) {
                Entry e = all.get(i);
                if (e.species != species) {
                    continue;
                }
                boolean levelOk = e.level == level;
                boolean anyLevel = e.level < 0;
                boolean free = !used[i];
                if (pass == 0 && levelOk && free || pass == 1 && anyLevel && free || pass == 2 && (levelOk || anyLevel)) {
                    return i;
                }
                // a different level is accepted only when this species has a single entry in the whole section
                // (a game version changes levels, for example Lugia 45 or 70), never to pick between several places
                if (pass == 3 && sameSpecies == 1) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * A place for each in-game trade of the ORIGINAL game, in the order of {@code original.getInGameTrades()}, or
     * null when the game has no trade support.
     */
    public List<Found> forTrades(RomHandler original) {
        List<InGameTrade> list = original.getInGameTrades();
        return list == null ? null : forTrades(original.getROMName(), list);
    }

    /** The same from the original game's name and its trade list. */
    public List<Found> forTrades(String romName, List<InGameTrade> list) {
        Family family = families.get(familyOf(romName));
        List<Found> out = new ArrayList<>();
        boolean[] used = family == null ? new boolean[0] : new boolean[family.trades.size()];
        for (InGameTrade t : list) {
            int given = t.getGivenSpecies().getBaseForme().getNumber();
            int wanted = t.getRequestedSpecies() == null ? -1 : t.getRequestedSpecies().getBaseForme().getNumber();
            int idx = family == null ? -1 : matchTrade(family, used, given, wanted);
            if (idx >= 0) {
                used[idx] = true;
                Entry e = family.trades.get(idx);
                out.add(new Found(e.en, e.es, true));
            } else {
                out.add(TRADE_FALLBACK);
            }
        }
        return out;
    }

    private static int matchTrade(Family family, boolean[] used, int given, int wanted) {
        List<Entry> all = family.trades;
        int sameGiven = 0;
        for (Entry e : all) {
            if (e.species == given) {
                sameGiven++;
            }
        }
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < all.size(); i++) {
                Entry e = all.get(i);
                if (e.species != given) {
                    continue;
                }
                boolean wantOk = e.wanted == wanted || e.wanted < 0;
                if (pass == 0 && wantOk && !used[i] || pass == 1 && wantOk || pass == 2 && sameGiven == 1) {
                    return i;
                }
            }
        }
        return -1;
    }
}
