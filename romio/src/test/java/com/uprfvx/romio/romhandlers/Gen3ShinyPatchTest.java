package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.RomFunctions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class Gen3ShinyPatchTest {

    /** A 1 MB fake ROM with the original Emerald code planted where the patch expects it. */
    private static byte[] fakeRom() {
        byte[] rom = new byte[0x100000];
        new Random(5).nextBytes(rom);
        plant(rom, Gen3ShinyPatch.FUNCTION, Gen3ShinyPatch.FUNCTION_ORIGINAL);
        plant(rom, Gen3ShinyPatch.SITE_PALETTE_1, Gen3ShinyPatch.SITE_PALETTE_1_ORIGINAL);
        plant(rom, Gen3ShinyPatch.SITE_PALETTE_2, Gen3ShinyPatch.SITE_PALETTE_2_ORIGINAL);
        // wrapper: bl 0x806EBD0 from 0x806EBC0
        int at = Gen3ShinyPatch.WRAPPER_CALL;
        long relative = Gen3ShinyPatch.FUNCTION - (at + 4L);
        int hi = 0xF000 | (int) ((relative >> 12) & 0x7FF);
        int lo = 0xF800 | (int) ((relative >> 1) & 0x7FF);
        rom[at] = (byte) hi;
        rom[at + 1] = (byte) (hi >> 8);
        rom[at + 2] = (byte) lo;
        rom[at + 3] = (byte) (lo >> 8);
        return rom;
    }

    private static void plant(byte[] rom, int offset, String hex) {
        byte[] bytes = RomFunctions.hexToBytes(hex);
        System.arraycopy(bytes, 0, rom, offset, bytes.length);
    }

    private static int callTarget(byte[] rom, int at) {
        int hi = (rom[at] & 0xFF) | ((rom[at + 1] & 0xFF) << 8);
        int lo = (rom[at + 2] & 0xFF) | ((rom[at + 3] & 0xFF) << 8);
        int offset = ((hi & 0x7FF) << 12) | ((lo & 0x7FF) << 1);
        if ((offset & 0x400000) != 0) {
            offset -= 0x800000;
        }
        return at + 4 + offset;
    }

    @Test
    public void recognisesTheOriginalCode() {
        assertTrue(Gen3ShinyPatch.check(fakeRom()).available());
    }

    @Test
    public void notRecognisedWhenAnythingDiffers() {
        byte[] rom = fakeRom();
        rom[Gen3ShinyPatch.SITE_PALETTE_2 + 5] ^= 1;
        assertFalse(Gen3ShinyPatch.check(rom).available());
        byte[] before = rom.clone();
        PatchResult result = Gen3ShinyPatch.apply(rom, 50);
        assertFalse(result.applied());
        assertTrue(result.message().contains("not found"));
        assertArrayEquals(before, rom);
        assertFalse(Gen3ShinyPatch.check(new byte[0]).available());
        assertFalse(Gen3ShinyPatch.apply(new byte[10], 50).applied());
    }

    @Test
    public void zeroChanceChangesNothing() {
        byte[] rom = fakeRom();
        byte[] before = rom.clone();
        assertFalse(Gen3ShinyPatch.apply(rom, 0).applied());
        assertArrayEquals(before, rom);
    }

    @Test
    public void patchWritesTheFunctionTheThresholdAndRetargetsOnlyTheDisplayCalls() {
        byte[] rom = fakeRom();
        PatchResult result = Gen3ShinyPatch.apply(rom, 50);
        assertTrue(result.applied(), result.message());
        int f = Gen3ShinyPatch.FUNCTION;
        assertArrayEquals(RomFunctions.hexToBytes(DsShinyPatch.REPLACEMENT_CODE), Arrays.copyOfRange(rom, f, f + 36));
        int threshold = (rom[f + 36] & 0xFF) | ((rom[f + 37] & 0xFF) << 8) | ((rom[f + 38] & 0xFF) << 16);
        assertEquals(32768, threshold);
        int newEntry = f + DsShinyPatch.NEW_ENTRY_OFFSET;
        assertEquals(newEntry, callTarget(rom, Gen3ShinyPatch.WRAPPER_CALL));
        assertEquals(newEntry, callTarget(rom, Gen3ShinyPatch.SITE_PALETTE_1 + 4));
        assertEquals(newEntry, callTarget(rom, Gen3ShinyPatch.SITE_PALETTE_2 + 4));
    }

    @Test
    public void everythingOutsideThePatchedPlacesIsLeftAlone() {
        byte[] rom = fakeRom();
        byte[] before = rom.clone();
        assertTrue(Gen3ShinyPatch.apply(rom, 12.5).applied());
        for (int i = 0; i < rom.length; i++) {
            boolean allowed = (i >= Gen3ShinyPatch.WRAPPER_CALL && i < Gen3ShinyPatch.WRAPPER_CALL + 4)
                    || (i >= Gen3ShinyPatch.FUNCTION && i < Gen3ShinyPatch.FUNCTION + 40)
                    || (i >= Gen3ShinyPatch.SITE_PALETTE_1 && i < Gen3ShinyPatch.SITE_PALETTE_1 + 20)
                    || (i >= Gen3ShinyPatch.SITE_PALETTE_2 && i < Gen3ShinyPatch.SITE_PALETTE_2 + 20);
            if (!allowed) {
                assertEquals(before[i], rom[i], "offset 0x" + Integer.toHexString(i));
            }
        }
    }

    @Test
    public void siteReplacementsAreExactlyTwentyBytes() {
        assertEquals(20, 4 + 4 + RomFunctions.hexToBytes(Gen3ShinyPatch.SITE_PALETTE_1_AFTER_CALL).length);
        assertEquals(20, RomFunctions.hexToBytes(Gen3ShinyPatch.SITE_PALETTE_1_BEFORE_CALL).length + 4
                + RomFunctions.hexToBytes(Gen3ShinyPatch.SITE_PALETTE_1_AFTER_CALL).length);
        assertEquals(20, RomFunctions.hexToBytes(Gen3ShinyPatch.SITE_PALETTE_2_BEFORE_CALL).length + 4
                + RomFunctions.hexToBytes(Gen3ShinyPatch.SITE_PALETTE_2_AFTER_CALL).length);
        assertEquals(20, RomFunctions.hexToBytes(Gen3ShinyPatch.SITE_PALETTE_1_ORIGINAL).length);
        assertEquals(20, RomFunctions.hexToBytes(Gen3ShinyPatch.SITE_PALETTE_2_ORIGINAL).length);
    }
}
