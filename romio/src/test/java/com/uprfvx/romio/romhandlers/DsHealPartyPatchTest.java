package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.RomFunctions;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class DsHealPartyPatchTest {

    private static byte[] fakeArm9(int offset) {
        byte[] arm9 = new byte[0x1000];
        new Random(21).nextBytes(arm9);
        byte[] sig = RomFunctions.hexToBytes(DsHealPartyPatch.GEN4_SIGNATURE);
        System.arraycopy(sig, 0, arm9, offset, sig.length);
        return arm9;
    }

    @Test
    public void signatureHasTheExpectedShape() {
        byte[] sig = RomFunctions.hexToBytes(DsHealPartyPatch.GEN4_SIGNATURE);
        assertEquals(42, sig.length);
        assertEquals(DsHealPartyPatch.ORIGINAL_FIELD, sig[DsHealPartyPatch.PATCH_OFFSET]);
        assertEquals(0x21, sig[DsHealPartyPatch.PATCH_OFFSET + 1] & 0xFF); // Thumb "movs r1, #imm8" high byte
    }

    @Test
    public void patchChangesExactlyOneByte() {
        byte[] arm9 = fakeArm9(0x300);
        byte[] before = arm9.clone();
        PatchResult result = DsHealPartyPatch.apply(arm9);
        assertTrue(result.applied(), result.message());
        int changed = 0;
        for (int i = 0; i < arm9.length; i++) {
            if (arm9[i] != before[i]) {
                changed++;
                assertEquals(0x300 + DsHealPartyPatch.PATCH_OFFSET, i);
                assertEquals(DsHealPartyPatch.NEW_FIELD, arm9[i]);
            }
        }
        assertEquals(1, changed);
    }

    @Test
    public void checkAgreesWithApplyAndChangesNothing() {
        byte[] arm9 = fakeArm9(0x300);
        byte[] before = arm9.clone();
        assertTrue(DsHealPartyPatch.check(arm9).available());
        assertArrayEquals(before, arm9);
    }

    @Test
    public void unknownOrAmbiguousCodeIsRefusedAndLeftAlone() {
        byte[] other = new byte[0x800];
        new Random(4).nextBytes(other);
        byte[] before = other.clone();
        assertFalse(DsHealPartyPatch.check(other).available());
        assertFalse(DsHealPartyPatch.apply(other).applied());
        assertArrayEquals(before, other);

        byte[] twice = fakeArm9(0x100);
        byte[] sig = RomFunctions.hexToBytes(DsHealPartyPatch.GEN4_SIGNATURE);
        System.arraycopy(sig, 0, twice, 0x500, sig.length);
        byte[] twiceBefore = twice.clone();
        assertFalse(DsHealPartyPatch.apply(twice).applied());
        assertArrayEquals(twiceBefore, twice);
    }

    @Test
    public void emptyArm9NeverThrows() {
        assertDoesNotThrow(() -> DsHealPartyPatch.apply(new byte[0]));
        assertFalse(DsHealPartyPatch.check(new byte[0]).available());
    }

    @Test
    public void gen5FunctionIsRecognisedAndPatchedWithOneByte() {
        byte[] arm9 = new byte[0x1000];
        new Random(8).nextBytes(arm9);
        byte[] sig = RomFunctions.hexToBytes(DsHealPartyPatch.GEN5_SIGNATURE);
        assertEquals(52, sig.length);
        assertEquals(DsHealPartyPatch.GEN5_ORIGINAL_FIELD, sig[DsHealPartyPatch.GEN5_PATCH_OFFSET]);
        System.arraycopy(sig, 0, arm9, 0x400, sig.length);
        byte[] before = arm9.clone();
        assertTrue(DsHealPartyPatch.check(arm9).available());
        PatchResult result = DsHealPartyPatch.apply(arm9);
        assertTrue(result.applied(), result.message());
        int changed = 0;
        for (int i = 0; i < arm9.length; i++) {
            if (arm9[i] != before[i]) {
                changed++;
                assertEquals(0x400 + DsHealPartyPatch.GEN5_PATCH_OFFSET, i);
                assertEquals(DsHealPartyPatch.GEN5_NEW_FIELD, arm9[i]);
            }
        }
        assertEquals(1, changed);
    }

    @Test
    public void bothGenerationsPresentIsAmbiguousAndRefused() {
        byte[] arm9 = fakeArm9(0x100);
        byte[] g5 = RomFunctions.hexToBytes(DsHealPartyPatch.GEN5_SIGNATURE);
        System.arraycopy(g5, 0, arm9, 0x600, g5.length);
        byte[] before = arm9.clone();
        assertFalse(DsHealPartyPatch.check(arm9).available());
        assertFalse(DsHealPartyPatch.apply(arm9).applied());
        assertArrayEquals(before, arm9);
    }
}
