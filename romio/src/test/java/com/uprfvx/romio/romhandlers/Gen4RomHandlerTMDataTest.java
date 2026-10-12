package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen4Constants;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Modified from FVX 11/10/2026: regression test for the HGSS getTMMoves() null bug.
 * <br><br>
 * {@link Gen4RomHandler#getTMMoves()}, {@link Gen4RomHandler#getHMMoves()} and
 * {@link Gen4RomHandler#setTMMoves(List)} used to locate the TM/HM data table in ARM9 by searching
 * for {@link Gen4Constants#hgssTMDataPrefix} (for HGSS) on every call. That prefix is only 4 bytes
 * long, the vanilla TM01/TM02 move ids (30, 50). Once TM moves are randomized, a different pair of
 * adjacent slots in the very same table can coincidentally be rerolled to exactly (30, 50), which
 * makes the byte search match twice ("not unique") and getTMMoves()/getHMMoves() return null, or
 * setTMMoves() silently fail to write, depending on the seed. This was reproducible on SoulSilver
 * (U) by forcing such a collision.
 */
public class Gen4RomHandlerTMDataTest extends RomHandlerTest {

    @Test
    public void getTMMovesSurvivesAHgssAnchorCollision() {
        loadROM("SoulSilver (U)");
        Gen4RomHandler rh = (Gen4RomHandler) romHandler;

        List<Integer> vanillaTms = rh.getTMMoves();
        assertNotNull(vanillaTms, "Should find the TM table in a freshly loaded ROM");
        assertEquals(Gen4Constants.tmCount, vanillaTms.size());

        // Force a collision: rewrite the table so that two slots elsewhere coincidentally
        // reproduce the vanilla TM01/TM02 pair (30, 50) that the HGSS search anchor is built from.
        // Before the fix, this made getTMMoves() return null and HMs come back empty, because the
        // live re-search could no longer tell the real table location apart from the coincidental
        // match.
        List<Integer> rerolled = new ArrayList<>(vanillaTms);
        for (int i = 0; i < rerolled.size(); i++) {
            rerolled.set(i, 1); // Pound, arbitrary, just something that won't itself collide
        }
        rerolled.set(10, 30);
        rerolled.set(11, 50);
        rh.setTMMoves(rerolled);

        List<Integer> afterTms = rh.getTMMoves();
        assertNotNull(afterTms, "getTMMoves() must keep working after a randomization that "
                + "recreates the search anchor's own byte pattern elsewhere in the table");
        assertEquals(rerolled, afterTms);

        List<Integer> afterHms = rh.getHMMoves();
        assertNotNull(afterHms, "getHMMoves() must keep working under the same conditions");
        assertEquals(Gen4Constants.hmCount, afterHms.size());
    }

    @Test
    public void getTMMovesIsStableAcrossRepeatedRandomization() {
        loadROM("SoulSilver (S)");
        Gen4RomHandler rh = (Gen4RomHandler) romHandler;

        List<Integer> original = rh.getTMMoves();
        assertNotNull(original);

        // Randomize the TM table several times in a row, as a real randomization run with TM
        // move randomization enabled would, including once with the colliding pair front and
        // center (TM01/TM02 themselves, the worst case: the very slots the anchor overlaps).
        List<Integer> firstPass = new ArrayList<>(original);
        firstPass.set(0, 30);
        firstPass.set(1, 50);
        rh.setTMMoves(firstPass);
        assertEquals(firstPass, rh.getTMMoves());

        List<Integer> secondPass = new ArrayList<>(original);
        for (int i = 0; i < secondPass.size(); i++) {
            secondPass.set(i, (i % 400) + 1);
        }
        rh.setTMMoves(secondPass);
        assertEquals(secondPass, rh.getTMMoves());
    }
}
