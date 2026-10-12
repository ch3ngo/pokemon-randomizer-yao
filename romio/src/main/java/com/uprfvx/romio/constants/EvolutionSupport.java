package com.uprfvx.romio.constants;

/*----------------------------------------------------------------------------*/
/*--  EvolutionSupport.java - what evolutions each generation can store.    --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.EvolutionType;

/**
 * Which evolution methods a game can store, and how many evolutions one Pokemon can have there. The method
 * tables are the ones the ROM handlers use to write evolutions, so what passes here can be written.
 */
public final class EvolutionSupport {

    private EvolutionSupport() {
    }

    /** True if this generation has a slot type for the method. NONE and the internal ITEM are never supported. */
    public static boolean supports(int generation, EvolutionType type) {
        if (type == null || type == EvolutionType.NONE || type == EvolutionType.ITEM) {
            return false;
        }
        try {
            return switch (generation) {
                case 1 -> Gen1Constants.evolutionTypeToIndex(type) != -1;
                case 2 -> Gen2Constants.evolutionTypeToIndex(type) != -1;
                case 3 -> Gen3Constants.evolutionTypeToIndex(type) != -1;
                case 4 -> Gen4Constants.evolutionTypeToIndex(type) != -1;
                case 5 -> Gen5Constants.evolutionTypeToIndex(type) != -1;
                case 6 -> Gen6Constants.evolutionTypeToIndex(type) != -1;
                case 7 -> Gen7Constants.evolutionTypeToIndex(type) != -1;
                default -> false;
            };
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** How many evolutions one Pokemon can have: the number of slots the handlers write. */
    public static int maxEvolutions(int generation) {
        return switch (generation) {
            case 3 -> 5;
            case 4, 5 -> 7;
            case 6, 7 -> 8;
            default -> 1; // Gen 1 and 2 store evolutions in variable length data; only editing is supported there
        };
    }

    /**
     * Gen 1 and 2 keep evolutions in variable length data with no spare room, so evolutions can only be edited
     * in place there (same method, new level), never added, removed or changed to another method.
     */
    public static boolean canAddOrRemove(int generation) {
        return generation >= 3;
    }
}
