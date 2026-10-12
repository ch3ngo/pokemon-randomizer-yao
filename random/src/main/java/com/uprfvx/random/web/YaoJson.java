package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  YaoJson.java - the extra options of this program, to and from JSON.   --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.random.yao.YaoSettings;
import com.uprfvx.romio.gamedata.EvolutionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the interface's choices for the extra options ({@link YaoSettings}) and writes them back. Everything goes
 * through the setters of {@code YaoSettings}, which repair or drop values that make no sense and say so in the
 * settings' warnings, so a bad request cannot produce a broken settings object.
 */
final class YaoJson {

    private YaoJson() {
    }

    static void apply(YaoSettings yao, Json.Obj in) {
        if (in.has("shinyPercent")) {
            yao.setShinyChancePercent(in.number("shinyPercent", 0));
        }
        if (in.has("locke")) {
            YaoSettings.LockeMode mode = YaoSettings.parseLocke(in.string("locke", "none"));
            yao.setLockeMode(mode == null ? YaoSettings.LockeMode.NONE : mode);
        }
        yao.setKeepBasicShopItems(in.bool("keepBasicShopItems", false));
        for (Object id : in.list("protectedItems")) {
            if (id instanceof Number n) {
                yao.addProtectedShopItem(n.intValue());
            }
        }
        for (Object entry : in.list("shopItems")) {
            if (entry instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked") Json.Obj o = new Json.Obj((Map<String, Object>) m);
                yao.addCustomShopItem(o.integer("id", 0), o.integer("price", 1));
            }
        }
        if (in.has("boostFloor")) {
            yao.setBoostFloorBst(in.integer("boostFloor", 0));
        }
        for (Object entry : in.list("statEdits")) {
            if (entry instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked") Json.Obj o = new Json.Obj((Map<String, Object>) m);
                if ("fine".equals(o.string("mode", "quick"))) {
                    yao.addFineTuneStatEdit(o.integer("species", 0), o.integer("hp", 0), o.integer("attack", 0),
                            o.integer("defense", 0), o.integer("spatk", 0), o.integer("spdef", 0),
                            o.integer("speed", 0));
                } else {
                    yao.addQuickStatEdit(o.integer("species", 0), o.integer("percent", 0));
                }
            }
        }
        for (Object entry : in.list("removals")) {
            if (entry instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked") Json.Obj o = new Json.Obj((Map<String, Object>) m);
                yao.addEvolutionRemoval(o.integer("from", 0), o.integer("to", 0));
            }
        }
        for (Object entry : in.list("evolutions")) {
            if (entry instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked") Json.Obj o = new Json.Obj((Map<String, Object>) m);
                EvolutionType type = null;
                try {
                    type = EvolutionType.valueOf(o.string("type", ""));
                } catch (IllegalArgumentException e) {
                    yao.addWarning("An evolution with the unknown method \"" + o.string("type", "") + "\" was ignored.");
                }
                if (type != null) {
                    yao.addEvolutionEdit(o.integer("from", 0), o.integer("to", 0), type, o.integer("extra", 0));
                }
            }
        }
        Json.Obj scale = in.object("evolutionScale");
        if (scale.has("percent") && scale.integer("percent", 0) > 0) {
            yao.setEvolutionLevelScale(scale.integer("minLevel", 30), scale.integer("percent", 0));
        }
        Json.Obj sprites = in.object("playerSprites");
        if (sprites.has("male") || sprites.has("female")) {
            yao.setPlayerSprites(sprites.string("male", ""), sprites.string("female", ""));
        }
        // Modified (12/10/2026): megaFinale is an object of independent tiers plus a start level.
        Json.Obj mega = in.object("megaFinale");
        if (mega.has("gymLeaders") || mega.has("league") || mega.has("evilBosses") || mega.has("otherBosses")
                || mega.has("everyone") || mega.has("fromLevel")) {
            yao.setMegaFinaleTiers(mega.bool("gymLeaders", false), mega.bool("league", false), mega.bool("evilBosses", false),
                    mega.bool("otherBosses", false), mega.bool("everyone", false), mega.integer("fromLevel", 0));
            yao.setMegaFinaleReplace(mega.bool("replace", false));
        }
        for (Object entry : in.list("battleStyleRules")) {
            if (entry instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked") Json.Obj o = new Json.Obj((Map<String, Object>) m);
                yao.addBattleStyleRule(com.uprfvx.random.yao.TrainerGroups.Group.parse(o.string("group", "")),
                        YaoSettings.parseRuleStyle(o.string("style", "")), o.integer("chance", 0));
            }
        }
        if (in.has("fullyEvolvedByLevel")) {
            yao.setFullyEvolvedByLevel(in.integer("fullyEvolvedByLevel", 0));
        }
        Json.Obj egg = in.object("eggBox");
        if (egg.has("count")) {
            yao.setEggBoxCount(egg.integer("count", 0));
            switch (egg.string("legendary", "any").toLowerCase(Locale.ROOT)) {
                case "none" -> yao.setEggBoxLegendary(YaoSettings.EggLegendary.NONE);
                case "only" -> yao.setEggBoxLegendary(YaoSettings.EggLegendary.ONLY);
                default -> yao.setEggBoxLegendary(YaoSettings.EggLegendary.ANY);
            }
            List<Integer> stages = new ArrayList<>();
            for (Object s : egg.list("stages")) {
                if (s instanceof Number n) {
                    stages.add(n.intValue());
                }
            }
            yao.setEggBoxStages(stages);
            yao.setEggBoxBasicOnly(egg.bool("basicOnly", false));
            yao.setEggBoxDuplicates(egg.bool("duplicates", false));
        }
    }

