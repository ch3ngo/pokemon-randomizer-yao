package com.uprfvx.romio.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  RomIntegrityReport.java - how a loaded ROM differs from a retail dump. --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Says whether a loaded ROM is an unmodified retail dump and, if it is not, which parts differ and which
 * randomizer options read or write those parts, and so may not work as expected.
 *
 * <p>Upstream only reports "this is not a clean ROM". The checksums were already computed, so this class
 * keeps the detail instead of throwing it away. The list of affected options is derived from which
 * handler code touches each kind of component, not guessed, and it errs on the side of listing too much:
 * a difference in the ARM9 binary does not mean every listed option is broken, only that it could be.
 */
public final class RomIntegrityReport {

    public enum Kind {
        /** The whole ROM is compared as one unit (Game Boy, Game Boy Advance). */
        WHOLE_ROM,
        /** The main ARM9 binary of a DS game. */
        ARM9,
        /** One overlay (extra code) of a DS game. */
        OVERLAY,
        /** One data archive or file of a DS game. */
        DATA_FILE
    }

    /**
     * One component whose checksum differs. {@code expected} or {@code actual} is -1 when unknown.
     */
    public record Difference(Kind kind, String name, long expected, long actual) {
    }

    private final int generation;
    private final String variant; // for example "BW2", or null when the generation is enough
    private final List<Difference> differences;

    private RomIntegrityReport(int generation, String variant, List<Difference> differences) {
        this.generation = generation;
        this.variant = variant;
        this.differences = Collections.unmodifiableList(differences);
    }

    public static RomIntegrityReport clean(int generation) {
        return new RomIntegrityReport(generation, null, new ArrayList<>());
    }

    /** For handlers that only know "valid or not". */
    public static RomIntegrityReport wholeRom(int generation, long expected, long actual) {
        List<Difference> list = new ArrayList<>();
        if (expected != actual) {
            list.add(new Difference(Kind.WHOLE_ROM, "ROM", expected, actual));
        }
        return new RomIntegrityReport(generation, null, list);
    }

    /**
     * Builds the report for a DS game from the expected and actual checksums of its components.
     * A component missing from {@code actual} counts as different.
     */
    public static RomIntegrityReport forDSGame(int generation, long expectedArm9, long actualArm9,
                                               Map<Integer, Long> expectedOverlays, Map<Integer, Long> actualOverlays,
                                               Map<String, Long> expectedFiles, Map<String, Long> actualFiles) {
        return forDSGame(generation, null, expectedArm9, actualArm9, expectedOverlays, actualOverlays,
                expectedFiles, actualFiles);
    }

    /**
     * Like the other overload, with a game variant ("BW2" for Black 2 and White 2) for games of a
     * generation that use their components differently.
     */
    public static RomIntegrityReport forDSGame(int generation, String variant, long expectedArm9, long actualArm9,
                                               Map<Integer, Long> expectedOverlays, Map<Integer, Long> actualOverlays,
                                               Map<String, Long> expectedFiles, Map<String, Long> actualFiles) {
        List<Difference> list = new ArrayList<>();
        if (expectedArm9 != actualArm9) {
            list.add(new Difference(Kind.ARM9, "ARM9", expectedArm9, actualArm9));
        }
        for (Map.Entry<Integer, Long> e : new java.util.TreeMap<>(expectedOverlays).entrySet()) {
            long actual = actualOverlays.getOrDefault(e.getKey(), -1L);
            if (!e.getValue().equals(actual)) {
                list.add(new Difference(Kind.OVERLAY, "Overlay " + e.getKey(), e.getValue(), actual));
            }
        }
        for (Map.Entry<String, Long> e : new java.util.TreeMap<>(expectedFiles).entrySet()) {
            long actual = actualFiles.getOrDefault(e.getKey(), -1L);
            if (!e.getValue().equals(actual)) {
                list.add(new Difference(Kind.DATA_FILE, e.getKey(), e.getValue(), actual));
            }
        }
        return new RomIntegrityReport(generation, variant, list);
    }

    public boolean isClean() {
        return differences.isEmpty();
    }

    public List<Difference> getDifferences() {
        return differences;
    }

    /**
     * The options that read or write a component that differs, without repeats, in a stable order.
     * Empty if the ROM is clean.
     */
    public List<String> getAffectedFeatures() {
        Set<String> features = new LinkedHashSet<>();
        for (Difference d : differences) {
            features.addAll(featuresFor(generation, variant, d));
        }
        return new ArrayList<>(features);
    }

