package com.uprfvx.romio.graphics.ds;

/*----------------------------------------------------------------------------*/
/*--  DsPlayerSprites.java - the walking sprites of the player in Gen 4/5.  --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.newnds.NARCArchive;
import com.uprfvx.romio.romhandlers.PatchResult;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Exports and replaces the overworld walking sprites of the player character in the DS games that keep them as
 * texture archives (HeartGold, SoulSilver and Black 2 so far): one member of a NARC per character. The sprites are
 * replaced in place, so nothing changes size. See {@link SpriteSheet} for the sheet layout.
 */
public final class DsPlayerSprites {

    private DsPlayerSprites() {
    }

    /** Where the player's sprites are in one game. */
    public record Location(String narcPath, int maleMember, int femaleMember) {
    }

    public static final Location HGSS = new Location("a/0/8/1", 69, 70);
    public static final Location BLACK2 = new Location("a/0/4/8", 7, 10);

    /** Whether the members look like the expected texture archives (a cheap check that changes nothing). */
    public static boolean looksRight(NARCArchive narc, Location where) {
        try {
            List<byte[]> files = narc.files;
            if (files.size() <= Math.max(where.maleMember(), where.femaleMember())) {
                return false;
            }
            for (int member : new int[]{where.maleMember(), where.femaleMember()}) {
                Btx0 btx = new Btx0(files.get(member).clone());
                if (btx.paletteCount() < 1 || btx.textures().isEmpty() || !btx.textures().get(0).is16Colours()
                        || btx.textures().get(0).width != 32 || btx.textures().get(0).height != 32) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static BufferedImage export(NARCArchive narc, int member) {
        return SpriteSheet.render(new Btx0(narc.files.get(member).clone()));
    }

    /** Puts the sheet into the member of the NARC (in place). */
    public static PatchResult apply(NARCArchive narc, int member, BufferedImage sheet, String who) {
        try {
            byte[] copy = narc.files.get(member).clone();
            SpriteSheet.Result result = SpriteSheet.apply(new Btx0(copy), sheet);
            narc.files.set(member, copy);
            StringBuilder message = new StringBuilder("The walking sprites of the " + who + " were replaced ("
                    + result.coloursUsed() + " colours).");
            for (String note : result.notes()) {
                message.append(' ').append(note);
            }
            return PatchResult.applied(message.toString());
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return PatchResult.notApplied("The walking sprites of the " + who + " were not replaced: " + e.getMessage());
        }
    }
}
