package com.uprfvx.romio.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  DsShinyPatch.java - adjustable shiny chance for Gen 4 and 5 (DS).     --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.RomFunctions;

import java.util.List;

/**
 * Makes a chosen percentage of all Pokemon count as shiny in the Nintendo DS games (Gen 4 and Gen 5), by changing
 * what the game answers when it is asked "is this Pokemon shiny?" for <b>display</b> (the sprite palette, the shiny
 * sparkle, the summary screen and the rest of the game).
 *
 * <p>Original behaviour: shiny if {@code (TID ^ SID ^ PIDhigh ^ PIDlow) < 8}. New behaviour: shiny if that
 * is true <b>or</b> if bits 8 to 23 of the Pokemon's PID, read as a number from 0 to 65535, are below a
 * threshold T. PIDs are random, so a threshold of {@code percent / 100 * 65536} makes that percentage of
 * Pokemon shiny. Because the decision depends only on the PID, it covers every Pokemon whatever its origin:
 * wild, static, gift, egg and also the trainers' Pokemon, which cannot be made shiny by editing data because
 * their PID is derived from the trainer and the game never lets their trainer id roll shiny.
 *
 * <p><b>Why the function has two entry points.</b> The game's shiny test is called from two kinds of places: the
 * code that <i>displays</i> a Pokemon, and the code that <i>creates</i> one and keeps rolling a new PID while it is
 * (or is not) shiny. Changing the test for the creation loops would be a bug: with a chance of 100 percent a loop
 * that rolls "until not shiny" would never end (the game would freeze when a trainer's Pokemon is created), and
 * with any other chance the trainers' PIDs could never fall in the shiny band. So the original function stays at its
 * original address with its exact original behaviour (every creation loop keeps calling it, unchanged), and the new
 * test is a second entry point placed right after it that does the PID test first and then falls back to the
 * original one. Only the display callers are pointed at the new entry point: in both generations the small wrapper
 * just before the function ("is this Pokemon shiny", reading the id and PID from the Pokemon), and in Gen 5 the one
 * display call in an overlay.
 *
 * <p>The replacement is the same size as the original, needs no free space, uses no absolute address and is
 * found by its exact bytes, so it works in every language version that has the same code and does nothing
 * (and says so) if the function is not recognised. The original entry point and the new one with a threshold
 * of 0 both behave exactly like the original function; this was checked against the original over 100,000
 * random inputs per generation with a CPU emulator, and the observed shiny rates from 0.1 to 100 percent
 * matched the requested ones.
 *
 * <p>Limitation: the shininess lives in the game's check, not in the Pokemon's data. A Pokemon that is
 * shiny here will not be shiny if it is moved to a game without this patch.
 */
public final class DsShinyPatch {

    private DsShinyPatch() {
    }

    /**
     * What the patch needs to know about the game beyond the ARM9 bytes: where the ARM9 and the overlays are loaded
     * in memory and how to read and write the overlays. Only needed to point the one overlay call at the new entry.
     */
    public interface Memory {
        int arm9Base();

        int overlayCount();

        int overlayBase(int number);

        byte[] readOverlay(int number) throws java.io.IOException;

        void writeOverlay(int number, byte[] data) throws java.io.IOException;
    }

    /**
     * The original function of HeartGold and SoulSilver (Gen 4), followed by the 0xFFFF0000 literal that it
     * loads. 40 bytes of code. English and Spanish are identical, at 0x02070068 in the versions checked.
     */
    static final String GEN4_SIGNATURE =
            "094b0a041940034000041b0c000c090c5840120c48405040082801d2012000e000200006000e7047" + "0000ffff";

    /**
     * The same test as compiled in Black 2 and White 2 (Gen 5), followed by its 0xFFFF0000 literal. 36 bytes of
     * code, which is exactly the room needed for the replacement code (36 bytes) and its threshold (4 bytes, which
     * take the place of the original literal). English and Spanish Black 2 are identical, at ARM9 offset 0x18DD8
     * (RAM 0x0201CDD8).
     */
    static final String GEN5_SIGNATURE =
            "084b0a041940034000041b0c000c090c5840120c48405040082801d20120704700207047" + "0000ffff";

    /** Every function this patch knows how to replace. */
    static final List<String> ORIGINAL_SIGNATURES = List.of(GEN4_SIGNATURE, GEN5_SIGNATURE);

    /** Finds every place any known original function occurs in this ARM9. */
    private static List<Integer> findOriginal(byte[] arm9) {
        List<Integer> all = new java.util.ArrayList<>();
        for (String signature : ORIGINAL_SIGNATURES) {
            all.addAll(RomFunctions.search(arm9, RomFunctions.hexToBytes(signature)));
        }
        return all;
    }

