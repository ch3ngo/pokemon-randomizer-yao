package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  RomSession.java - the game the web interface is working with.         --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.gamedata.basestats.BaseStats;
import com.uprfvx.romio.constants.EvolutionSupport;
import com.uprfvx.romio.romhandlers.FeatureAvailability;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romhandlers.RomIntegrityReport;
import com.uprfvx.romio.romio.RomOpener;
import com.uprfvx.random.yao.EggBoxSelector;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opens a game once, writes down everything the interface needs to know about it (what it is, which extra options it
 * supports, the names of its items, Pokemon and moves, which options apply) and lets the game go again. The real work
 * is done later by a separate program run, so a large 3DS game is not kept in memory next to it.
 */
final class RomSession {

    private final SettingsSchema schema;
    private final RomOpener opener;

    private String path;
    private Map<String, Object> info = new LinkedHashMap<>();
    private Map<String, Object> described;
    private List<Object> items = new ArrayList<>();
    private List<Object> species = new ArrayList<>();
    private List<Object> moves = new ArrayList<>();
    private List<Object> evolutionTypes = new ArrayList<>();
    private List<Object> trainerProgress = new ArrayList<>();
    private int generation;
    private int miscTweaks = -1;
    private String romName = "";
    private boolean loaded;

    RomSession(SettingsSchema schema, RomOpener opener) {
        this.schema = schema;
        this.opener = opener;
    }

    synchronized boolean isLoaded() {
        return loaded;
    }

    synchronized String path() {
        return path;
    }

    synchronized int generation() {
        return generation;
    }

    synchronized int miscTweaksAvailable() {
        return miscTweaks;
    }

    synchronized String romName() {
        return romName;
    }

    synchronized Map<String, Object> info() {
        return info;
    }

    synchronized Map<String, Object> schemaForRom() {
        return described;
    }

    synchronized List<Object> items() {
        return items;
    }

    synchronized List<Object> species() {
        return species;
    }

    synchronized List<Object> moves() {
        return moves;
    }

    /** Added (12/10/2026): the loaded game's gym leaders, league and bosses with their vanilla top level, in story order. */
    synchronized List<Object> trainerProgress() {
        return trainerProgress;
    }

    synchronized List<Object> evolutionTypes() {
        return evolutionTypes;
    }

    /** Opens a game and records what is needed. Returns an object with {@code ok} and either the info or an error. */
    synchronized Map<String, Object> load(String romPath) {
        Map<String, Object> result = new LinkedHashMap<>();
        loaded = false;
        File file = new File(romPath == null ? "" : romPath);
        if (!file.isFile()) {
            return failure("That file does not exist: " + romPath);
        }
        RomOpener.Results opened;
        try {
            opened = opener.openRomFile(file);
        } catch (Exception | OutOfMemoryError e) {
            return failure("The game could not be opened: " + e);
        }
        if (!opened.wasOpeningSuccessful()) {
            String reason = String.valueOf(opened.getFailType());
            String hint = switch (reason) {
                case "EXTRA_MEMORY_NOT_AVAILABLE" -> " This is a 3DS game and needs more memory: start the program with the "
                        + "launcher (it asks Java for 6 GB) or run it with -Xmx6g.";
                case "UNRECOGNIZED_FILE" -> " The file is not a game this program knows (check the region and that it is "
                        + "an unmodified dump).";
                case "UNSUPPORTED_ROM", "UNSUPPORTED_VERSION" -> " This version of the game is not supported.";
                default -> "";
            };
            return failure("The game could not be opened (" + reason + ")." + hint);
        }
        RomHandler rom = opened.getRomHandler();
        try {
            describe(rom, romPath);
        } catch (RuntimeException | StackOverflowError e) {
            return failure("The game was opened but could not be described: " + e);
        } finally {
            try {
                rom.closeResources();
            } catch (Exception ignored) {
                // nothing useful to do
            }
        }
        loaded = true;
        result.put("ok", true);
        result.put("game", info);
        return result;
    }

