package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  PerSpeciesStatEditor.java - precise, single-Pokemon base stat edits.   --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.basestats.BaseStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Precise, single-Pokemon base stat edits: pick one species and either scale its whole stat spread by a
 * percentage ({@link YaoSettings.StatEditMode#QUICK}, allowed to also lower stats since this is a deliberate,
 * surgical edit rather than an automatic "never make a Pokemon weaker" rule), or set each of its six base stats to
 * an exact value ({@link YaoSettings.StatEditMode#FINE_TUNE}), clamped to the real minimum and maximum any Pokemon
 * in the currently loaded game has for that stat (see {@link #computeBounds}) rather than a guessed range.
 *
 * <p>Quick mode is THE way to raise or lower one chosen Pokemon's base stats by a percentage: an earlier,
 * separate "boost chosen Pokemon" picker in {@link StatFloorApplier} did the same thing but could only raise
 * stats, so it was merged into this class's quick mode (11/10/2026) and removed.
 *
 * <p>Runs after {@link StatFloorApplier} in {@code GameRandomizer} (see {@code maybeApplyStatEdits}), so a manual
 * edit made here is always the final word for that species, even when whole-dex base stat randomization or the
 * automatic floor also touched it.
 *
 * <p>Nothing here ever throws because of a Pokemon's data: an edit that cannot be applied is skipped and reported.
 */
public final class PerSpeciesStatEditor {

    private PerSpeciesStatEditor() {
    }

    /** The lowest and highest value any Pokemon in the loaded game has for one stat. */
    public record StatRange(int min, int max) {

        int clamp(int value) {
            return Math.max(min, Math.min(max, value));
        }
    }

    /** The real, per-stat bounds of the loaded game, used to clamp fine-tune edits instead of a guessed range. */
    public record Bounds(StatRange hp, StatRange attack, StatRange defense, StatRange spatk, StatRange spdef,
                          StatRange speed) {
    }

    /**
     * The real minimum and maximum of each stat across every Pokemon in {@code allSpecies} (for example Shedinja's
     * HP of 1, or whatever this game's highest Speed is). If no species has usable base stats, falls back to the
     * game-wide 0 to {@value YaoSettings#MAX_STAT_VALUE} range so a caller always gets a usable bound.
     */
    public static Bounds computeBounds(Collection<Species> allSpecies) {
        int[] min = new int[6];
        int[] max = new int[6];
        java.util.Arrays.fill(min, Integer.MAX_VALUE);
        java.util.Arrays.fill(max, Integer.MIN_VALUE);
        boolean any = false;
        if (allSpecies != null) {
            for (Species sp : allSpecies) {
                BaseStats bs = sp == null ? null : sp.getBaseStats();
                if (bs == null) {
                    continue;
                }
                any = true;
                int[] stats = {bs.getHp(), bs.getAttack(), bs.getDefense(), bs.getSpatk(), bs.getSpdef(), bs.getSpeed()};
                for (int i = 0; i < 6; i++) {
                    min[i] = Math.min(min[i], stats[i]);
                    max[i] = Math.max(max[i], stats[i]);
                }
            }
        }
        if (!any) {
            java.util.Arrays.fill(min, 0);
            java.util.Arrays.fill(max, YaoSettings.MAX_STAT_VALUE);
        }
        return new Bounds(new StatRange(min[0], max[0]), new StatRange(min[1], max[1]), new StatRange(min[2], max[2]),
                new StatRange(min[3], max[3]), new StatRange(min[4], max[4]), new StatRange(min[5], max[5]));
    }

    public static List<String> apply(Collection<Species> allSpecies, YaoSettings yao) {
        List<String> notes = new ArrayList<>();
        if (yao.getStatEdits().isEmpty()) {
            return notes;
        }
        Map<Integer, Species> byNumber = new HashMap<>();
        for (Species sp : allSpecies) {
            if (sp != null) {
                byNumber.put(sp.getNumber(), sp);
            }
        }
        Bounds bounds = computeBounds(allSpecies);
        for (YaoSettings.StatEdit edit : yao.getStatEdits()) {
            Species sp = byNumber.get(edit.speciesNumber());
            if (sp == null) {
                notes.add("Stat edit: Pokemon #" + edit.speciesNumber() + " does not exist in this game; skipped.");
                continue;
            }
            BaseStats bs = sp.getBaseStats();
            if (bs == null) {
                notes.add("Stat edit: " + sp.getFullName() + " has no base stats to edit; skipped.");
                continue;
            }
            try {
                if (edit.mode() == YaoSettings.StatEditMode.QUICK) {
                    int before = bs.getBST();
                    long rawTarget = Math.round(before * (1.0 + edit.percent() / 100.0));
                    int target = (int) Math.max(1, Math.min(bs.getMaxBST(), rawTarget));
                    bs.setBST(target);
                    notes.add("Stat edit: " + sp.getFullName() + " base stat total " + before + " to " + bs.getBST()
                            + " (" + (edit.percent() >= 0 ? "+" : "") + edit.percent() + "%).");
                } else {
                    int hp = bounds.hp().clamp(edit.hp());
                    int attack = bounds.attack().clamp(edit.attack());
                    int defense = bounds.defense().clamp(edit.defense());
                    int spatk = bounds.spatk().clamp(edit.spatk());
                    int spdef = bounds.spdef().clamp(edit.spdef());
                    int speed = bounds.speed().clamp(edit.speed());
                    // setStatRatios alone keeps the current BST; setBST afterwards, with ratios equal to the exact
                    // values wanted and a matching total, makes the distribution land on those values exactly.
                    bs.setStatRatios(hp, attack, defense, spatk, spdef, speed);
                    bs.setBST(hp + attack + defense + spatk + spdef + speed);
                    notes.add("Stat edit: " + sp.getFullName() + " set to HP " + bs.getHp() + ", Attack "
                            + bs.getAttack() + ", Defense " + bs.getDefense() + ", Sp.Atk " + bs.getSpatk()
                            + ", Sp.Def " + bs.getSpdef() + ", Speed " + bs.getSpeed() + ".");
                }
            } catch (RuntimeException e) {
                notes.add("Stat edit: " + sp.getFullName() + " could not be adjusted (" + e.getMessage() + "); skipped.");
            }
        }
        return notes;
    }
}
