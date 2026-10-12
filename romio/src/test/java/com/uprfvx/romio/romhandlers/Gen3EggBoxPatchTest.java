package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.RomFunctions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class Gen3EggBoxPatchTest {

    private static final int FREE = 0x300000;

    /** A fake ROM with the original Lavaridge egg woman script planted. */
    private static byte[] fakeRom(int species) {
        byte[] rom = new byte[0x400000];
        Arrays.fill(rom, (byte) 0xFF);
        byte[] start = RomFunctions.hexToBytes(Gen3EggBoxPatch.SCRIPT_START_BYTES);
        System.arraycopy(start, 0, rom, Gen3EggBoxPatch.SCRIPT_START, start.length);
        byte[] check = RomFunctions.hexToBytes(Gen3EggBoxPatch.PARTY_CHECK_BYTES);
        System.arraycopy(check, 0, rom, Gen3EggBoxPatch.PARTY_CHECK, check.length);
        byte[] message = RomFunctions.hexToBytes("67f4ac1e08");
        System.arraycopy(message, 0, rom, Gen3EggBoxPatch.MESSAGE, message.length);
        rom[Gen3EggBoxPatch.TAIL - 1] = 0x32;
        rom[Gen3EggBoxPatch.TAIL] = 0x7A;
        rom[Gen3EggBoxPatch.TAIL + 1] = (byte) species;
        rom[Gen3EggBoxPatch.TAIL + 2] = (byte) (species >> 8);
        rom[Gen3EggBoxPatch.TAIL + 3] = 0x6C;
        rom[Gen3EggBoxPatch.TAIL + 4] = 0x02;
        return rom;
    }

    private static int word(byte[] rom, int offset) {
        return (rom[offset] & 0xFF) | ((rom[offset + 1] & 0xFF) << 8);
    }

    @Test
    public void recognisesTheScriptWhateverSpeciesTheRandomizerPutInTheOriginalEgg() {
        assertTrue(Gen3EggBoxPatch.check(fakeRom(360)).available());
        assertTrue(Gen3EggBoxPatch.check(fakeRom(5)).available());
    }

    @Test
    public void refusesUnknownScriptsWithoutChangingAnything() {
        byte[] rom = fakeRom(360);
        rom[Gen3EggBoxPatch.SCRIPT_START] = 0;
        byte[] before = rom.clone();
        assertFalse(Gen3EggBoxPatch.check(rom).available());
        PatchResult result = Gen3EggBoxPatch.apply(rom, new int[]{1, 2}, n -> FREE);
        assertFalse(result.applied());
        assertTrue(result.message().contains("not found"));
        assertArrayEquals(before, rom);
        assertFalse(Gen3EggBoxPatch.check(new byte[0]).available());
        assertFalse(Gen3EggBoxPatch.apply(new byte[10], new int[]{1}, n -> FREE).applied());
    }

    @Test
    public void emptyOrTooBigBoxesAreRefused() {
        byte[] rom = fakeRom(360);
        byte[] before = rom.clone();
        assertFalse(Gen3EggBoxPatch.apply(rom, new int[0], n -> FREE).applied());
        assertFalse(Gen3EggBoxPatch.apply(rom, null, n -> FREE).applied());
        assertFalse(Gen3EggBoxPatch.apply(rom, new int[Gen3EggBoxPatch.MAX_EGGS + 1], n -> FREE).applied());
        assertFalse(Gen3EggBoxPatch.apply(rom, new int[]{0}, n -> FREE).applied());
        assertFalse(Gen3EggBoxPatch.apply(rom, new int[]{70000}, n -> FREE).applied());
        assertArrayEquals(before, rom);
    }

    @Test
    public void noRoomOrAFailingAllocatorLeavesTheRomAlone() {
        byte[] rom = fakeRom(360);
        byte[] before = rom.clone();
        assertFalse(Gen3EggBoxPatch.apply(rom, new int[]{1, 2}, n -> {
            throw new RuntimeException("ROM full");
        }).applied());
        assertFalse(Gen3EggBoxPatch.apply(rom, new int[]{1, 2}, n -> -1).applied());
        assertFalse(Gen3EggBoxPatch.apply(rom, new int[]{1, 2}, n -> rom.length).applied());
        assertArrayEquals(before, rom);
    }

    @Test
    public void theBlockHasTheMessageTheOtherEggsAndAJumpBack() {
        byte[] rom = fakeRom(360);
        assertTrue(Gen3EggBoxPatch.apply(rom, new int[]{10, 20, 30, 40}, n -> FREE).applied());
        // goto <block> over the original message
        assertEquals(0x05, rom[Gen3EggBoxPatch.MESSAGE] & 0xFF);
        int pointer = (rom[Gen3EggBoxPatch.MESSAGE + 1] & 0xFF) | ((rom[Gen3EggBoxPatch.MESSAGE + 2] & 0xFF) << 8)
                | ((rom[Gen3EggBoxPatch.MESSAGE + 3] & 0xFF) << 16) | ((rom[Gen3EggBoxPatch.MESSAGE + 4] & 0xFF) << 24);
        assertEquals(FREE + 0x08000000, pointer);
        // the block: message (same pointer), three giveegg, goto back to the waitmessage
        assertEquals(0x67, rom[FREE] & 0xFF);
        assertArrayEquals(RomFunctions.hexToBytes("f4ac1e08"), Arrays.copyOfRange(rom, FREE + 1, FREE + 5));
        for (int i = 0; i < 3; i++) {
            assertEquals(0x7A, rom[FREE + 5 + 3 * i] & 0xFF);
            assertEquals(10 * (i + 1), word(rom, FREE + 6 + 3 * i));
        }
        int jump = FREE + 5 + 9;
        assertEquals(0x05, rom[jump] & 0xFF);
        int back = (rom[jump + 1] & 0xFF) | ((rom[jump + 2] & 0xFF) << 8) | ((rom[jump + 3] & 0xFF) << 16)
                | ((rom[jump + 4] & 0xFF) << 24);
        assertEquals(Gen3EggBoxPatch.TAIL - 1 + 0x08000000, back);
        // the original ending is still there and gives the last egg
        assertEquals(0x32, rom[Gen3EggBoxPatch.TAIL - 1] & 0xFF);
        assertEquals(0x7A, rom[Gen3EggBoxPatch.TAIL] & 0xFF);
        assertEquals(40, word(rom, Gen3EggBoxPatch.TAIL + 1));
        assertEquals(0x6C, rom[Gen3EggBoxPatch.TAIL + 3] & 0xFF);
        assertEquals(0x02, rom[Gen3EggBoxPatch.TAIL + 4] & 0xFF);
    }

    @Test
    public void thePartyIsNeverReportedFull() {
        byte[] rom = fakeRom(360);
        assertEquals(6, rom[Gen3EggBoxPatch.PARTY_CHECK_VALUE]);
        assertTrue(Gen3EggBoxPatch.apply(rom, new int[]{1}, n -> FREE).applied());
        assertEquals(7, rom[Gen3EggBoxPatch.PARTY_CHECK_VALUE]);
    }

    @Test
    public void aSingleEggJustSetsTheOriginalEndingAndKeepsEverythingElse() {
        byte[] rom = fakeRom(360);
        byte[] before = rom.clone();
        assertTrue(Gen3EggBoxPatch.apply(rom, new int[]{77}, n -> FREE).applied());
        assertEquals(77, word(rom, Gen3EggBoxPatch.TAIL + 1));
        // block is message + goto only
        assertEquals(0x05, rom[FREE + 5] & 0xFF);
        for (int i = 0; i < rom.length; i++) {
            boolean allowed = i == Gen3EggBoxPatch.PARTY_CHECK_VALUE
                    || (i >= Gen3EggBoxPatch.MESSAGE && i < Gen3EggBoxPatch.MESSAGE + 5)
                    || i == Gen3EggBoxPatch.TAIL + 1 || i == Gen3EggBoxPatch.TAIL + 2
                    || (i >= FREE && i < FREE + 10);
            if (!allowed) {
                assertEquals(before[i], rom[i], "offset 0x" + Integer.toHexString(i));
            }
        }
    }

    @Test
    public void ninetyEggsFit() {
        byte[] rom = fakeRom(360);
        int[] species = new int[90];
        for (int i = 0; i < species.length; i++) {
            species[i] = i + 1;
        }
        assertTrue(Gen3EggBoxPatch.apply(rom, species, n -> FREE).applied());
        assertEquals(5 + 89 * 3 + 5, Gen3EggBoxPatch.buildBlock(species, new byte[4], 0).length);
    }
}