    /**
     * Replacement code, 36 bytes of Thumb, assembled with Keystone and verified with an emulator. It starts with the
     * original test (kept exactly, so the original entry point behaves like the original function), and the new
     * entry point follows it:
     * <pre>
     * original:                      ; r0 = id, r1 = PID, returns 1 if shiny
     *   lsrs r2, r0, #16
     *   eors r0, r2
     *   lsrs r2, r1, #16
     *   eors r1, r2
     *   eors r0, r1
     *   lsls r0, r0, #16
     *   lsrs r0, r0, #16             ; low 16 bits = the original shiny value
     *   cmp  r0, #8
     *   sbcs r0, r0                  ; 0xFFFFFFFF if below 8, else 0
     *   negs r0, r0                  ; 1 or 0
     *   bx   lr
     * new entry (offset {@link #NEW_ENTRY_OFFSET}):
     *   lsls r2, r1, #8
     *   lsrs r2, r2, #16             ; bits 8 to 23 of the PID
     *   ldr  r3, [pc, #8]            ; threshold T, stored right after the code
     *   cmp  r2, r3
     *   bcs  original                ; not below the threshold: ask the original test
     *   movs r0, #1
     *   bx   lr
     * </pre>
     */
    static final String REPLACEMENT_CODE =
            "020c50400a0c514048400004000c08288041404270470a02120c024b9a42efd201207047";

    /** Length of the replacement code in bytes; the threshold follows it. */
    static final int CODE_LENGTH = 36;

    /** Where the new entry point starts, counted from the start of the original function. */
    static final int NEW_ENTRY_OFFSET = 22;

    /** A threshold of 65536 makes every Pokemon shiny. */
    static final int MAX_THRESHOLD = 65536;

    /**
     * The threshold for a percentage from 0 to 100 (decimals allowed). Out of range or not-a-number values
     * are moved to the nearest valid one. The check has 65536 levels, so the resolution is about 0.0015 percent,
     * far finer than the 0.1 percent steps the settings offer.
     */
    public static int thresholdFor(double percent) {
        if (Double.isNaN(percent)) {
            return 0;
        }
        double clamped = Math.max(0.0, Math.min(100.0, percent));
        return (int) Math.round(clamped / 100.0 * MAX_THRESHOLD);
    }

    /**
     * Whether the shiny test can be found, exactly once, in this ARM9. Changes nothing.
     */
    public static FeatureAvailability check(byte[] arm9) {
        List<Integer> found = findOriginal(arm9);
        if (found.size() == 1) {
            return FeatureAvailability.yes();
        }
        return FeatureAvailability.no(found.isEmpty()
                ? "The shiny test was not found in this game's code (a different version, or a modified ROM)."
                : "The shiny test was found more than once in this game's code.");
    }

    private static String formatPercent(double percent) {
        return percent == Math.rint(percent) ? String.valueOf((long) percent) : String.format(java.util.Locale.ROOT, "%.1f", percent);
    }

    // ---- Thumb "bl" helpers ----

    /** Returned by {@link #blTarget} when there is no Thumb bl at that place. */
    private static final long NO_CALL = Long.MIN_VALUE;

    /**
     * The target of the Thumb bl at {@code at} in {@code code}, as an offset relative to the start of {@code code}
     * (negative or past the end when it calls code outside of it), or {@link #NO_CALL}.
     */
    private static long blTarget(byte[] code, int at) {
        if (at < 0 || at + 4 > code.length) {
            return NO_CALL;
        }
        int hi = (code[at] & 0xFF) | ((code[at + 1] & 0xFF) << 8);
        int lo = (code[at + 2] & 0xFF) | ((code[at + 3] & 0xFF) << 8);
        if ((hi & 0xF800) != 0xF000 || (lo & 0xF800) != 0xF800) {
            return NO_CALL;
        }
        int offset = ((hi & 0x7FF) << 12) | ((lo & 0x7FF) << 1);
        if ((offset & 0x400000) != 0) {
            offset -= 0x800000;
        }
        return (long) at + 4 + offset;
    }

    /** Rewrites the Thumb bl at {@code at} so that it calls {@code target} (both relative to the start of {@code code}). */
    private static void writeBl(byte[] code, int at, long target) {
        long offset = target - (at + 4L);
        int hi = 0xF000 | (int) ((offset >> 12) & 0x7FF);
        int lo = 0xF800 | (int) ((offset >> 1) & 0x7FF);
        code[at] = (byte) hi;
        code[at + 1] = (byte) (hi >> 8);
        code[at + 2] = (byte) lo;
        code[at + 3] = (byte) (lo >> 8);
    }

