package com.uprfvx.romio.graphics.ds;

/*----------------------------------------------------------------------------*/
/*--  Btx0.java - reads and patches Nintendo DS texture archives (NSBTX).   --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.util.ArrayList;
import java.util.List;

/**
 * A reader and in-place patcher for the DS texture archive format (BTX0, "NSBTX") that the Gen 4 and Gen 5 games use
 * for the overworld sprites of people. It understands only what that needs: 16 colour (4 bits per pixel) textures and
 * 16 entry palettes. Texture and palette data are overwritten in place, so the file never changes size.
 *
 * <p>Layout used (offsets inside the TEX0 block): texture data offset at +0x14, texture info dictionary offset at
 * +0x0E, palette data offset at +0x38 and palette info dictionary offset at +0x34. A dictionary starts with a 4 byte
 * header, a 4 byte part, {@code (count + 1) * 4} bytes of search tree, then a 4 byte data header, then
 * {@code count} data entries and {@code count} 16 byte names.
 */
public final class Btx0 {

    /** One texture of the archive. */
    public static final class Texture {
        public final String name;
        public final int width;
        public final int height;
        public final int format;
        public final boolean color0Transparent;
        /** Absolute offset of the texel data in the file. */
        public final int dataOffset;
        public final int paletteIndex;

        Texture(String name, int width, int height, int format, boolean color0Transparent, int dataOffset, int paletteIndex) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.format = format;
            this.color0Transparent = color0Transparent;
            this.dataOffset = dataOffset;
            this.paletteIndex = paletteIndex;
        }

        public boolean is16Colours() {
            return format == 3;
        }

        public int byteLength() {
            return width * height / 2;
        }
    }

    private final byte[] data;
    private final List<Texture> textures = new ArrayList<>();
    private final List<Integer> paletteOffsets = new ArrayList<>();
    private final List<String> paletteNames = new ArrayList<>();

    /** Reads the archive; {@code data} is kept and patched in place. Throws IllegalArgumentException if it is not understood. */
    public Btx0(byte[] data) {
        this.data = data;
        if (data == null || data.length < 0x40 || data[0] != 'B' || data[1] != 'T' || data[2] != 'X' || data[3] != '0') {
            throw new IllegalArgumentException("Not a BTX0 texture archive.");
        }
        int blocks = u16(0x0E);
        int tex0 = -1;
        for (int i = 0; i < blocks; i++) {
            int offset = u32(0x10 + 4 * i);
            if (offset > 0 && offset + 4 <= data.length && data[offset] == 'T' && data[offset + 1] == 'E'
                    && data[offset + 2] == 'X' && data[offset + 3] == '0') {
                tex0 = offset;
            }
        }
        if (tex0 < 0) {
            throw new IllegalArgumentException("The archive has no TEX0 block.");
        }
        int texDataOffset = tex0 + u32(tex0 + 0x14);
        int texInfo = tex0 + u16(tex0 + 0x0E);
        int palInfo = tex0 + u32(tex0 + 0x34);
        int palData = tex0 + u32(tex0 + 0x38);
        check(texDataOffset, 0);
        check(texInfo, 4);
        check(palInfo, 4);

        int count = data[texInfo + 1] & 0xFF;
        int entries = texInfo + 4 + 4 + (count + 1) * 4 + 4;
        int names = entries + count * 8;
        for (int i = 0; i < count; i++) {
            int e = entries + i * 8;
            check(e, 8);
            check(names + i * 16, 16);
            int offset = u16(e) << 3;
            int param = u16(e + 2);
            int width = 8 << ((param >> 4) & 7);
            int height = 8 << ((param >> 7) & 7);
            int format = (param >> 10) & 7;
            boolean transparent = ((param >> 13) & 1) != 0;
            textures.add(new Texture(name(names + i * 16), width, height, format, transparent, texDataOffset + offset, 0));
        }
        int pCount = data[palInfo + 1] & 0xFF;
        int pEntries = palInfo + 4 + 4 + (pCount + 1) * 4 + 4;
        int pNames = pEntries + pCount * 4;
        for (int i = 0; i < pCount; i++) {
            check(pEntries + i * 4, 4);
            check(pNames + i * 16, 16);
            paletteOffsets.add(palData + (u16(pEntries + i * 4) << 3));
            paletteNames.add(name(pNames + i * 16));
        }
    }

    private void check(int offset, int length) {
        if (offset < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("The texture archive is damaged.");
        }
    }

    private int u16(int o) {
        check(o, 2);
        return (data[o] & 0xFF) | ((data[o + 1] & 0xFF) << 8);
    }

    private int u32(int o) {
        check(o, 4);
        return u16(o) | (u16(o + 2) << 16);
    }

    private String name(int o) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16 && data[o + i] != 0; i++) {
            sb.append((char) (data[o + i] & 0xFF));
        }
        return sb.toString();
    }

    public List<Texture> textures() {
        return textures;
    }

    public int paletteCount() {
        return paletteOffsets.size();
    }

    /** The colours of a palette as 0xRRGGBB (16 entries). */
    public int[] palette(int index) {
        int base = paletteOffsets.get(index);
        check(base, 32);
        int[] colours = new int[16];
        for (int i = 0; i < 16; i++) {
            int v = u16(base + i * 2);
            int r = ((v & 31) * 255 + 15) / 31;
            int g = (((v >> 5) & 31) * 255 + 15) / 31;
            int b = (((v >> 10) & 31) * 255 + 15) / 31;
            colours[i] = (r << 16) | (g << 8) | b;
        }
        return colours;
    }

    /** Writes a palette from 0xRRGGBB colours (at most 16). */
    public void setPalette(int index, int[] colours) {
        int base = paletteOffsets.get(index);
        check(base, 32);
        for (int i = 0; i < 16; i++) {
            int c = i < colours.length ? colours[i] : 0;
            int r = (((c >> 16) & 0xFF) * 31 + 127) / 255;
            int g = (((c >> 8) & 0xFF) * 31 + 127) / 255;
            int b = ((c & 0xFF) * 31 + 127) / 255;
            int v = r | (g << 5) | (b << 10);
            data[base + i * 2] = (byte) v;
            data[base + i * 2 + 1] = (byte) (v >> 8);
        }
    }

    /** The palette indexes (0 to 15) of a 16 colour texture, row by row. */
    public int[] readIndexes(Texture t) {
        check(t.dataOffset, t.byteLength());
        int[] out = new int[t.width * t.height];
        for (int i = 0; i < out.length; i++) {
            int b = data[t.dataOffset + i / 2] & 0xFF;
            out[i] = (i & 1) == 0 ? b & 0xF : b >> 4;
        }
        return out;
    }

    public void writeIndexes(Texture t, int[] indexes) {
        check(t.dataOffset, t.byteLength());
        if (indexes.length != t.width * t.height) {
            throw new IllegalArgumentException("Wrong number of pixels for the texture.");
        }
        for (int i = 0; i < indexes.length; i += 2) {
            data[t.dataOffset + i / 2] = (byte) ((indexes[i] & 0xF) | ((indexes[i + 1] & 0xF) << 4));
        }
    }

    public byte[] bytes() {
        return data;
    }
}
