package com.uprfvx.romio.graphics.ds;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class SpriteSheetTest {

    private static void u16(ByteArrayOutputStream o, int v) {
        o.write(v);
        o.write(v >> 8);
    }

    private static void u32(ByteArrayOutputStream o, int v) {
        u16(o, v & 0xFFFF);
        u16(o, v >>> 16);
    }

    private static void name(ByteArrayOutputStream o, String n) {
        byte[] b = n.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < 16; i++) {
            o.write(i < b.length ? b[i] : 0);
        }
    }

    /** A minimal BTX0 with the given number of 32x32 16 colour frames, the last frame sharing the first one's pixels, and one palette. */
    private static byte[] makeBtx(int frames) {
        int tex0Start = 0x14;
        int infoOffset = 0x3C;
        int texCount = frames;
        int texDictSize = 4 + 4 + (texCount + 1) * 4 + 4 + texCount * 8 + texCount * 16;
        int palInfoOffset = infoOffset + texDictSize;
        int palDictSize = 4 + 4 + 2 * 4 + 4 + 4 + 16;
        int texData = palInfoOffset + palDictSize;
        int unique = frames - 1;
        int palData = texData + unique * 512;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes("BTX0".getBytes(StandardCharsets.US_ASCII));
        u16(o, 0xFEFF);
        u16(o, 1);
        u32(o, 0);
        u16(o, 16);
        u16(o, 1);
        u32(o, 0x14);
        o.writeBytes("TEX0".getBytes(StandardCharsets.US_ASCII));
        u32(o, 0);
        u32(o, 0);
        u16(o, 0);
        u16(o, infoOffset);
        u32(o, 0);
        u32(o, texData);
        while (o.size() < tex0Start + 0x34) {
            o.write(0);
        }
        u32(o, palInfoOffset);
        u32(o, palData);
        while (o.size() < tex0Start + infoOffset) {
            o.write(0);
        }
        o.write(0);
        o.write(texCount);
        u16(o, texDictSize);
        u16(o, 8);
        u16(o, 0);
        for (int i = 0; i <= texCount; i++) {
            u32(o, 0);
        }
        u16(o, 8);
        u16(o, 4 + texCount * 8);
        for (int i = 0; i < texCount; i++) {
            int offset = (i == frames - 1 ? 0 : i * 512) >> 3;
            u16(o, offset);
            u16(o, 3 << 10 | 1 << 13 | 2 << 4 | 2 << 7);
            u32(o, 0);
        }
        for (int i = 0; i < texCount; i++) {
            name(o, "f." + (i + 1));
        }
        o.write(0);
        o.write(1);
        u16(o, palDictSize);
        u16(o, 8);
        u16(o, 0);
        u32(o, 0);
        u32(o, 0);
        u16(o, 4);
        u16(o, 8);
        u16(o, 0);
        u16(o, 0);
        name(o, "pal");
        while (o.size() < tex0Start + texData) {
            o.write(0);
        }
        for (int i = 0; i < unique * 512; i++) {
            o.write(0);
        }
        for (int i = 0; i < 32; i++) {
            o.write(0);
        }
        return o.toByteArray();
    }

    @Test
    public void readsTheFramesAndPalette() {
        Btx0 btx = new Btx0(makeBtx(9));
        assertEquals(9, btx.textures().size());
        assertEquals(1, btx.paletteCount());
        Btx0.Texture t = btx.textures().get(0);
        assertEquals(32, t.width);
        assertEquals(32, t.height);
        assertTrue(t.is16Colours());
        assertTrue(t.color0Transparent);
        assertEquals(btx.textures().get(8).dataOffset, t.dataOffset, "the last frame shares the first one's pixels");
    }

    @Test
    public void notATextureArchiveIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Btx0(new byte[100]));
        assertThrows(IllegalArgumentException.class, () -> new Btx0(null));
        byte[] damaged = makeBtx(2);
        damaged[0x14 + 0x14] = (byte) 0xFF;
        damaged[0x14 + 0x15] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> new Btx0(damaged));
    }

    @Test
    public void aSheetWrittenIntoAnArchiveReadsBackTheSame() {
        byte[] data = makeBtx(9);
        Btx0 btx = new Btx0(data);
        BufferedImage sheet = SpriteSheet.render(btx);
        assertEquals(256, sheet.getWidth());
        assertEquals(64, sheet.getHeight());
        for (int y = 4; y < 12; y++) {
            for (int x = 36; x < 44; x++) {
                sheet.setRGB(x, y, 0xFFFF0000);
            }
        }
        sheet.setRGB(70, 5, 0xFF0000FF);
        SpriteSheet.Result result = SpriteSheet.apply(btx, sheet);
        assertEquals(2, result.coloursUsed());
        BufferedImage back = SpriteSheet.render(new Btx0(data));
        assertEquals(0xFFFF0000, back.getRGB(40, 8));
        assertEquals(0xFF0000FF, back.getRGB(70, 5));
        assertEquals(0, back.getRGB(0, 0) >>> 24, "transparent stays transparent");
    }

    @Test
    public void moreThanFifteenColoursAreReducedWithANote() {
        byte[] data = makeBtx(9);
        Btx0 btx = new Btx0(data);
        BufferedImage sheet = SpriteSheet.render(btx);
        for (int i = 0; i < 20; i++) {
            sheet.setRGB(i, 0, 0xFF000000 | (i * 12) << 16 | (255 - i * 12));
        }
        SpriteSheet.Result result = SpriteSheet.apply(btx, sheet);
        assertEquals(15, result.coloursUsed());
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("20 colours")));
    }

    @Test
    public void aSheetOfTheWrongSizeIsRefusedWithTheRightSizeInTheMessage() {
        Btx0 btx = new Btx0(makeBtx(9));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SpriteSheet.apply(btx, new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)));
        assertTrue(e.getMessage().contains("256 by 64"));
    }

    @Test
    public void sharedFramesAreReportedAndTheFirstWins() {
        byte[] data = makeBtx(9);
        Btx0 btx = new Btx0(data);
        BufferedImage sheet = SpriteSheet.render(btx);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                sheet.setRGB(x, y, 0xFF00FF00);
                sheet.setRGB(x, 32 + y, 0xFFFF00FF);
            }
        }
        SpriteSheet.Result result = SpriteSheet.apply(btx, sheet);
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("Frame 9")));
        assertEquals(0xFF00FF00, SpriteSheet.render(new Btx0(data)).getRGB(5, 5));
    }
}
