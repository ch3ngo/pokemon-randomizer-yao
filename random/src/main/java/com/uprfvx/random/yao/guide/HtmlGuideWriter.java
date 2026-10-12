package com.uprfvx.random.yao.guide;

/*----------------------------------------------------------------------------*/
/*--  HtmlGuideWriter.java - writes the single file HTML guide.             --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.romhandlers.RomHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes an optional guide to the randomized game as one self-contained HTML file: no external files, no
 * network access, works offline in any browser. It shows everything the randomization changed (Pokemon, moves,
 * evolutions, wild areas, static and gift Pokemon, trades, trainers, shops) with search, sortable tables,
 * before and after highlighting, a "where to find each Pokemon" index, and a spoiler-safe mode that hides
 * the content until it is clicked. Labels and game names (Pokemon, moves, abilities, items, places) are in English
 * and Spanish with a switch, whatever the ROM's language is (see {@link GuideNames}).
 *
 * <p>The guide reveals the whole game, so asking for it is always an explicit choice.
 */
public final class HtmlGuideWriter {

    private static final String TEMPLATE_RESOURCE = "/com/uprfvx/random/yao/guide/guide.html";
    private static final String PLACEHOLDER = "/*__GUIDE_DATA__*/null";
    // Added 12/10/2026: every visible string of the page, English and Spanish, lives in this one file.
    static final String STRINGS_RESOURCE = "/com/uprfvx/random/yao/guide/guide-strings.json";
    private static final String STRINGS_PLACEHOLDER = "/*__GUIDE_STRINGS__*/null";

    private HtmlGuideWriter() {
    }

    /**
     * @param original   a freshly opened copy of the ROM before randomization
     * @param randomized the ROM after randomization
     * @param notes      lines to show on the Overview tab (what the YAO additions did, warnings)
     */
    public static void write(Path output, RomHandler original, RomHandler randomized, long seed, List<String> notes)
            throws IOException {
        write(output, original, randomized, seed, notes, true);
    }

    /** @param useTimeOfDay whether the randomization ran with time based encounters, see {@link GuideDataCollector} */
    public static void write(Path output, RomHandler original, RomHandler randomized, long seed, List<String> notes,
                             boolean useTimeOfDay) throws IOException {
        write(output, original, randomized, seed, notes, useTimeOfDay, null);
    }

    public static void write(Path output, RomHandler original, RomHandler randomized, long seed, List<String> notes,
                             boolean useTimeOfDay, Extras extras) throws IOException {
        Files.writeString(output, render(original, randomized, seed, notes, useTimeOfDay, extras), StandardCharsets.UTF_8);
    }

    /**
     * Added 12/10/2026: what the page shows besides the game data. The log text makes the guide a one file record
     * of the run (it opens in the "Randomization log" section); the two lists are the options that differ from the
     * defaults, as written by {@code SettingsSummary}.
     */
    public record Extras(String logText, List<String> changedOptions, List<String> extraOptions) {
    }

    public static String render(RomHandler original, RomHandler randomized, long seed, List<String> notes)
            throws IOException {
        return render(original, randomized, seed, notes, true);
    }

    public static String render(RomHandler original, RomHandler randomized, long seed, List<String> notes,
                                boolean useTimeOfDay) throws IOException {
        return render(original, randomized, seed, notes, useTimeOfDay, null);
    }

    public static String render(RomHandler original, RomHandler randomized, long seed, List<String> notes,
                                boolean useTimeOfDay, Extras extras) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("rom", randomized.getROMName());
        meta.put("code", randomized.getROMCode());
        meta.put("generation", randomized.generationOfPokemon());
        meta.put("seed", seed);
        // the page opens in the language of the game (English or Spanish), the reader can switch it
        String romLang = GuideNames.romLang(randomized);
        meta.put("lang", romLang.equals("es") ? "es" : "en");
        meta.put("romLang", romLang);
        // Added 12/10/2026: no game handler exposes where a static, gift or traded Pokemon is found, so the page says so
        Map<String, Object> locationInfo = new LinkedHashMap<>();
        locationInfo.put("statics", false);
        locationInfo.put("trades", false);
        meta.put("locationInfo", locationInfo);
        meta.put("version", com.uprfvx.random.Version.LATEST.name);
        // dates are always written DD/MM/YYYY
        meta.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        root.put("meta", meta);
        root.put("notes", notes == null ? new ArrayList<String>() : notes);
        if (extras != null) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("changed", extras.changedOptions() == null ? new ArrayList<String>() : extras.changedOptions());
            summary.put("extras", extras.extraOptions() == null ? new ArrayList<String>() : extras.extraOptions());
            root.put("summary", summary);
            if (extras.logText() != null && !extras.logText().isBlank()) {
                root.put("log", extras.logText());
            }
        }
        // The original is read the same way as the randomized game so rows line up one to one.
        // The randomized game is read first: it is the one that is still in use, and reading data from a second
        // handler on the same DS game must never be able to change what it sees (see NDSRom's temporary folder).
        Map<String, Object> randomizedData = GuideDataCollector.collect(randomized, useTimeOfDay,
                new GuideNames.Resolver(randomized));
        Map<String, Object> originalData = GuideDataCollector.collect(original, useTimeOfDay, new GuideNames.Resolver(original));
        // game order numbers are worked out from the original game and written to both
        GuideOrder.apply(originalData, randomizedData, randomized);
        root.put("placeNames", placeNames(originalData, randomizedData));
        root.put("original", originalData);
        root.put("randomized", randomizedData);
        return injectStrings(inject(loadTemplate(), JsonWriter.toJson(root)), loadStrings());
    }

    /** The English and Spanish name of every place tag used by the wild sections (tags the table does not know are left out). */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static Map<String, Object> placeNames(Map<String, Object>... datasets) {
        Map<String, Object> out = new LinkedHashMap<>();
        GuideNames names = GuideNames.get();
        for (Map<String, Object> d : datasets) {
            if (d.get("wild") instanceof List<?> places) {
                for (Object p : places) {
                    Object tag = ((Map<String, Object>) p).get("tag");
                    String[] pair = names.place(tag == null ? null : String.valueOf(tag));
                    if (pair != null) {
                        out.put(String.valueOf(tag), List.of(pair[0], pair[1]));
                    }
                }
            }
        }
        return out;
    }

    static String injectStrings(String template, String json) {
        int at = template.indexOf(STRINGS_PLACEHOLDER);
        if (at < 0) {
            throw new IllegalStateException("The guide template has no strings placeholder.");
        }
        return template.substring(0, at) + json + template.substring(at + STRINGS_PLACEHOLDER.length());
    }

    /** The strings file as compact JSON that is safe inside a script element. */
    static String loadStrings() throws IOException {
        try (InputStream in = HtmlGuideWriter.class.getResourceAsStream(STRINGS_RESOURCE)) {
            if (in == null) {
                throw new IOException("The guide strings are missing from the program.");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JsonWriter.toJson(com.uprfvx.random.web.Json.parseObject(text));
        }
    }

    static String inject(String template, String json) {
        int at = template.indexOf(PLACEHOLDER);
        if (at < 0) {
            throw new IllegalStateException("The guide template has no data placeholder.");
        }
        return template.substring(0, at) + json + template.substring(at + PLACEHOLDER.length());
    }

    private static String loadTemplate() throws IOException {
        try (InputStream in = HtmlGuideWriter.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IOException("The guide template is missing from the program.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
