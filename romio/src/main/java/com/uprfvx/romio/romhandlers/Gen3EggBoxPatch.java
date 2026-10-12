package com.uprfvx.romio.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  Gen3EggBoxPatch.java - the egg box for Emerald (Gen 3).               --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.RomFunctions;

import java.util.function.IntUnaryOperator;

/**
 * Makes the egg woman of Lavaridge Town in Pokemon Emerald (English) hand over a whole box of eggs instead of the one
 * Wynaut egg. This is a change to the game's script, not to its program code.
 *
 * <p>The script of that woman checks whether the party is full (and refuses if so), sets the "received" flag, shows a
 * message and ends with {@code waitmessage; giveegg WYNAUT; release; end}. In Gen 3 the {@code giveegg} command puts the
 * egg in the party if there is room and in the PC (starting with the current box and moving on through all fourteen
 * boxes) if there is not, so a script that repeats it delivers as many eggs as asked, up to the room that the save has.
 *
 * <p>The patch leaves the ending of the script exactly where it is, so the Wynaut egg (a "static Pokemon" the randomizer
 * knows about and reads again later) stays valid, and gives it the species of the last egg. It replaces the
 * five bytes of the {@code message} command just before it with a {@code goto} to a new block of script in free space
 * that shows the same message, gives all the other eggs ({@code giveegg} each) and jumps back to the {@code waitmessage}
 * of the original ending. It also changes the number the script compares the party size with from 6 to 7, so the woman
 * never refuses because the party is full.
 *
 * <p>Every place is checked byte by byte first; if anything differs the ROM is left untouched and the result says why.
 * The number of eggs is limited to 90 (three boxes) because the game silently loses an egg when every box is full.
 */
public final class Gen3EggBoxPatch {

    private Gen3EggBoxPatch() {
    }

    /** The most eggs one box can hold, and the most this patch will give. */
    public static final int MAX_EGGS = 90;

    /** Start of the script of the egg woman: {@code lock; faceplayer; checkflag ...}. */
    static final int SCRIPT_START = 0x1EA744;
    static final String SCRIPT_START_BYTES = "6a5a2b0a0106";

    /** {@code getpartysize; compare VAR_RESULT, 6; goto_if_eq <party full>}. The compare value is 4 bytes in. */
    static final int PARTY_CHECK = 0x1EA762;
    static final String PARTY_CHECK_BYTES = "43210d8006000601";
    static final int PARTY_CHECK_VALUE = PARTY_CHECK + 4;

    /** {@code message <text>}: five bytes, the opcode and a pointer. */
    static final int MESSAGE = 0x1EA77C;

    /**
     * The ending: {@code waitmessage} at {@code TAIL - 1}, then {@code giveegg <species>; release; end}. The species (two
     * bytes, at {@code TAIL + 1}) is Wynaut in the original game and whatever the static Pokemon randomization chose if
     * that ran first, so it is not checked.
     */
    static final int TAIL = 0x1EA782;

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

    private static boolean tailMatches(byte[] rom) {
        return TAIL + 5 <= rom.length
                && rom[MESSAGE] == 0x67                             // message
                && rom[TAIL - 1] == 0x32                            // waitmessage
                && rom[TAIL] == 0x7A                                // giveegg
                && rom[TAIL + 3] == 0x6C && rom[TAIL + 4] == 0x02;  // release; end
    }

    private static boolean recognised(byte[] rom) {
        return matches(rom, SCRIPT_START, SCRIPT_START_BYTES)
                && matches(rom, PARTY_CHECK, PARTY_CHECK_BYTES)
                && tailMatches(rom);
    }

    /** Whether the egg woman's script is found where and as expected. Changes nothing. */
    public static FeatureAvailability check(byte[] rom) {
        return recognised(rom)
                ? FeatureAvailability.yes()
                : FeatureAvailability.no("The egg woman of Lavaridge Town was not found in this game's scripts (only "
                + "the English Emerald is supported, and its scripts must not have been modified).");
    }

