package com.uprfvx.random.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only the headless path is exercised here: actually opening the native dialog needs a real display and a human
 * to click something, which an automated test must never wait on. See the project notes for how the interactive
 * path was verified instead (code review plus a manual check of this environment's display availability).
 */
public class NativeFileDialogTest {

    @Test
    public void reportsACleanErrorWhenNoDisplayIsAvailable() {
        String previous = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "true");
        try {
            Map<String, Object> result = NativeFileDialog.choose("open", "rom", "Choose a game", "");
            assertEquals(false, result.get("ok"));
            assertNull(result.get("path"));
            assertTrue(String.valueOf(result.get("error")).toLowerCase(java.util.Locale.ROOT).contains("display"),
                    String.valueOf(result.get("error")));
        } finally {
            if (previous == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", previous);
            }
        }
    }
}
