package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  StatFloorApplier.java - raise the base stats of weak Pokemon.         --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.basestats.BaseStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lifts every fully evolved Pokemon below a base stat total floor up to it. The floor skips any Pokemon that
 * still has a further evolution ahead of it (a baby or mid-stage Pokemon only gets there by evolving, not by this
 * automatic rule). The extra points are spread over the stats in the proportions the Pokemon already has, so a
 * Pokemon keeps its identity (a fast one stays fast), each stat is capped at 255, and a Pokemon is never made
 * weaker.
 *
 * <p>Modified from FVX (11/10/2026): this class used to also hold a "boost chosen Pokemon by percentage" list.
 * That picker was a strict subset of {@link PerSpeciesStatEditor}'s quick mode (which can also lower stats), so
 * the project owner merged the two and this class now only does the automatic floor.
 *
 * <p>Nothing here throws because of a Pokemon's data: one that cannot be adjusted is skipped and reported.
 */
public final class StatFloorApplier {

    private StatFloorApplier() {
    }

    /** The new base stat total for a Pokemon, before the stat cap. Never lower than the current one. */
    static int targetBst(int currentBst, Integer percent, int floor) {
        long target = currentBst;
        if (percent != null && percent > 0) {
            target = Math.round(currentBst * (1.0 + percent / 100.0));
        }
        if (floor > 0 && target < floor) {
            target = floor;
        }
        return (int) Math.max(currentBst, target);
    }

    public static List<String> apply(Collection<Species> allSpecies, YaoSettings yao) {
        List<String> notes = new ArrayList<>();
        int floor = yao.getBoostFloorBst();
        if (floor == 0) {
            return notes;
        }

        int changed = 0;
        List<String> examples = new ArrayList<>();

        for (Species sp : allSpecies) {
            if (sp == null) {
                continue;
            }
            // Modified from FVX (11/10/2026): the floor only lifts Pokemon that are fully evolved (no evolution
            // ahead of them), since boosting a baby or mid-stage Pokemon that will grow stronger by evolving
            // anyway does not make sense.
            boolean fullyEvolved = sp.getEvolutionsFrom().isEmpty();
            if (!fullyEvolved) {
                continue;
            }
            BaseStats bs = sp.getBaseStats();
            if (bs == null) {
                continue;
            }
            if (isCosmeticForme(sp)) {
                continue; // the floor does not touch formes that only change looks
            }
            try {
                int before = bs.getBST();
                int target = Math.min(targetBst(before, null, floor), bs.getMaxBST());
                if (target > before) {
                    bs.setBST(target);
                    int after = bs.getBST();
                    if (after != before) {
                        changed++;
                        if (examples.size() < 6) {
                            examples.add(sp.getFullName() + " " + before + " to " + after);
                        }
                    }
                }
            } catch (RuntimeException e) {
                notes.add("Stat floor: " + sp.getFullName() + " could not be adjusted (" + e.getMessage()
                        + "); skipped.");
            }
        }

        if (changed > 0) {
            notes.add("Stat floor: " + changed + " Pokemon had their base stat total raised (for example "
                    + String.join(", ", examples) + ").");
        } else if (notes.isEmpty()) {
            notes.add("Stat floor: no Pokemon needed a boost.");
        }
        return notes;
    }

    private static boolean isCosmeticForme(Species sp) {
        try {
            return sp.isEssentiallyCosmetic();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
