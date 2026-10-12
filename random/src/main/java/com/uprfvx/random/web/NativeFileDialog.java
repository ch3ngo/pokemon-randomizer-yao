package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  NativeFileDialog.java - the OS's own file dialog, opened server side. --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Added for YAO (11/10/2026): replaces the old in-browser custom file picker ({@code FileBrowser}, removed), which
 * the user reported showed broken, non-working paths. Since the web interface only ever talks to the browser on the
 * same machine it runs on (see {@link WebServer}'s loopback-only binding and per-run token), opening the operating
 * system's own file dialog on the server and handing the chosen path back to the page is both simpler and far more
 * reliable than re-implementing a file browser in the page itself.
 *
 * <p>This needs a graphical display, which a true headless deployment of this program would not have (this program
 * is meant to run locally with a display, but nothing stops someone from trying it on a remote/server box). That
 * case is detected up front with {@link GraphicsEnvironment#isHeadless()} and reported as an ordinary error, so the
 * page can fall back to its plain manual path text field instead of the request hanging or the program crashing.
 */
final class NativeFileDialog {

    private NativeFileDialog() {
    }

    /** Extensions of the games the randomizer can open (kept here since the old {@code FileBrowser} was removed). */
    private static final java.util.Set<String> ROM_EXTENSIONS =
            java.util.Set.of("gb", "gbc", "gba", "nds", "3ds", "cxi", "cci", "sgb");

    private static boolean isRomFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot >= 0 && ROM_EXTENSIONS.contains(lower.substring(dot + 1));
    }

    /**
     * @param mode     {@code "open"} or {@code "save"}
     * @param filter   {@code "rom"}, {@code "png"} or anything else for no filter
     * @param title    the dialog's title, or blank for a default based on {@code mode}
     * @param startDir the folder to start in, or blank for the OS default
     */
    static Map<String, Object> choose(String mode, String filter, String title, String startDir) {
        if (GraphicsEnvironment.isHeadless()) {
            return error("No graphical display is available on this machine, so the native file dialog cannot open. "
                    + "Type the path by hand instead.");
        }
        boolean save = "save".equalsIgnoreCase(mode);
        String[] chosen = new String[1];
        String[] failure = new String[1];
        try {
            EventQueue.invokeAndWait(() -> {
                try {
                    FileDialog dialog = new FileDialog((java.awt.Frame) null,
                            title == null || title.isBlank() ? (save ? "Save as" : "Choose a file") : title,
                            save ? FileDialog.SAVE : FileDialog.LOAD);
                    if (startDir != null && !startDir.isBlank()) {
                        dialog.setDirectory(startDir);
                    }
                    if (!save && "rom".equalsIgnoreCase(filter)) {
                        dialog.setFilenameFilter((dir, name) -> isRomFile(name));
                    } else if (!save && "png".equalsIgnoreCase(filter)) {
                        dialog.setFilenameFilter((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
                    }
                    dialog.setAlwaysOnTop(true);
                    dialog.setVisible(true);
                    String file = dialog.getFile();
                    String directory = dialog.getDirectory();
                    if (file != null && directory != null) {
                        chosen[0] = new File(directory, file).getAbsolutePath();
                    }
                    dialog.dispose();
                } catch (RuntimeException | Error e) {
                    failure[0] = String.valueOf(e);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("The native file dialog was interrupted.");
        } catch (java.lang.reflect.InvocationTargetException e) {
            return error("The native file dialog could not be shown: " + e.getCause());
        }
        if (failure[0] != null) {
            return error("The native file dialog could not be shown: " + failure[0]);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("path", chosen[0]); // null means the user cancelled
        return m;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", message);
        return m;
    }
}
