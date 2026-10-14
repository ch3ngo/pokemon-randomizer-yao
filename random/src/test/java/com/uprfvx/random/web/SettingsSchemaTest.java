package com.uprfvx.random.web;

import com.uprfvx.random.Settings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SettingsSchemaTest {

    private final SettingsSchema schema = new SettingsSchema();

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> allFields(Map<String, Object> described) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object s : (List<Object>) described.get("sections")) {
            for (Object g : (List<Object>) ((Map<String, Object>) s).get("groups")) {
                Map<String, Object> group = (Map<String, Object>) g;
                if (group.get("main") != null) {
                    out.add((Map<String, Object>) group.get("main"));
                }
                for (Object f : (List<Object>) group.get("fields")) {
                    out.add((Map<String, Object>) f);
                }
            }
        }
        return out;
    }

    @Test
    public void everyOptionHasATypeADefaultAndEnumsHaveChoices() {
        List<Map<String, Object>> fields = allFields(schema.describe(null));
        assertTrue(fields.size() > 150);
        for (Map<String, Object> f : fields) {
            assertNotNull(f.get("type"), String.valueOf(f.get("key")));
            if (f.get("type").equals("enum")) {
                assertFalse(((List<?>) f.get("options")).isEmpty(), String.valueOf(f.get("key")));
            }
            if (f.get("type").equals("int")) {
                assertTrue(((Number) f.get("min")).intValue() <= ((Number) f.get("max")).intValue(), String.valueOf(f.get("key")));
            }
        }
    }

    @Test
    public void everyOptionHasAReadableLabel() {
        for (Map<String, Object> f : allFields(schema.describe(null))) {
            String type = (String) f.get("type");
            if (type.equals("bool") || type.equals("int") || type.equals("genRestrictions") || type.equals("customStarters")
                    || type.equals("miscTweaks") || type.equals("battleStyle")) {
                assertNotNull(f.get("label"), "no label for " + f.get("key"));
                assertFalse(((String) f.get("label")).isBlank(), "blank label for " + f.get("key"));
            }
            // Added (13/10/2026, web UI feedback item 9): the field itself having a label was already checked
            // above, but nothing checked each individual CHOICE of an enum field, which is how
            // "wildPokemonZoneMod"'s last two options ("1 Per Named Location", "1 In Whole Game", the latter also
            // being the default) ended up rendered as blank, unreadable entries in the web interface's dropdown,
            // with no way to tell what was actually selected.
            if (type.equals("enum")) {
                for (Object o : (List<?>) f.get("options")) {
                    Map<?, ?> opt = (Map<?, ?>) o;
                    assertNotNull(opt.get("label"), "null option label for " + f.get("key"));
                    assertFalse(((String) opt.get("label")).isBlank(), "blank option label for " + f.get("key"));
                }
            }
        }
    }

    @Test
    public void jsonOfTheSchemaIsValid() {
        assertDoesNotThrow(() -> Json.parse(Json.write(schema.describe(null))));
    }

    @Test
    public void valuesRoundTripThroughSettingsAndBack() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("startersMod", "COMPLETELY_RANDOM");
        values.put("randomizeWildPokemon", true);
        values.put("trainersMod", "TYPE_THEMED");
        values.put("wildLevelModifier", 20);
        values.put("tmsMod", "RANDOM");
        values.put("customStarters", List.of(25, 133, 150));
        values.put("battleStyle", Map.of("mod", "SINGLE_STYLE", "style", "DOUBLE_BATTLE"));
        values.put("genRestrictions", Map.of("gens", List.of(true, true, false, false, false, false, false), "relatives", false));
        Settings settings = schema.build(values, -1, "Some Game");
        Map<String, Object> back = schema.extract(settings);
        assertEquals("COMPLETELY_RANDOM", back.get("startersMod"));
        assertEquals(true, back.get("randomizeWildPokemon"));
        assertEquals("TYPE_THEMED", back.get("trainersMod"));
        assertEquals(20, back.get("wildLevelModifier"));
        assertArrayEquals(new int[]{25, 133, 150}, (int[]) back.get("customStarters"));
        assertEquals("DOUBLE_BATTLE", ((Map<?, ?>) back.get("battleStyle")).get("style"));
        Map<?, ?> gens = (Map<?, ?>) back.get("genRestrictions");
        assertEquals(false, gens.get("relatives"));
        assertEquals(List.of(true, true, false, false, false, false, false), gens.get("gens"));
    }

    @Test
    public void settingsBuiltFromTheInterfaceCanAlwaysBeSavedAndReadAgain() throws Exception {
        Settings settings = schema.build(new LinkedHashMap<>(), -1, "Some Game");
        String text = settings.toString();
        assertNotNull(Settings.fromString(text));
        File temp = File.createTempFile("yao-test", ".rnqs");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                settings.writeToFileFormat(out);
            }
            try (FileInputStream in = new FileInputStream(temp)) {
                assertNotNull(Settings.readFromFileFormat(in));
            }
        } finally {
            temp.delete();
        }
    }

    @Test
    public void nonsenseValuesAreIgnoredOrMovedNeverFatal() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("startersMod", "NO_SUCH_CHOICE");
        values.put("randomizeWildPokemon", "yes");
        values.put("wildLevelModifier", 100000);
        values.put("trainersLevelModifier", -100000);
        values.put("doesNotExist", 5);
        values.put("yaoSettings", "x");
        values.put("romName", "evil");
        values.put("customStarters", List.of(1, 2));
        values.put("battleStyle", "nonsense");
        values.put("genRestrictions", Map.of("gens", List.of(false, false, false, false, false, false, false)));
        values.put("miscTweaks", "not a list");
        Settings settings = assertDoesNotThrow(() -> schema.build(values, -1, null));
        Map<String, Object> back = schema.extract(settings);
        assertEquals("UNCHANGED", back.get("startersMod"));
        assertEquals(false, back.get("randomizeWildPokemon"));
        assertEquals(50, back.get("wildLevelModifier"));
        assertEquals(-50, back.get("trainersLevelModifier"));
        List<?> gens = (List<?>) ((Map<?, ?>) back.get("genRestrictions")).get("gens");
        assertTrue(gens.contains(true), "an empty choice of generations is repaired");
        assertDoesNotThrow(settings::toString);
    }

    @Test
    public void tweaksNotAvailableForTheGameAreDropped() {
        Settings none = schema.build(Map.of("miscTweaks", List.of("fastestText", "nerfXAcc")), 0, "G");
        assertTrue(((List<?>) schema.extract(none).get("miscTweaks")).isEmpty());
        Settings all = schema.build(Map.of("miscTweaks", List.of("fastestText", "bogus")), -1, "G");
        assertEquals(List.of("fastestText"), schema.extract(all).get("miscTweaks"));
    }

    // Added (13/10/2026, web UI feedback): the type of the starters can be "None" (the program picks the type), it
    // starts as None, and is only enabled while the type rule is "Single type".
    @Test
    public void startersTypeCanBeNoneAndOnlyAppliesToSingleTypeRule() {
        Map<String, Object> field = null;
        for (Map<String, Object> f : allFields(schema.describe(null))) {
            if ("startersSingleType".equals(f.get("key"))) {
                field = f;
            }
        }
        assertNotNull(field);
        assertEquals("", field.get("default"));
        assertEquals("", ((Map<?, ?>) ((List<?>) field.get("options")).get(0)).get("value"));
        assertEquals("startersTypeMod", field.get("dependsOn"));
        assertEquals("SINGLE_TYPE", field.get("dependsOnValue"));

        Settings none = schema.build(Map.of("startersSingleType", ""), -1, "G");
        assertNull(none.getStartersSingleType());
        assertEquals("", schema.extract(none).get("startersSingleType"));
        Settings fire = schema.build(Map.of("startersSingleType", "FIRE"), -1, "G");
        assertEquals("FIRE", schema.extract(fire).get("startersSingleType"));
    }

    // Added (13/10/2026, web UI feedback item 50): the wild Pokemon picker in app.js maps its plain-language modes onto
    // randomizeWildPokemon and wildPokemonZoneMod. These tests keep the page and the settings from drifting apart.
    @Test
    @SuppressWarnings("unchecked")
    public void wildPickerKeysAndModesExistInTheSchemaAndInThePage() throws Exception {
        Map<String, Object> described = schema.describe(null);
        Map<String, Object> wild = null;
        for (Object s : (List<Object>) described.get("sections")) {
            for (Object g : (List<Object>) ((Map<String, Object>) s).get("groups")) {
                if ("wild".equals(((Map<String, Object>) g).get("id"))) {
                    wild = (Map<String, Object>) g;
                }
            }
        }
        assertNotNull(wild);
        List<String> keys = new ArrayList<>();
        for (Object f : (List<Object>) wild.get("fields")) {
            keys.add((String) ((Map<String, Object>) f).get("key"));
        }
        String page;
        try (java.io.InputStream in = SettingsSchemaTest.class.getResourceAsStream("/com/uprfvx/random/web/app.js")) {
            page = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (String key : List.of("wildPokemonZoneMod", "splitWildZoneByEncounterTypes", "useTimeBasedEncounters",
                "similarStrengthEncounters", "wildPokemonTypeMod", "keepWildTypeThemes", "wildPokemonEvolutionMod",
                "keepWildEvolutionFamilies", "blockWildLegendaries", "allowWildAltFormes", "catchEmAllEncounters",
                "balanceShakingGrass", "useMinimumCatchRate", "minimumCatchRateLevel", "randomizeWildPokemonHeldItems",
                "banBadRandomWildPokemonHeldItems", "wildLevelsModified", "wildLevelModifier")) {
            assertTrue(keys.contains(key), "the wild group lost " + key);
            assertTrue(page.contains("'" + key + "'"), "the wild picker no longer places " + key);
        }
        Map<String, Object> zone = null;
        for (Object f : (List<Object>) wild.get("fields")) {
            if ("wildPokemonZoneMod".equals(((Map<String, Object>) f).get("key"))) {
                zone = (Map<String, Object>) f;
            }
        }
        assertNotNull(zone);
        for (Object o : (List<Object>) zone.get("options")) {
            String value = (String) ((Map<String, Object>) o).get("value");
            assertTrue(page.contains("id: '" + value + "'"), "no picker card for the area mode " + value);
        }
    }

    @Test
    public void everyWildAreaModeSurvivesSaveAndLoad() {
        for (Settings.WildPokemonZoneMod mod : Settings.WildPokemonZoneMod.values()) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("randomizeWildPokemon", true);
            values.put("wildPokemonZoneMod", mod.name());
            Settings settings = schema.build(values, -1, "G");
            assertEquals(mod.name(), schema.extract(Settings.fromString(settings.toString())).get("wildPokemonZoneMod"));
        }
    }
}