    /**
     * The block of script: the original {@code message}, a {@code giveegg} for each species except the last (which the
     * original ending gives), and a {@code goto} back to the original {@code waitmessage}.
     *
     * @param internalSpecies the species of all the eggs, as the game numbers them internally
     * @param messagePointer  the four bytes of the original message's pointer
     * @param resumePointer   the ROM address (with the 0x08000000 base) of the original {@code waitmessage}
     */
    static byte[] buildBlock(int[] internalSpecies, byte[] messagePointer, int resumePointer) {
        int others = internalSpecies.length - 1;
        byte[] block = new byte[5 + others * 3 + 5];
        int i = 0;
        block[i++] = 0x67;                           // message <original text>
        for (int b = 0; b < 4; b++) {
            block[i++] = messagePointer[b];
        }
        for (int e = 0; e < others; e++) {
            block[i++] = 0x7A;                       // giveegg
            block[i++] = (byte) internalSpecies[e];
            block[i++] = (byte) (internalSpecies[e] >> 8);
        }
        block[i++] = 0x05;                           // goto <the original waitmessage>
        block[i++] = (byte) resumePointer;
        block[i++] = (byte) (resumePointer >> 8);
        block[i++] = (byte) (resumePointer >> 16);
        block[i] = (byte) (resumePointer >> 24);
        return block;
    }

    /**
     * Applies the patch in place.
     *
     * @param internalSpecies the species of the eggs, as the game numbers them internally
     * @param allocate        reserves free space in the ROM for a number of bytes and returns its offset
     */
    public static PatchResult apply(byte[] rom, int[] internalSpecies, IntUnaryOperator allocate) {
        if (internalSpecies == null || internalSpecies.length == 0) {
            return PatchResult.notApplied("There are no eggs to add, nothing was changed.");
        }
        if (internalSpecies.length > MAX_EGGS) {
            return PatchResult.notApplied("More than " + MAX_EGGS + " eggs do not fit in the PC boxes at once, nothing "
                    + "was changed.");
        }
        if (!recognised(rom)) {
            return PatchResult.notApplied("The egg woman of Lavaridge Town was not found in this game's scripts, so "
                    + "the egg box was not added. Only the English Emerald can take it, and only if that script has "
                    + "not been modified.");
        }
        for (int species : internalSpecies) {
            if (species <= 0 || species > 0xFFFF) {
                return PatchResult.notApplied("A species of the egg box does not exist in this game, nothing was "
                        + "changed.");
            }
        }
        byte[] messagePointer = java.util.Arrays.copyOfRange(rom, MESSAGE + 1, MESSAGE + 5);
        int resume = TAIL - 1 + 0x08000000;
        byte[] block = buildBlock(internalSpecies, messagePointer, resume);
        int offset;
        try {
            offset = allocate.applyAsInt(block.length);
        } catch (RuntimeException e) {
            return PatchResult.notApplied("There is no free space in the ROM for the egg box (" + e.getMessage() + ").");
        }
        if (offset < 0 || offset + block.length > rom.length) {
            return PatchResult.notApplied("There is no free space in the ROM for the egg box.");
        }
        System.arraycopy(block, 0, rom, offset, block.length);

        rom[PARTY_CHECK_VALUE] = 7;                  // never refuse for a full party: the eggs go to the PC
        int pointer = offset + 0x08000000;
        rom[MESSAGE] = 0x05;                         // goto <block>, over the original message
        rom[MESSAGE + 1] = (byte) pointer;
        rom[MESSAGE + 2] = (byte) (pointer >> 8);
        rom[MESSAGE + 3] = (byte) (pointer >> 16);
        rom[MESSAGE + 4] = (byte) (pointer >> 24);
        int last = internalSpecies[internalSpecies.length - 1];
        rom[TAIL + 1] = (byte) last;                 // the original ending gives the last egg
        rom[TAIL + 2] = (byte) (last >> 8);
        return PatchResult.applied(internalSpecies.length + " egg(s) will be given by the egg woman of Lavaridge Town "
                + "(party first, then the PC boxes). They are all given in one conversation, so keep room: an egg "
                + "is lost if every box is full.");
    }
}
