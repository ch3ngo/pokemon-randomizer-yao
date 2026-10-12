package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  YaoSettings.java - settings that only exist in Pokemon Randomizer YAO. --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), itself built on         --*/
/*--  "Universal Pokemon Randomizer ZX" and "Universal Pokemon Randomizer"  --*/
/*--  by Dabomstew.                                                         --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings added by this fork. They are kept out of the upstream settings string and file layout on
 * purpose: upstream's format is fixed-length and checksummed, so extending it would break compatibility
 * and make every rebase onto upstream painful. Instead these settings are serialized as a small
 * text block that is appended after the upstream data, which older parsers simply ignore.
 *
 * <p>Text format, one {@code key=value} per line: {@code yao.format=1}, and
 * {@code shop.<n>=<itemId>:<price>} for each extra shop item. Unknown keys are ignored, so
 * later versions can add settings without breaking this reader.
 *
 * <p><b>Nothing here ever throws because of a bad value.</b> A value that can be repaired is repaired
 * to the closest valid one, a value that cannot is dropped, and either way a message is added to
 * {@link #getWarnings()} so the user can be told. A damaged YAO block must never stop the rest of the
 * settings from loading, nor stop a randomization.
 */
public class YaoSettings {

    /** Marker that starts the extension block inside a settings file (after the upstream data). */
    public static final byte[] FILE_MAGIC = "YAO1".getBytes(StandardCharsets.US_ASCII);

    /** Separator between the upstream settings string and the YAO block in a settings string. */
    public static final char STRING_SEPARATOR = '~';

    public static final int MAX_CUSTOM_SHOP_ITEMS = 64;

    /** Prices above this are clamped when read. The per-generation limit is applied later. */
    public static final int MAX_REASONABLE_PRICE = 10_000_000;

    private static final int FORMAT = 1;

    /**
     * Challenge run rules. NONE changes nothing. SOFT keeps revive items but stops Pokemon Centers from reviving
     * fainted Pokemon. FULL also removes the revive items (Revive, Max Revive, Revival Herb, Sacred Ash) from
     * shops, field items and pickup, so a fainted Pokemon stays fainted.
     */
    public enum LockeMode { NONE, SOFT, FULL }

    /**
     * An item to add to the regular shops, and the price it should have.
     * The item id is FVX's cross-generation item id (see {@code ItemIDs}).
     * Always valid: use {@link YaoSettings#addCustomShopItem} to build one from untrusted input.
     */
    public record CustomShopItem(int itemId, int price) {
        public CustomShopItem {
            if (itemId <= 0 || price <= 0) {
                throw new IllegalArgumentException("Invalid custom shop item: " + itemId + ":" + price);
            }
        }
    }

    // keyed by item id so that an item can only be listed once; the last one set wins
    private final Map<Integer, CustomShopItem> customShopItems = new LinkedHashMap<>();

    private LockeMode lockeMode = LockeMode.NONE;

    // ---- Stat floor: raise every fully evolved Pokemon below a base stat total up to it ----
    public static final int MAX_BASE_STAT_TOTAL = 1530; // 255 in each of the six stats
    private int boostFloorBst = 0; // 0 = off

    // ---- Egg boxes: the game hands over a number of randomized eggs ----
    public enum EggLegendary { ANY, NONE, ONLY }
    public static final int MAX_EGG_BOX = 90;
    private int eggBoxCount = 0; // 0 = off
    private EggLegendary eggBoxLegendary = EggLegendary.ANY;
    private final java.util.TreeSet<Integer> eggBoxStages = new java.util.TreeSet<>(); // evolution line lengths, empty = any
    private boolean eggBoxBasicOnly = false;
    private boolean eggBoxDuplicates = false;

    // ---- Player sprites: paths of the sprite sheets that replace the player's walking sprites ----
    private String playerSpriteMale = "";
    private String playerSpriteFemale = "";

    // ---- Mega finale: the last Pokemon of trainers is a Mega Evolution ----
    // Modified (12/10/2026): independent tiers instead of one scope, plus a "from this level onward" threshold.
    private boolean megaGymLeaders = false;
    private boolean megaLeague = false;
    private boolean megaEvilBosses = false;
    private boolean megaOtherBosses = false;
    private boolean megaEveryone = false;
    private boolean megaFinaleReplace = false;
    private int megaFromLevel = 0; // 0 = from the start

    // ---- Battle style rules (12/10/2026): a chance that a trainer group fights as a double, triple or rotation battle ----
    public static final int MAX_BATTLE_STYLE_RULES = 8;
    public enum RuleStyle { DOUBLE, TRIPLE, ROTATION }

    public record BattleStyleRule(TrainerGroups.Group group, RuleStyle style, int chance) {
    }

    private final List<BattleStyleRule> battleStyleRules = new ArrayList<>();

    // ---- Fully evolved by level (12/10/2026): independent of "Make Evolutions Easier"; 0 = off ----
    public static final int MIN_FULLY_EVOLVED_LEVEL = 15;
    public static final int MAX_FULLY_EVOLVED_LEVEL = 100;
    private int fullyEvolvedByLevel = 0;

    // ---- Custom evolutions ----
    public static final int MAX_EVOLUTION_EDITS = 256;

    /** Makes {@code from} evolve into {@code to} by {@code type} (the number is the level, item, move, species...). */
    public record EvolutionEdit(int fromSpecies, int toSpecies, com.uprfvx.romio.gamedata.EvolutionType type, int extra) {
    }

    /** Takes away the evolution of {@code from} into {@code to}. */
    public record EvolutionRemoval(int fromSpecies, int toSpecies) {
    }

