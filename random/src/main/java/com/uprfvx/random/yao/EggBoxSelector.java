package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  EggBoxSelector.java - which species the egg box delivers.             --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.Species;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Picks the species of the eggs that the egg box delivers, from the Pokemon of the game, following the filters of
 * the {@link YaoSettings} (legendary rule, evolution line length, only Pokemon that do not evolve from anything,
 * duplicates). Pure data: it never touches a ROM, so it is fully testable. The pick uses only the random generator
 * it is given, so the same seed always gives the same eggs.
 */
public final class EggBoxSelector {

    private EggBoxSelector() {
    }

    /** The eggs and what happened while choosing them (shown in the log). */
    public record Result(List<Species> eggs, List<String> notes) {
    }

    /** How many stages the evolution line of this species has (1 for a Pokemon that does not evolve), at most 3 counted. */
    public static int lineLength(Species species) {
        Species root = species;
        Set<Species> seen = new HashSet<>();
        seen.add(root);
        while (!root.getEvolutionsTo().isEmpty()) {
            Species parent = root.getEvolutionsTo().get(0).getFrom();
            if (parent == null || !seen.add(parent)) {
                break;
            }
            root = parent;
        }
        return Math.min(3, depth(root, new HashSet<>()));
    }

    private static int depth(Species species, Set<Species> path) {
        if (!path.add(species)) {
            return 0;
        }
        int deepest = 0;
        for (Evolution evolution : species.getEvolutionsFrom()) {
            if (evolution.getTo() != null) {
                deepest = Math.max(deepest, depth(evolution.getTo(), path));
            }
        }
        path.remove(species);
        return 1 + deepest;
    }

    /** Whether this species does not evolve from anything. */
    public static boolean isBasic(Species species) {
        return species.getEvolutionsTo().isEmpty();
    }

    private static boolean allowed(Species species, YaoSettings yao) {
        if (species == null) {
            return false;
        }
        switch (yao.getEggBoxLegendary()) {
            case NONE -> {
                if (species.isLegendary()) {
                    return false;
                }
            }
            case ONLY -> {
                if (!species.isLegendary()) {
                    return false;
                }
            }
            default -> {
            }
        }
        if (yao.isEggBoxBasicOnly() && !isBasic(species)) {
            return false;
        }
        Set<Integer> stages = yao.getEggBoxStages();
        return stages.isEmpty() || stages.contains(lineLength(species));
    }

    /**
     * Chooses up to {@code yao.getEggBoxCount()} species from {@code pool}. If fewer species pass the filters than eggs
     * were asked for and duplicates are not allowed, the box gets one of each and the log says so; if nothing passes
     * the filters the box is empty and the log says that too.
     */
    public static Result pick(Collection<Species> pool, YaoSettings yao, Random random) {
        List<String> notes = new ArrayList<>();
        List<Species> eggs = new ArrayList<>();
        int count = yao.getEggBoxCount();
        if (count <= 0 || pool == null) {
            return new Result(eggs, notes);
        }
        List<Species> candidates = new ArrayList<>();
        for (Species species : pool) {
            if (allowed(species, yao)) {
                candidates.add(species);
            }
        }
        candidates.sort(Comparator.comparingInt(Species::getNumber));
        if (candidates.isEmpty()) {
            notes.add("No Pokemon of this game pass the egg filters, so no eggs were added.");
            return new Result(eggs, notes);
        }
        if (yao.isEggBoxDuplicates()) {
            for (int i = 0; i < count; i++) {
                eggs.add(candidates.get(random.nextInt(candidates.size())));
            }
        } else {
            java.util.Collections.shuffle(candidates, random);
            int taken = Math.min(count, candidates.size());
            eggs.addAll(candidates.subList(0, taken));
            if (taken < count) {
                notes.add("Only " + taken + " different Pokemon pass the egg filters, so the box has " + taken
                        + " egg(s) instead of " + count + ".");
            }
        }
        return new Result(eggs, notes);
    }
}
