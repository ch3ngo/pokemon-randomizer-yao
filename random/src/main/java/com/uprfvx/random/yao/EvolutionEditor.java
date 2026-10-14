package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  EvolutionEditor.java - custom evolutions chosen by the user.          --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.constants.EvolutionSupport;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Species;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets the user decide how Pokemon evolve: take an evolution away, add one, or change how it happens (Eevee into
 * Umbreon with a Moon Stone), plus a percentage that lowers high evolution levels.
 *
 * <p>Every edit is checked, and one that is not safe is skipped with a note instead of being forced in:
 * <ul>
 *     <li>the Pokemon must exist, and the method must be one the game can store;</li>
 *     <li>the number (level, item, move, Pokemon) must be valid for the method;</li>
 *     <li>no two evolutions of the same Pokemon may happen the same way (that would make one unreachable);</li>
 *     <li>no Pokemon may already evolve from somebody else, and no loops;</li>
 *     <li>a Pokemon cannot have more evolutions than the game has slots (5, 7 or 8 depending on the generation);</li>
 *     <li>Gen 1 and 2 only allow changing the level of an existing level evolution, because their evolution data
 *     has no spare room.</li>
 * </ul>
 * Structural edits run right after the game's own evolution step so everything later sees the new evolutions.
 * Level edits and the level scale run after the "make evolutions easier" step, so the user's numbers win.
 */
public final class EvolutionEditor {

    /** What the editor needs to know about the loaded game. */
    public interface World {
        int generation();

        /** The species with this number, or null. */
        Species species(int number);

        boolean itemExists(int id);

        boolean moveExists(int id);

        /** Every species including formes, or null when the world cannot list them (then numbers are scanned). */
        default java.util.Collection<Species> allSpecies() {
            return null;
        }
    }

    private final World world;
    private final YaoSettings yao;
    private final Set<Long> editedPairs = new HashSet<>();
    private final List<Evolution> editedLevelEvolutions = new ArrayList<>();
    private final List<Integer> editedLevels = new ArrayList<>();

    public EvolutionEditor(World world, YaoSettings yao) {
        this.world = world;
        this.yao = yao;
    }

    private static long pair(int from, int to) {
        return ((long) from << 32) | (to & 0xFFFFFFFFL);
    }

    // ---------------------------------------------------------------- structural edits

    public List<String> applyStructural() {
        List<String> notes = new ArrayList<>();
        int gen = world.generation();

        for (YaoSettings.EvolutionRemoval removal : yao.getEvolutionRemovals()) {
            Species from = world.species(removal.fromSpecies());
            Species to = world.species(removal.toSpecies());
            if (from == null || to == null) {
                notes.add("Remove evolution: Pokemon #" + removal.fromSpecies() + " or #" + removal.toSpecies()
                        + " does not exist in this game; skipped.");
                continue;
            }
            if (!EvolutionSupport.canAddOrRemove(gen)) {
                notes.add("Remove evolution " + from.getFullName() + " to " + to.getFullName()
                        + ": this game cannot take evolutions away; skipped.");
                continue;
            }
            Evolution found = find(from, to);
            if (found == null) {
                notes.add("Remove evolution: " + from.getFullName() + " does not evolve into " + to.getFullName()
                        + "; nothing to remove.");
                continue;
            }
            from.getEvolutionsFrom().remove(found);
            to.getEvolutionsTo().remove(found);
            notes.add("Removed the evolution of " + from.getFullName() + " into " + to.getFullName() + ".");
        }

        for (YaoSettings.EvolutionEdit edit : yao.getEvolutionEdits()) {
            applyEdit(edit, gen, notes);
        }
        return notes;
    }

