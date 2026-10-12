package com.uprfvx.romio.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  FeatureAvailability.java - can a YAO option work with this game?      --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

/**
 * Whether an option that needs support from the game (for example a code patch) can be used with the
 * ROM that is loaded, and if not, why. The interface uses this to enable an option, or to disable it
 * and show the reason. Checking never changes the ROM.
 */
public record FeatureAvailability(boolean available, String reason) {

    public static FeatureAvailability yes() {
        return new FeatureAvailability(true, "");
    }

    public static FeatureAvailability no(String reason) {
        return new FeatureAvailability(false, reason);
    }
}
