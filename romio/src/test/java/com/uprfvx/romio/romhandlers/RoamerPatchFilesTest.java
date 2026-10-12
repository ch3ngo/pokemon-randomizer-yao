package com.uprfvx.romio.romhandlers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Spanish SoulSilver roamer patch is the English one with its subroutine data written 0x40 bytes later
 * (the ARM9 extension area starts 0x40 further on in Spanish). These tests keep the two files in step.
 */
public class RoamerPatchFilesTest {

    private record Record(int offset, byte[] data) {
    }

    private static List<Record> readIps(String resource) throws IOException {
        try (InputStream in = RoamerPatchFilesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            byte[] d = in.readAllBytes();
            assertEquals("PATCH", new String(d, 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
            List<Record> records = new ArrayList<>();
            int i = 5;
            while (!(d[i] == 'E' && d[i + 1] == 'O' && d[i + 2] == 'F')) {
                int offset = ((d[i] & 0xFF) << 16) | ((d[i + 1] & 0xFF) << 8) | (d[i + 2] & 0xFF);
                int length = ((d[i + 3] & 0xFF) << 8) | (d[i + 4] & 0xFF);
                i += 5;
                assertNotEquals(0, length, "run length records are not expected");
                byte[] data = java.util.Arrays.copyOfRange(d, i, i + length);
                i += length;
                records.add(new Record(offset, data));
            }
            return records;
        }
    }

    @Test
    public void spanishPatchIsTheEnglishOneWithTheExtensionAreaShiftedByForty() throws IOException {
        List<Record> en = readIps("/com/uprfvx/romio/patches/hardcoded_statics/roamers/hgss_roamers.ips");
        List<Record> es = readIps("/com/uprfvx/romio/patches/hardcoded_statics/roamers/hgss_roamers_es.ips");
        assertEquals(en.size(), es.size());
        for (int i = 0; i < en.size(); i++) {
            assertArrayEquals(en.get(i).data(), es.get(i).data(), "record " + i + " data");
            boolean inExtensionArea = en.get(i).offset() >= 0x111000;
            assertEquals(en.get(i).offset() + (inExtensionArea ? 0x40 : 0), es.get(i).offset(), "record " + i + " offset");
        }
    }

    @Test
    public void englishPatchHasTheTwoHooksAndTheSubroutines() throws IOException {
        List<Record> en = readIps("/com/uprfvx/romio/patches/hardcoded_statics/roamers/hgss_roamers.ips");
        assertEquals(3, en.size());
        assertEquals(0x67709, en.get(0).offset());
        assertEquals(0x677F6, en.get(1).offset());
        assertEquals(0x111EDD, en.get(2).offset());
    }
}