    private void applyEdit(YaoSettings.EvolutionEdit edit, int gen, List<String> notes) {
        Species from = world.species(edit.fromSpecies());
        Species to = world.species(edit.toSpecies());
        if (from == null || to == null) {
            notes.add("Evolution edit: Pokemon #" + edit.fromSpecies() + " or #" + edit.toSpecies()
                    + " does not exist in this game; skipped.");
            return;
        }
        String label = from.getFullName() + " to " + to.getFullName();
        EvolutionType type = edit.type();
        if (!EvolutionSupport.supports(gen, type)) {
            notes.add("Evolution " + label + ": this game cannot store the method " + type + "; skipped.");
            return;
        }

        int extra = edit.extra();
        String problem = checkParameter(type, extra);
        if (problem != null) {
            // a level that is out of range is moved into range; anything else cannot be repaired
            if (type.usesLevelThreshold()) {
                int fixed = Math.max(1, Math.min(100, extra));
                notes.add("Evolution " + label + ": level " + extra + " is not valid, using " + fixed + " instead.");
                extra = fixed;
            } else {
                notes.add("Evolution " + label + ": " + problem + "; skipped.");
                return;
            }
        }

        Evolution existing = find(from, to);

        if (!EvolutionSupport.canAddOrRemove(gen)) {
            // Gen 1 and 2: only the level of an existing level evolution
            if (existing == null || existing.getType() != EvolutionType.LEVEL || type != EvolutionType.LEVEL) {
                notes.add("Evolution " + label + ": this game can only change the level of an existing level "
                        + "evolution; skipped.");
                return;
            }
            existing.updateEvolutionMethod(EvolutionType.LEVEL, extra);
            rememberLevel(existing, from, to, extra);
            notes.add("Evolution " + label + ": level set to " + extra + ".");
            return;
        }

        Species conflict = overlapWith(from, existing, type, extra);
        if (conflict != null) {
            notes.add("Evolution " + label + ": " + from.getFullName() + " already evolves into " + conflict.getFullName()
                    + " the same way (" + type + "), so one of them could never happen; skipped.");
            return;
        }

        if (existing != null) {
            existing.updateEvolutionMethod(type, extra);
            if (type.usesLevelThreshold()) {
                rememberLevel(existing, from, to, extra);
            } else {
                editedPairs.add(pair(from.getNumber(), to.getNumber()));
            }
            notes.add("Evolution " + label + ": now by " + describe(type, extra) + ".");
            return;
        }

        // a brand new evolution
        if (countReal(from) >= EvolutionSupport.maxEvolutions(gen)) {
            notes.add("Evolution " + label + ": " + from.getFullName() + " already has the most evolutions this game "
                    + "can store (" + EvolutionSupport.maxEvolutions(gen) + "); skipped.");
            return;
        }
        for (Evolution other : to.getEvolutionsTo()) {
            if (other.getType() != EvolutionType.NONE && other.getFrom() != from) {
                notes.add("Evolution " + label + ": " + to.getFullName() + " already evolves from "
                        + other.getFrom().getFullName() + "; remove that evolution first. Skipped.");
                return;
            }
        }
        if (reaches(to, from)) {
            notes.add("Evolution " + label + ": that would make a loop of evolutions; skipped.");
            return;
        }
        Evolution created = new Evolution(from, to, type, extra);
        from.getEvolutionsFrom().add(created);
        to.getEvolutionsTo().add(created);
        if (type.usesLevelThreshold()) {
            rememberLevel(created, from, to, extra);
        } else {
            editedPairs.add(pair(from.getNumber(), to.getNumber()));
        }
        notes.add("Evolution " + label + ": added, by " + describe(type, extra) + ".");
    }

    private void rememberLevel(Evolution evo, Species from, Species to, int level) {
        editedPairs.add(pair(from.getNumber(), to.getNumber()));
        editedLevelEvolutions.add(evo);
        editedLevels.add(level);
    }

    // ---------------------------------------------------------------- stone evolutions also by level

