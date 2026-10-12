package com.uprfvx.romio.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  Gen2ShinyPatch.java - adjustable shiny chance for Gen 2 (Game Boy).   --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.RomFunctions;

/**
 * Makes a chosen percentage of the Pokemon count as shiny in Gen 2 (checked with Crystal).
 *
 * <p>Gen 2 has no PID. A Pokemon's four DVs (Attack, Defense, Speed and Special, 4 bits each) decide everything,
 * and the game answers "is this shiny?" with one small function ({@code CheckShininess}, called with the address of
 * the DVs in {@code bc}, answering in the carry flag) that is only used to <i>show</i> a Pokemon (palette and the
 * shiny sparkle). It is never used while creating a Pokemon, so, unlike Gen 3 to 5, there are no creation loops to
 * protect.
 *
 * <p>The new function makes a Pokemon shiny when the original test says so <b>or</b> when the 16 bit number
 * {@code (Attack,Defense) xor (Speed,Special)} followed by {@code (Speed,Special)} is below a threshold. That
 * number is a one to one rearrangement of the four DVs, so with random DVs it is uniform and the threshold gives the
 * chosen percentage; and because Attack is mixed with Speed (which is independent of it), the shiny Pokemon still have
 * every single DV uniformly distributed, so they are not skewed towards one gender or towards weak stats.
 *
 * <p>The original function (32 bytes) has no room, so the new one is written into the unused zero bytes at the end
 * of the same ROM bank and the first three bytes of the original become a {@code jp} to it; all callers (in the same
 * bank or through far calls) keep working. Found by the exact bytes of the original function, so it only works on a
 * version that has them, and does nothing (and says so) otherwise.
 *
 * <p>Limitation: trainers' Pokemon use fixed DVs per trainer class, so for them the share of shiny Pokemon is not
 * the chosen percentage (a class either is shiny or is not). Wild, gift and egg Pokemon use random DVs and follow the
 * percentage. The shininess lives in the game's check, not in the Pokemon's data.
 */
public final class Gen2ShinyPatch {

    private Gen2ShinyPatch() {
    }

    /** The original function: ld l,c; ld h,b; then the DV test, 32 bytes, ending in the two returns. */
    static final String ORIGINAL = "69607ee62028172ae60ffe0a20107ee6f0fea020097ee60ffe0a200237c9a7c9";

    /** The part of the original after {@code ld l,c; ld h,b}, copied into the new function. */
    static final int BODY_START = 2;
    static final int ORIGINAL_LENGTH = 32;

    /** Length of the new function written into free space. */
    static final int NEW_LENGTH = 49;

    /** Free space needed: the new function and some margin so nothing else is crowded. */
    private static final int FREE_NEEDED = 64;

    private static byte[] original() {
        return RomFunctions.hexToBytes(ORIGINAL);
    }

    private static int findFunction(byte[] rom) {
        java.util.List<Integer> found = RomFunctions.search(rom, original());
        return found.size() == 1 ? found.get(0) : -1;
    }

    /** The offset of the last run of {@link #FREE_NEEDED} zero bytes at the end of the 16 KB bank, or -1. */
    private static int findFreeSpace(byte[] rom, int bank) {
        int bankStart = bank * 0x4000;
        int bankEnd = bankStart + 0x4000;
        if (bankStart == 0 || bankEnd > rom.length) {
            return -1;
        }
        int start = bankEnd - FREE_NEEDED;
        for (int i = start; i < bankEnd; i++) {
            if (rom[i] != 0) {
                return -1;
            }
        }
        return start;
    }

    /** Whether the function is found once, in a switchable bank, with free space after it. Changes nothing. */
    public static FeatureAvailability check(byte[] rom) {
        int function = findFunction(rom);
        if (function < 0) {
            return FeatureAvailability.no("The shiny test was not found in this game's code (a different version, "
                    + "or a modified ROM).");
        }
        if (findFreeSpace(rom, function / 0x4000) < 0) {
            return FeatureAvailability.no("There is no free space next to the shiny test to put the new code in.");
        }
        return FeatureAvailability.yes();
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
        int function = findFunction(rom);
        if (function < 0) {
            return PatchResult.notApplied("The shiny test was not found in this game's code, so the shiny chance "
                    + "was not applied. Only a version with the expected code can be patched.");
        }
        int bank = function / 0x4000;
        int free = findFreeSpace(rom, bank);
        if (free < 0) {
            return PatchResult.notApplied("There is no free space next to the shiny test, so the shiny chance was "
                    + "not applied.");
        }
        double clamped = Math.min(percent, 100.0);
        int thresholdHigh = Math.min(threshold >> 8, 0xFF);
        int thresholdLow = threshold & 0xFF;

        byte[] original = original();
        byte[] code = new byte[NEW_LENGTH];
        int i = 0;
        code[i++] = 0x69;                      // ld l, c
        code[i++] = 0x60;                      // ld h, b
        code[i++] = 0x2A;                      // ld a, [hli]            Attack and Defense
        code[i++] = (byte) 0xAE;               // xor [hl]               xor Speed and Special
        code[i++] = (byte) 0xFE;               // cp high
        code[i++] = (byte) thresholdHigh;
        code[i++] = 0x38;                      // jr c, shiny
        code[i++] = 0x27;
        code[i++] = 0x20;                      // jr nz, original test
        code[i++] = 0x05;
        code[i++] = 0x7E;                      // ld a, [hl]             Speed and Special
        code[i++] = (byte) 0xFE;               // cp low
        code[i++] = (byte) thresholdLow;
        code[i++] = 0x38;                      // jr c, shiny
        code[i++] = 0x20;
        code[i++] = 0x69;                      // original test: ld l, c
        code[i++] = 0x60;                      //                ld h, b
        System.arraycopy(original, BODY_START, code, i, ORIGINAL_LENGTH - BODY_START);
        i += ORIGINAL_LENGTH - BODY_START;
        code[i++] = 0x37;                      // shiny: scf
        code[i++] = (byte) 0xC9;               //        ret
        if (threshold >= DsShinyPatch.MAX_THRESHOLD) {
            code[0] = 0x37;                    // every Pokemon: scf; ret
            code[1] = (byte) 0xC9;
        }

        System.arraycopy(code, 0, rom, free, NEW_LENGTH);
        int address = 0x4000 + (free % 0x4000);
        rom[function] = (byte) 0xC3;           // jp new function
        rom[function + 1] = (byte) address;
        rom[function + 2] = (byte) (address >> 8);

        String text = clamped == Math.rint(clamped) ? String.valueOf((long) clamped)
                : String.format(java.util.Locale.ROOT, "%.1f", clamped);
        return PatchResult.applied("About " + text + " percent of the wild, gift and egg Pokemon will be shiny. "
                + "Trainers' Pokemon use fixed values per trainer class in this game, so for them it is not the "
                + "chosen percentage.");
    }
}