    /** Human readable text for a warning or a log. Empty string if the ROM is clean. */
    public String describe() {
        if (isClean()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("This ROM is not an unmodified retail dump. It differs in: ");
        List<String> parts = new ArrayList<>();
        for (Difference d : differences) {
            parts.add(d.name());
        }
        sb.append(String.join(", ", abbreviate(parts, 8))).append(".\n");
        List<String> features = getAffectedFeatures();
        if (!features.isEmpty()) {
            sb.append("These options use the parts that differ and may not work as expected: ")
                    .append(String.join("; ", features)).append(".\n");
        }
        sb.append("Everything else should behave normally. For the most reliable result, use an unmodified retail "
                + "dump of the same game, version and language.");
        return sb.toString();
    }

    private static List<String> abbreviate(List<String> parts, int max) {
        if (parts.size() <= max) {
            return parts;
        }
        List<String> shortened = new ArrayList<>(parts.subList(0, max));
        shortened.add("and " + (parts.size() - max) + " more");
        return shortened;
    }

    // ---- which options depend on which component ----

    // Derived from the methods of Gen4RomHandler and Gen5RomHandler that read or write the ARM9 binary.
    private static final List<String> GEN4_ARM9 = List.of(
            "starter Pokemon", "intro Pokemon", "static Pokemon and roamers", "TM and HM moves",
            "egg moves", "shop items", "field items", "double battle mode", "Shedinja evolution",
            "easier evolutions", "fastest text", "running without Running Shoes", "catching tutorial",
            "reusable TMs", "correct music for static Pokemon", "wild encounter map names");

    private static final List<String> GEN5_ARM9 = List.of(
            "TM and HM moves", "forgettable HMs", "fastest text", "forced Challenge Mode", "double battle mode",
            "correct music for static Pokemon", "Shedinja evolution", "easier evolutions",
            "shop items (Black and White)", "national Pokedex at start");

    private static final Map<String, List<String>> DATA_FILE_FEATURES = Map.ofEntries(
            Map.entry("PokemonStats", List.of("Pokemon base stats, types and abilities")),
            Map.entry("MoveData", List.of("move data and move updates")),
            Map.entry("PokemonMovesets", List.of("Pokemon movesets")),
            Map.entry("PokemonEvolutions", List.of("evolutions")),
            Map.entry("WildPokemon", List.of("wild encounters")),
            Map.entry("TrainerData", List.of("trainers")),
            Map.entry("TrainerPokemon", List.of("trainer Pokemon")),
            Map.entry("ItemData", List.of("items and item prices")),
            Map.entry("Scripts", List.of("static, gift and in-game trade Pokemon")),
            Map.entry("InGameTrades", List.of("in-game trades")),
            Map.entry("Text", List.of("names and in-game text")),
            Map.entry("Events", List.of("field items and events")),
            Map.entry("EggMoves", List.of("egg moves")),
            Map.entry("HeadbuttPokemon", List.of("Headbutt encounters")),
            Map.entry("BabyPokemon", List.of("baby Pokemon")),
            Map.entry("MoveTutorCompat", List.of("move tutor compatibility")));

    private static List<String> featuresFor(int generation, String variant, Difference d) {
        switch (d.kind()) {
            case ARM9:
                if (generation == 4) {
                    return GEN4_ARM9;
                }
                if (generation == 5) {
                    // Black 2 and White 2 keep their shop data elsewhere; only Black and White 1 read it from the ARM9.
                    return "BW2".equals(variant)
                            ? GEN5_ARM9.stream().filter(f -> !f.startsWith("shop items")).toList()
                            : GEN5_ARM9;
                }
                return List.of("any option that edits the game's main program");
            case OVERLAY:
                return List.of("options that patch extra game code (for example the music fix for static Pokemon, "
                        + "Shedinja evolution, the Mystery Egg and wild encounter changes)");
            case DATA_FILE:
                return DATA_FILE_FEATURES.getOrDefault(d.name(), List.of("the data file \"" + d.name() + "\""));
            case WHOLE_ROM:
            default:
                return List.of("any option; the ROM as a whole differs from retail");
        }
    }

    /** Component names that differ, sorted, for tests and compact displays. */
    public Set<String> differingNames() {
        Set<String> names = new TreeSet<>();
        for (Difference d : differences) {
            names.add(d.name());
        }
        return names;
    }
}