    /**
     * Added for YAO (13/10/2026, Phase 19). A Pokemon with exactly ONE evolution, reached by a stone, gets a second
     * evolution into the same Pokemon by plain leveling, keeping the stone. Pokemon with several evolutions
     * (Eevee, Gloom, Poliwhirl, Tyrogue, Wurmple...) are never touched, and neither is anything the user edited
     * or removed in the evolution editor (those choices always win).
     *
     * <p>Level: the game's own estimate for that evolution ({@link Evolution#getEstimatedEvoLvl()}, worked out from
     * how similar-sized Pokemon evolve by level, and already lowered by "make evolutions easier" when on). When
     * there is no estimate, {@code round(BST of the evolved Pokemon / 16)}, so a 500 BST evolution comes at 31.
     * It is then limited to {@code levelCap} (the "fully evolved by" or "make evolutions easier" level; 75% of it
     * when the evolved Pokemon evolves further), kept above the level the Pokemon itself evolved at, and kept
     * within 2 to 100.
     *
     * <p>Gen 1 and 2 cannot hold an extra evolution, so nothing is added there. A Pokemon with no free evolution
     * slot is skipped. Every skip and the total are returned as notes for the log.
     *
     * @param levelCap the level every Pokemon is fully evolved by, or 0 for no limit
     */
    public List<String> addLevelRoutesToStoneEvolutions(int levelCap) {
        List<String> notes = new ArrayList<>();
        int gen = world.generation();
        if (!EvolutionSupport.canAddOrRemove(gen)) {
            notes.add("Stone evolutions also by level: this game cannot store a second evolution for a Pokemon; "
                    + "nothing was added.");
            return notes;
        }
        Set<Integer> touched = new HashSet<>();
        for (YaoSettings.EvolutionEdit edit : yao.getEvolutionEdits()) {
            touched.add(edit.fromSpecies());
        }
        for (YaoSettings.EvolutionRemoval removal : yao.getEvolutionRemovals()) {
            touched.add(removal.fromSpecies());
        }
        java.util.Collection<Species> all = world.allSpecies();
        List<Species> candidates = new ArrayList<>();
        if (all != null) {
            candidates.addAll(all);
        } else {
            for (int number = 1; number <= 2000; number++) {
                Species sp = world.species(number);
                if (sp != null) {
                    candidates.add(sp);
                }
            }
        }
        int added = 0;
        List<String> examples = new ArrayList<>();
        Set<Species> seen = new HashSet<>();
        for (Species from : candidates) {
            if (from == null || !seen.add(from) || countReal(from) != 1) {
                continue;
            }
            Evolution stone = null;
            for (Evolution evo : from.getEvolutionsFrom()) {
                if (evo.getType() != EvolutionType.NONE) {
                    stone = evo;
                }
            }
            if (stone == null || !isStone(stone.getType())) {
                continue;
            }
            Species to = stone.getTo();
            if (touched.contains(from.getNumber()) || editedPairs.contains(pair(from.getNumber(), to.getNumber()))) {
                notes.add("Stone evolutions also by level: " + from.getFullName() + " was edited by you; left as it is.");
                continue;
            }
            if (!EvolutionSupport.supports(gen, EvolutionType.LEVEL)
                    || countReal(from) >= EvolutionSupport.maxEvolutions(gen)) {
                notes.add("Stone evolutions also by level: " + from.getFullName() + " has no free evolution slot; skipped.");
                continue;
            }
            int level = stone.getEstimatedEvoLvl() > 1 ? stone.getEstimatedEvoLvl()
                    : (int) Math.round(bstOf(to) / 16.0);
            if (levelCap > 0) {
                int cap = to.getEvolutionsFrom().stream().anyMatch(e -> e.getType() != EvolutionType.NONE)
                        ? (int) Math.ceil(0.75 * levelCap) : levelCap;
                level = Math.min(level, cap);
            }
            for (Evolution before : from.getEvolutionsTo()) {
                if (before.getType().usesLevelThreshold()) {
                    level = Math.max(level, before.getExtraInfo() + 1);
                }
            }
            level = Math.max(2, Math.min(100, level));
            Evolution created = new Evolution(from, to, EvolutionType.LEVEL, level);
            from.getEvolutionsFrom().add(created);
            to.getEvolutionsTo().add(created);
            added++;
            if (examples.size() < 5) {
                examples.add(from.getFullName() + " to " + to.getFullName() + " at level " + level);
            }
        }
        notes.add("Stone evolutions also by level: " + added + " Pokemon with a single stone evolution can now also "
                + "evolve by leveling" + (examples.isEmpty() ? "" : " (for example " + String.join(", ", examples) + ")")
                + ". The stone still works.");
        return notes;
    }