    private final List<EvolutionEdit> evolutionEdits = new ArrayList<>();
    private final List<EvolutionRemoval> evolutionRemovals = new ArrayList<>();
    private int evolutionScaleMinLevel = 0;  // level evolutions at or above this level are lowered...
    private int evolutionScalePercent = 0;   // ...by this percentage; 0 = off

    // Shop randomization leaves these alone: the everyday supplies when keepBasicShopItems is on, plus any
    // items listed explicitly (see ProtectedShopItems).
    private boolean keepBasicShopItems = false;
    private final java.util.Set<Integer> protectedShopItems = new java.util.LinkedHashSet<>();

    // 0 to 1000, in tenths of a percent (so 1000 = 100.0 percent): how many Pokemon should be shiny; 0 leaves the game unchanged
    private int shinyChancePermille = 0;

    // Per run only, never serialized: whether the user accepted the code patch notice (see CodePatchDisclaimer).
    // Keeping it out of the settings file means a shared file or string cannot accept the notice for someone else.
    private boolean codePatchesAccepted = false;

    // what had to be repaired or dropped while reading; never serialized
    private final List<String> warnings = new ArrayList<>();

    // what the randomization actually did with these settings, for the log; never serialized
    private final List<String> runNotes = new ArrayList<>();

    public List<CustomShopItem> getCustomShopItems() {
        return Collections.unmodifiableList(new ArrayList<>(customShopItems.values()));
    }

    public boolean isKeepBasicShopItems() {
        return keepBasicShopItems;
    }

    public void setKeepBasicShopItems(boolean keep) {
        this.keepBasicShopItems = keep;
    }

    public java.util.Set<Integer> getProtectedShopItems() {
        return Collections.unmodifiableSet(protectedShopItems);
    }

    /** Adds an item that shop randomization must leave in place. An invalid id is dropped with a warning. */
    public boolean addProtectedShopItem(int itemId) {
        if (itemId <= 0) {
            warnings.add("Protected shop item with invalid id " + itemId + " was ignored.");
            return false;
        }
        if (protectedShopItems.size() >= MAX_CUSTOM_SHOP_ITEMS && !protectedShopItems.contains(itemId)) {
            warnings.add("Only " + MAX_CUSTOM_SHOP_ITEMS + " protected shop items are supported; item #" + itemId
                    + " was ignored.");
            return false;
        }
        protectedShopItems.add(itemId);
        return true;
    }

    /** Everything shop randomization must leave in place: the basics if switched on, plus the explicit items. */
    public java.util.Set<Integer> effectiveProtectedShopItems() {
        java.util.Set<Integer> all = new java.util.LinkedHashSet<>(protectedShopItems);
        if (keepBasicShopItems) {
            all.addAll(ProtectedShopItems.BASICS);
        }
        return all;
    }

    // ---------------- player sprites ----------------

    public String getPlayerSpriteMale() {
        return playerSpriteMale;
    }

    public String getPlayerSpriteFemale() {
        return playerSpriteFemale;
    }

    /** Paths of the sprite sheets (PNG) for the boy and the girl; empty keeps the game's own. */
    public void setPlayerSprites(String male, String female) {
        playerSpriteMale = clean(male);
        playerSpriteFemale = clean(female);
    }

    private static String clean(String path) {
        return path == null ? "" : path.replace('\n', ' ').replace('\r', ' ').strip();
    }

    // ---------------- mega finale ----------------

    public boolean isMegaGymLeaders() {
        return megaGymLeaders;
    }

    public boolean isMegaLeague() {
        return megaLeague;
    }

    public boolean isMegaEvilBosses() {
        return megaEvilBosses;
    }

    public boolean isMegaOtherBosses() {
        return megaOtherBosses;
    }

    public boolean isMegaEveryone() {
        return megaEveryone;
    }

    public int getMegaFromLevel() {
        return megaFromLevel;
    }

    /** Which trainers get the mega finale. {@code fromLevel} outside 0 to 100 is clamped, with a warning. */
    public void setMegaFinaleTiers(boolean gymLeaders, boolean league, boolean evilBosses, boolean otherBosses,
                                   boolean everyone, int fromLevel) {
        megaGymLeaders = gymLeaders;
        megaLeague = league;
        megaEvilBosses = evilBosses;
        megaOtherBosses = otherBosses;
        megaEveryone = everyone;
        int fixed = Math.max(0, Math.min(100, fromLevel));
        if (fixed != fromLevel) {
            warnings.add("Mega finale start level " + fromLevel + " is not valid; using " + fixed + " instead.");
        }
        megaFromLevel = fixed;
    }

    /** True if the mega finale is on for at least one tier. */
    public boolean isMegaFinaleOn() {
        return megaGymLeaders || megaLeague || megaEvilBosses || megaOtherBosses || megaEveryone;
    }

    public MegaFinaleApplier.Selection getMegaFinaleSelection() {
        return new MegaFinaleApplier.Selection(megaGymLeaders, megaLeague, megaEvilBosses, megaOtherBosses,
                megaEveryone, megaFromLevel);
    }

    public boolean isMegaFinaleReplace() {
        return megaFinaleReplace;
    }

    /** Replace a last Pokemon that has no Mega Evolution by one that has. */
    public void setMegaFinaleReplace(boolean replace) {
        megaFinaleReplace = replace;
    }

