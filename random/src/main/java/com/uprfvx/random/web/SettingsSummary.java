package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  SettingsSummary.java - the options that differ from the defaults.     --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.random.Settings;
import com.uprfvx.random.yao.YaoSettings;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Added for YAO (12/10/2026). Turns a settings object into plain sentences listing only what differs from the
 * defaults, for the text log. The wording of every option comes from the same {@code settings-meta.json} the web
 * interface uses, and this program's own extras (shiny, Locke, egg box, shops, stat floor and edits, evolutions,
 * fully evolved by level, evolution scale, mega finale, battle style rules) are described from their own values,
 * with Pokemon and item names taken from the game. Never throws because of odd values: a line that cannot be
 * written is skipped.
 */
public final class SettingsSummary {

    private SettingsSummary() {
    }

    /** One line per changed native option, as {@code "Section > Group: Option = value"}. */
    @SuppressWarnings("unchecked")
    public static List<String> nativeChanges(Settings settings, RomHandler rom) {
        List<String> lines = new ArrayList<>();
        try {
            SettingsSchema schema = new SettingsSchema();
            Map<String, Object> now = schema.extract(settings);
            Map<String, Object> defaults = schema.extract(SettingsSchema.freshSettings());
            Map<String, Object> described = schema.describe(rom);
            List<Object> hiddenGroups = (List<Object>) described.get("hiddenGroups");
            List<Object> hiddenFields = (List<Object>) described.get("hiddenFields");
            for (Object so : (List<Object>) described.get("sections")) {
                Map<String, Object> section = (Map<String, Object>) so;
                for (Object go : (List<Object>) section.get("groups")) {
                    Map<String, Object> group = (Map<String, Object>) go;
                    if (hiddenGroups.contains(group.get("id"))) {
                        continue;
                    }
                    List<Map<String, Object>> fields = new ArrayList<>();
                    if (group.get("main") != null) {
                        fields.add((Map<String, Object>) group.get("main"));
                    }
                    for (Object fo : (List<Object>) group.get("fields")) {
                        fields.add((Map<String, Object>) fo);
                    }
                    for (Map<String, Object> field : fields) {
                        String key = String.valueOf(field.get("key"));
                        // the generation pool is narrowed by the game itself (a Gen 6 game has no Gen 7 Pokemon), so it
                        // always differs from the plain default without the user having chosen anything
                        if (hiddenFields.contains(key) || !now.containsKey(key) || key.equals("genRestrictions")) {
                            continue;
                        }
                        Object value = now.get(key);
                        if (Objects.equals(json(value), json(defaults.get(key)))) {
                            continue;
                        }
                        String label = field.get("label") != null ? String.valueOf(field.get("label"))
                                : String.valueOf(group.get("title"));
                        lines.add(section.get("title") + " > " + group.get("title") + ": " + label + " = "
                                + valueText(field, value));
                    }
                }
            }
        } catch (RuntimeException e) {
            lines.add("(the list of changed options could not be written: " + e + ")");
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static String valueText(Map<String, Object> field, Object value) {
        String type = String.valueOf(field.get("type"));
        switch (type) {
            case "bool":
                return Boolean.TRUE.equals(value) ? "on" : "off";
            case "enum":
                for (Object oo : (List<Object>) field.get("options")) {
                    Map<String, Object> option = (Map<String, Object>) oo;
                    if (Objects.equals(option.get("value"), value) && option.get("label") != null) {
                        return String.valueOf(option.get("label"));
                    }
                }
                return String.valueOf(value);
            default:
                return plain(value);
        }
    }

    private static String plain(Object value) {
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object o : list) {
                parts.add(plain(o));
            }
            return String.join(", ", parts);
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                parts.add(e.getKey() + ": " + plain(e.getValue()));
            }
            return String.join("; ", parts);
        }
        if (value instanceof int[] ints) {
            List<String> parts = new ArrayList<>();
            for (int i : ints) {
                parts.add(String.valueOf(i));
            }
            return String.join(", ", parts);
        }
        return String.valueOf(value);
    }

    private static String json(Object value) {
        return Json.write(value);
    }

    // ------------------------------------------------------------------ this program's own extras

    /** One line per extra option that is in use. Empty when none is. */
    @SuppressWarnings("unchecked")
    public static List<String> extras(YaoSettings yao, RomHandler rom) {
        List<String> lines = new ArrayList<>();
        try {
            Map<String, Object> y = YaoJson.extract(yao);
            Json.Obj o = new Json.Obj(y);
            double shiny = o.number("shinyPercent", 0);
            if (shiny > 0) {
                lines.add("Real shiny chance: " + trim(shiny) + "% for wild, static, gift, egg and trainer Pokemon");
            }
            String locke = o.string("locke", "none");
            if (!locke.equals("none")) {
                lines.add("Locke rules: " + (locke.equals("soft") ? "Soft-locke (Pokemon Centers do not revive fainted Pokemon)"
                        : "Locke (Pokemon Centers do not revive, revive items removed)"));
            }
            int eggs = o.object("eggBox").integer("count", 0);
            if (eggs > 0) {
                Json.Obj egg = o.object("eggBox");
                lines.add("Egg box: " + eggs + " egg(s), legendaries: " + egg.string("legendary", "any")
                        + (egg.list("stages").isEmpty() ? "" : ", evolution line length: " + plain(egg.list("stages")))
                        + (egg.bool("basicOnly", false) ? ", only Pokemon that do not evolve from another" : "")
                        + (egg.bool("duplicates", false) ? ", the same species may repeat" : ""));
            }
            for (Object s : o.list("shopItems")) {
                Json.Obj item = new Json.Obj((Map<String, Object>) s);
                lines.add("Extra shop item: " + itemName(rom, item.integer("id", 0)) + " for " + item.integer("price", 0));
            }
            if (!o.bool("incrementalMovesets", true)) {
                lines.add("Better movesets may use the strongest moves at any level (incremental movesets off)");
            }
            if ("unchanged".equals(o.string("trainerItems", "tiered"))) {
                lines.add("Trainer battle items: the game's own are kept");
            } else if (o.bool("regularTrainersKeepItems", false)) {
                lines.add("Trainer battle items: limited by tier and stage, ordinary trainers keep Potion and Super Potion");
            }
            if (o.bool("keepBasicShopItems", false)) {
                lines.add("Everyday shop supplies keep their place when shops are randomized");
            }
            List<Object> protectedItems = o.list("protectedItems");
            if (!protectedItems.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (Object id : protectedItems) {
                    names.add(itemName(rom, ((Number) id).intValue()));
                }
                lines.add("Items kept in place in shops: " + String.join(", ", names));
            }
            int floor = o.integer("boostFloor", 0);
            int boostPercentSummary = o.integer("boostPercent", 0);
            if (floor > 0) {
                lines.add(boostPercentSummary > 0
                        ? "Automatic stat boost: fully evolved Pokemon below a base stat total of " + floor + " are boosted by " + boostPercentSummary + "%"
                        : "Automatic stat floor: fully evolved Pokemon below a base stat total of " + floor + " are raised to it");
            }
            for (Object e : o.list("statEdits")) {
                Json.Obj edit = new Json.Obj((Map<String, Object>) e);
                String name = speciesName(rom, edit.integer("species", 0));
                if ("fine".equals(edit.string("mode", "quick"))) {
                    lines.add("Stat edit: " + name + " HP " + edit.integer("hp", 0) + ", Atk " + edit.integer("attack", 0)
                            + ", Def " + edit.integer("defense", 0) + ", SpA " + edit.integer("spatk", 0)
                            + ", SpD " + edit.integer("spdef", 0) + ", Spe " + edit.integer("speed", 0));
                } else {
                    int pct = edit.integer("percent", 0);
                    lines.add("Stat edit: " + name + " " + (pct >= 0 ? "+" : "") + pct + "% on the whole spread");
                }
            }
            for (Object e : o.list("evolutions")) {
                Json.Obj evo = new Json.Obj((Map<String, Object>) e);
                lines.add("Custom evolution: " + speciesName(rom, evo.integer("from", 0)) + " into "
                        + speciesName(rom, evo.integer("to", 0)) + " by " + evo.string("type", "?").toLowerCase(Locale.ROOT)
                        .replace('_', ' ') + (evo.integer("extra", 0) > 0 ? " (" + evo.integer("extra", 0) + ")" : ""));
            }
            for (Object e : o.list("removals")) {
                Json.Obj rem = new Json.Obj((Map<String, Object>) e);
                lines.add("Removed evolution: " + speciesName(rom, rem.integer("from", 0)) + " no longer evolves into "
                        + speciesName(rom, rem.integer("to", 0)));
            }
            if (o.bool("stoneEvolutionsAlsoLevel", false)) {
                lines.add("Stone evolutions can also be reached by leveling (Pokemon with a single evolution)");
            }
            int fully = o.integer("fullyEvolvedByLevel", 0);
            if (fully > 0) {
                lines.add("Every Pokemon is fully evolved by level " + fully);
            }
            Json.Obj scale = o.object("evolutionScale");
            if (scale.integer("percent", 0) > 0) {
                lines.add("Evolution level scale: level-up evolutions from level " + scale.integer("minLevel", 0)
                        + " are lowered by " + scale.integer("percent", 0) + "%");
            }
            Json.Obj mega = o.object("megaFinale");
            List<String> tiers = new ArrayList<>();
            if (mega.bool("gymLeaders", false)) {
                tiers.add("gym leaders");
            }
            if (mega.bool("league", false)) {
                tiers.add("Elite Four and Champion");
            }
            if (mega.bool("evilBosses", false)) {
                tiers.add("evil team bosses");
            }
            if (mega.bool("otherBosses", false)) {
                tiers.add("other boss trainers");
            }
            if (mega.bool("everyone", false)) {
                tiers.add("every trainer");
            }
            if (!tiers.isEmpty()) {
                lines.add("Mega finale: the last Pokemon of " + String.join(", ", tiers)
                        + (mega.integer("fromLevel", 0) > 0 ? ", only from level " + mega.integer("fromLevel", 0) + " onward" : "")
                        + (mega.bool("replace", false) ? "; a last Pokemon without a Mega may be replaced by one that has one" : ""));
            }
            for (Object r : o.list("battleStyleRules")) {
                Json.Obj rule = new Json.Obj((Map<String, Object>) r);
                lines.add("Battle style rule: " + rule.integer("chance", 0) + "% of " + rule.string("group", "?")
                        + " trainers fight as " + rule.string("style", "?").toLowerCase(Locale.ROOT).replace('_', ' '));
            }
        } catch (RuntimeException e) {
            lines.add("(the list of extra options could not be written: " + e + ")");
        }
        return lines;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String itemName(RomHandler rom, int id) {
        try {
            for (Item item : rom.getItems()) {
                if (item != null && item.getId() == id) {
                    return item.getName();
                }
            }
        } catch (RuntimeException ignored) {
            // falls through to the number
        }
        return "item #" + id;
    }

    private static String speciesName(RomHandler rom, int number) {
        try {
            for (Species s : rom.getSpeciesInclFormes()) {
                if (s != null && s.getNumber() == number) {
                    return s.getFullName();
                }
            }
        } catch (RuntimeException ignored) {
            // falls through to the number
        }
        return "Pokemon #" + number;
    }

    /** Used by tests: the YAO values as the JSON-shaped map the summary reads. */
    static Map<String, Object> asMap(YaoSettings yao) {
        return new LinkedHashMap<>(YaoJson.extract(yao));
    }
}
