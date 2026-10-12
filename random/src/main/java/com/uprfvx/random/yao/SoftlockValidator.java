package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  SoftlockValidator.java - finds randomizations that may block the game. --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.Encounter;
import com.uprfvx.romio.gamedata.EncounterArea;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.StaticEncounter;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks at the randomized data (before it is saved) for the problems that are known to block a playthrough, and
 * reports them in plain words. It never changes anything. Today it checks one family of problems: the field moves
 * the game needs early on (the HMs needed for the first badges). If no Pokemon the player can obtain, directly or by
 * evolving, can learn one of them, the game cannot be finished without a trade or a cheat, which is a softlock.
 *
 * <p>The checks read the game through the small {@link Data} interface, so they can be tested without a game.
 */
public final class SoftlockValidator {

    private SoftlockValidator() {
    }

    /** What the checks need to know about the randomized game. */
    public interface Data {
        /** The moves the player must be able to use early in the game (field moves of the first badges). */
        List<Integer> requiredMoves();

        /** The Pokemon the player can get: starters, wild, static and gift Pokemon, and everything they evolve into. */
        Set<Species> obtainable();

        boolean canLearn(Species species, int move);

        String moveName(int move);
    }

    /** The problems found, each one a sentence for the log. Empty if nothing was found. */
    public static List<String> check(Data data) {
        List<String> problems = new ArrayList<>();
        Set<Species> obtainable = data.obtainable();
        if (obtainable.isEmpty()) {
            problems.add("Could not tell which Pokemon the player can get, so the field moves were not checked.");
            return problems;
        }
        for (int move : data.requiredMoves()) {
            boolean found = false;
            for (Species species : obtainable) {
                if (data.canLearn(species, move)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                problems.add("No Pokemon you can get can learn " + data.moveName(move) + ", which the game needs early on. "
                        + "You may not be able to finish the game without trading or cheating. Try again with another "
                        + "seed, or turn on full HM compatibility.");
            }
        }
        return problems;
    }

    /** Adds a Pokemon and everything it can evolve into. */
    private static void addFamilyAhead(Species species, Set<Species> into) {
        if (species == null || !into.add(species)) {
            return;
        }
        for (Evolution evolution : species.getEvolutionsFrom()) {
            addFamilyAhead(evolution.getTo(), into);
        }
    }

    /** The checks' view of a loaded and randomized game. */
    public static Data of(RomHandler rom) {
        return new Data() {
            // Modified from FVX 11/10/2026: getHMMoves() can return null for a ROM whose HM data
            // could not be located; fall back to an empty list instead of crashing.
            private final List<Integer> hms = rom.getHMMoves() == null ? List.of() : rom.getHMMoves();
            // Modified from FVX 11/10/2026: getTMMoves() can return null for a ROM whose TM data
            // could not be located; fall back to 0 instead of crashing.
            private final int tmCount = rom.getTMMoves() == null ? 0 : rom.getTMMoves().size();
            private Map<Species, boolean[]> compat;
            private Map<Integer, List<MoveLearnt>> learnt;

            @Override
            public List<Integer> requiredMoves() {
                return rom.getEarlyRequiredHMMoves();
            }

            @Override
            public Set<Species> obtainable() {
                Set<Species> set = new LinkedHashSet<>();
                for (Species s : rom.getStarters()) {
                    addFamilyAhead(s, set);
                }
                for (EncounterArea area : rom.getEncounters(false)) {
                    for (Encounter e : area) {
                        addFamilyAhead(e.getSpecies(), set);
                    }
                }
                for (StaticEncounter s : rom.getStaticPokemon()) {
                    addFamilyAhead(s.getSpecies(), set);
                }
                return set;
            }

            @Override
            public boolean canLearn(Species species, int move) {
                if (compat == null) {
                    compat = rom.getTMHMCompatibility();
                    learnt = rom.getMovesLearnt();
                }
                int hmIndex = hms.indexOf(move);
                boolean[] flags = compat == null ? null : compat.get(species);
                if (hmIndex >= 0 && flags != null && tmCount + 1 + hmIndex < flags.length && flags[tmCount + 1 + hmIndex]) {
                    return true;
                }
                List<MoveLearnt> list = learnt == null ? null : learnt.get(species.getNumber());
                if (list != null) {
                    for (MoveLearnt m : list) {
                        if (m.move == move) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public String moveName(int move) {
                List<Move> moves = rom.getMoves();
                return move >= 0 && move < moves.size() && moves.get(move) != null ? moves.get(move).name : "move #" + move;
            }
        };
    }
}