    /**
     * In a Gen 5 overlay, the one display call: {@code ldr r0,[r6,#12]; ldr r1,[r6,#8]; bl <shiny test>; adds r7,r0,#0}.
     * Returns the offsets (inside the overlay) of those bl instructions that call the test.
     */
    private static List<Integer> findOverlayDisplayCalls(byte[] overlay, int overlayBase, int functionAddress) {
        List<Integer> calls = new java.util.ArrayList<>();
        for (int i = 4; i + 8 <= overlay.length; i += 2) {
            if ((overlay[i - 4] & 0xFF) != 0xF0 || (overlay[i - 3] & 0xFF) != 0x68
                    || (overlay[i - 2] & 0xFF) != 0xB1 || (overlay[i - 1] & 0xFF) != 0x68
                    || (overlay[i + 4] & 0xFF) != 0x07 || (overlay[i + 5] & 0xFF) != 0x1C) {
                continue;
            }
            long target = blTarget(overlay, i);
            if (target != NO_CALL && overlayBase + target == functionAddress) {
                calls.add(i);
            }
        }
        return calls;
    }

    /**
     * Applies the patch to the given ARM9 data in place (and, when {@code memory} is given, to the one overlay call
     * that needs it). Without {@code memory} only the ARM9 is changed, which is enough for Gen 4.
     * Never throws because of the contents of the ARM9: anything unexpected leaves it untouched and is
     * reported in the result.
     */
    public static PatchResult apply(byte[] arm9, double percent) {
        return apply(arm9, percent, null);
    }

    public static PatchResult apply(byte[] arm9, double percent, Memory memory) {
        int threshold = thresholdFor(percent);
        if (threshold <= 0) {
            return PatchResult.notApplied("The shiny chance is 0 (or too small to matter), nothing was changed.");
        }
        double clamped = Math.min(percent, 100.0);

        List<Integer> found = findOriginal(arm9);
        if (found.isEmpty()) {
            return PatchResult.notApplied("The shiny test was not found in this game's code, so the shiny "
                    + "chance was not applied. Only a version with the expected code can be patched.");
        }
        if (found.size() > 1) {
            return PatchResult.notApplied("The shiny test was found more than once, so the shiny chance "
                    + "was not applied to avoid patching the wrong place.");
        }

        int offset = found.get(0);
        // the display wrapper sits 8 bytes before the function and calls it; without it nothing would be shown
        int wrapperCall = offset - 8;
        if (blTarget(arm9, wrapperCall) != offset) {
            return PatchResult.notApplied("The part of the game that asks whether a Pokemon is shiny was not "
                    + "found next to the shiny test, so the shiny chance was not applied.");
        }

        byte[] code = RomFunctions.hexToBytes(REPLACEMENT_CODE);
        System.arraycopy(code, 0, arm9, offset, CODE_LENGTH);
        arm9[offset + CODE_LENGTH] = (byte) threshold;
        arm9[offset + CODE_LENGTH + 1] = (byte) (threshold >> 8);
        arm9[offset + CODE_LENGTH + 2] = (byte) (threshold >> 16);
        arm9[offset + CODE_LENGTH + 3] = (byte) (threshold >> 24);
        writeBl(arm9, wrapperCall, offset + NEW_ENTRY_OFFSET);

        String note = "";
        if (memory != null) {
            try {
                int functionAddress = memory.arm9Base() + offset;
                int patched = 0;
                for (int n = 0; n < memory.overlayCount(); n++) {
                    byte[] overlay = memory.readOverlay(n);
                    if (overlay == null) {
                        continue;
                    }
                    int base = memory.overlayBase(n);
                    List<Integer> calls = findOverlayDisplayCalls(overlay, base, functionAddress);
                    if (calls.isEmpty()) {
                        continue;
                    }
                    for (int at : calls) {
                        writeBl(overlay, at, (long) functionAddress + NEW_ENTRY_OFFSET - base);
                        patched++;
                    }
                    memory.writeOverlay(n, overlay);
                }
                if (patched > 0) {
                    note = " (" + patched + " more place(s) outside the main code were updated too)";
                }
            } catch (java.io.IOException | RuntimeException e) {
                note = " (some screens outside the main code could not be updated: " + e.getMessage() + ")";
            }
        }
        return PatchResult.applied("About " + formatPercent(clamped) + " percent of all Pokemon (wild, static, "
                + "gift, egg and trainer Pokemon) will be shiny." + note);
    }
}
