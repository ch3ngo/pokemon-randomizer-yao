package com.uprfvx.romio.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  DsHealPartyPatch.java - Pokemon Centers that do not revive Pokemon.   --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.RomFunctions;

import java.util.List;

/**
 * Makes the game's "heal the whole party" function skip fainted Pokemon (Gen 4 and Gen 5), so a Pokemon Center no longer revives
 * them (the basis of Locke and Soft-locke modes).
 *
 * <p>In HeartGold and SoulSilver that function loops over the party and, for each slot, first asks the game
 * "is there a Pokemon here?" (data field 172) and only then restores HP, status and PP. The patch changes that
 * one field number to 163, the Pokemon's current HP, so the very same check becomes "does it have HP left?".
 * It is a single byte. The function is also used when the player whites out, so after a whiteout the fainted
 * Pokemon stay fainted: with no healthy Pokemon left the run is over, which is the point of the mode.
 *
 * <p>Found by its exact bytes (identical in the English and Spanish versions checked), so it does nothing, and
 * says so, on any ARM9 that does not contain it exactly once.
 */
public final class DsHealPartyPatch {

    private DsHealPartyPatch() {
    }

    /** One known version of the heal function: how to find it and which byte to change. */
    private record Variant(String signature, int patchOffset, byte originalField, byte newField, String where) {
    }

    /**
     * The first 42 bytes of the HeartGold and SoulSilver heal function, up to and including the load of the
     * data field number that is patched (172, the species exists; patched to 163, the current HP). Thumb code.
     */
    static final String GEN4_SIGNATURE =
            "f0b583b00090e3f70dfd00270190002830dd7f260098391ce3f706fdac210022051cddf77ffc002820d0";

    /** Position of the field number inside the signature. */
    static final int PATCH_OFFSET = 28;
    static final byte ORIGINAL_FIELD = (byte) 0xAC; // 172, the species exists
    static final byte NEW_FIELD = (byte) 0xA3;      // 163, the current HP

    /**
     * Black 2 and White 2: the whole 52 byte "recover the whole party" function (PokeParty_RecoverAll). It asks
     * field 169 (the Pokemon exists) before recovering a slot; patched to 160, the current HP.
     */
    static final String GEN5_SIGNATURE =
            "f8b5071cfef772ff061c0025002e10dd381c291cfef7f2ffa9210022041cfbf7e5fe002802d0201cfff7c8ff6d1cb542eedbf8bd";
    static final int GEN5_PATCH_OFFSET = 24;
    static final byte GEN5_ORIGINAL_FIELD = (byte) 0xA9; // 169
    static final byte GEN5_NEW_FIELD = (byte) 0xA0;      // 160

    private static final List<Variant> VARIANTS = List.of(
            new Variant(GEN4_SIGNATURE, PATCH_OFFSET, ORIGINAL_FIELD, NEW_FIELD, "Gen 4"),
            new Variant(GEN5_SIGNATURE, GEN5_PATCH_OFFSET, GEN5_ORIGINAL_FIELD, GEN5_NEW_FIELD, "Gen 5"));

    /** Every (variant, offset) at which a known heal function occurs in this ARM9. */
    private static List<Object[]> find(byte[] arm9) {
        List<Object[]> all = new java.util.ArrayList<>();
        for (Variant v : VARIANTS) {
            for (int at : RomFunctions.search(arm9, RomFunctions.hexToBytes(v.signature()))) {
                all.add(new Object[]{v, at});
            }
        }
        return all;
    }

    public static FeatureAvailability check(byte[] arm9) {
        List<Object[]> found = find(arm9);
        if (found.size() == 1) {
            return FeatureAvailability.yes();
        }
        return FeatureAvailability.no(found.isEmpty()
                ? "The party heal function was not found in this game's code (a different version, or a modified ROM)."
                : "The party heal function was found more than once in this game's code.");
    }

    /** Applies the patch to the ARM9 in place. Never throws because of the ARM9's contents. */
    public static PatchResult apply(byte[] arm9) {
        List<Object[]> found = find(arm9);
        if (found.isEmpty()) {
            return PatchResult.notApplied("The party heal function was not found in this game's code, so Pokemon "
                    + "Centers will still revive fainted Pokemon.");
        }
        if (found.size() > 1) {
            return PatchResult.notApplied("The party heal function was found more than once, so it was not patched "
                    + "to avoid changing the wrong place. Pokemon Centers will still revive fainted Pokemon.");
        }
        Variant variant = (Variant) found.get(0)[0];
        int at = (Integer) found.get(0)[1] + variant.patchOffset();
        if (arm9[at] != variant.originalField()) {
            return PatchResult.notApplied("The party heal function does not look as expected, so it was not patched.");
        }
        arm9[at] = variant.newField();
        return PatchResult.applied("Pokemon Centers (and a whiteout) no longer revive fainted Pokemon.");
    }
}
