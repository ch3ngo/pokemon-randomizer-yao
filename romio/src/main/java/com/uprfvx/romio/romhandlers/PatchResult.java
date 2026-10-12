package com.uprfvx.romio.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  PatchResult.java - what happened when a code patch was applied.       --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

/**
 * Outcome of a code patch such as {@link RomHandler#applyShinyChance(double)}. Never an exception: a game that cannot take the
 * patch simply reports why, so the randomization carries on without it.
 */
public record PatchResult(boolean applied, String message) {

    public static PatchResult applied(String message) {
        return new PatchResult(true, message);
    }

    public static PatchResult notApplied(String message) {
        return new PatchResult(false, message);
    }

    public static PatchResult unsupported() {
        return notApplied("An adjustable shiny chance is not available for this game yet.");
    }
}
