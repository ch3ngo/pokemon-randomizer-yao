package com.uprfvx.romio.romhandlers;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class CtrShinyPatchTest {

    private static final int WRAPPER = 0x2000;
    private static final int TEXT_SIZE = 0x6A04;     // not page aligned, like the real games
    private static final int CODE_SIZE = 0x9000;

    private static void put(byte[] code, int offset, int value) {
        ByteBuffer.wrap(code, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value);
    }

    private static int get(byte[] code, int offset) {
        return ByteBuffer.wrap(code, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static byte[] fakeCode() {
        byte[] code = new byte[CODE_SIZE];
        new Random(4).nextBytes(code);
        for (int i = 0; i < CtrShinyPatch.WRAPPER_END.length; i++) {
            put(code, WRAPPER + 4 * i, CtrShinyPatch.WRAPPER_END[i]);
        }
        for (int i = 0; i < CtrShinyPatch.FUNCTION.length; i++) {
            put(code, WRAPPER + 12 + 4 * i, CtrShinyPatch.FUNCTION[i]);
        }
        // zero padding after the text segment up to the page end
        int start = (TEXT_SIZE + 3) & ~3;
        for (int i = start; i < 0x7000; i++) {
            code[i] = 0;
        }
        return code;
    }

    @Test
    public void recognisesTheOriginalCode() {
        assertTrue(CtrShinyPatch.check(fakeCode(), TEXT_SIZE).available());
    }

    @Test
    public void refusesWhenTheFunctionIsMissingOrFoundTwice() {
        byte[] none = new byte[CODE_SIZE];
        new Random(1).nextBytes(none);
        byte[] before = none.clone();
        assertFalse(CtrShinyPatch.check(none, TEXT_SIZE).available());
        assertFalse(CtrShinyPatch.apply(none, TEXT_SIZE, 50).applied());
        assertArrayEquals(before, none);

        byte[] twice = fakeCode();
        for (int i = 0; i < 12; i++) {
            put(twice, 0x4000 + 4 * i, i < 3 ? CtrShinyPatch.WRAPPER_END[i] : CtrShinyPatch.FUNCTION[i - 3]);
        }
        byte[] copy = twice.clone();
        FeatureAvailability availability = CtrShinyPatch.check(twice, TEXT_SIZE);
        assertFalse(availability.available());
        assertTrue(availability.reason().contains("more than once"));
        assertFalse(CtrShinyPatch.apply(twice, TEXT_SIZE, 50).applied());
        assertArrayEquals(copy, twice);
    }

    @Test
    public void refusesWithoutRoomOrWithABadSegmentSize() {
        byte[] full = fakeCode();
        full[(TEXT_SIZE + 4) & ~3] = 1;                // padding not empty
        byte[] copy = full.clone();
        assertFalse(CtrShinyPatch.apply(full, TEXT_SIZE, 50).applied());
        assertArrayEquals(copy, full);

        byte[] code = fakeCode();
        assertFalse(CtrShinyPatch.apply(code, -1, 50).applied());
        assertFalse(CtrShinyPatch.apply(code, 0, 50).applied());
        assertFalse(CtrShinyPatch.apply(code, CODE_SIZE + 1, 50).applied());
        assertFalse(CtrShinyPatch.apply(code, 0x7000 - 16, 50).applied());   // less than 64 bytes of padding left
        assertFalse(CtrShinyPatch.check(code, 0).available());
    }

    @Test
    public void zeroChanceChangesNothing() {
        byte[] code = fakeCode();
        byte[] before = code.clone();
        assertFalse(CtrShinyPatch.apply(code, TEXT_SIZE, 0).applied());
        assertArrayEquals(before, code);
    }

    @Test
    public void patchReplacesOnlyTheNopAndWritesTheNewCodeAfterTheSegment() {
        byte[] code = fakeCode();
        byte[] before = code.clone();
        assertTrue(CtrShinyPatch.apply(code, TEXT_SIZE, 50).applied());
        int nop = WRAPPER + 8;
        int free = (TEXT_SIZE + 3) & ~3;
        // the nop became a branch to the new code
        int branch = get(code, nop);
        assertEquals(0xEA, (branch >>> 24) & 0xFF);
        int target = nop + 8 + ((branch << 8) >> 6);
        assertEquals(free, target);
        // the new code ends with a branch to the original function and the threshold
        int back = get(code, free + 24);
        assertEquals(WRAPPER + 12, free + 24 + 8 + ((back << 8) >> 6));
        assertEquals(32768, get(code, free + 28));
        // nothing else changed
        for (int i = 0; i < code.length; i++) {
            boolean allowed = (i >= nop && i < nop + 4) || (i >= free && i < free + 32);
            if (!allowed) {
                assertEquals(before[i], code[i], "offset 0x" + Integer.toHexString(i));
            }
        }
    }

    @Test
    public void thresholdMatchesThePercentage() {
        byte[] code = fakeCode();
        assertTrue(CtrShinyPatch.apply(code, TEXT_SIZE, 0.1).applied());
        assertEquals(66, get(code, ((TEXT_SIZE + 3) & ~3) + 28));
        byte[] full = fakeCode();
        assertTrue(CtrShinyPatch.apply(full, TEXT_SIZE, 250).applied());
        assertEquals(65536, get(full, ((TEXT_SIZE + 3) & ~3) + 28));
    }

    @Test
    public void emptyCodeNeverThrows() {
        assertDoesNotThrow(() -> CtrShinyPatch.apply(new byte[0], 100, 50));
        assertFalse(CtrShinyPatch.apply(new byte[0], 100, 50).applied());
        assertFalse(CtrShinyPatch.check(new byte[3], 100).available());
    }
}
