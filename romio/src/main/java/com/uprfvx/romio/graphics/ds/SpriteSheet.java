package com.uprfvx.romio.graphics.ds;

/*----------------------------------------------------------------------------*/
/*--  SpriteSheet.java - turns the frames of a texture archive into a PNG   --*/
/*--  sheet and back.                                                       --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts between the 16 colour frames of a {@link Btx0} and one PNG sheet (frames side by side, {@link #COLUMNS} per
 * row, in the order of the archive). Converting a sheet back uses one 16 colour palette for all frames (colour 0 is
 * the transparent one), so the artwork is reduced to 15 colours plus transparency; if it already uses 15 or fewer
 * colours they are kept exactly, otherwise the most used colours are kept and the others become the nearest of them.
 */
public final class SpriteSheet {

    private SpriteSheet() {
    }

    public static final int COLUMNS = 8;

    /** The sheet of all the frames of the texture archive that are 16 colour textures. */
    public static BufferedImage render(Btx0 btx) {
        List<Btx0.Texture> frames = frames(btx);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("The archive has no 16 colour frames.");
        }
        int fw = frames.get(0).width, fh = frames.get(0).height;
        int rows = (frames.size() + COLUMNS - 1) / COLUMNS;
        BufferedImage image = new BufferedImage(fw * COLUMNS, fh * rows, BufferedImage.TYPE_INT_ARGB);
        int[] palette = btx.paletteCount() > 0 ? btx.palette(0) : new int[16];
        for (int f = 0; f < frames.size(); f++) {
            Btx0.Texture t = frames.get(f);
            int[] px = btx.readIndexes(t);
            int ox = (f % COLUMNS) * fw, oy = (f / COLUMNS) * fh;
            for (int y = 0; y < t.height; y++) {
                for (int x = 0; x < t.width; x++) {
                    int idx = px[y * t.width + x];
                    boolean transparent = idx == 0 && t.color0Transparent;
                    image.setRGB(ox + x, oy + y, transparent ? 0 : 0xFF000000 | palette[idx]);
                }
            }
        }
        return image;
    }

    private static List<Btx0.Texture> frames(Btx0 btx) {
        List<Btx0.Texture> frames = new ArrayList<>();
        for (Btx0.Texture t : btx.textures()) {
            if (t.is16Colours()) {
                frames.add(t);
            }
        }
        return frames;
    }

    /** What happened while putting a sheet into an archive. */
    public record Result(List<String> notes, int coloursUsed) {
    }

    /** Writes the sheet into the archive in place. Throws IllegalArgumentException if the sheet has the wrong size. */
    public static Result apply(Btx0 btx, BufferedImage sheet) {
        List<Btx0.Texture> frames = frames(btx);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("The archive has no 16 colour frames.");
        }
        int fw = frames.get(0).width, fh = frames.get(0).height;
        int rows = (frames.size() + COLUMNS - 1) / COLUMNS;
        if (sheet.getWidth() != fw * COLUMNS || sheet.getHeight() != fh * rows) {
            throw new IllegalArgumentException("The sheet must be " + (fw * COLUMNS) + " by " + (fh * rows)
                    + " pixels (" + COLUMNS + " frames of " + fw + " by " + fh + " per row), but it is " + sheet.getWidth()
                    + " by " + sheet.getHeight() + ".");
        }
        List<String> notes = new ArrayList<>();
        // 1. count the colours of the opaque pixels of the frames that are actually written
        Map<Integer, Integer> counts = new HashMap<>();
        Set<Integer> written = new HashSet<>();
        List<Btx0.Texture> toWrite = new ArrayList<>();
        List<Integer> toWriteFrame = new ArrayList<>();
        for (int f = 0; f < frames.size(); f++) {
            Btx0.Texture t = frames.get(f);
            if (!written.add(t.dataOffset)) {
                notes.add("Frame " + (f + 1) + " shares its pixels with an earlier frame of the game, so its drawing is ignored.");
                continue;
            }
            toWrite.add(t);
            toWriteFrame.add(f);
            int ox = (f % COLUMNS) * fw, oy = (f / COLUMNS) * fh;
            for (int y = 0; y < fh; y++) {
                for (int x = 0; x < fw; x++) {
                    int argb = sheet.getRGB(ox + x, oy + y);
                    if (((argb >>> 24) & 0xFF) >= 128) {
                        counts.merge(argb & 0xFFFFFF, 1, Integer::sum);
                    }
                }
            }
        }
        List<Integer> colours = new ArrayList<>(counts.keySet());
        colours.sort((a, b) -> counts.get(b) != counts.get(a) ? counts.get(b) - counts.get(a) : a - b);
        int kept = Math.min(15, colours.size());
        if (colours.size() > 15) {
            notes.add("The drawing uses " + colours.size() + " colours; the 15 most used were kept and the others were "
                    + "changed to the nearest of them.");
        }
        int[] palette = new int[16];
        for (int i = 0; i < kept; i++) {
            palette[i + 1] = colours.get(i);
        }
        // 2. write the frames
        for (int n = 0; n < toWrite.size(); n++) {
            Btx0.Texture t = toWrite.get(n);
            int f = toWriteFrame.get(n);
            int ox = (f % COLUMNS) * fw, oy = (f / COLUMNS) * fh;
            int[] idx = new int[fw * fh];
            for (int y = 0; y < fh; y++) {
                for (int x = 0; x < fw; x++) {
                    int argb = sheet.getRGB(ox + x, oy + y);
                    idx[y * fw + x] = ((argb >>> 24) & 0xFF) < 128 || kept == 0 ? 0 : 1 + nearest(palette, kept, argb & 0xFFFFFF);
                }
            }
            btx.writeIndexes(t, idx);
        }
        // 3. one palette for every frame (all frames of these archives share palette 0)
        btx.setPalette(0, palette);
        return new Result(notes, kept);
    }

    private static int nearest(int[] palette, int kept, int rgb) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < kept; i++) {
            int c = palette[i + 1];
            long dr = ((rgb >> 16) & 0xFF) - ((c >> 16) & 0xFF);
            long dg = ((rgb >> 8) & 0xFF) - ((c >> 8) & 0xFF);
            long db = (rgb & 0xFF) - (c & 0xFF);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }
}