    static Map<String, Object> extract(YaoSettings yao) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shinyPercent", yao.getShinyChancePercent());
        out.put("locke", yao.getLockeMode().name().toLowerCase(Locale.ROOT));
        out.put("keepBasicShopItems", yao.isKeepBasicShopItems());
        out.put("protectedItems", new ArrayList<>(yao.getProtectedShopItems()));
        List<Object> shop = new ArrayList<>();
        for (YaoSettings.CustomShopItem item : yao.getCustomShopItems()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.itemId());
            m.put("price", item.price());
            shop.add(m);
        }
        out.put("shopItems", shop);
        out.put("boostFloor", yao.getBoostFloorBst());
        List<Object> statEdits = new ArrayList<>();
        for (YaoSettings.StatEdit e : yao.getStatEdits()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("species", e.speciesNumber());
            m.put("mode", e.mode() == YaoSettings.StatEditMode.FINE_TUNE ? "fine" : "quick");
            m.put("percent", e.percent());
            m.put("hp", e.hp());
            m.put("attack", e.attack());
            m.put("defense", e.defense());
            m.put("spatk", e.spatk());
            m.put("spdef", e.spdef());
            m.put("speed", e.speed());
            statEdits.add(m);
        }
        out.put("statEdits", statEdits);
        List<Object> evolutions = new ArrayList<>();
        for (YaoSettings.EvolutionEdit e : yao.getEvolutionEdits()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", e.fromSpecies());
            m.put("to", e.toSpecies());
            m.put("type", e.type().name());
            m.put("extra", e.extra());
            evolutions.add(m);
        }
        out.put("evolutions", evolutions);
        List<Object> removals = new ArrayList<>();
        for (YaoSettings.EvolutionRemoval r : yao.getEvolutionRemovals()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", r.fromSpecies());
            m.put("to", r.toSpecies());
            removals.add(m);
        }
        out.put("removals", removals);
        Map<String, Object> scale = new LinkedHashMap<>();
        scale.put("minLevel", yao.getEvolutionScaleMinLevel());
        scale.put("percent", yao.getEvolutionScalePercent());
        out.put("evolutionScale", scale);
        Map<String, Object> sprites = new LinkedHashMap<>();
        sprites.put("male", yao.getPlayerSpriteMale());
        sprites.put("female", yao.getPlayerSpriteFemale());
        out.put("playerSprites", sprites);
        Map<String, Object> mega = new LinkedHashMap<>();
        mega.put("gymLeaders", yao.isMegaGymLeaders());
        mega.put("league", yao.isMegaLeague());
        mega.put("evilBosses", yao.isMegaEvilBosses());
        mega.put("otherBosses", yao.isMegaOtherBosses());
        mega.put("everyone", yao.isMegaEveryone());
        mega.put("replace", yao.isMegaFinaleReplace());
        mega.put("fromLevel", yao.getMegaFromLevel());
        out.put("megaFinale", mega);
        List<Object> rules = new ArrayList<>();
        for (YaoSettings.BattleStyleRule r : yao.getBattleStyleRules()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("group", r.group().key());
            m.put("style", r.style().name());
            m.put("chance", r.chance());
            rules.add(m);
        }
        out.put("battleStyleRules", rules);
        out.put("fullyEvolvedByLevel", yao.getFullyEvolvedByLevel());
        Map<String, Object> egg = new LinkedHashMap<>();
        egg.put("count", yao.getEggBoxCount());
        egg.put("legendary", yao.getEggBoxLegendary().name().toLowerCase(Locale.ROOT));
        egg.put("stages", new ArrayList<>(yao.getEggBoxStages()));
        egg.put("basicOnly", yao.isEggBoxBasicOnly());
        egg.put("duplicates", yao.isEggBoxDuplicates());
        out.put("eggBox", egg);
        out.put("warnings", new ArrayList<>(yao.getWarnings()));
        return out;
    }
}
