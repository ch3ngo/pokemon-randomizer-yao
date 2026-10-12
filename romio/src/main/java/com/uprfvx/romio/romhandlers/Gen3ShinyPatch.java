package com.uprfvx.romio.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  Gen3ShinyPatch.java - adjustable shiny chance for Emerald (Gen 3).    --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.RomFunctions;

/**
 * Makes a chosen percentage of all Pokemon count as shiny in Pokemon Emerald (English), with the same idea and the
 * same two-entry function as {@link DsShinyPatch} (read that class first for the reasoning, including why the
 * creation loops must keep the original test).
 *
 * <p>What the game does: the shiny test {@code (TID ^ SID ^ PIDhigh ^ PIDlow) < 8} exists as one small function at
 * 0x0806EBD0, called by the "is this Pokemon shiny" wrapper at 0x0806EBC0 (summary screen, battle sparkle and the
 * rest) and by two creation loops that keep rolling a PID while it is shiny (0x08165458 and 0x081A4EAC, left
 * unchanged). It also exists inline, with no function call, in the two sprite palette functions at 0x0806E740
 * and 0x0806E7CC (the colours of the Pokemon in battle and in menus), and once more in a function at 0x08172F36
 * that is not a display function and is left alone. The creation of a Pokemon (0x08067C56) keeps the original test.
 *
 * <p>The patch rewrites the function at 0x0806EBD0 into the same two-entry function as on the DS (the original
 * entry unchanged at the original address, the new test right after it), points the wrapper's call and the two
 * inline copies in the palette functions at the new entry, and stores the threshold in the 4 bytes where the
 * function's own literal was. The two inline copies are replaced by a call (the functions have already saved the
 * link register) followed by a conditional branch, padded with no-ops. Every place is checked byte by byte first;
 * if anything differs the ROM is left untouched and the result says so. Only the English Emerald is supported.
 *
 * <p>Limitation: the shininess lives in the game's check, not in the Pokemon's data.
 */
public final class Gen3ShinyPatch {

    private Gen3ShinyPatch() {
    }

    /** ROM offset of the original function (RAM 0x0806EBD0), 34 bytes of code. */
    static final int FUNCTION = 0x6EBD0;

    /** The original function, then two bytes of padding, then the 0x0000FFFF literal it loads. */
    static final String FUNCTION_ORIGINAL = "10b50024020c074b18404240080c424019404a40072a00d80124201c10bc02bc0847" + "0000" + "ffff0000";

    /** The wrapper's call to the function, a Thumb bl at 0x0806EBC0. */
    static final int WRAPPER_CALL = 0x6EBC0;

    /** First inline copy: the 20 bytes from {@code lsrs r0, r1, #16} up to and including {@code bls shiny}. */
    static final int SITE_PALETTE_1 = 0x6E75C;
    static final String SITE_PALETTE_1_ORIGINAL = "080c064a11404840190c484013405840072807d9";

    /** Second inline copy in the other palette function. */
    static final int SITE_PALETTE_2 = 0x6E7D2;
    static final String SITE_PALETTE_2_ORIGINAL = "080c054b19404840110c48401a405040072806d9";

    /**
     * Replacement for the two inline copies, in three parts: two moves that put the id in r0 and the PID in r1 (the
     * registers the function expects), a Thumb bl to the new entry point (written at run time, 4 bytes) and then
     * {@code cmp r0, #0; bne <shiny path>} followed by four no-ops. Assembled with Keystone.
     */
    static final String SITE_PALETTE_1_BEFORE_CALL = "081c191c";
    static final String SITE_PALETTE_1_AFTER_CALL = "00280bd1c046c046c046c046";
    static final String SITE_PALETTE_2_BEFORE_CALL = "081c111c";
    static final String SITE_PALETTE_2_AFTER_CALL = "00280ad1c046c046c046c046";