    private static Map<String, Object> failure(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("error", message);
        return result;
    }

    private static Map<String, Object> rangeMap(com.uprfvx.random.yao.PerSpeciesStatEditor.StatRange r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("min", r.min());
        m.put("max", r.max());
        return m;
    }

    private static Map<String, Object> availability(FeatureAvailability a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", a.available());
        m.put("reason", a.available() ? "" : a.reason());
        return m;
    }

    private void describe(RomHandler rom, String romPath) {
        this.path = romPath;
        this.generation = rom.generationOfPokemon();
        this.miscTweaks = rom.miscTweaksAvailable();
        this.romName = rom.getROMName();
        Map<String, Object> game = new LinkedHashMap<>();
        game.put("name", rom.getROMName());
        game.put("code", rom.getROMCode());
        game.put("generation", generation);
        game.put("extension", rom.getDefaultExtension());
        game.put("english", rom.isEnglish());
        game.put("threeDs", generation == 6 || generation == 7);
        game.put("path", romPath);
        RomIntegrityReport report = rom.getIntegrityReport();
        Map<String, Object> integrity = new LinkedHashMap<>();
        integrity.put("clean", report.isClean());
        integrity.put("text", report.isClean() ? "" : report.describe());
        game.put("integrity", integrity);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("shiny", availability(rom.checkShinyChance()));
        features.put("lockeCenters", availability(rom.checkCentersSkipFainted()));
        features.put("eggBox", availability(rom.checkEggBoxes()));
        features.put("playerSprites", availability(rom.checkPlayerSprites()));
        features.put("shopsResizable", rom.canChangeShopSizes());
        // Added (12/10/2026): shop randomization never touches the regular shops, so in some games it has nothing to change.
        boolean shopsRandomizable = false;
        try {
            List<com.uprfvx.romio.gamedata.Shop> shops = rom.hasShopSupport() ? rom.getShops() : null;
            shopsRandomizable = shops != null && shops.stream().anyMatch(sh -> sh.isSpecialShop());
        } catch (RuntimeException e) {
            // leave it false, the notice only informs
        }
        features.put("shopsRandomizable", shopsRandomizable);
        features.put("evolutionEditing", true);
        features.put("megas", rom.hasMegaEvolutions());
        // Added (12/10/2026): the styles battle style rules can use in this game (none in Gen 1 and 2).
        List<Object> battleStyles = new ArrayList<>();
        for (com.uprfvx.random.yao.YaoSettings.RuleStyle style : com.uprfvx.random.yao.BattleStyleRules.supportedStyles(generation)) {
            battleStyles.add(style.name());
        }
        features.put("battleStyles", battleStyles);
        // Added 11/10/2026 (web UI feedback item 14): the custom shop item price field needs this
        // game's actual limits (ShopPriceLimits), instead of a hardcoded range in the frontend.
        Map<String, Object> shopPriceLimits = new LinkedHashMap<>();
        shopPriceLimits.put("min", com.uprfvx.random.yao.ShopPriceLimits.minPrice(generation));
        shopPriceLimits.put("max", com.uprfvx.random.yao.ShopPriceLimits.maxPrice(generation));
        shopPriceLimits.put("step", com.uprfvx.random.yao.ShopPriceLimits.pricesAreMultiplesOfTen(generation) ? 10 : 1);
        features.put("shopPriceLimits", shopPriceLimits);
        // Added for YAO (11/10/2026): the per-species stat editor's fine-tune mode needs the real minimum and
        // maximum of each stat in this game (e.g. Shedinja's HP of 1), not a guessed range.
        com.uprfvx.random.yao.PerSpeciesStatEditor.Bounds bounds =
                com.uprfvx.random.yao.PerSpeciesStatEditor.computeBounds(rom.getSpeciesSetInclFormes());
        Map<String, Object> statBounds = new LinkedHashMap<>();
        statBounds.put("hp", rangeMap(bounds.hp()));
        statBounds.put("attack", rangeMap(bounds.attack()));
        statBounds.put("defense", rangeMap(bounds.defense()));
        statBounds.put("spatk", rangeMap(bounds.spatk()));
        statBounds.put("spdef", rangeMap(bounds.spdef()));
        statBounds.put("speed", rangeMap(bounds.speed()));
        features.put("statBounds", statBounds);
        // Added for YAO (11/10/2026): a real, median level-up evolution level from this game's own vanilla
        // species data, so the evolution level scale control can show a live "Level X -> approximately Level Y"
        // preview instead of an abstract percentage. Null when the game has no level-up evolutions at all.
        List<Integer> levelThresholds = new ArrayList<>();
        // Added for YAO (11/10/2026): the real evolution-stone items of this game, collected from its own
        // vanilla STONE-family evolutions (not a hardcoded per-generation item id table, which could drift).
        // Item.java has no general item-category field (isAllowed()/isBad()/isTM() only), so there is no
        // direct "is this a stone" flag to read; the vanilla evolution data itself is the reliable source.
        // STONE, STONE_MALE_ONLY, STONE_FEMALE_ONLY and STONE_ULTRA are all genuinely stone-triggered; the
        // other usesItem() types (TRADE_ITEM, ITEM_DAY/NIGHT, ITEM) are held or traded items, not stones.
        java.util.Set<Integer> stoneItemIds = new java.util.HashSet<>();
        for (Species sp : rom.getSpeciesSet()) {
            for (com.uprfvx.romio.gamedata.Evolution evo : sp.getEvolutionsFrom()) {
                EvolutionType type = evo.getType();
                if (type != null && type.name().startsWith("STONE") && evo.getExtraInfo() > 0) {
                    stoneItemIds.add(evo.getExtraInfo());
                }
                if (type != null && type.usesLevelThreshold() && evo.getExtraInfo() > 0) {
                    levelThresholds.add(evo.getExtraInfo());
                }
            }
        }
        Integer exampleEvolutionLevel = null;
        if (!levelThresholds.isEmpty()) {
            java.util.Collections.sort(levelThresholds);
            exampleEvolutionLevel = levelThresholds.get(levelThresholds.size() / 2);
        }
        features.put("exampleEvolutionLevel", exampleEvolutionLevel);
        game.put("features", features);
        this.info = game;

        List<Object> itemList = new ArrayList<>();
        List<Item> romItems = rom.getItems();
        for (int id = 0; id < romItems.size(); id++) {
            Item item = romItems.get(id);
            if (item != null && item.getName() != null && !item.getName().isBlank()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", id);
                m.put("name", item.getName());
                m.put("allowed", item.isAllowed());
                m.put("stone", stoneItemIds.contains(id));
                itemList.add(m);
            }
        }
        this.items = itemList;

        List<Object> speciesList = new ArrayList<>();
        List<Species> sorted = new ArrayList<>(rom.getSpeciesSet());
        sorted.sort(java.util.Comparator.comparingInt(Species::getNumber));
        for (Species sp : sorted) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("n", sp.getNumber());
            m.put("name", sp.getFullName());
            m.put("legendary", sp.isLegendary());
            m.put("basic", EggBoxSelector.isBasic(sp));
            m.put("stages", EggBoxSelector.lineLength(sp));
            // Added (12/10/2026): lets the interface count how many Pokemon an automatic stat floor would touch.
            m.put("final", sp.getEvolutionsFrom().isEmpty());
            BaseStats total = sp.getBaseStats();
            m.put("bst", total == null ? 0 : total.getHp() + total.getAttack() + total.getDefense()
                    + total.getSpatk() + total.getSpdef() + total.getSpeed());
            // Added for YAO (11/10/2026): the per-species stat editor shows a Pokemon's real current base stats
            // before editing them.
            BaseStats bs = sp.getBaseStats();
            if (bs != null) {
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("hp", bs.getHp());
                stats.put("attack", bs.getAttack());
                stats.put("defense", bs.getDefense());
                stats.put("spatk", bs.getSpatk());
                stats.put("spdef", bs.getSpdef());
                stats.put("speed", bs.getSpeed());
                m.put("bs", stats);
            }
            speciesList.add(m);
        }
        this.species = speciesList;

