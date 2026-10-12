package com.uprfvx.random.yao.guide;

/*----------------------------------------------------------------------------*/
/*--  GuideNames.java - English and Spanish game names for the HTML guide.  --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.random.web.Json;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/**
 * Added for YAO (12/10/2026). The guide can be read in English or Spanish whatever language the ROM is in, so every
 * game term (Pokemon, moves, abilities, items, places) needs both names. They come from {@code guide-names.json},
 * a bundled table built from PokeAPI's open CSV data (species, moves and abilities by their national id, items by
 * the game's own item id per generation, places by the location tags this program already uses).
 *
 * <p>Rules, in order, for one name in a target language:
 * <ol>
 *   <li>If the ROM itself is in the target language, the ROM's own text is used (it is what that game really
 *       calls the thing, including its abbreviations). Text the game stores in capitals is turned into normal
 *       capitalization, and if the bundled table has the same name once accents, case and punctuation are ignored
 *       the table spelling is used.</li>
 *   <li>Otherwise the table entry for the id is used.</li>
 *   <li>If the table has no entry, the ROM's name is used and the miss is counted, never a blank or a crash.</li>
 * </ol>
 */
public final class GuideNames {

    static final String RESOURCE = "/com/uprfvx/random/yao/guide/guide-names.json";
    private static GuideNames instance;