    private static boolean matches(byte[] rom, int offset, String hex) {
        byte[] expected = RomFunctions.hexToBytes(hex);
        if (offset < 0 || offset + expected.length > rom.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (rom[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean wrapperCallsFunction(byte[] rom) {
        if (WRAPPER_CALL + 4 > rom.length) {
            return false;
        }
        int hi = (rom[WRAPPER_CALL] & 0xFF) | ((rom[WRAPPER_CALL + 1] & 0xFF) << 8);
        int lo = (rom[WRAPPER_CALL + 2] & 0xFF) | ((rom[WRAPPER_CALL + 3] & 0xFF) << 8);
        if ((hi & 0xF800) != 0xF000 || (lo & 0xF800) != 0xF800) {
            return false;
        }
        int offset = ((hi & 0x7FF) << 12) | ((lo & 0x7FF) << 1);
        if ((offset & 0x400000) != 0) {
            offset -= 0x800000;
        }
        return WRAPPER_CALL + 4 + offset == FUNCTION;
    }

    private static void writeBl(byte[] rom, int at, int target) {
        long offset = target - (at + 4L);
        int hi = 0xF000 | (int) ((offset >> 12) & 0x7FF);
        int lo = 0xF800 | (int) ((offset >> 1) & 0x7FF);
        rom[at] = (byte) hi;
        rom[at + 1] = (byte) (hi >> 8);
        rom[at + 2] = (byte) lo;
        rom[at + 3] = (byte) (lo >> 8);
    }

    private static boolean recognised(byte[] rom) {
        return matches(rom, FUNCTION, FUNCTION_ORIGINAL)
                && wrapperCallsFunction(rom)
                && matches(rom, SITE_PALETTE_1, SITE_PALETTE_1_ORIGINAL)
                && matches(rom, SITE_PALETTE_2, SITE_PALETTE_2_ORIGINAL);
    }

    /** Whether the shiny code of the English Emerald is found where and as expected. Changes nothing. */
    public static FeatureAvailability check(byte[] rom) {
        return recognised(rom)
                ? FeatureAvailability.yes()
                : FeatureAvailability.no("The shiny test was not found in this game's code (only the English Emerald "
                + "is supported, and the ROM must not have been modified in that area).");
    }

    /**
     * Applies the patch in place. Never throws because of the contents of the ROM: anything unexpected leaves it
     * untouched and is reported in the result.
     */
    public static PatchResult apply(byte[] rom, double percent) {
        int threshold = DsShinyPatch.thresholdFor(percent);
        if (threshold <= 0) {
            return PatchResult.notApplied("The shiny chance is 0 (or too small to matter), nothing was changed.");
        }
        if (!recognised(rom)) {
            return PatchResult.notApplied("The shiny test was not found in this game's code, so the shiny chance "
                    + "was not applied. Only the English Emerald can be patched, and only if that part of the game "
                    + "has not been modified.");
        }
        double clamped = Math.min(percent, 100.0);

        byte[] code = RomFunctions.hexToBytes(DsShinyPatch.REPLACEMENT_CODE);
        System.arraycopy(code, 0, rom, FUNCTION, DsShinyPatch.CODE_LENGTH);
        int literal = FUNCTION + DsShinyPatch.CODE_LENGTH;
        rom[literal] = (byte) threshold;
        rom[literal + 1] = (byte) (threshold >> 8);
        rom[literal + 2] = (byte) (threshold >> 16);
        rom[literal + 3] = (byte) (threshold >> 24);

        int newEntry = FUNCTION + DsShinyPatch.NEW_ENTRY_OFFSET;
        writeBl(rom, WRAPPER_CALL, newEntry);
        patchSite(rom, SITE_PALETTE_1, SITE_PALETTE_1_BEFORE_CALL, SITE_PALETTE_1_AFTER_CALL, newEntry);
        patchSite(rom, SITE_PALETTE_2, SITE_PALETTE_2_BEFORE_CALL, SITE_PALETTE_2_AFTER_CALL, newEntry);

        return PatchResult.applied("About " + (clamped == Math.rint(clamped) ? String.valueOf((long) clamped)
                : String.format(java.util.Locale.ROOT, "%.1f", clamped)) + " percent of all Pokemon (wild, static, "
                + "gift, egg and trainer Pokemon) will be shiny.");
    }

    private static void patchSite(byte[] rom, int site, String before, String after, int newEntry) {
        byte[] first = RomFunctions.hexToBytes(before);
        byte[] last = RomFunctions.hexToBytes(after);
        System.arraycopy(first, 0, rom, site, first.length);
        writeBl(rom, site + first.length, newEntry);
        System.arraycopy(last, 0, rom, site + first.length + 4, last.length);
    }
}
