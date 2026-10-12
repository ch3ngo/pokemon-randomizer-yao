package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.RomFunctions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class Gen2ShinyPatchTest {

    private static final int FUNCTION = 0x8A68;      // bank 2

    private static byte[] fakeRom() {
        byte[] rom = new byte[0x20000];
        new Random(2).nextBytes(rom);
        // random bytes cannot contain the function by accident; plant it, and zero the end of its bank
        byte[] original = RomFunctions.hexToBytes(Gen2ShinyPatch.ORIGINAL);
        System.arraycopy(original, 0, rom, FUNCTION, original.length);
        Arrays.fill(rom, 0xC000 - 0x100, 0xC000, (byte) 0);
        return rom;
    }

    @Test
    public void recognisesTheOriginalFunction() {
        assertTrue(Gen2ShinyPatch.check(fakeRom()).available());
    }

    @Test
    public void unknownCodeOrMissingFreeSpaceIsRefusedUntouched() {
        byte[] noFunction = new byte[0x20000];
        new Random(1).nextBytes(noFunction);
        byte[] before = noFunction.clone();
        assertFalse(Gen2ShinyPatch.check(noFunction).available());
        assertFalse(Gen2ShinyPatch.apply(noFunction, 50).applied());
        assertArrayEquals(before, noFunction);

        byte[] crowded = fakeRom();
        crowded[0xC000 - 10] = 5;
        byte[] copy = crowded.clone();
        assertFalse(Gen2ShinyPatch.check(crowded).available());
        assertFalse(Gen2ShinyPatch.apply(crowded, 50).applied());
        assertArrayEquals(copy, crowded);
        assertFalse(Gen2ShinyPatch.check(new byte[0]).available());
    }

    @Test
    public void zeroChanceChangesNothing() {
        byte[] rom = fakeRom();
        byte[] before = rom.clone();
        assertFalse(Gen2ShinyPatch.apply(rom, 0).applied());
        assertArrayEquals(before, rom);
    }

    @Test
    public void patchJumpsToTheNewFunctionAndStoresTheThreshold() {
        byte[] rom = fakeRom();
        assertTrue(Gen2ShinyPatch.apply(rom, 25).applied());
        assertEquals((byte) 0xC3, rom[FUNCTION]);
        int target = (rom[FUNCTION + 1] & 0xFF) | ((rom[FUNCTION + 2] & 0xFF) << 8);
        assertEquals(0x4000 + (0xC000 - 64 - 0x8000), target);
        int newFunction = 0x8000 + (target - 0x4000);
        assertEquals(0x40, rom[newFunction + 5] & 0xFF);      // 25 percent: 16384 = 0x4000
        assertEquals(0x00, rom[newFunction + 12] & 0xFF);
    }

    @Test
    public void fullChanceMakesTheNewFunctionAlwaysShiny() {
        byte[] rom = fakeRom();
        assertTrue(Gen2ShinyPatch.apply(rom, 100).applied());
        int target = (rom[FUNCTION + 1] & 0xFF) | ((rom[FUNCTION + 2] & 0xFF) << 8);
        int newFunction = 0x8000 + (target - 0x4000);
        assertEquals(0x37, rom[newFunction] & 0xFF);          // scf
        assertEquals(0xC9, rom[newFunction + 1] & 0xFF);      // ret
    }

    @Test
    public void onlyTheStubAndTheEndOfTheBankChange() {
        byte[] rom = fakeRom();
        byte[] before = rom.clone();
        assertTrue(Gen2ShinyPatch.apply(rom, 10).applied());
        for (int i = 0; i < rom.length; i++) {
            boolean allowed = (i >= FUNCTION && i < FUNCTION + 3) || (i >= 0xC000 - 64 && i < 0xC000);
            if (!allowed) {
                assertEquals(before[i], rom[i], "offset " + i);
            }
        }
    }

    @Test
    public void newFunctionIsExactlyTheDocumentedLength() {
        assertEquals(15 + 2 + (Gen2ShinyPatch.ORIGINAL_LENGTH - Gen2ShinyPatch.BODY_START) + 2, Gen2ShinyPatch.NEW_LENGTH);
        assertEquals(Gen2ShinyPatch.ORIGINAL_LENGTH, RomFunctions.hexToBytes(Gen2ShinyPatch.ORIGINAL).length);
    }
}
