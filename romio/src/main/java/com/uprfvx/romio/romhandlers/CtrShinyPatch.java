package com.uprfvx.romio.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  CtrShinyPatch.java - adjustable shiny chance for Gen 6 and 7 (3DS).   --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Makes a chosen percentage of all Pokemon count as shiny in the 3DS games (Gen 6 and Gen 7), with the same idea as
 * {@link DsShinyPatch} (read that class first for the reasoning).
 *
 * <p>In all four games checked (X, Omega Ruby, Sun and Ultra Sun, English text, identical code) the shiny test
 * {@code (TID ^ SID ^ PIDhigh ^ PIDlow) < 16} is one small ARM function. The code that <i>creates</i> a Pokemon calls
 * that function directly in loops (and has other inline copies); the code that <i>shows</i> a Pokemon (sprite, shiny
 * sparkle, summary, party menu, battle) calls a small wrapper just before it that reads the ids and PID out of the
 * Pokemon's data and runs into the function. The wrapper ends with {@code pop {r4,r5,r6,lr}} and a no-op, then the
 * function follows. So the patch only has to replace that one no-op with a jump to a few new instructions that ask
 * "are bits 8 to 23 of the PID below the threshold?" and answer yes, or continue to the original function: the
 * creation code is not touched at all (at 100 percent a loop that rerolls "until not shiny" would otherwise never end).
 *
 * <p>The new instructions go into the zero padding at the end of the code segment (the segment is padded to a 4096
 * byte page and that padding is executable). The size of the segment is read from the ExHeader. If the padding is
 * missing, too small or not all zero, or the wrapper and function are not found exactly once, nothing is changed and
 * the result says why.
 */
public final class CtrShinyPatch {

    private CtrShinyPatch() {
    }

    /** {@code mov r0,r5; pop {r4,r5,r6,lr}; nop}, the end of the wrapper (little endian words). */
    static final int[] WRAPPER_END = {0xE1A00005, 0xE8BD4070, 0xE1A00000};

    /**
     * The function: {@code uxth r3,r0; eor r0,r3,r0,lsr #16; uxth r2,r1; eor r0,r0,r1,lsr #16; eor r0,r0,r2;
     * cmp r0,#16; movlo r0,#1; movhs r0,#0; bx lr}.
     */
    static final int[] FUNCTION = {0xE6FF3070, 0xE0230820, 0xE6FF2071, 0xE0200821, 0xE0200002, 0xE3500010,
            0x33A00001, 0x23A00000, 0xE12FFF1E};

    /** Bytes of padding needed: 8 words for the new code, plus margin. */
    static final int NEW_CODE_WORDS = 8;
    static final int FREE_NEEDED = 64;

    /** A threshold of 65536 makes every Pokemon shiny. */
    static final int MAX_THRESHOLD = 65536;

    private static int word(byte[] code, int offset) {
        return ByteBuffer.wrap(code, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static void putWord(byte[] code, int offset, int value) {
        ByteBuffer.wrap(code, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value);
    }

    /** The offset of the first word of the wrapper end followed by the function, if found exactly once, else -1. */
    private static int findWrapper(byte[] code) {
        int total = WRAPPER_END.length + FUNCTION.length;
        int found = -1;
        for (int i = 0; i + total * 4 <= code.length; i += 4) {
            if (word(code, i) != WRAPPER_END[0]) {
                continue;
            }
            boolean match = true;
            for (int k = 1; k < total && match; k++) {
                int expected = k < WRAPPER_END.length ? WRAPPER_END[k] : FUNCTION[k - WRAPPER_END.length];
                match = word(code, i + 4 * k) == expected;
            }
            if (match) {
                if (found >= 0) {
                    return -2;
                }
                found = i;
            }
        }
        return found;
    }

    /** The start of the zero padding after the code segment, or -1 if there is not enough room. */
    private static int findFreeSpace(byte[] code, int textSize) {
        if (textSize <= 0 || textSize > code.length) {
            return -1;
        }
        int start = (textSize + 3) & ~3;
        int pageEnd = (textSize + 0xFFF) & ~0xFFF;
        if (pageEnd > code.length || pageEnd - start < FREE_NEEDED) {
            return -1;
        }
        for (int i = start; i < start + FREE_NEEDED; i++) {
            if (code[i] != 0) {
                return -1;
            }
        }
        return start;
    }

    /** Whether the wrapper and function are found once, with executable padding free after the code. Changes nothing. */
    public static FeatureAvailability check(byte[] code, int textSize) {
        int wrapper = findWrapper(code);
        if (wrapper < 0) {
            return FeatureAvailability.no(wrapper == -2
                    ? "The shiny test was found more than once in this game's code."
                    : "The shiny test was not found in this game's code (a different version, or a modified ROM).");
        }
        if (findFreeSpace(code, textSize) < 0) {
            return FeatureAvailability.no("There is no free space at the end of this game's code to put the new "
                    + "code in.");
        }
        return FeatureAvailability.yes();
    }

    /**
     * Applies the patch to the executable in place. Never throws because of its contents: anything unexpected leaves
     * it untouched and is reported in the result.
     *
     * @param textSize the size of the code segment in bytes, from the ExHeader
     */
    public static PatchResult apply(byte[] code, int textSize, double percent) {
        int threshold = DsShinyPatch.thresholdFor(percent);
        if (threshold <= 0) {
            return PatchResult.notApplied("The shiny chance is 0 (or too small to matter), nothing was changed.");
        }
        int wrapper = findWrapper(code);
        if (wrapper == -2) {
            return PatchResult.notApplied("The shiny test was found more than once, so the shiny chance was not "
                    + "applied to avoid patching the wrong place.");
        }
        if (wrapper < 0) {
            return PatchResult.notApplied("The shiny test was not found in this game's code, so the shiny chance "
                    + "was not applied. Only a version with the expected code can be patched.");
        }
        int free = findFreeSpace(code, textSize);
        if (free < 0) {
            return PatchResult.notApplied("There is no free space at the end of this game's code, so the shiny "
                    + "chance was not applied.");
        }
        double clamped = Math.min(percent, 100.0);

        int nop = wrapper + 8;                       // the no-op that ends the wrapper
        int function = wrapper + 12;                 // the original test, kept as it is
        putWord(code, free, 0xE1A02401);             // mov r2, r1, lsl #8
        putWord(code, free + 4, 0xE1A02822);         // mov r2, r2, lsr #16     bits 8 to 23 of the PID
        putWord(code, free + 8, 0xE59F300C);         // ldr r3, [pc, #12]       the threshold, 8 bytes further on
        putWord(code, free + 12, 0xE1520003);        // cmp r2, r3
        putWord(code, free + 16, 0x33A00001);        // movlo r0, #1
        putWord(code, free + 20, 0x312FFF1E);        // bxlo lr                 below the threshold: shiny
        putWord(code, free + 24, 0xEA000000 | (((function - (free + 24) - 8) >> 2) & 0xFFFFFF)); // b function
        putWord(code, free + 28, threshold);
        putWord(code, nop, 0xEA000000 | (((free - nop - 8) >> 2) & 0xFFFFFF));                    // b new code

        return PatchResult.applied("About " + (clamped == Math.rint(clamped) ? String.valueOf((long) clamped)
                : String.format(java.util.Locale.ROOT, "%.1f", clamped)) + " percent of all Pokemon (wild, static, "
                + "gift, egg and trainer Pokemon) will be shiny.");
    }
}