    /**
     * Converts the single-choice mega finale scope of the older settings format ("none", "leaders", "boss", "all")
     * into tiers, adding to whatever is already set. Returns false if the text is not a known scope. "boss" kept its
     * old meaning (gym leaders, league and evil-team bosses; it never covered rivals).
     */
    static boolean applyOldMegaFinaleScope(YaoSettings settings, String text) {
        if (text == null) {
            return false;
        }
        switch (text.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "none", "off" -> {
            }
            case "leaders", "leader", "leaders-league", "leaders_and_league", "gymleaders" -> {
                settings.megaGymLeaders = true;
                settings.megaLeague = true;
            }
            case "boss", "bosses" -> {
                settings.megaGymLeaders = true;
                settings.megaLeague = true;
                settings.megaEvilBosses = true;
            }
            case "all" -> settings.megaEveryone = true;
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------- battle style rules ----------------

    public List<BattleStyleRule> getBattleStyleRules() {
        return java.util.Collections.unmodifiableList(battleStyleRules);
    }

    /**
     * Adds a rule. A chance outside 1 to 100 is clamped with a warning, a null group or style is dropped with a
     * warning, and rules beyond {@link #MAX_BATTLE_STYLE_RULES} are dropped with a warning. Returns true if added.
     */
    public boolean addBattleStyleRule(TrainerGroups.Group group, RuleStyle style, int chance) {
        if (group == null || style == null) {
            warnings.add("A battle style rule with an unknown trainer group or style was ignored.");
            return false;
        }
        if (battleStyleRules.size() >= MAX_BATTLE_STYLE_RULES) {
            warnings.add("Only " + MAX_BATTLE_STYLE_RULES + " battle style rules are used; the extra ones were ignored.");
            return false;
        }
        int fixed = Math.max(1, Math.min(100, chance));
        if (fixed != chance) {
            warnings.add("Battle style chance " + chance + " is not valid; using " + fixed + " instead.");
        }
        battleStyleRules.add(new BattleStyleRule(group, style, fixed));
        return true;
    }

    public static RuleStyle parseRuleStyle(String text) {
        if (text == null) {
            return null;
        }
        switch (text.strip().toLowerCase(java.util.Locale.ROOT).replace("_battle", "")) {
            case "double" -> {
                return RuleStyle.DOUBLE;
            }
            case "triple" -> {
                return RuleStyle.TRIPLE;
            }
            case "rotation" -> {
                return RuleStyle.ROTATION;
            }
            default -> {
                return null;
            }
        }
    }

    // ---------------- fully evolved by level ----------------

    public int getFullyEvolvedByLevel() {
        return fullyEvolvedByLevel;
    }

    /** 0 turns it off; otherwise the level every Pokemon is fully evolved by (15 to 100, clamped with a warning). */
    public void setFullyEvolvedByLevel(int level) {
        if (level <= 0) {
            fullyEvolvedByLevel = 0;
            return;
        }
        int fixed = Math.max(MIN_FULLY_EVOLVED_LEVEL, Math.min(MAX_FULLY_EVOLVED_LEVEL, level));
        if (fixed != level) {
            warnings.add("Fully evolved by level " + level + " is not valid; using " + fixed + " instead.");
        }
        fullyEvolvedByLevel = fixed;
    }

    // ---------------- egg boxes ----------------

    public int getEggBoxCount() {
        return eggBoxCount;
    }

    /** How many eggs the game hands over, 0 to {@value #MAX_EGG_BOX}; 0 turns the feature off. Out of range values are moved. */
    public void setEggBoxCount(int count) {
        int fixed = Math.max(0, Math.min(MAX_EGG_BOX, count));
        if (fixed != count) {
            warnings.add("Egg box size " + count + " is not valid; using " + fixed + " instead.");
        }
        eggBoxCount = fixed;
    }

    public EggLegendary getEggBoxLegendary() {
        return eggBoxLegendary;
    }

    public void setEggBoxLegendary(EggLegendary mode) {
        eggBoxLegendary = mode == null ? EggLegendary.ANY : mode;
    }

    /** The evolution line lengths (1 to 3) the eggs may have; empty means any. */
    public java.util.Set<Integer> getEggBoxStages() {
        return Collections.unmodifiableSet(eggBoxStages);
    }

    public void setEggBoxStages(java.util.Collection<Integer> stages) {
        eggBoxStages.clear();
        if (stages == null) {
            return;
        }
        for (Integer stage : stages) {
            if (stage != null && stage >= 1 && stage <= 3) {
                eggBoxStages.add(stage);
            } else {
                warnings.add("Egg line length " + stage + " is not valid (1 to 3) and was ignored.");
            }
        }
    }

    public boolean isEggBoxBasicOnly() {
        return eggBoxBasicOnly;
    }

    /** Only Pokemon that do not evolve from anything (the way eggs normally are). */
    public void setEggBoxBasicOnly(boolean basicOnly) {
        eggBoxBasicOnly = basicOnly;
    }

    public boolean isEggBoxDuplicates() {
        return eggBoxDuplicates;
    }

    /** Whether the same species may appear more than once in the box. */
    public void setEggBoxDuplicates(boolean duplicates) {
        eggBoxDuplicates = duplicates;
    }

    // ---------------- stat floor ----------------

    public int getBoostFloorBst() {
        return boostFloorBst;
    }

    /** Every Pokemon below this base stat total is raised to it. 0 turns it off; above 1530 is moved to 1530. */
    public void setBoostFloorBst(int floor) {
        int fixed = Math.max(0, Math.min(MAX_BASE_STAT_TOTAL, floor));
        if (fixed != floor) {
            warnings.add("Boost floor " + floor + " is not valid; using " + fixed + " instead.");
        }
        boostFloorBst = fixed;
    }

    // ---------------- per-species stat edits ----------------
    // Added for YAO (11/10/2026): a precise, single-Pokemon alternative to the whole-dex booster above. See
    // PerSpeciesStatEditor for how an edit is actually applied to a loaded game.

    /** The game's own universal per-stat cap (same as {@code BaseStats.STAT_MAX}); the real, narrower minimum and
     * maximum for a given stat in the loaded game are applied later, by {@link PerSpeciesStatEditor}. */
    public static final int MAX_STAT_VALUE = 255;

    public static final int MAX_STAT_EDITS = 256;
    public static final int MIN_QUICK_STAT_PERCENT = -90;
    public static final int MAX_QUICK_STAT_PERCENT = 300;

    public enum StatEditMode { QUICK, FINE_TUNE }

    /**
     * One Pokemon's stat edit. In {@link StatEditMode#QUICK}, {@code percent} is the only field that matters (the
     * six stats are 0); in {@link StatEditMode#FINE_TUNE}, the six stats are the only fields that matter.
     */
    public record StatEdit(int speciesNumber, StatEditMode mode, int percent, int hp, int attack, int defense,
                            int spatk, int spdef, int speed) {
    }

    // keyed by species number so a Pokemon can only have one edit; the last one set wins
    private final Map<Integer, StatEdit> statEdits = new LinkedHashMap<>();

    public List<StatEdit> getStatEdits() {
        return Collections.unmodifiableList(new ArrayList<>(statEdits.values()));
    }

    /**
     * Quick mode: scale this Pokemon's whole stat spread by a percentage, keeping its shape, the same way as the
     * whole-dex booster but scoped to one species. Unlike the booster this can also lower stats (down to -90%),
     * since this is meant as a precise, deliberate edit rather than a "never make a Pokemon weaker" bulk pass.
     */
    public boolean addQuickStatEdit(int speciesNumber, int percent) {
        if (speciesNumber <= 0) {
            warnings.add("Stat edit for an invalid Pokemon (" + speciesNumber + ") was ignored.");
            return false;
        }
        if (!statEdits.containsKey(speciesNumber) && statEdits.size() >= MAX_STAT_EDITS) {
            warnings.add("Only " + MAX_STAT_EDITS + " stat edits are supported; Pokemon #" + speciesNumber
                    + " was ignored.");
            return false;
        }
        int fixed = Math.max(MIN_QUICK_STAT_PERCENT, Math.min(MAX_QUICK_STAT_PERCENT, percent));
        if (fixed != percent) {
            warnings.add("Stat edit percentage " + percent + " for Pokemon #" + speciesNumber + " is not valid; "
                    + "using " + fixed + " instead.");
        }
        statEdits.put(speciesNumber, new StatEdit(speciesNumber, StatEditMode.QUICK, fixed, 0, 0, 0, 0, 0, 0));
        return true;
    }

    /**
     * Fine-tune mode: set this Pokemon's six base stats to exact values. Each value is clamped here only to the
     * game-wide 0 to {@value #MAX_STAT_VALUE} range; the real, narrower bounds for the loaded game (for example
     * Shedinja's HP of 1, or this game's own highest Speed) are applied later by {@link PerSpeciesStatEditor}, which
     * is the only place that actually knows the loaded game's data.
     */
    public boolean addFineTuneStatEdit(int speciesNumber, int hp, int attack, int defense, int spatk, int spdef,
                                        int speed) {
        if (speciesNumber <= 0) {
            warnings.add("Stat edit for an invalid Pokemon (" + speciesNumber + ") was ignored.");
            return false;
        }
        if (!statEdits.containsKey(speciesNumber) && statEdits.size() >= MAX_STAT_EDITS) {
            warnings.add("Only " + MAX_STAT_EDITS + " stat edits are supported; Pokemon #" + speciesNumber
                    + " was ignored.");
            return false;
        }
        int[] raw = {hp, attack, defense, spatk, spdef, speed};
        int[] fixed = new int[6];
        boolean repaired = false;
        for (int i = 0; i < 6; i++) {
            fixed[i] = Math.max(0, Math.min(MAX_STAT_VALUE, raw[i]));
            repaired |= fixed[i] != raw[i];
        }
        if (repaired) {
            warnings.add("Stat edit for Pokemon #" + speciesNumber + " had a stat outside 0-" + MAX_STAT_VALUE
                    + "; it was moved into range.");
        }
        statEdits.put(speciesNumber, new StatEdit(speciesNumber, StatEditMode.FINE_TUNE, 0, fixed[0], fixed[1],
                fixed[2], fixed[3], fixed[4], fixed[5]));
        return true;
    }

    // ---------------- custom evolutions ----------------

    public List<EvolutionEdit> getEvolutionEdits() {
        return Collections.unmodifiableList(evolutionEdits);
    }

    public List<EvolutionRemoval> getEvolutionRemovals() {
        return Collections.unmodifiableList(evolutionRemovals);
    }

    /** Adds an evolution to set. Invalid input (ids, type NONE or ITEM) is dropped with a warning. */
    public boolean addEvolutionEdit(int from, int to, com.uprfvx.romio.gamedata.EvolutionType type, int extra) {
        if (from <= 0 || to <= 0 || from == to) {
            warnings.add("Evolution edit " + from + " to " + to + " is not valid and was ignored.");
            return false;
        }
        if (type == null || type == com.uprfvx.romio.gamedata.EvolutionType.NONE
                || type == com.uprfvx.romio.gamedata.EvolutionType.ITEM) {
            warnings.add("Evolution method " + type + " cannot be used and the edit " + from + " to " + to
                    + " was ignored.");
            return false;
        }
        if (evolutionEdits.size() >= MAX_EVOLUTION_EDITS) {
            warnings.add("Only " + MAX_EVOLUTION_EDITS + " evolution edits are supported; one was ignored.");
            return false;
        }
        // the same pair twice: the later one replaces the earlier one
        evolutionEdits.removeIf(e -> e.fromSpecies() == from && e.toSpecies() == to);
        evolutionEdits.add(new EvolutionEdit(from, to, type, Math.max(0, extra)));
        return true;
    }

    public boolean addEvolutionRemoval(int from, int to) {
        if (from <= 0 || to <= 0 || from == to) {
            warnings.add("Evolution removal " + from + " to " + to + " is not valid and was ignored.");
            return false;
        }
        if (evolutionRemovals.size() >= MAX_EVOLUTION_EDITS) {
            warnings.add("Only " + MAX_EVOLUTION_EDITS + " evolution removals are supported; one was ignored.");
            return false;
        }
        EvolutionRemoval removal = new EvolutionRemoval(from, to);
        if (!evolutionRemovals.contains(removal)) {
            evolutionRemovals.add(removal);
        }
        return true;
    }

    public int getEvolutionScaleMinLevel() {
        return evolutionScaleMinLevel;
    }

    public int getEvolutionScalePercent() {
        return evolutionScalePercent;
    }

    /**
     * Lowers every level-up evolution at or above {@code minLevel} by {@code percent}. The minimum level is moved
     * into 2 to 100 and the percentage into 0 to 90 (0 turns it off), each repair reported as a warning.
     */
    public void setEvolutionLevelScale(int minLevel, int percent) {
        int fixedLevel = Math.max(2, Math.min(100, minLevel));
        int fixedPercent = Math.max(0, Math.min(90, percent));
        if (fixedLevel != minLevel || fixedPercent != percent) {
            warnings.add("Evolution level scale " + minLevel + ":" + percent + " is not valid; using " + fixedLevel
                    + ":" + fixedPercent + " instead.");
        }
        evolutionScaleMinLevel = fixedLevel;
        evolutionScalePercent = fixedPercent;
    }

    public LockeMode getLockeMode() {
        return lockeMode;
    }

    public void setLockeMode(LockeMode mode) {
        this.lockeMode = mode == null ? LockeMode.NONE : mode;
    }

    /** The shiny chance as a percentage with one decimal, from 0.0 to 100.0. */
    public double getShinyChancePercent() {
        return shinyChancePermille / 10.0;
    }

    public int getShinyChancePermille() {
        return shinyChancePermille;
    }

    /**
     * Sets the shiny chance in percent. Decimals are kept to one place (12.34 becomes 12.3). A value outside
     * 0 to 100 is moved to the nearest valid one, and one that is not a number becomes 0, each with a warning.
     */
    public void setShinyChancePercent(double percent) {
        if (Double.isNaN(percent)) {
            warnings.add("The shiny chance is not a number; using 0 instead.");
            shinyChancePermille = 0;
            return;
        }
        double clamped = Math.max(0.0, Math.min(100.0, percent));
        if (clamped != percent) {
            warnings.add("Shiny chance " + percent + " is not valid; using " + clamped + " instead.");
        }
        shinyChancePermille = (int) Math.round(clamped * 10.0);
    }

    public boolean isCodePatchesAccepted() {
        return codePatchesAccepted;
    }

    public void setCodePatchesAccepted(boolean accepted) {
        this.codePatchesAccepted = accepted;
    }

    /** True if some chosen option needs a code patch, so the notice has to be shown. */
    public boolean needsCodePatchConsent() {
        return shinyChancePermille > 0 || lockeMode != LockeMode.NONE;
    }

    /** What the randomization did with these settings (applied, skipped and why), for the log. */
    public List<String> getRunNotes() {
        return Collections.unmodifiableList(runNotes);
    }

    public void addRunNote(String note) {
        runNotes.add(note);
    }

    /** Problems found (and already handled) while reading or building these settings. */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     * Replaces the custom items. Entries are sanitized one by one, see {@link #addCustomShopItem}.
     */
    public void setCustomShopItems(List<CustomShopItem> items) {
        customShopItems.clear();
        for (CustomShopItem item : items) {
            addCustomShopItem(item.itemId(), item.price());
        }
    }

    /**
     * Adds an item, repairing or dropping bad input instead of failing.
     * An item id that is not positive is dropped. A price that is not positive becomes 1, and a price
     * above {@link #MAX_REASONABLE_PRICE} is capped. Beyond {@link #MAX_CUSTOM_SHOP_ITEMS} items the
     * extra ones are dropped, but re-pricing an item that is already listed always works.
     *
     * @return whether the item is now in the list
     */
    public boolean addCustomShopItem(int itemId, int price) {
        if (itemId <= 0) {
            warnings.add("Shop item with invalid id " + itemId + " was ignored.");
            return false;
        }
        if (!customShopItems.containsKey(itemId) && customShopItems.size() >= MAX_CUSTOM_SHOP_ITEMS) {
            warnings.add("Only " + MAX_CUSTOM_SHOP_ITEMS + " custom shop items are supported; item #" + itemId
                    + " was ignored.");
            return false;
        }
        int fixed = Math.min(Math.max(price, 1), MAX_REASONABLE_PRICE);
        if (fixed != price) {
            warnings.add("Price " + price + " for item #" + itemId + " is not valid; using " + fixed + " instead.");
        }
        customShopItems.put(itemId, new CustomShopItem(itemId, fixed));
        return true;
    }

    /** True if no YAO-only setting differs from its default, i.e. there is nothing worth saving. */
    public boolean isDefault() {
        return customShopItems.isEmpty() && shinyChancePermille == 0 && lockeMode == LockeMode.NONE
                && !keepBasicShopItems && protectedShopItems.isEmpty()
                && boostFloorBst == 0 && eggBoxCount == 0 && !isMegaFinaleOn()
                && battleStyleRules.isEmpty() && fullyEvolvedByLevel == 0 && playerSpriteMale.isEmpty() && playerSpriteFemale.isEmpty()
                && evolutionEdits.isEmpty() && evolutionRemovals.isEmpty() && evolutionScalePercent == 0
                && statEdits.isEmpty();
    }

    public YaoSettings copy() {
        YaoSettings copy = new YaoSettings();
        copy.customShopItems.putAll(customShopItems);
        copy.shinyChancePermille = shinyChancePermille;
        copy.lockeMode = lockeMode;
        copy.keepBasicShopItems = keepBasicShopItems;
        copy.protectedShopItems.addAll(protectedShopItems);
        copy.boostFloorBst = boostFloorBst;
        copy.statEdits.putAll(statEdits);
        copy.eggBoxCount = eggBoxCount;
        copy.megaGymLeaders = megaGymLeaders;
        copy.megaLeague = megaLeague;
        copy.megaEvilBosses = megaEvilBosses;
        copy.megaOtherBosses = megaOtherBosses;
        copy.megaEveryone = megaEveryone;
        copy.megaFromLevel = megaFromLevel;
        copy.battleStyleRules.addAll(battleStyleRules);
        copy.fullyEvolvedByLevel = fullyEvolvedByLevel;
        copy.playerSpriteMale = playerSpriteMale;
        copy.playerSpriteFemale = playerSpriteFemale;
        copy.megaFinaleReplace = megaFinaleReplace;
        copy.eggBoxLegendary = eggBoxLegendary;
        copy.eggBoxStages.addAll(eggBoxStages);
        copy.eggBoxBasicOnly = eggBoxBasicOnly;
        copy.eggBoxDuplicates = eggBoxDuplicates;
        copy.evolutionEdits.addAll(evolutionEdits);
        copy.evolutionRemovals.addAll(evolutionRemovals);
        copy.evolutionScaleMinLevel = evolutionScaleMinLevel;
        copy.evolutionScalePercent = evolutionScalePercent;
        copy.codePatchesAccepted = codePatchesAccepted;
        copy.warnings.addAll(warnings);
        return copy;
    }

    /** Serializes to the text block (not base64). */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("yao.format=").append(FORMAT).append('\n');
        if (boostFloorBst != 0) {
            sb.append("boost.floor=").append(boostFloorBst).append('\n');
        }
        if (!playerSpriteMale.isEmpty()) {
            sb.append("sprite.male=").append(playerSpriteMale).append('\n');
        }
        if (!playerSpriteFemale.isEmpty()) {
            sb.append("sprite.female=").append(playerSpriteFemale).append('\n');
        }
        if (isMegaFinaleOn()) {
            sb.append("megafinale.gym=").append(megaGymLeaders ? 1 : 0).append('\n');
            sb.append("megafinale.league=").append(megaLeague ? 1 : 0).append('\n');
            sb.append("megafinale.evil=").append(megaEvilBosses ? 1 : 0).append('\n');
            sb.append("megafinale.other=").append(megaOtherBosses ? 1 : 0).append('\n');
            sb.append("megafinale.everyone=").append(megaEveryone ? 1 : 0).append('\n');
            if (megaFinaleReplace) {
                sb.append("megafinale.replace=1\n");
            }
            if (megaFromLevel > 0) {
                sb.append("megafinale.fromlevel=").append(megaFromLevel).append('\n');
            }
        }
        int br = 0;
        for (BattleStyleRule r : battleStyleRules) {
            sb.append("battlerule.").append(br++).append('=').append(r.group().key()).append(':')
                    .append(r.style().name().toLowerCase(java.util.Locale.ROOT)).append(':').append(r.chance()).append('\n');
        }
        if (fullyEvolvedByLevel != 0) {
            sb.append("fullyevolved=").append(fullyEvolvedByLevel).append('\n');
        }
        if (eggBoxCount != 0) {
            sb.append("eggbox.count=").append(eggBoxCount).append('\n');
            sb.append("eggbox.legendary=").append(eggBoxLegendary.name().toLowerCase(java.util.Locale.ROOT)).append('\n');
            if (!eggBoxStages.isEmpty()) {
                sb.append("eggbox.stages=");
                boolean first = true;
                for (int stage : eggBoxStages) {
                    sb.append(first ? "" : ",").append(stage);
                    first = false;
                }
                sb.append('\n');
            }
            if (eggBoxBasicOnly) {
                sb.append("eggbox.basic=1\n");
            }
            if (eggBoxDuplicates) {
                sb.append("eggbox.duplicates=1\n");
            }
        }
        int se = 0;
        for (StatEdit e : statEdits.values()) {
            sb.append("statedit.").append(se++).append('=').append(e.speciesNumber());
            if (e.mode() == StatEditMode.FINE_TUNE) {
                sb.append(":fine:").append(e.hp()).append(':').append(e.attack()).append(':').append(e.defense())
                        .append(':').append(e.spatk()).append(':').append(e.spdef()).append(':').append(e.speed());
            } else {
                sb.append(":quick:").append(e.percent());
            }
            sb.append('\n');
        }
        int ev = 0;
        for (EvolutionEdit e : evolutionEdits) {
            sb.append("evo.").append(ev++).append('=').append(e.fromSpecies()).append(':').append(e.toSpecies())
                    .append(':').append(e.type().name()).append(':').append(e.extra()).append('\n');
        }
        int rm = 0;
        for (EvolutionRemoval r : evolutionRemovals) {
            sb.append("evoremove.").append(rm++).append('=').append(r.fromSpecies()).append(':').append(r.toSpecies()).append('\n');
        }
        if (evolutionScalePercent != 0) {
            sb.append("evoscale=").append(evolutionScaleMinLevel).append(':').append(evolutionScalePercent).append('\n');
        }
        if (keepBasicShopItems) {
            sb.append("shopkeep.basics=1\n");
        }
        int p = 0;
        for (int id : protectedShopItems) {
            sb.append("shopkeep.").append(p++).append('=').append(id).append('\n');
        }
        if (lockeMode != LockeMode.NONE) {
            sb.append("locke=").append(lockeMode.name().toLowerCase(java.util.Locale.ROOT)).append('\n');
        }
        if (shinyChancePermille != 0) {
            sb.append("shiny.permille=").append(shinyChancePermille).append('\n');
        }
        int n = 0;
        for (CustomShopItem item : customShopItems.values()) {
            sb.append("shop.").append(n++).append('=').append(item.itemId()).append(':').append(item.price()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Parses the text block without ever throwing. Lines that cannot be understood are skipped and
     * reported in {@link #getWarnings()}; unknown keys are ignored.
     */
    public static YaoSettings fromText(String text) {
        YaoSettings settings = new YaoSettings();
        for (String rawLine : text.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                settings.warnings.add("Unreadable YAO setting skipped: \"" + abbreviate(line) + "\".");
                continue;
            }
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1);
            if (key.equals("yao.format")) {
                Integer format = parseInt(value);
                if (format != null && format > FORMAT) {
                    settings.warnings.add("These YAO settings were saved by a newer version; settings this version "
                            + "does not know were ignored.");
                }
            } else if (key.equals("sprite.male")) {
                settings.playerSpriteMale = clean(value);
            } else if (key.equals("sprite.female")) {
                settings.playerSpriteFemale = clean(value);
            } else if (key.equals("megafinale")) {
                // older format: a single scope. Converted into tiers rather than rejected.
                if (applyOldMegaFinaleScope(settings, value)) {
                    settings.warnings.add("The mega finale was saved in an older format and was converted to the "
                            + "tier choices.");
                } else {
                    settings.warnings.add("Unknown mega finale mode \"" + abbreviate(value) + "\" skipped.");
                }
            } else if (key.equals("megafinale.evilbosses")) {
                settings.megaEvilBosses |= value.strip().equals("1");
            } else if (key.equals("megafinale.gym")) {
                settings.megaGymLeaders = value.strip().equals("1");
            } else if (key.equals("megafinale.league")) {
                settings.megaLeague = value.strip().equals("1");
            } else if (key.equals("megafinale.evil")) {
                settings.megaEvilBosses = value.strip().equals("1");
            } else if (key.equals("megafinale.other")) {
                settings.megaOtherBosses = value.strip().equals("1");
            } else if (key.equals("megafinale.everyone")) {
                settings.megaEveryone = value.strip().equals("1");
            } else if (key.equals("megafinale.replace")) {
                settings.setMegaFinaleReplace(value.strip().equals("1"));
            } else if (key.equals("megafinale.fromlevel")) {
                Integer level = parseInt(value);
                if (level == null) {
                    settings.warnings.add("Unreadable mega finale start level skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.setMegaFinaleTiers(settings.megaGymLeaders, settings.megaLeague, settings.megaEvilBosses,
                            settings.megaOtherBosses, settings.megaEveryone, level);
                }
            } else if (key.startsWith("battlerule.")) {
                String[] parts = value.split(":");
                TrainerGroups.Group group = parts.length == 3 ? TrainerGroups.Group.parse(parts[0]) : null;
                RuleStyle style = parts.length == 3 ? parseRuleStyle(parts[1]) : null;
                Integer chance = parts.length == 3 ? parseInt(parts[2]) : null;
                if (group == null || style == null || chance == null) {
                    settings.warnings.add("Unreadable battle style rule skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.addBattleStyleRule(group, style, chance);
                }
            } else if (key.equals("fullyevolved")) {
                Integer level = parseInt(value);
                if (level == null) {
                    settings.warnings.add("Unreadable fully evolved level skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.setFullyEvolvedByLevel(level);
                }
            } else if (key.equals("eggbox.count")) {
                Integer count = parseInt(value);
                if (count == null) {
                    settings.warnings.add("Unreadable egg box size skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.setEggBoxCount(count);
                }
            } else if (key.equals("eggbox.legendary")) {
                String mode = value.strip().toLowerCase(java.util.Locale.ROOT);
                switch (mode) {
                    case "any" -> settings.setEggBoxLegendary(EggLegendary.ANY);
                    case "none" -> settings.setEggBoxLegendary(EggLegendary.NONE);
                    case "only" -> settings.setEggBoxLegendary(EggLegendary.ONLY);
                    default -> settings.warnings.add("Unknown egg legendary mode \"" + abbreviate(value)
                            + "\" skipped (any, none or only).");
                }
            } else if (key.equals("eggbox.stages")) {
                java.util.List<Integer> stages = new java.util.ArrayList<>();
                for (String part : value.split(",")) {
                    Integer stage = parseInt(part);
                    if (stage == null) {
                        settings.warnings.add("Unreadable egg line length skipped: \"" + abbreviate(part) + "\".");
                    } else {
                        stages.add(stage);
                    }
                }
                settings.setEggBoxStages(stages);
            } else if (key.equals("eggbox.basic")) {
                settings.setEggBoxBasicOnly(value.strip().equals("1"));
            } else if (key.equals("eggbox.duplicates")) {
                settings.setEggBoxDuplicates(value.strip().equals("1"));
            } else if (key.equals("boost.floor")) {
                Integer floor = parseInt(value);
                if (floor == null) {
                    settings.warnings.add("Unreadable boost floor skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.setBoostFloorBst(floor);
                }
            } else if (key.startsWith("statedit.")) {
                String[] parts = value.split(":");
                Integer species = parts.length >= 3 ? parseInt(parts[0]) : null;
                String mode = parts.length >= 3 ? parts[1].strip().toLowerCase(java.util.Locale.ROOT) : "";
                if (species == null) {
                    settings.warnings.add("Unreadable stat edit skipped: \"" + abbreviate(line) + "\".");
                } else if (mode.equals("quick") && parts.length == 3) {
                    Integer percent = parseInt(parts[2]);
                    if (percent == null) {
                        settings.warnings.add("Unreadable stat edit skipped: \"" + abbreviate(line) + "\".");
                    } else {
                        settings.addQuickStatEdit(species, percent);
                    }
                } else if (mode.equals("fine") && parts.length == 8) {
                    Integer[] stats = new Integer[6];
                    boolean ok = true;
                    for (int i = 0; i < 6; i++) {
                        stats[i] = parseInt(parts[2 + i]);
                        ok &= stats[i] != null;
                    }
                    if (!ok) {
                        settings.warnings.add("Unreadable stat edit skipped: \"" + abbreviate(line) + "\".");
                    } else {
                        settings.addFineTuneStatEdit(species, stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
                    }
                } else {
                    settings.warnings.add("Unreadable stat edit skipped: \"" + abbreviate(line) + "\".");
                }
            } else if (key.startsWith("evoremove.")) {
                int colon = value.indexOf(':');
                Integer from = colon > 0 ? parseInt(value.substring(0, colon)) : null;
                Integer to = colon > 0 ? parseInt(value.substring(colon + 1)) : null;
                if (from == null || to == null) {
                    settings.warnings.add("Unreadable evolution removal skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.addEvolutionRemoval(from, to);
                }
            } else if (key.equals("evoscale")) {
                int colon = value.indexOf(':');
                Integer level = colon > 0 ? parseInt(value.substring(0, colon)) : null;
                Integer percent = colon > 0 ? parseInt(value.substring(colon + 1)) : null;
                if (level == null || percent == null) {
                    settings.warnings.add("Unreadable evolution level scale skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.setEvolutionLevelScale(level, percent);
                }
            } else if (key.startsWith("evo.")) {
                String[] parts = value.split(":");
                Integer from = parts.length == 4 ? parseInt(parts[0]) : null;
                Integer to = parts.length == 4 ? parseInt(parts[1]) : null;
                Integer extra = parts.length == 4 ? parseInt(parts[3]) : null;
                com.uprfvx.romio.gamedata.EvolutionType type = null;
                if (parts.length == 4) {
                    try {
                        type = com.uprfvx.romio.gamedata.EvolutionType.valueOf(parts[2].strip().toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        // reported below
                    }
                }
                if (from == null || to == null || extra == null || type == null) {
                    settings.warnings.add("Unreadable evolution edit skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.addEvolutionEdit(from, to, type, extra);
                }
            } else if (key.equals("shopkeep.basics")) {
                settings.keepBasicShopItems = "1".equals(value.strip()) || "true".equalsIgnoreCase(value.strip());
            } else if (key.startsWith("shopkeep.")) {
                Integer id = parseInt(value);
                if (id == null) {
                    settings.warnings.add("Unreadable protected shop item skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.addProtectedShopItem(id);
                }
            } else if (key.equals("locke")) {
                LockeMode mode = parseLocke(value);
                if (mode == null) {
                    settings.warnings.add("Unknown Locke mode skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.lockeMode = mode;
                }
            } else if (key.equals("shiny.permille")) {
                Integer permille = parseInt(value);
                if (permille == null) {
                    settings.warnings.add("Unreadable shiny chance skipped: \"" + abbreviate(line) + "\".");
                } else {
                    settings.setShinyChancePercent(permille / 10.0);
                }
            } else if (key.startsWith("shop.")) {
                int colon = value.indexOf(':');
                Integer id = colon > 0 ? parseInt(value.substring(0, colon)) : null;
                Integer price = colon > 0 ? parseInt(value.substring(colon + 1)) : null;
                if (id == null || price == null) {
                    settings.warnings.add("Unreadable shop item skipped: \"" + abbreviate(line) + "\".");
                    continue;
                }
                settings.addCustomShopItem(id, price);
            }
        }
        return settings;
    }

    /** Serializes to a base64 string that is safe to append to the upstream settings string. */
    public String toBase64() {
        return Base64.getEncoder().encodeToString(toText().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses a base64 block without ever throwing. If it is not valid base64 the result is the
     * default settings with a warning explaining that the YAO part was ignored.
     */
    public static YaoSettings fromBase64(String base64) {
        try {
            return fromText(new String(Base64.getDecoder().decode(base64.strip()), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            YaoSettings fallback = new YaoSettings();
            fallback.warnings.add("The YAO part of the settings is damaged and was ignored; defaults are used for it.");
            return fallback;
        }
    }

    /** "none", "soft" or "full" (any case); null if it is anything else. */
    public static LockeMode parseLocke(String text) {
        if (text == null) {
            return null;
        }
        switch (text.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "none", "off", "no" -> {
                return LockeMode.NONE;
            }
            case "soft", "soft-locke", "softlocke" -> {
                return LockeMode.SOFT;
            }
            case "full", "locke", "hard" -> {
                return LockeMode.FULL;
            }
            default -> {
                return null;
            }
        }
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String abbreviate(String line) {
        return line.length() <= 40 ? line : line.substring(0, 40) + "...";
    }
}
