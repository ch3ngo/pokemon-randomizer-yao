package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.RomFunctions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class DsShinyPatchTest {

    private static byte[] fakeArm9(int offset) {
        byte[] arm9 = new byte[0x1000];
        new Random(7).nextBytes(arm9);
        // make sure random bytes cannot contain the signature by accident, then plant it
        byte[] sig = RomFunctions.hexToBytes(DsShinyPatch.GEN4_SIGNATURE);
        System.arraycopy(sig, 0, arm9, offset, sig.length);
        plantWrapper(arm9, offset);
        return arm9;
    }

    /** A Thumb bl at offset - 8 that calls the function at offset, like the game's "is this Pokemon shiny" wrapper. */
    private static void plantWrapper(byte[] arm9, int functionOffset) {
        int at = functionOffset - 8;
        long relative = functionOffset - (at + 4L);
        int hi = 0xF000 | (int) ((relative >> 12) & 0x7FF);
        int lo = 0xF800 | (int) ((relative >> 1) & 0x7FF);
        arm9[at] = (byte) hi;
        arm9[at + 1] = (byte) (hi >> 8);
        arm9[at + 2] = (byte) lo;
        arm9[at + 3] = (byte) (lo >> 8);
    }

    private static int callTarget(byte[] arm9, int at) {
        int hi = (arm9[at] & 0xFF) | ((arm9[at + 1] & 0xFF) << 8);
        int lo = (arm9[at + 2] & 0xFF) | ((arm9[at + 3] & 0xFF) << 8);
        int offset = ((hi & 0x7FF) << 12) | ((lo & 0x7FF) << 1);
        if ((offset & 0x400000) != 0) {
            offset -= 0x800000;
        }
        return at + 4 + offset;
    }

    private static int readThreshold(byte[] arm9, int offset) {
        int o = offset + DsShinyPatch.CODE_LENGTH;
        return (arm9[o] & 0xFF) | ((arm9[o + 1] & 0xFF) << 8) | ((arm9[o + 2] & 0xFF) << 16) | ((arm9[o + 3] & 0xFF) << 24);
    }

    @Test
    public void thresholdsMatchThePercentage() {
        assertEquals(0, DsShinyPatch.thresholdFor(0));
        assertEquals(655, DsShinyPatch.thresholdFor(1));
        assertEquals(32768, DsShinyPatch.thresholdFor(50));
        assertEquals(65536, DsShinyPatch.thresholdFor(100));
    }

    @Test
    public void outOfRangePercentagesAreClamped() {
        assertEquals(0, DsShinyPatch.thresholdFor(-20));
        assertEquals(65536, DsShinyPatch.thresholdFor(250));
    }

    @Test
    public void patchWritesTheReplacementCodeAndThreshold() {
        byte[] arm9 = fakeArm9(0x240);
        PatchResult result = DsShinyPatch.apply(arm9, 50);
        assertTrue(result.applied(), result.message());
        byte[] expected = RomFunctions.hexToBytes(DsShinyPatch.REPLACEMENT_CODE);
        assertArrayEquals(expected, Arrays.copyOfRange(arm9, 0x240, 0x240 + DsShinyPatch.CODE_LENGTH));
        assertEquals(32768, readThreshold(arm9, 0x240));
    }

    @Test
    public void percentageAboveOneHundredIsTreatedAsOneHundred() {
        byte[] arm9 = fakeArm9(0x100);
        assertTrue(DsShinyPatch.apply(arm9, 5000).applied());
        assertEquals(65536, readThreshold(arm9, 0x100));
    }

    @Test
    public void zeroOrNegativeChangesNothing() {
        byte[] arm9 = fakeArm9(0x240);
        byte[] before = arm9.clone();
        assertFalse(DsShinyPatch.apply(arm9, 0).applied());
        assertFalse(DsShinyPatch.apply(arm9, -5).applied());
        assertArrayEquals(before, arm9);
    }

    @Test
    public void unrecognisedCodeIsLeftUntouchedWithAnExplanation() {
        byte[] arm9 = new byte[0x1000];
        new Random(3).nextBytes(arm9);
        byte[] before = arm9.clone();
        PatchResult result = DsShinyPatch.apply(arm9, 50);
        assertFalse(result.applied());
        assertTrue(result.message().contains("not found"));
        assertArrayEquals(before, arm9);
    }

    @Test
    public void ambiguousMatchIsRefused() {
        byte[] arm9 = fakeArm9(0x100);
        byte[] sig = RomFunctions.hexToBytes(DsShinyPatch.GEN4_SIGNATURE);
        System.arraycopy(sig, 0, arm9, 0x400, sig.length);
        byte[] before = arm9.clone();
        PatchResult result = DsShinyPatch.apply(arm9, 50);
        assertFalse(result.applied());
        assertTrue(result.message().contains("more than once"));
        assertArrayEquals(before, arm9);
    }

    @Test
    public void emptyArm9NeverThrows() {
        assertDoesNotThrow(() -> DsShinyPatch.apply(new byte[0], 50));
        assertFalse(DsShinyPatch.apply(new byte[0], 50).applied());
    }

    @Test
    public void replacementIsExactlyAsLongAsTheOriginalFunctionAllowsAndKeepsAlignment() {
        // the original function plus its literal is 44 bytes; the new code and its threshold must fit in the
        // function itself (40 bytes) and the threshold must stay 4-byte aligned like the original function start
        assertTrue(DsShinyPatch.CODE_LENGTH + 4 <= 40);
        assertEquals(0, DsShinyPatch.CODE_LENGTH % 4);
    }

    @Test
    public void decimalPercentagesGiveTheExpectedThresholds() {
        assertEquals(66, DsShinyPatch.thresholdFor(0.1));      // 0.1 percent of 65536
        assertEquals(655, DsShinyPatch.thresholdFor(1.0));
        assertEquals(8192, DsShinyPatch.thresholdFor(12.5));
        assertEquals(65470, DsShinyPatch.thresholdFor(99.9));
        assertEquals(65536, DsShinyPatch.thresholdFor(100.0));
    }

    @Test
    public void everyTenthOfAPercentGivesADistinctIncreasingThreshold() {
        int previous = -1;
        for (int permille = 0; permille <= 1000; permille++) {
            int t = DsShinyPatch.thresholdFor(permille / 10.0);
            assertTrue(t > previous, "permille " + permille);
            previous = t;
        }
    }

    @Test
    public void zeroPointOnePercentIsApplied() {
        byte[] arm9 = fakeArm9(0x300);
        PatchResult result = DsShinyPatch.apply(arm9, 0.1);
        assertTrue(result.applied(), result.message());
        assertEquals(66, readThreshold(arm9, 0x300));
        assertTrue(result.message().contains("0.1"));
    }

    @Test
    public void notANumberOrTinyValuesChangeNothing() {
        byte[] arm9 = fakeArm9(0x300);
        byte[] before = arm9.clone();
        assertFalse(DsShinyPatch.apply(arm9, Double.NaN).applied());
        assertFalse(DsShinyPatch.apply(arm9, 0.0001).applied());
        assertArrayEquals(before, arm9);
    }

    @Test
    public void checkSaysYesForRecognisedCodeAndChangesNothing() {
        byte[] arm9 = fakeArm9(0x240);
        byte[] before = arm9.clone();
        assertTrue(DsShinyPatch.check(arm9).available());
        assertArrayEquals(before, arm9);
    }

    @Test
    public void checkSaysNoWithAReasonForUnknownOrAmbiguousCode() {
        byte[] other = new byte[0x800];
        new Random(5).nextBytes(other);
        FeatureAvailability none = DsShinyPatch.check(other);
        assertFalse(none.available());
        assertFalse(none.reason().isEmpty());

        byte[] twice = fakeArm9(0x100);
        byte[] sig = RomFunctions.hexToBytes(DsShinyPatch.GEN4_SIGNATURE);
        System.arraycopy(sig, 0, twice, 0x400, sig.length);
        assertFalse(DsShinyPatch.check(twice).available());
        assertFalse(DsShinyPatch.check(new byte[0]).available());
    }

    @Test
    public void gen5FunctionIsRecognisedAndPatchedToo() {
        byte[] arm9 = new byte[0x1000];
        new Random(11).nextBytes(arm9);
        byte[] sig = RomFunctions.hexToBytes(DsShinyPatch.GEN5_SIGNATURE);
        assertEquals(40, sig.length); // 36 bytes of code plus the 4 byte literal
        System.arraycopy(sig, 0, arm9, 0x180, sig.length);
        plantWrapper(arm9, 0x180);
        assertTrue(DsShinyPatch.check(arm9).available());
        PatchResult result = DsShinyPatch.apply(arm9, 25);
        assertTrue(result.applied(), result.message());
        assertEquals(16384, readThreshold(arm9, 0x180));
        assertArrayEquals(RomFunctions.hexToBytes(DsShinyPatch.REPLACEMENT_CODE),
                Arrays.copyOfRange(arm9, 0x180, 0x180 + DsShinyPatch.CODE_LENGTH));
    }

    @Test
    public void gen5PatchStaysInsideTheOriginalFunction() {
        // original code is 36 bytes plus its 4 byte literal: the 36 bytes of new code plus the 4 byte threshold
        // must fit exactly
        assertEquals(36, RomFunctions.hexToBytes(DsShinyPatch.GEN5_SIGNATURE).length - 4);
        assertEquals(36, RomFunctions.hexToBytes(DsShinyPatch.REPLACEMENT_CODE).length);
        assertTrue(DsShinyPatch.CODE_LENGTH + 4 <= 40);
    }

    @Test
    public void theOriginalEntryPointIsKeptSoCreationLoopsAreNotChanged() {
        byte[] code = RomFunctions.hexToBytes(DsShinyPatch.REPLACEMENT_CODE);
        // starts with lsrs r2, r0, #16 and eors r0, r2: the original test, not the threshold test
        assertEquals(0x02, code[0] & 0xFF);
        assertEquals(0x0C, code[1] & 0xFF);
        // the new entry point starts with lsls r2, r1, #8
        assertEquals(0x0A, code[DsShinyPatch.NEW_ENTRY_OFFSET] & 0xFF);
        assertEquals(0x02, code[DsShinyPatch.NEW_ENTRY_OFFSET + 1] & 0xFF);
    }

    @Test
    public void onlyTheDisplayWrapperIsPointedAtTheNewEntryPoint() {
        byte[] arm9 = fakeArm9(0x240);
        // another caller of the same function, like a loop that creates a Pokemon
        byte[] other = new byte[0x1000];
        System.arraycopy(arm9, 0, other, 0, arm9.length);
        int creationCall = 0x600;
        long relative = 0x240 - (creationCall + 4L);
        other[creationCall] = (byte) (0xF000 | (int) ((relative >> 12) & 0x7FF));
        other[creationCall + 1] = (byte) ((0xF000 | (int) ((relative >> 12) & 0x7FF)) >> 8);
        other[creationCall + 2] = (byte) (0xF800 | (int) ((relative >> 1) & 0x7FF));
        other[creationCall + 3] = (byte) ((0xF800 | (int) ((relative >> 1) & 0x7FF)) >> 8);
        // the fake array is only 0x1000 long, so reuse it
        byte[] big = Arrays.copyOf(other, 0x1000);
        assertEquals(0x240, callTarget(big, creationCall));
        assertTrue(DsShinyPatch.apply(big, 50).applied());
        assertEquals(0x240 + DsShinyPatch.NEW_ENTRY_OFFSET, callTarget(big, 0x240 - 8), "display wrapper");
        assertEquals(0x240, callTarget(big, creationCall), "creation caller keeps the original entry point");
    }

    @Test
    public void withoutTheDisplayWrapperNothingIsChanged() {
        byte[] arm9 = new byte[0x1000];
        new Random(21).nextBytes(arm9);
        byte[] sig = RomFunctions.hexToBytes(DsShinyPatch.GEN4_SIGNATURE);
        System.arraycopy(sig, 0, arm9, 0x300, sig.length);
        arm9[0x2F8] = 0;
        arm9[0x2F9] = 0;
        byte[] before = arm9.clone();
        PatchResult result = DsShinyPatch.apply(arm9, 50);
        assertFalse(result.applied());
        assertArrayEquals(before, arm9);
    }

    @Test
    public void bothGenerationsPresentIsAmbiguousAndRefused() {
        byte[] arm9 = fakeArm9(0x100); // Gen 4 function
        byte[] g5 = RomFunctions.hexToBytes(DsShinyPatch.GEN5_SIGNATURE);
        System.arraycopy(g5, 0, arm9, 0x500, g5.length);
        assertFalse(DsShinyPatch.check(arm9).available());
        assertFalse(DsShinyPatch.apply(arm9, 50).applied());
    }

    @Test
    public void theGen5OverlayDisplayCallIsPointedAtTheNewEntryPoint() throws Exception {
        byte[] arm9 = new byte[0x1000];
        new Random(31).nextBytes(arm9);
        byte[] sig = RomFunctions.hexToBytes(DsShinyPatch.GEN5_SIGNATURE);
        System.arraycopy(sig, 0, arm9, 0x400, sig.length);
        plantWrapper(arm9, 0x400);
        final int arm9Base = 0x02004000;
        final int overlayBase = 0x021BB700;
        // overlay: ldr r0,[r6,#12]; ldr r1,[r6,#8]; bl <function>; adds r7,r0,#0, plus another call that must stay
        byte[] overlay = new byte[0x100];
        int at = 0x20;
        overlay[at - 4] = (byte) 0xF0;
        overlay[at - 3] = 0x68;
        overlay[at - 2] = (byte) 0xB1;
        overlay[at - 1] = 0x68;
        long relative = (arm9Base + 0x400L) - (overlayBase + at + 4L);
        int hi = 0xF000 | (int) ((relative >> 12) & 0x7FF);
        int lo = 0xF800 | (int) ((relative >> 1) & 0x7FF);
        overlay[at] = (byte) hi;
        overlay[at + 1] = (byte) (hi >> 8);
        overlay[at + 2] = (byte) lo;
        overlay[at + 3] = (byte) (lo >> 8);
        overlay[at + 4] = 0x07;
        overlay[at + 5] = 0x1C;
        // a creation style call (no ldr pair before it) to the same function
        int other = 0x60;
        long relative2 = (arm9Base + 0x400L) - (overlayBase + other + 4L);
        int hi2 = 0xF000 | (int) ((relative2 >> 12) & 0x7FF);
        int lo2 = 0xF800 | (int) ((relative2 >> 1) & 0x7FF);
        overlay[other] = (byte) hi2;
        overlay[other + 1] = (byte) (hi2 >> 8);
        overlay[other + 2] = (byte) lo2;
        overlay[other + 3] = (byte) (lo2 >> 8);
        byte[] untouchedCreation = Arrays.copyOfRange(overlay, other, other + 4);
        final byte[][] stored = {overlay};

        DsShinyPatch.Memory memory = new DsShinyPatch.Memory() {
            public int arm9Base() { return arm9Base; }
            public int overlayCount() { return 1; }
            public int overlayBase(int number) { return overlayBase; }
            public byte[] readOverlay(int number) { return stored[0].clone(); }
            public void writeOverlay(int number, byte[] data) { stored[0] = data; }
        };
        PatchResult result = DsShinyPatch.apply(arm9, 50, memory);
        assertTrue(result.applied(), result.message());
        assertTrue(result.message().contains("1 more place"), result.message());
        long newTarget = overlayBase + callTarget(stored[0], at);
        assertEquals(arm9Base + 0x400L + DsShinyPatch.NEW_ENTRY_OFFSET, newTarget);
        assertArrayEquals(untouchedCreation, Arrays.copyOfRange(stored[0], other, other + 4));
    }

    @Test
    public void anOverlayThatCannotBeReadDoesNotStopTheMainPatch() {
        byte[] arm9 = fakeArm9(0x240);
        DsShinyPatch.Memory broken = new DsShinyPatch.Memory() {
            public int arm9Base() { return 0x02000000; }
            public int overlayCount() { return 3; }
            public int overlayBase(int number) { return 0x02100000; }
            public byte[] readOverlay(int number) throws java.io.IOException { throw new java.io.IOException("boom"); }
            public void writeOverlay(int number, byte[] data) { }
        };
        PatchResult result = DsShinyPatch.apply(arm9, 50, broken);
        assertTrue(result.applied());
        assertEquals(32768, readThreshold(arm9, 0x240));
    }
}