    private final Map<String, String[]> species = new HashMap<>();
    private final Map<String, String[]> moves = new HashMap<>();
    private final Map<String, String[]> abilities = new HashMap<>();
    private final List<String[]> itemPool = new ArrayList<>();
    private final Map<String, String[]> itemPoolByNorm = new HashMap<>();
    private final Map<String, Map<String, String[]>> itemsByGen = new HashMap<>();
    private final Map<String, String[]> places = new LinkedHashMap<>();
    private final Map<String, String> formes = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    private GuideNames() throws IOException {
        Map<String, Object> data;
        try (InputStream in = GuideNames.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("The guide names are missing from the program.");
            }
            data = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        readPairs((Map<String, Object>) data.get("species"), species);
        readPairs((Map<String, Object>) data.get("moves"), moves);
        readPairs((Map<String, Object>) data.get("abilities"), abilities);
        readPairs((Map<String, Object>) data.get("places"), places);
        for (Object o : (List<Object>) data.get("itemPool")) {
            List<Object> p = (List<Object>) o;
            String[] pair = {String.valueOf(p.get(0)), String.valueOf(p.get(1))};
            itemPool.add(pair);
            itemPoolByNorm.putIfAbsent(norm(pair[0]), pair);
            itemPoolByNorm.putIfAbsent(norm(pair[1]), pair);
        }
        Map<String, Object> gens = (Map<String, Object>) data.get("itemsByGen");
        for (Map.Entry<String, Object> g : gens.entrySet()) {
            Map<String, String[]> table = new HashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) g.getValue()).entrySet()) {
                if (e.getValue() instanceof Number n) {
                    table.put(e.getKey(), itemPool.get(n.intValue()));
                } else {
                    List<Object> p = (List<Object>) e.getValue();
                    table.put(e.getKey(), new String[]{String.valueOf(p.get(0)), String.valueOf(p.get(1))});
                }
            }
            itemsByGen.put(g.getKey(), table);
        }
        for (Map.Entry<String, Object> e : ((Map<String, Object>) data.get("formes")).entrySet()) {
            formes.put(e.getKey(), String.valueOf(e.getValue()));
        }
    }

    @SuppressWarnings("unchecked")
    private static void readPairs(Map<String, Object> source, Map<String, String[]> into) {
        for (Map.Entry<String, Object> e : source.entrySet()) {
            List<Object> p = (List<Object>) e.getValue();
            into.put(e.getKey(), new String[]{String.valueOf(p.get(0)), String.valueOf(p.get(1))});
        }
    }

    public static synchronized GuideNames get() {
        if (instance == null) {
            try {
                instance = new GuideNames();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return instance;
    }

    // ---- accessors for tests and the writer ----

    public Map<String, String[]> speciesTable() {
        return species;
    }

    public Map<String, String[]> moveTable() {
        return moves;
    }

    public Map<String, String[]> abilityTable() {
        return abilities;
    }

    public Map<String, Map<String, String[]>> itemTables() {
        return itemsByGen;
    }

    public Map<String, String[]> placeTable() {
        return places;
    }

    public Map<String, String> formeTable() {
        return formes;
    }

    /** The two names of a place tag, or null when the table does not know it. */
    public String[] place(String tag) {
        return tag == null ? null : places.get(tag);
    }

    // ---- text helpers ----

    /** Lower case, no accents, only letters and digits: the way names are compared. */
    public static String norm(String s) {
        if (s == null) {
            return "";
        }
        String d = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            char l = Character.toLowerCase(c);
            if ((l >= 'a' && l <= 'z') || (l >= '0' && l <= '9')) {
                sb.append(l);
            }
        }
        return sb.toString();
    }

    private static final Set<String> SMALL_WORDS = Set.of("de", "del", "la", "las", "los", "el", "y", "en", "al", "of", "the");

    /**
     * Text a game stores in capitals ("MR. MIME", "V. CORTANTE") gets normal capitalization ("Mr. Mime", "V. Cortante");
     * other text is kept. Words of one or two letters ("PP", "HM") and words with digits ("TM01") stay in capitals, and
     * short Spanish and English joining words ("DE", "OF") go to lower case after the first word.
     */
    public static String fixShouting(String s) {
        if (s == null) {
            return "";
        }
        boolean letters = false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                letters = true;
                break;
            }
        }
        if (!letters || !s.equals(s.toUpperCase(Locale.ROOT))) {
            return s;
        }
        StringBuilder out = new StringBuilder();
        StringBuilder word = new StringBuilder();
        boolean first = true;
        char before = ' ';
        for (int i = 0; i <= s.length(); i++) {
            char c = i < s.length() ? s.charAt(i) : ' ';
            boolean sep = i == s.length() || c == ' ' || c == '-' || c == '/' || c == '(';
            if (!sep) {
                word.append(c);
                continue;
            }
            // the parts of a hyphenated name ("HO-OH") are capitalized, not kept in capitals as short words are
            out.append(fixWord(word.toString(), first, before == '-' || c == '-'));
            if (word.length() > 0) {
                first = false;
            }
            word.setLength(0);
            before = c;
            if (i < s.length()) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static final Set<String> TITLES = Set.of("MR.", "MS.", "MT.", "DR.", "JR.", "ST.");

    private static String fixWord(String w, boolean first, boolean hyphenated) {
        if (w.isEmpty()) {
            return w;
        }
        int letterCount = 0;
        boolean digits = false;
        for (int i = 0; i < w.length(); i++) {
            if (Character.isLetter(w.charAt(i))) {
                letterCount++;
            } else if (Character.isDigit(w.charAt(i))) {
                digits = true;
            }
        }
        String lower = w.toLowerCase(Locale.ROOT);
        if (!first && SMALL_WORDS.contains(lower)) {
            return lower;
        }
        if (TITLES.contains(w)) {
            return w.charAt(0) + w.substring(1).toLowerCase(Locale.ROOT);
        }
        if (digits || (letterCount <= 2 && !hyphenated)) {
            return w;
        }
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            sb.append(up && Character.isLetter(c) ? c : Character.toLowerCase(c));
            up = c == '.';
        }
        return sb.toString();
    }

    /** "en" or "es" for a ROM in that language, "other" for any other language (kept out of both). */
    public static String romLang(RomHandler rom) {
        if (rom.isEnglish()) {
            return "en";
        }
        String code = rom.getROMCode();
        if (code != null && code.endsWith("S")) {
            return "es";
        }
        // the 3DS handlers only tell English from "not English"; the program supports English and Spanish only
        return code != null && code.startsWith("CTR") ? "es" : "other";
    }

    // ---- per ROM resolver ----

    /** Resolves names for one loaded ROM and counts what the table could not match. */
    public static final class Resolver {
        private final GuideNames names = GuideNames.get();
        private final String romLang;
        private final int gen;
        private final Map<String, Set<String>> unmatched = new LinkedHashMap<>();
        private final Map<String, Integer> total = new LinkedHashMap<>();
        private final Map<String, Integer> romDiffers = new LinkedHashMap<>();
        private final Map<String, Set<String>> romDiffList = new LinkedHashMap<>();

        public Resolver(RomHandler rom) {
            this.romLang = GuideNames.romLang(rom);
            this.gen = rom.generationOfPokemon();
        }

        public String romLang() {
            return romLang;
        }

        public int generation() {
            return gen;
        }

        public Map<String, Set<String>> unmatched() {
            return unmatched;
        }

        public Map<String, Integer> totals() {
            return total;
        }

        /** Names where the ROM, in its own language, spells the thing differently from the table. */
        public Map<String, Set<String>> romDifferences() {
            return romDiffList;
        }

        private void miss(String category, String what) {
            unmatched.computeIfAbsent(category, k -> new LinkedHashSet<>()).add(what);
        }

        private void count(String category) {
            total.merge(category, 1, Integer::sum);
        }

        /** Both names for an entry whose table pair is {@code entry} (may be null) and whose ROM text is {@code romName}. */
        private String[] pick(String category, String[] entry, String romName) {
            String fixed = fixShouting(romName == null ? "" : romName.strip());
            // text with unconverted control codes ("TUBO [PO][Ke]...") is no use to a reader: the table name is used
            boolean garbled = fixed.contains("[") && fixed.contains("]");
            String[] out = new String[2];
            for (int l = 0; l < 2; l++) {
                String target = l == 0 ? "en" : "es";
                if (target.equals(romLang) && !fixed.isEmpty() && !(garbled && entry != null)) {
                    String tableName = entry == null ? null : entry[l];
                    if (tableName != null && norm(tableName).equals(norm(romName)) && !norm(romName).isEmpty()) {
                        out[l] = tableName;
                    } else {
                        out[l] = fixed;
                        if (tableName != null && !similar(tableName, romName)) {
                            romDiffList.computeIfAbsent(category, k -> new LinkedHashSet<>())
                                    .add(fixed + " | " + tableName);
                        }
                    }
                } else if (entry != null) {
                    out[l] = entry[l];
                } else {
                    out[l] = fixed;
                }
            }
            if (entry == null) {
                miss(category, fixed);
            }
            return out;
        }

        /** Abbreviations such as "Picotazo Ven" for "Picotazo Veneno" count as the same name. */
        private static boolean similar(String table, String rom) {
            String a = norm(table), b = norm(rom);
            if (a.equals(b)) {
                return true;
            }
            if (b.length() >= 3 && a.startsWith(b)) {
                return true;
            }
            // abbreviated words: every word of the ROM text starts a word of the table text
            String[] wa = table.toLowerCase(Locale.ROOT).split("[\\s.\\-/]+");
            String[] wb = rom.toLowerCase(Locale.ROOT).split("[\\s.\\-/]+");
            int ai = 0;
            for (String w : wb) {
                String nw = norm(w);
                if (nw.isEmpty()) {
                    continue;
                }
                boolean found = false;
                while (ai < wa.length) {
                    if (norm(wa[ai++]).startsWith(nw)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        /** National number based names; {@code suffix} is the program's forme suffix such as "-Attack" (or empty). */
        public String[] species(int baseNumber, String romBaseName, String suffix) {
            count("species");
            String[] entry = names.species.get(String.valueOf(baseNumber));
            String[] base = pick("species", entry, romBaseName);
            if (suffix == null || suffix.isEmpty()) {
                return base;
            }
            String pattern = names.formes.get(suffix);
            if (pattern == null) {
                miss("forme", suffix);
                return new String[]{base[0] + suffix, base[1] + suffix};
            }
            return new String[]{base[0] + suffix, pattern.replace("{b}", base[1])};
        }

        public String[] move(int id, String romName) {
            count("moves");
            return pick("moves", names.moves.get(String.valueOf(id)), romName);
        }

        public String[] ability(int id, String romName) {
            count("abilities");
            if (gen == 3) {
                // Generation 3 numbers its abilities differently from the table (national numbers of Gen 4 onwards): it has
                // the unused Cacophony at 76 and Air Lock at 77, where Gen 4 onwards has Air Lock at 76
                if (id == 76) {
                    return pick("abilities", new String[]{"Cacophony", "Cacofon\u00eda"}, romName);
                }
                if (id == 77) {
                    return pick("abilities", names.abilities.get("76"), romName);
                }
            }
            return pick("abilities", names.abilities.get(String.valueOf(id)), romName);
        }

        /**
         * Items have the game's own ids, so the table is per generation, built from the English games of that
         * generation (the item ids are the same in every language of a game). Gen 1 has no table and is matched by name.
         */
        public String[] item(int id, String romName) {
            count("items");
            Map<String, String[]> table = names.itemsByGen.get(String.valueOf(gen));
            String[] entry = table == null ? null : table.get(String.valueOf(id));
            if (entry == null) {
                // no table for this generation (Gen 1) or an id it does not know: try the name itself
                entry = names.itemPoolByNorm.get(norm(romName));
            }
            return pick("items", entry, romName);
        }
    }
}
