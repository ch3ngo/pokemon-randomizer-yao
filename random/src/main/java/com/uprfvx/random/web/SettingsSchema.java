package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  SettingsSchema.java - the randomizer's options, described for the     --*/
/*--  local web interface and read back from it.                            --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.random.Settings;
import com.uprfvx.romio.MiscTweak;
import com.uprfvx.romio.gamedata.BattleStyle;
import com.uprfvx.romio.gamedata.ExpCurve;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Describes every option of {@link Settings} to the web interface and applies the interface's choices to a
 * {@code Settings} object. The labels, tooltips and grouping come from {@code settings-meta.json}, which was generated
 * from the texts of the original Swing interface (so the wording is the same), while the types, the list of choices of
 * each option and the default values are read from the {@code Settings} class itself, so they can never disagree with
 * it. Values that do not make sense are ignored or moved to the nearest valid one: a bad request can never crash the
 * program or produce a settings object that cannot be saved.
 */
public final class SettingsSchema {

    private final Map<String, Object> meta;
    private final Settings defaults;

    public SettingsSchema() {
        this.meta = loadMeta();
        this.defaults = freshSettings();
    }

    private static Map<String, Object> loadMeta() {
        try (InputStream in = SettingsSchema.class.getResourceAsStream("settings-meta.json")) {
            if (in == null) {
                throw new IllegalStateException("settings-meta.json is missing from the program.");
            }
            return Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("settings-meta.json could not be read: " + e.getMessage(), e);
        }
    }

    /** A settings object with every option at a value that can be saved. */
    static Settings freshSettings() {
        Settings settings = new Settings();
        setField(settings, "selectedEXPCurve", ExpCurve.MEDIUM_FAST);
        setField(settings, "customStarters", new int[]{1, 4, 7});
        setField(settings, "limitPokemon", false);
        setField(settings, "settingBattleStyle", new BattleStyle());
        setField(settings, "startersBSTMinimum", 0);
        setField(settings, "startersBSTMaximum", 1000);
        setField(settings, "updateBaseStatsToGeneration", 6);
        setField(settings, "updateMovesToGeneration", 6);
        setField(settings, "guaranteedMoveCount", 2);
        return settings;
    }

    // ---------------------------------------------------------------- reflection helpers

