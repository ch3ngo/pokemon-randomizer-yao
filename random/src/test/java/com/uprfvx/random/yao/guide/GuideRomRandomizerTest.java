package com.uprfvx.random.yao.guide;

import com.uprfvx.random.GameRandomizer;
import com.uprfvx.random.Settings;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romio.RomOpener;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Added for YAO (12/10/2026). ROM based check (runs with the {@code testROMs} task, skipped when the ROM is not in
 * {@code roms/}): randomizes every aspect the guide shows with the real randomizer, then collects the guide data
 * from the randomized handler exactly the way the CLI does, and asserts each section differs from the vanilla data.
 * This guards against the class of bug where the guide shows the original data in a column that should show the
 * randomized one (reported for abilities).
 */
public class GuideRomRandomizerTest {

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("com/uprfvx/random/gui/Bundle");

    private static RomHandler open(File f) {
        RomOpener opener = new RomOpener();
        opener.setExtraMemoryAvailable(Runtime.getRuntime().maxMemory() >= 3_800_000_000L);
        RomOpener.Results r = opener.openRomFile(f);
        assertTrue(r.wasOpeningSuccessful(), "could not open " + f);
        return r.getRomHandler();
    }

    private static String json(Object o) {
        return JsonWriter.toJson(o);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Emerald (U).gba", "FireRed (U).gba", "FireRed (S).gba", "SoulSilver (U).nds", "SoulSilver (S).nds",
            "Platinum (U).nds", "Platinum (S).nds", "Diamond (E).nds", "Diamond (S).nds", "Black (U).nds", "Black (S).nds",
            "Black 2 (U).nds", "Black 2 (S).nds", "Omega Ruby (E).3ds", "Crystal (U).gbc", "X (E).3ds", "Sun (E).3ds", "Ultra Sun (E).3ds"})
    public void guideDataShowsTheRandomizedGame(String romName) throws Exception {
        String romsPath = System.getProperty("romsPath");
        Assumptions.assumeTrue(romsPath != null, "no romsPath");
        File romFile = new File(romsPath, romName);
        Assumptions.assumeTrue(romFile.isFile(), romName + " is not in roms/");

        // Same order as the real run: randomize one handler, then open the original again for the guide. (Two
        // handlers on the same DS game open at once share a temporary folder and would see each other's changes.)
        RomHandler working = open(romFile);
        // a complete settings object, built the way the web interface builds one
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("baseStatisticsMod", "RANDOM");
        values.put("speciesTypesMod", "COMPLETELY_RANDOM");
        values.put("abilitiesMod", "RANDOMIZE");
        values.put("evolutionsMod", "RANDOM");
        values.put("startersMod", "COMPLETELY_RANDOM");
        values.put("movesetsMod", "COMPLETELY_RANDOM");
        values.put("trainersMod", "RANDOM");
        values.put("randomizeWildPokemon", true);
        // time of day and season tables, so the grouped wild data has morning/day/night and spring..winter variants
        values.put("useTimeBasedEncounters", true);
        values.put("staticPokemonMod", "COMPLETELY_RANDOM");
        values.put("tmsMod", "RANDOM");
        values.put("tmsHmsCompatibilityMod", "COMPLETELY_RANDOM");
        values.put("moveTutorMovesMod", "RANDOM");
        values.put("moveTutorsCompatibilityMod", "COMPLETELY_RANDOM");
        values.put("fieldItemsMod", "RANDOM");
        values.put("shopItemsMod", "RANDOM");
        // trade randomization reads custom names from a data/ folder in the working directory (the release layout)
        boolean canTrade = new File("data").isDirectory();
        if (canTrade) {
            values.put("inGameTradesMod", "RANDOMIZE_GIVEN_AND_REQUESTED");
        }
        Settings s = new com.uprfvx.random.web.SettingsSchema().build(values, -1, working.getROMName());
        // a few of this program's own extras, so the settings summary has something to show
        s.getYaoSettings().setBoostFloorBst(450);
        s.getYaoSettings().setFullyEvolvedByLevel(40);
        s.getYaoSettings().setEvolutionLevelScale(30, 20);
        s.getYaoSettings().addQuickStatEdit(25, 30);

        ByteArrayOutputStream logBytes = new ByteArrayOutputStream();
        boolean threeDs = romName.endsWith(".3ds");
        File out = threeDs ? Files.createTempDirectory("guide-rom-test").toFile()
                : Files.createTempFile("guide-rom-test", romName.substring(romName.lastIndexOf('.'))).toFile();
        if (!threeDs) {
            out.delete();
        }
        try {
            GameRandomizer randomizer = new GameRandomizer(s, null, working, BUNDLE, threeDs);
            GameRandomizer.Results results = randomizer.randomize(out.getAbsolutePath(),
                    new PrintStream(logBytes, false, java.nio.charset.StandardCharsets.UTF_8), 1234567L);
            assertTrue(results.wasSaveSuccessful(), () -> "randomization failed: " + results.getException());
            assertTrue(results.wasLogSuccessful(), () -> "the log could not be written: " + results.getLogException());
        } finally {
            deleteRecursively(out);
        }

        // collected from the same handler object the CLI hands to the guide writer, the way the CLI reads wild data
        GuideNames.Resolver randomizedNames = new GuideNames.Resolver(working);
        Map<String, Object> randomized = GuideDataCollector.collect(working, s.isUseTimeBasedEncounters(), randomizedNames);
        RomHandler originalHandler = open(romFile);
        GuideNames.Resolver originalNames = new GuideNames.Resolver(originalHandler);
        Map<String, Object> original = GuideDataCollector.collect(originalHandler, s.isUseTimeBasedEncounters(), originalNames);
        GuideOrder.apply(original, randomized, working);

        // the whole page can be produced, and is kept for a look in a browser
        String logText = logBytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertLogIsStructured(romName, logText, working.abilitiesPerSpecies() > 0);
        HtmlGuideWriter.Extras extras = new HtmlGuideWriter.Extras(logText,
                com.uprfvx.random.web.SettingsSummary.nativeChanges(s, working),
                com.uprfvx.random.web.SettingsSummary.extras(s.getYaoSettings(), working));
        String page = HtmlGuideWriter.render(originalHandler, working, 1234567L, s.getYaoSettings().getRunNotes(),
                s.isUseTimeBasedEncounters(), extras);
        assertFalse(page.contains("/*__GUIDE_DATA__*/") || page.contains("/*__GUIDE_STRINGS__*/"));
        File sampleDir = new File("build/guide-rom-test");
        sampleDir.mkdirs();
        String stem = romName.replaceAll("[^A-Za-z0-9]+", "_");
        Files.writeString(new File(sampleDir, stem + ".html").toPath(), page);
        Files.writeString(new File(sampleDir, stem + ".log").toPath(), logText);
        assertEquals(java.util.List.of(), randomized.get("errors"));

        // "shops" is not in this list: the regular Poke Marts are never randomized, so a game whose shops are all
        // regular marts would legitimately stay identical (checked separately by MainShopsUntouchedRandomizerTest).
        // Tutors: Sun and Moon have move tutors but the handler cannot change them (hasMoveTutors is false), so the
        // guide shows no tutor section there (checked 12/10/2026, this was not a randomization bug).
        for (String section : new String[]{"tms", "tutors", "starters", "wild", "statics", "trades", "trainers",
                "fieldItems"}) {
            Object vanilla = original.get(section);
            // nothing to randomize in this game (Diamond/Pearl and Black/White have no move tutors)
            if (vanilla == null || (vanilla instanceof java.util.Collection<?> c && c.isEmpty())
                    || (section.equals("trades") && !canTrade)) {
                continue;
            }
            assertNotEquals(json(original.get(section)), json(randomized.get(section)),
                    romName + ": guide section \"" + section + "\" is identical to the vanilla data");
        }
        assertNotNull(randomized.get("species"));
        assertLogAgreesWithGuide(romName, logText, randomized);
        assertNamesAreInBothLanguages(romName, "original", original, originalNames);
        assertNamesAreInBothLanguages(romName, "randomized", randomized, randomizedNames);
        assertWildIsGroupedAndOrdered(romName, randomized, working);
        assertOtherSectionsAreOrdered(romName, randomized);
        assertLogGroupsWildEncounters(romName, logText, randomized);
        for (String field : new String[]{"types", "stats", "evos", "learn", "tm"}) {
            assertNotEquals(speciesField(original, field), speciesField(randomized, field),
                    romName + ": guide species field \"" + field + "\" is identical to the vanilla data");
        }
        if (originalHandler.abilitiesPerSpecies() > 0) {
            assertNotEquals(speciesField(original, "abilities"), speciesField(randomized, "abilities"),
                    romName + ": guide species abilities are identical to the vanilla data");
        }
    }

    /** The text log has its labelled header, its table of contents and the summary and warnings sections near the top. */
    private static void assertLogIsStructured(String romName, String log, boolean hasAbilities) {
        assertTrue(log.contains("[ Log for Randomized Game ]"), romName);
        assertTrue(log.matches("(?s).* Date +: \\d{2}/\\d{2}/\\d{4}\\R.*"), romName + ": date must be DD/MM/YYYY");
        for (String label : new String[]{"Program", "Game", "Game language", "Random seed", "Settings string"}) {
            assertTrue(log.contains(" " + label), romName + ": header row " + label);
        }
        int toc = log.indexOf("{TABL}");
        int settings = log.indexOf("( Settings Summary {SETT} )");
        int warnings = log.indexOf("( Warnings and Notes {WARN} )");
        int overview = log.indexOf("( Overview of Randomization {OVRD} )");
        assertTrue(toc >= 0 && toc < settings && settings < warnings && warnings < overview, romName + ": section order");
        assertTrue(log.indexOf("Settings Summary---") >= 0 && log.indexOf("Warnings and Notes---") >= 0, romName + ": table of contents rows");
        // the sections written by this program carry no dash characters the project forbids (game text may)
        String ours = log.substring(settings, overview);
        assertFalse(ours.contains("\u2014"), romName + ": em dash in the summary or warnings");
        assertTrue(ours.contains("Extras of Pokemon Randomizer YAO in use"), romName + ": extras are summarized");
        if (hasAbilities) {
            assertTrue(ours.contains("Abilities = Random"), romName + ": abilities option is summarized");
        }
    }

    /**
     * The text log and the guide are written from the same randomized game, so what they say about every Pokemon
     * (types, base stat total, abilities) must be identical. This is the check that would have caught a guide
     * showing the original abilities, or a log that disagrees with it.
     */
    @SuppressWarnings("unchecked")
    private static void assertLogAgreesWithGuide(String romName, String logText, Map<String, Object> randomized) {
        java.util.List<String> lines = java.util.Arrays.asList(logText.replace("\r", "").split("\n"));
        int header = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("NUM|")) {
                header = i;
                break;
            }
        }
        assertTrue(header >= 0, romName + ": the log has no Pokemon table");
        String[] cols = lines.get(header).split("\\|", -1);
        java.util.Map<String, Integer> at = new java.util.HashMap<>();
        for (int i = 0; i < cols.length; i++) {
            at.put(cols[i].trim(), i);
        }
        java.util.Map<String, String[]> rows = new java.util.HashMap<>();
        for (int i = header + 1; i < lines.size() && !lines.get(i).isBlank(); i++) {
            String[] cells = lines.get(i).split("\\|", -1);
            if (cells.length >= cols.length - 1) {
                rows.put(cells[0].trim() + "|" + cells[at.get("NAME")].trim().toLowerCase(java.util.Locale.ROOT), cells);
            }
        }
        java.util.Map<Integer, String> abilityRomNames = new java.util.HashMap<>();
        if (randomized.get("abilities") != null) {
            for (Object o : (java.util.List<Object>) randomized.get("abilities")) {
                Map<String, Object> a = (Map<String, Object>) o;
                abilityRomNames.put(((Number) a.get("id")).intValue(), String.valueOf(a.get("name")));
            }
        }
        int compared = 0;
        for (Object o : (java.util.List<Object>) randomized.get("species")) {
            Map<String, Object> sp = (Map<String, Object>) o;
            String[] cells = rows.get(sp.get("id") + "|" + String.valueOf(sp.get("name")).toLowerCase(java.util.Locale.ROOT));
            if (cells == null) {
                continue;
            }
            compared++;
            java.util.List<String> types = (java.util.List<String>) sp.get("types");
            assertEquals(String.join("/", types), cells[at.get("TYPE")].trim(), romName + ": types of " + sp.get("name"));
            // Shedinja keeps 1 HP whatever its base stat total is, so the program's own total (what the log prints)
            // is not the sum of its six stats (what the guide shows). Every other Pokemon must agree.
            if (sp.get("stats") instanceof int[] stats && !String.valueOf(sp.get("name")).equalsIgnoreCase("Shedinja")) {
                int total = java.util.Arrays.stream(stats).sum();
                assertEquals(String.valueOf(total), cells[at.get("BST")].trim(), romName + ": base stat total of " + sp.get("name"));
            }
            java.util.List<String> logged = new java.util.ArrayList<>();
            for (String col : new String[]{"ABILITY 1", "ABILITY 2", "H. ABILITY"}) {
                if (at.containsKey(col)) {
                    String name = cells[at.get(col)].trim();
                    // "no ability" is written differently by each game's text ("-------", "(?)", a dash)
                    if (!name.isEmpty() && !name.matches("[-\u2014\u2013]+") && !name.equals("(?)") && !name.equals("???")) {
                        logged.add(name.toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
            if (at.containsKey("ABILITY 1")) {
                java.util.List<String> guide = new java.util.ArrayList<>();
                for (Object a : (java.util.List<Object>) sp.get("abilities")) {
                    // the species list refers to abilities by id; "name" of the abilities section is the ROM's own text
                    guide.add(abilityRomNames.get(((Number) a).intValue()).toLowerCase(java.util.Locale.ROOT));
                }
                assertEquals(logged, guide, romName + ": abilities of " + sp.get("name") + " differ between log and guide");
            }
        }
        assertTrue(compared > 100, romName + ": only " + compared + " Pokemon could be compared between log and guide");
    }


    /**
     * In Spanish mode no game term may stay English and in English mode none may stay Spanish, whatever the ROM's
     * language: for the language the ROM is not in, a name must be exactly the bundled table's name. Anything the table
     * could not match falls back to the ROM's text, is counted, and must be a small share.
     */
    @SuppressWarnings("unchecked")
    private static void assertNamesAreInBothLanguages(String romName, String label, Map<String, Object> data,
                                                      GuideNames.Resolver resolver) {
        GuideNames table = GuideNames.get();
        String romLang = resolver.romLang();
        StringBuilder report = new StringBuilder(romName + " " + label + " (" + romLang + ") names the table could not match: ");
        for (String section : new String[]{"species", "moves", "abilities", "items"}) {
            List<Object> list = (List<Object>) data.get(section);
            assertNotNull(list, romName + ": no " + section);
            int checked = 0;
            for (Object o : list) {
                if (o == null) {
                    continue;
                }
                Map<String, Object> e = (Map<String, Object>) o;
                String en = String.valueOf(e.get("ne")), es = String.valueOf(e.get("ns"));
                assertFalse(en.isBlank() || es.isBlank() || en.equals("null") || es.equals("null"), romName + ": " + section + " " + e.get("id"));
                assertFalse(en.contains("\u2014") || es.contains("\u2014"), "em dash in a name");
                String key = String.valueOf(e.get("id"));
                String[] pair = null;
                if (section.equals("moves")) {
                    pair = table.moveTable().get(key);
                } else if (section.equals("abilities")) {
                    pair = table.abilityTable().get(key);
                    if (resolver.generation() == 3 && key.equals("76")) {
                        pair = new String[]{"Cacophony", "Cacofon\u00eda"}; // Gen 3 only, see GuideNames.Resolver.ability
                    } else if (resolver.generation() == 3 && key.equals("77")) {
                        pair = table.abilityTable().get("76"); // Air Lock is 77 in Gen 3, 76 from Gen 4 on
                    }
                }
                if (pair != null) {
                    checked++;
                    // the language the ROM is not in must be the table's name, so nothing English can be left in Spanish mode
                    if (!"en".equals(romLang)) {
                        assertEquals(pair[0], en, romName + ": English " + section + " " + key);
                    }
                    if (!"es".equals(romLang)) {
                        assertEquals(pair[1], es, romName + ": Spanish " + section + " " + key);
                    }
                }
            }
            if (section.equals("moves")) {
                assertTrue(checked > 100, romName + ": only " + checked + " moves were checked");
            }
            java.util.Set<String> misses = resolver.unmatched().getOrDefault(section, java.util.Set.of());
            int total = resolver.totals().getOrDefault(section, 0);
            report.append(section).append("=").append(misses.size()).append("/").append(total).append(' ');
            // species, moves and abilities are keyed by national id and must always resolve
            if (!section.equals("items")) {
                assertTrue(misses.isEmpty(), romName + ": unmatched " + section + ": " + misses);
            } else {
                assertTrue(misses.size() <= Math.max(25, total / 20), romName + ": too many unmatched items: " + misses);
            }
        }
        if (!resolver.unmatched().getOrDefault("forme", java.util.Set.of()).isEmpty()) {
            report.append("formeSuffixes=").append(resolver.unmatched().get("forme"));
        }
        String items = String.valueOf(resolver.unmatched().getOrDefault("items", java.util.Set.of()));
        report.append(" items:").append(items);
        System.out.println(report);
        try {
            File dir = new File("build/guide-rom-test");
            dir.mkdirs();
            StringBuilder diffs = new StringBuilder(report).append(System.lineSeparator());
            for (Map.Entry<String, java.util.Set<String>> d : resolver.romDifferences().entrySet()) {
                diffs.append("ROM spells differently from the table (").append(d.getKey()).append("): ").append(d.getValue())
                        .append(System.lineSeparator());
            }
            Files.writeString(new File(dir, romName.replaceAll("[^A-Za-z0-9]+", "_") + "." + label + ".names.txt").toPath(),
                    diffs.toString());
        } catch (java.io.IOException ignored) {
            // the report is only a convenience
        }
    }

    /**
     * Wild encounters are written one entry per place with one entry per Pokemon per table: no Pokemon may repeat
     * inside a table, no place may repeat, and the places follow the story order list of the game.
     */
    @SuppressWarnings("unchecked")
    private static void assertWildIsGroupedAndOrdered(String romName, Map<String, Object> data, RomHandler rom) {
        List<Object> places = (List<Object>) data.get("wild");
        assertNotNull(places, romName + ": no wild section");
        List<String> order = GuideWild.traverseOrder(rom);
        java.util.Set<String> tags = new java.util.HashSet<>();
        int lastOrd = -1;
        int tables = 0, monsTotal = 0;
        for (Object po : places) {
            Map<String, Object> p = (Map<String, Object>) po;
            String tag = (String) p.get("tag");
            if (tag != null && !tag.equals("UNKNOWN")) {
                assertTrue(tags.add(tag), romName + ": place " + tag + " appears twice");
            }
            int ord = ((Number) p.get("ord")).intValue();
            assertTrue(ord >= lastOrd, romName + ": places are not in story order at " + tag);
            lastOrd = ord;
            if (tag != null && order.contains(tag)) {
                assertEquals(order.indexOf(tag), ord, romName + ": " + tag);
            }
            java.util.Set<String> tableKeys = new java.util.HashSet<>();
            for (Object to : (List<Object>) p.get("tables")) {
                Map<String, Object> t = (Map<String, Object>) to;
                assertTrue(tableKeys.add(t.get("m") + "|" + t.get("v")), romName + ": table repeated in " + tag + ": " + t.get("m") + "|" + t.get("v"));
                tables++;
                java.util.Set<Object> seen = new java.util.HashSet<>();
                for (Object mo : (List<Object>) t.get("mons")) {
                    List<Object> m = (List<Object>) mo;
                    assertTrue(seen.add(m.get(0)), romName + ": species " + m.get(0) + " repeated in " + tag + " " + t.get("m"));
                    monsTotal++;
                    int lo = ((Number) m.get(1)).intValue(), hi = ((Number) m.get(2)).intValue();
                    assertTrue(lo >= 1 && hi >= lo && hi <= 100, romName + ": level range " + lo + "-" + hi);
                    if (m.get(3) != null) {
                        double a = ((Number) m.get(3)).doubleValue(), b = ((Number) m.get(4)).doubleValue();
                        assertTrue(a > 0 && b >= a && b <= 100.05, romName + ": chance " + a + "-" + b + " in " + tag);
                    }
                }
            }
        }
        assertTrue(tables > 10 && monsTotal > 50, romName + ": wild section looks empty");
        java.util.Set<String> variants = new java.util.TreeSet<>();
        for (Object po : places) {
            for (Object to : (List<Object>) ((Map<String, Object>) po).get("tables")) {
                String v = String.valueOf(((Map<String, Object>) to).get("v"));
                if (!v.isEmpty()) {
                    variants.add(v);
                }
            }
        }
        int gen = rom.generationOfPokemon();
        if (gen == 5) {
            assertTrue(variants.containsAll(List.of("spring", "summer", "autumn", "winter")), romName + ": seasons missing: " + variants);
        } else if (gen == 4 && rom.getROMName().contains("Soul")) {
            assertTrue(variants.containsAll(List.of("morning", "day", "night")), romName + ": times of day missing: " + variants);
        } else if (gen == 4) {
            assertTrue(variants.contains("tod"), romName + ": time of day slots missing: " + variants);
        }
        System.out.println(romName + ": wild variants " + variants);
        // the grouping removes the slot repeats: compare with the raw encounter slots
        System.out.println(romName + ": wild places=" + places.size() + " tables=" + tables + " pokemon entries=" + monsTotal);
    }

    @SuppressWarnings("unchecked")
    private static void assertOtherSectionsAreOrdered(String romName, Map<String, Object> data) {
        List<Object> trainers = (List<Object>) data.get("trainers");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int lastGym = 0;
        double lastGymN = 0;
        List<Map<String, Object>> byN = new java.util.ArrayList<>();
        for (Object o : trainers) {
            Map<String, Object> t = (Map<String, Object>) o;
            assertTrue(seen.add(((Number) t.get("n")).intValue()), romName + ": duplicate trainer order number");
            byN.add(t);
        }
        byN.sort(java.util.Comparator.comparingInt(t -> ((Number) t.get("n")).intValue()));
        int gyms = 0;
        int lastGymNumber = -1, lastLeagueRank = -1;
        int firstLeague = Integer.MAX_VALUE, lastGymPos = -1;
        // only the first fight with each leader is in gym order, rematches (a much higher level) are placed by level
        java.util.Map<Integer, Integer> lowestGymLevel = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> lowestLeagueLevel = new java.util.HashMap<>();
        for (Map<String, Object> t : byN) {
            if ("gym".equals(t.get("tier"))) {
                lowestGymLevel.merge(((Number) t.get("rank")).intValue(), ((Number) t.get("lv")).intValue(), Math::min);
            } else if ("league".equals(t.get("tier"))) {
                lowestLeagueLevel.merge(((Number) t.get("rank")).intValue(), ((Number) t.get("lv")).intValue(), Math::min);
            }
        }
        for (int i = 0; i < byN.size(); i++) {
            Map<String, Object> t = byN.get(i);
            if ("gym".equals(t.get("tier"))) {
                int rank = ((Number) t.get("rank")).intValue();
                if (((Number) t.get("lv")).intValue() > lowestGymLevel.get(rank)) {
                    continue;
                }
                gyms++;
                assertTrue(rank >= lastGymNumber, romName + ": gym leaders out of order at " + t.get("name") + " (gym " + rank + " after " + lastGymNumber + ")");
                lastGymNumber = rank;
                lastGymPos = i;
            } else if ("league".equals(t.get("tier"))
                    && ((Number) t.get("lv")).intValue() <= lowestLeagueLevel.get(((Number) t.get("rank")).intValue())) {
                firstLeague = Math.min(firstLeague, i);
                int rank = ((Number) t.get("rank")).intValue();
                assertTrue(rank >= lastLeagueRank, romName + ": the league is out of order at " + t.get("name"));
                lastLeagueRank = rank;
            }
        }
        if (gyms > 0 && firstLeague != Integer.MAX_VALUE) {
            assertTrue(lastGymPos < firstLeague, romName + ": the league starts before the last gym leader");
        }
        List<Object> statics = (List<Object>) data.get("statics");
        if (statics != null) {
            int lastLevel = 0;
            List<Map<String, Object>> sorted = new java.util.ArrayList<>();
            for (Object o : statics) {
                sorted.add((Map<String, Object>) o);
            }
            sorted.sort(java.util.Comparator.comparingInt(t -> ((Number) t.get("n")).intValue()));
            for (Map<String, Object> s : sorted) {
                assertNull(s.get("loc"), romName + ": static locations are not available in any handler");
            }
        }
        List<Object> shops = (List<Object>) data.get("shops");
        if (shops != null) {
            java.util.Set<Integer> ns = new java.util.HashSet<>();
            for (Object o : shops) {
                assertTrue(ns.add(((Number) ((Map<String, Object>) o).get("n")).intValue()), romName + ": duplicate shop number");
            }
        }
    }

    /** The log lists wild encounters by place in the same order as the guide, with one line per Pokemon per method. */
    @SuppressWarnings("unchecked")
    private static void assertLogGroupsWildEncounters(String romName, String logText, Map<String, Object> data) {
        int wild = logText.indexOf("Place #1:");
        assertTrue(wild > 0, romName + ": the log has no grouped wild section");
        List<Object> places = (List<Object>) data.get("wild");
        int last = wild;
        // the first few places appear in the log in the guide's order
        GuideNames names = GuideNames.get();
        for (int i = 0; i < Math.min(8, places.size()); i++) {
            Map<String, Object> p = (Map<String, Object>) places.get(i);
            int at = logText.indexOf("Place #" + (i + 1) + ": ", last);
            assertTrue(at >= last, romName + ": place " + (i + 1) + " is missing or out of order in the log");
            last = at;
            String tag = (String) p.get("tag");
            if (tag != null && names.place(tag) != null) {
                int eol = logText.indexOf('\n', at);
                assertTrue(logText.substring(at, eol).contains(names.place(tag)[0]), romName + ": place title for " + tag);
            }
        }
    }

    private static void deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursively(k);
            }
        }
        f.delete();
    }

    @SuppressWarnings("unchecked")
    private static String speciesField(Map<String, Object> data, String field) {
        StringBuilder sb = new StringBuilder();
        for (Object o : (java.util.List<Object>) data.get("species")) {
            sb.append(json(((Map<String, Object>) o).get(field))).append('\n');
        }
        return sb.toString();
    }
}