    private static int bstOf(Species species) {
        return species.getBaseStats() == null ? 400 : species.getBST(false);
    }

    private static boolean isStone(EvolutionType type) {
        return type == EvolutionType.STONE || type == EvolutionType.STONE_MALE_ONLY
                || type == EvolutionType.STONE_FEMALE_ONLY;
    }

    // ---------------------------------------------------------------- level edits and scale

    /** Re-applies the user's own levels (the game's "make evolutions easier" may have changed them), then scales. */
    public List<String> applyLevels() {
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < editedLevelEvolutions.size(); i++) {
            Evolution evo = editedLevelEvolutions.get(i);
            if (evo.getType().usesLevelThreshold() && evo.getExtraInfo() != editedLevels.get(i)) {
                evo.updateEvolutionMethod(evo.getType(), editedLevels.get(i));
            }
        }

        int percent = yao.getEvolutionScalePercent();
        int minLevel = yao.getEvolutionScaleMinLevel();
        if (percent > 0) {
            int changed = 0;
            List<String> examples = new ArrayList<>();
            Set<Species> seen = new HashSet<>();
            for (int number = 1; number <= 2000; number++) {
                Species from = world.species(number);
                if (from == null || !seen.add(from)) {
                    continue;
                }
                for (Evolution evo : from.getEvolutionsFrom()) {
                    if (!evo.getType().usesLevelThreshold()
                            || editedPairs.contains(pair(from.getNumber(), evo.getTo().getNumber()))) {
                        continue; // the user's own choices are never scaled
                    }
                    int level = evo.getExtraInfo();
                    if (level >= minLevel) {
                        int scaled = Math.max(2, (int) Math.round(level * (100 - percent) / 100.0));
                        if (scaled < level) {
                            evo.updateEvolutionMethod(evo.getType(), scaled);
                            changed++;
                            if (examples.size() < 5) {
                                examples.add(from.getFullName() + " " + level + " to " + scaled);
                            }
                        }
                    }
                }
            }
            notes.add("Evolution levels of " + minLevel + " or more were lowered by " + percent + " percent: "
                    + changed + " evolution(s)" + (examples.isEmpty() ? "" : " (for example "
                    + String.join(", ", examples) + ")") + ".");
        }
        return notes;
    }

    // ---------------------------------------------------------------- checks

    private String checkParameter(EvolutionType type, int extra) {
        if (type.usesLevelThreshold()) {
            return extra >= 1 && extra <= 100 ? null : "level " + extra + " is not between 1 and 100";
        }
        if (type.usesItem()) {
            return world.itemExists(extra) ? null : "item #" + extra + " does not exist in this game";
        }
        if (type.usesMove()) {
            return world.moveExists(extra) ? null : "move #" + extra + " does not exist in this game";
        }
        if (type.usesSpecies()) {
            return world.species(extra) != null ? null : "Pokemon #" + extra + " does not exist in this game";
        }
        return null; // happiness, trade, location types and the like carry no number the user chooses
    }

    private static String describe(EvolutionType type, int extra) {
        if (type.usesLevelThreshold()) {
            return type + " at level " + extra;
        }
        if (type.usesItem() || type.usesMove() || type.usesSpecies()) {
            return type + " (#" + extra + ")";
        }
        return type.toString();
    }

    private static Evolution find(Species from, Species to) {
        for (Evolution evo : from.getEvolutionsFrom()) {
            if (evo.getTo() == to && evo.getType() != EvolutionType.NONE) {
                return evo;
            }
        }
        return null;
    }

    private static int countReal(Species from) {
        int n = 0;
        for (Evolution evo : from.getEvolutionsFrom()) {
            if (evo.getType() != EvolutionType.NONE) {
                n++;
            }
        }
        return n;
    }

    /** True if {@code start} can evolve (through any number of steps) into {@code target}. */
    private static boolean reaches(Species start, Species target) {
        Deque<Species> queue = new ArrayDeque<>();
        Set<Species> seen = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            Species current = queue.poll();
            if (current == target) {
                return true;
            }
            if (seen.add(current)) {
                for (Evolution evo : current.getEvolutionsFrom()) {
                    if (evo.getType() != EvolutionType.NONE) {
                        queue.add(evo.getTo());
                    }
                }
            }
        }
        return false;
    }

    /**
     * The Pokemon that {@code from} already evolves into in a way that overlaps with this method, or null.
     * {@code ignored} is the evolution being replaced, which does not count.
     */
    private static Species overlapWith(Species from, Evolution ignored, EvolutionType type, int extra) {
        for (Evolution other : from.getEvolutionsFrom()) {
            if (other == ignored || other.getType() == EvolutionType.NONE) {
                continue;
            }
            if (overlaps(other.getType(), other.getExtraInfo(), type, extra)) {
                return other.getTo();
            }
        }
        return null;
    }

    /** The method with day or night and male or female stripped, so that "same way" can be compared. */
    private static EvolutionType kind(EvolutionType type) {
        EvolutionType timeless = type.timeless();
        EvolutionType base = timeless != null ? timeless : type;
        return switch (base) {
            case STONE_MALE_ONLY, STONE_FEMALE_ONLY -> EvolutionType.STONE;
            case LEVEL_MALE_ONLY, LEVEL_FEMALE_ONLY -> EvolutionType.LEVEL;
            default -> base;
        };
    }

    private static boolean oppositeVariants(EvolutionType a, EvolutionType b) {
        if (a.oppositeTime() == b) {
            return true;
        }
        return (a == EvolutionType.STONE_MALE_ONLY && b == EvolutionType.STONE_FEMALE_ONLY)
                || (a == EvolutionType.STONE_FEMALE_ONLY && b == EvolutionType.STONE_MALE_ONLY)
                || (a == EvolutionType.LEVEL_MALE_ONLY && b == EvolutionType.LEVEL_FEMALE_ONLY)
                || (a == EvolutionType.LEVEL_FEMALE_ONLY && b == EvolutionType.LEVEL_MALE_ONLY);
    }

    /**
     * Two evolutions of one Pokemon overlap when they happen the same way: the same kind of level up (whatever the
     * level, since the lower one always comes first), the same item, the same move, and so on. Day versus night and
     * male versus female are different ways.
     *
     * <p>Made {@code public} (11/10/2026, web UI feedback item 12) so the web interface can ask this exact rule
     * about a set of in-progress evolution edits (see {@code WebServer}'s {@code /api/evolution-conflicts}) and
     * highlight the rows live, instead of the frontend guessing at the rule with its own, less correct check.
     */
    public static boolean overlaps(EvolutionType a, int extraA, EvolutionType b, int extraB) {
        if (oppositeVariants(a, b)) {
            return false;
        }
        EvolutionType ka = kind(a);
        EvolutionType kb = kind(b);
        if (ka != kb) {
            return false;
        }
        if (ka.usesLevelThreshold()) {
            return true; // two level ups of the same kind: the lower level always wins, the other is unreachable
        }
        return extraA == extraB; // items, moves, species and the like must be the same to overlap
    }
}