    private static Field field(String name) {
        try {
            Field f = Settings.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static Object getField(Settings settings, String name) {
        Field f = field(name);
        try {
            return f == null ? null : f.get(settings);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static void setField(Settings settings, String name, Object value) {
        Field f = field(name);
        if (f == null) {
            return;
        }
        try {
            f.set(settings, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            // an option the interface sent in the wrong form is simply left at its default
        }
    }

    // ---------------------------------------------------------------- the schema

    /**
     * The description of all options, sorted into sections, for the given game (or for no game, with the widest
     * choices).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> describe(RomHandler rom) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> metaFields = (Map<String, Object>) meta.get("fields");
        List<Object> sections = new ArrayList<>();
        for (Object sectionObject : (List<Object>) meta.get("sections")) {
            Map<String, Object> section = (Map<String, Object>) sectionObject;
            Map<String, Object> sectionOut = new LinkedHashMap<>();
            sectionOut.put("id", section.get("id"));
            sectionOut.put("title", section.get("title"));
            List<Object> groups = new ArrayList<>();
            for (Object groupObject : (List<Object>) section.get("groups")) {
                Map<String, Object> group = (Map<String, Object>) groupObject;
                Map<String, Object> groupOut = new LinkedHashMap<>();
                groupOut.put("id", group.get("id"));
                groupOut.put("title", group.get("title"));
                String main = (String) group.get("main");
                groupOut.put("main", main == null ? null : describeField(main, metaFields, rom));
                List<Object> fields = new ArrayList<>();
                for (Object key : (List<Object>) group.get("fields")) {
                    Map<String, Object> described = describeField((String) key, metaFields, rom);
                    if (described != null) {
                        fields.add(described);
                    }
                }
                groupOut.put("fields", fields);
                groups.add(groupOut);
            }
            sectionOut.put("groups", groups);
            sections.add(sectionOut);
        }
        out.put("sections", sections);
        Applicability applicability = rom == null ? new Applicability() : Applicability.of(rom);
        out.put("hiddenGroups", new ArrayList<>(applicability.hiddenGroups));
        out.put("hiddenFields", new ArrayList<>(applicability.hiddenFields));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> describeField(String key, Map<String, Object> metaFields, RomHandler rom) {
        Map<String, Object> m = (Map<String, Object>) metaFields.getOrDefault(key, new LinkedHashMap<>());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", key);
        out.put("label", m.get("label"));
        out.put("tooltip", m.get("tooltip"));
        // Added (11/10/2026, web UI feedback item 27): the key of another bool field in the same group that this
        // one has no effect without (e.g. a percentage slider under its own "Force %..." checkbox). Carried
        // through unconditionally, before the per-type switch below, so every field type can be gated the same
        // way; the web interface greys out and disables the control while the named field is falsy.
        if (m.get("dependsOn") != null) {
            out.put("dependsOn", m.get("dependsOn"));
            // Added (13/10/2026): optional value the parent (an enum field) must have, so a field can depend on one
            // choice of a drop-down and not only on a switch being on.
            if (m.get("dependsOnValue") != null) {
                out.put("dependsOnValue", m.get("dependsOnValue"));
            }
        }
        // Added (12/10/2026): optional wiki page (path, with an optional #anchor) describing this option in detail.
        if (m.get("wiki") != null) {
            out.put("wiki", m.get("wiki"));
        }
        switch (key) {
            case "genRestrictions" -> {
                out.put("type", "genRestrictions");
                out.put("default", Map.of("gens", List.of(true, true, true, true, true, true, true), "relatives", true));
                return out;
            }
            case "customStarters" -> {
                out.put("type", "customStarters");
                out.put("default", List.of(1, 4, 7));
                return out;
            }
            case "miscTweaks" -> {
                out.put("type", "miscTweaks");
                List<Object> tweaks = new ArrayList<>();
                int available = rom == null ? -1 : rom.miscTweaksAvailable();
                for (MiscTweak tweak : MiscTweak.allTweaks) {
                    if ((available & tweak.getValue()) != 0) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("id", tweak.getID());
                        t.put("label", TweakTexts.name(tweak.getID()));
                        t.put("tooltip", TweakTexts.tooltip(tweak.getID()));
                        tweaks.add(t);
                    }
                }
                out.put("tweaks", tweaks);
                out.put("default", new ArrayList<>());
                return out;
            }
            // Changed from a bare "int" slider to a small discrete picker (11/10/2026, style polish item 4.1):
            // a generation number like "6" or "7" is a handful of named options, not a continuous range, so a
            // drag slider was the wrong control (the same reasoning this project already uses for enum choices).
            // Both "Update Moves to Generation" and "Update Base Stats to Generation" are real int fields of
            // Settings, so this is handled here (key-based, like the other special types above) rather than by
            // the generic type-based branch further down.
            case "updateMovesToGeneration", "updateBaseStatsToGeneration" -> {
                out.put("type", "genPicker");
                boolean isBaseStats = key.equals("updateBaseStatsToGeneration");
                int low = isBaseStats
                        ? (rom == null ? 6 : Math.max(6, Math.min(rom.generationOfPokemon() + 1, 7)))
                        : (rom == null ? 1 : Math.min(rom.generationOfPokemon() + 1, 7));
                List<Object> gens = new ArrayList<>();
                for (int g = low; g <= 7; g++) {
                    gens.add(g);
                }
                out.put("options", gens);
                Object defValue = getField(defaults, key);
                int defGen = defValue instanceof Integer di ? Math.max(low, di) : low;
                out.put("default", defGen);
                return out;
            }
            case "battleStyle" -> {
                out.put("type", "battleStyle");
                out.put("mods", List.of(choice("UNCHANGED", "Unchanged"), choice("RANDOM", "Random"),
                        choice("SINGLE_STYLE", "Always the same style")));
                out.put("styles", List.of(choice("SINGLE_BATTLE", "Single"), choice("DOUBLE_BATTLE", "Double"),
                        choice("TRIPLE_BATTLE", "Triple"), choice("ROTATION_BATTLE", "Rotation")));
                out.put("default", Map.of("mod", "UNCHANGED", "style", "SINGLE_BATTLE"));
                return out;
            }
            default -> {
            }
        }
        Field f = field(key);
        if (f == null) {
            return null;
        }
        Class<?> type = f.getType();
        Object def = getField(defaults, key);
        if (type == boolean.class) {
            out.put("type", "bool");
            out.put("default", def);
        } else if (type == int.class) {
            out.put("type", "int");
            out.put("default", def);
            Number min = (Number) m.get("min");
            Number max = (Number) m.get("max");
            int low = min == null ? 0 : min.intValue();
            int high = max == null ? 100 : max.intValue();
            out.put("min", low);
            out.put("max", Math.max(low, high));
            out.put("step", m.get("step") == null ? 1 : m.get("step"));
        } else if (type.isEnum()) {
            out.put("type", "enum");
            List<Object> options = new ArrayList<>();
            // Added (13/10/2026, web UI feedback): the type of the starters is optional (null means "the program
            // picks one type"), but the interface had no way to say so and always forced Normal. An explicit
            // "None" choice, value "", maps to null.
            if (key.equals("startersSingleType")) {
                Map<String, Object> none = new LinkedHashMap<>();
                none.put("value", "");
                none.put("label", "None (random type)");
                none.put("tooltip", "No fixed type: one type is picked at random and given to all the starters.");
                options.add(none);
            }
            Object[] constants = type.getEnumConstants();
            List<Object> metaOptions = (List<Object>) m.get("options");
            for (int i = 0; i < constants.length; i++) {
                String name = ((Enum<?>) constants[i]).name();
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("value", name);
                String label = null;
                String tooltip = null;
                if (metaOptions != null && metaOptions.size() == constants.length) {
                    Map<String, Object> mo = (Map<String, Object>) metaOptions.get(i);
                    label = (String) mo.get("label");
                    tooltip = (String) mo.get("tooltip");
                }
                option.put("label", label != null ? label : prettify(name));
                option.put("tooltip", tooltip);
                if (type == Type.class && rom != null
                        && !Type.getAllTypes(rom.generationOfPokemon()).contains(constants[i])) {
                    continue;
                }
                options.add(option);
            }
            out.put("options", options);
            out.put("default", def == null ? (key.equals("startersSingleType") ? "" : null) : ((Enum<?>) def).name());
        } else {
            return null;
        }
        return out;
    }

    private static Map<String, Object> choice(String value, String label) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("value", value);
        c.put("label", label);
        return c;
    }

    static String prettify(String constant) {
        String lower = constant.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // ---------------------------------------------------------------- applying values

    /**
     * Builds a settings object from the interface's values. Unknown keys are ignored; a value of the wrong kind is
     * ignored; an out of range number is moved to the nearest valid one.
     */
    @SuppressWarnings("unchecked")
    public Settings build(Map<String, Object> values, int availableTweaks, String romName) {
        Settings settings = freshSettings();
        setField(settings, "romName", romName == null || romName.isBlank() ? "Unknown game" : romName);
        Map<String, Object> metaFields = (Map<String, Object>) meta.get("fields");
        for (Map.Entry<String, Object> entry : (values == null ? new LinkedHashMap<String, Object>() : values).entrySet()) {
            apply(settings, entry.getKey(), entry.getValue(), metaFields, availableTweaks);
        }
        return settings;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void apply(Settings settings, String key, Object value, Map<String, Object> metaFields, int availableTweaks) {
        if (value == null) {
            return;
        }
        switch (key) {
            case "genRestrictions" -> {
                if (value instanceof Map<?, ?> map) {
                    Json.Obj o = new Json.Obj((Map<String, Object>) map);
                    GenRestrictions restrictions = new GenRestrictions();
                    List<Object> gens = o.list("gens");
                    for (int gen = 1; gen <= GenRestrictions.MAX_GENERATION; gen++) {
                        boolean allowed = gen - 1 >= gens.size() || !(gens.get(gen - 1) instanceof Boolean b) || b;
                        restrictions.setGenAllowed(gen, allowed);
                    }
                    restrictions.setAllowEvolutionaryRelatives(o.bool("relatives", true));
                    if (restrictions.nothingSelected()) {
                        restrictions = new GenRestrictions();
                    }
                    setField(settings, "currentRestrictions", restrictions);
                }
                return;
            }
            case "customStarters" -> {
                if (value instanceof List<?> list && list.size() == 3) {
                    int[] starters = new int[3];
                    for (int i = 0; i < 3; i++) {
                        starters[i] = list.get(i) instanceof Number n ? Math.max(1, n.intValue()) : 1;
                    }
                    setField(settings, "customStarters", starters);
                }
                return;
            }
            case "miscTweaks" -> {
                if (value instanceof List<?> list) {
                    int mask = 0;
                    for (Object id : list) {
                        for (MiscTweak tweak : MiscTweak.allTweaks) {
                            if (tweak.getID().equals(String.valueOf(id)) && (availableTweaks & tweak.getValue()) != 0) {
                                mask |= tweak.getValue();
                            }
                        }
                    }
                    setField(settings, "currentMiscTweaks", mask);
                }
                return;
            }
            case "battleStyle" -> {
                if (value instanceof Map<?, ?> map) {
                    Json.Obj o = new Json.Obj((Map<String, Object>) map);
                    try {
                        BattleStyle.Modification mod = BattleStyle.Modification.valueOf(o.string("mod", "UNCHANGED"));
                        BattleStyle.Style style = BattleStyle.Style.valueOf(o.string("style", "SINGLE_BATTLE"));
                        setField(settings, "settingBattleStyle", new BattleStyle(mod, style));
                    } catch (IllegalArgumentException e) {
                        // unknown name: left at unchanged
                    }
                }
                return;
            }
            default -> {
            }
        }
        Field f = field(key);
        if (f == null || key.equals("yaoSettings") || key.equals("romName") || key.equals("updatedFromOldVersion")) {
            return;
        }
        Class<?> type = f.getType();
        try {
            if (type == boolean.class) {
                if (value instanceof Boolean b) {
                    f.setBoolean(settings, b);
                }
            } else if (type == int.class) {
                if (value instanceof Number n) {
                    Map<String, Object> m = (Map<String, Object>) metaFields.get(key);
                    int low = m != null && m.get("min") instanceof Number mn ? mn.intValue() : 0;
                    int high = m != null && m.get("max") instanceof Number mx ? mx.intValue() : 100;
                    f.setInt(settings, Math.max(low, Math.min(high, (int) Math.round(n.doubleValue()))));
                }
            } else if (type.isEnum()) {
                String name = String.valueOf(value);
                if (name.isEmpty() && key.equals("startersSingleType")) {
                    f.set(settings, null); // "None": the randomizer picks the type
                } else {
                    f.set(settings, Enum.valueOf((Class<? extends Enum>) type, name));
                }
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            // ignored on purpose, see the class comment
        }
    }
    // ---------------------------------------------------------------- reading values back (for importing settings)

    /** The values of a settings object in the form the interface uses, for every option the interface shows. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extract(Settings settings) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> metaFields = (Map<String, Object>) meta.get("fields");
        for (String key : metaFields.keySet()) {
            switch (key) {
                case "genRestrictions" -> {
                    GenRestrictions r = (GenRestrictions) getField(settings, "currentRestrictions");
                    if (r != null) {
                        List<Object> gens = new ArrayList<>();
                        for (int gen = 1; gen <= GenRestrictions.MAX_GENERATION; gen++) {
                            gens.add(r.isGenAllowed(gen));
                        }
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("gens", gens);
                        m.put("relatives", r.isAllowEvolutionaryRelatives());
                        values.put(key, m);
                    }
                }
                case "customStarters" -> {
                    Object v = getField(settings, "customStarters");
                    if (v instanceof int[] ints && ints.length == 3) {
                        values.put(key, new int[]{ints[0], ints[1], ints[2]});
                    }
                }
                case "miscTweaks" -> {
                    Object v = getField(settings, "currentMiscTweaks");
                    int mask = v instanceof Integer i ? i : 0;
                    List<Object> ids = new ArrayList<>();
                    for (MiscTweak tweak : MiscTweak.allTweaks) {
                        if ((mask & tweak.getValue()) != 0) {
                            ids.add(tweak.getID());
                        }
                    }
                    values.put(key, ids);
                }
                case "battleStyle" -> {
                    Object v = getField(settings, "settingBattleStyle");
                    if (v instanceof BattleStyle b) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("mod", b.getModification().name());
                        m.put("style", b.getStyle().name());
                        values.put(key, m);
                    }
                }
                default -> {
                    Object v = getField(settings, key);
                    if (v instanceof Boolean || v instanceof Integer) {
                        values.put(key, v);
                    } else if (v instanceof Enum<?> e) {
                        values.put(key, e.name());
                    } else if (v == null && key.equals("startersSingleType")) {
                        values.put(key, "");
                    }
                }
            }
        }
        return values;
    }
}