        List<Object> moveList = new ArrayList<>();
        List<Move> romMoves = rom.getMoves();
        for (int id = 0; id < romMoves.size(); id++) {
            Move move = romMoves.get(id);
            if (move != null && move.name != null && !move.name.isBlank()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", id);
                m.put("name", move.name);
                moveList.add(m);
            }
        }
        this.moves = moveList;

        List<Object> types = new ArrayList<>();
        for (EvolutionType type : EvolutionType.values()) {
            if (type == EvolutionType.NONE || type == EvolutionType.ITEM || !EvolutionSupport.supports(generation, type)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", type.name());
            m.put("label", SettingsSchema.prettify(type.name()));
            m.put("level", type.usesLevelThreshold());
            m.put("item", type.usesItem());
            m.put("move", type.usesMove());
            m.put("species", type.usesSpecies());
            types.add(m);
        }
        this.evolutionTypes = types;

        List<Object> typeNames = new ArrayList<>();
        for (Type t : Type.getAllTypes(generation)) {
            typeNames.add(t.name());
        }
        game.put("types", typeNames);
        this.trainerProgress = buildTrainerProgress(rom);
        this.described = schema.describe(rom);
    }

    /**
     * Added (12/10/2026): gym leaders (by gym number), then league, then evil-team bosses, then other bosses (by trainer
     * index), each with the highest level of its Pokemon in the unmodified game. Never throws: a game whose trainer
     * data cannot be read just gives an empty list.
     */
    private static List<Object> buildTrainerProgress(RomHandler rom) {
        List<Object> out = new ArrayList<>();
        try {
            int gen = rom.generationOfPokemon();
            int romType = rom.getROMType();
            record Entry(int order, int index, String tier, String name, int level) {
            }
            List<Entry> entries = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (com.uprfvx.romio.gamedata.Trainer t : rom.getTrainers()) {
                if (t == null || t.getPokemon() == null || t.getPokemon().isEmpty()) {
                    continue;
                }
                com.uprfvx.random.yao.TrainerGroups.Group tier = com.uprfvx.random.yao.TrainerGroups.tierOf(t, gen, romType);
                if (tier == null) {
                    continue;
                }
                String tag = t.getTag() == null ? "" : t.getTag();
                String family = tag.replaceFirst("^((?:RIVAL|FRIEND)\\d+)-\\d+$", "$1");
                if (!seen.add(tier + "|" + family)) {
                    continue;
                }
                int order = 0;
                if (tier == com.uprfvx.random.yao.TrainerGroups.Group.GYM_LEADERS) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(?:GYM|GIO)(\\d*)").matcher(tag);
                    order = m.find() && !m.group(1).isEmpty() ? Integer.parseInt(m.group(1)) : 99;
                }
                String name = t.getFullDisplayName();
                if (name == null || name.isBlank() || "NAMELESS".equals(name)) {
                    name = tag;
                }
                String key = switch (tier) {
                    case GYM_LEADERS -> "gym";
                    case LEAGUE -> "league";
                    case EVIL_BOSSES -> "evil";
                    default -> "boss";
                };
                entries.add(new Entry(order, t.getIndex(), key, name, t.getHighestLevel()));
            }
            List<String> tierOrder = List.of("gym", "league", "evil", "boss");
            entries.sort(java.util.Comparator.comparingInt((Entry e) -> tierOrder.indexOf(e.tier()))
                    .thenComparingInt(Entry::order).thenComparingInt(Entry::index));
            for (Entry e : entries) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tier", e.tier());
                m.put("name", e.name());
                m.put("level", e.level());
                out.add(m);
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        return out;
    }
}
