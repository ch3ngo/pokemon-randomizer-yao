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
        // Added (13/10/2026, web UI feedback): on Windows the real Windows dialogs are used, through PowerShell and
        // System.Windows.Forms, instead of AWT's. AWT showed the Java logo as the window icon (as if Java were the
        // program asking), and cannot pick a folder at all, which forced people to type a file name just to choose
        // where to save. mode "folder" picks a folder. If PowerShell fails, the AWT dialog below is the fallback.
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            Map<String, Object> viaWindows = windowsDialog(mode, filter, title, startDir);
            if (viaWindows != null) {
                return viaWindows;
            }
        }
        // macOS: the system's own dialogs through osascript. Linux: zenity (GTK) or kdialog (KDE) when installed.
        // Both are optional, so each returns null when unavailable and the AWT/Swing fallback below takes over.
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Map<String, Object> viaTool = null;
        if (os.contains("mac")) {
            viaTool = macDialog(mode, filter, title, startDir);
        } else if (!os.contains("win")) {
            viaTool = linuxDialog(mode, filter, title, startDir);
        }
        if (viaTool != null) {
            return viaTool;
        }
        if ("folder".equalsIgnoreCase(mode)) {
            return chooseFolderSwing(title, startDir);
        }
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
                    // Fixed for YAO (13/10/2026, web UI feedback): leaving the directory unset let the OS pick its
                    // own default, which on Windows can land somewhere unhelpful (a temp/AppData-style folder) when
                    // no ROM has been loaded yet and nothing better is known. The user's home folder is always a
                    // sane, predictable starting point instead.
                    String effectiveStartDir = (startDir == null || startDir.isBlank())
                            ? System.getProperty("user.home", "") : startDir;
                    if (!effectiveStartDir.isBlank()) {
                        dialog.setDirectory(effectiveStartDir);
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

    /** Returns null when the Windows dialog could not be run, so the caller can fall back. */
    private static Map<String, Object> windowsDialog(String mode, String filter, String title, String startDir) {
        String kind = "folder".equalsIgnoreCase(mode) ? "folder" : "save".equalsIgnoreCase(mode) ? "save" : "open";
        String script = String.join("\n",
                "$ErrorActionPreference='Stop'",
                "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8",
                "Add-Type -AssemblyName System.Windows.Forms",
                "[System.Windows.Forms.Application]::EnableVisualStyles()",
                "$owner=New-Object System.Windows.Forms.Form",
                "$owner.TopMost=$true;$owner.ShowInTaskbar=$false;$owner.Opacity=0;$owner.StartPosition='CenterScreen'",
                "$owner.Show();$owner.Activate()",
                "$kind=$env:YAO_KIND;$start=$env:YAO_START;$title=$env:YAO_TITLE;$filter=$env:YAO_FILTER",
                "if($kind -eq 'folder'){$d=New-Object System.Windows.Forms.FolderBrowserDialog;$d.ShowNewFolderButton=$true;"
                        + "if($title){$d.Description=$title};if($start -and (Test-Path -LiteralPath $start -PathType Container)){$d.SelectedPath=$start}}",
                "else{if($kind -eq 'save'){$d=New-Object System.Windows.Forms.SaveFileDialog}else{$d=New-Object System.Windows.Forms.OpenFileDialog};"
                        + "if($title){$d.Title=$title};if($start -and (Test-Path -LiteralPath $start -PathType Container)){$d.InitialDirectory=$start};"
                        + "if($filter -eq 'rom'){$d.Filter='Games|*.gb;*.gbc;*.gba;*.nds;*.3ds;*.cxi;*.cci;*.sgb|All files|*.*'}"
                        + "elseif($filter -eq 'png'){$d.Filter='PNG images|*.png|All files|*.*'}}",
                "$r=$d.ShowDialog($owner)",
                "$owner.Close()",
                "if($r -eq [System.Windows.Forms.DialogResult]::OK){if($kind -eq 'folder'){Write-Output $d.SelectedPath}else{Write-Output $d.FileName}}");
        try {
            String encoded = java.util.Base64.getEncoder().encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-STA",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
            Map<String, String> env = pb.environment();
            env.put("YAO_KIND", kind);
            env.put("YAO_START", startDir == null || startDir.isBlank() ? System.getProperty("user.home", "") : startDir);
            env.put("YAO_TITLE", title == null ? "" : title);
            env.put("YAO_FILTER", filter == null ? "" : filter);
            pb.redirectErrorStream(false);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            process.getOutputStream().close();
            String out = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
            int code = process.waitFor();
            if (code != 0) {
                return null;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", true);
            m.put("path", out.isEmpty() ? null : out); // null means the user cancelled
            return m;
        } catch (java.io.IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String startFolder(String startDir) {
        File f = startDir == null || startDir.isBlank() ? null : new File(startDir);
        return f != null && f.isDirectory() ? f.getAbsolutePath() : System.getProperty("user.home", "");
    }

    /** Runs a dialog tool and returns its trimmed stdout. Exit code 1 means cancelled; anything else unusable is null. */
    private static Map<String, Object> runTool(java.util.List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            process.getOutputStream().close();
            String out = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
            int code = process.waitFor();
            Map<String, Object> m = new LinkedHashMap<>();
            if (code == 0 && !out.isEmpty()) {
                m.put("ok", true);
                m.put("path", out);
                return m;
            }
            if (code == 0 || code == 1) { // cancelled
                m.put("ok", true);
                m.put("path", null);
                return m;
            }
            return null;
        } catch (java.io.IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Map<String, Object> macDialog(String mode, String filter, String title, String startDir) {
        boolean folder = "folder".equalsIgnoreCase(mode);
        String prompt = title == null || title.isBlank() ? (folder ? "Choose a folder" : "Choose a file") : title;
        String esc = prompt.replace("\\", "\\\\").replace("\"", "\\\"");
        String start = startFolder(startDir).replace("\\", "\\\\").replace("\"", "\\\"");
        String what = folder ? "choose folder" : "choose file";
        String script = "POSIX path of (" + what + " with prompt \"" + esc + "\" default location (POSIX file \"" + start + "\"))";
        Map<String, Object> r = runTool(java.util.List.of("osascript", "-e", script));
        if (r != null && r.get("path") instanceof String p && p.endsWith("/") && p.length() > 1) {
            r.put("path", p.substring(0, p.length() - 1));
        }
        return r;
    }

    private static Map<String, Object> linuxDialog(String mode, String filter, String title, String startDir) {
        boolean folder = "folder".equalsIgnoreCase(mode);
        String start = startFolder(startDir);
        String heading = title == null || title.isBlank() ? (folder ? "Choose a folder" : "Choose a file") : title;
        if (onPath("zenity")) {
            java.util.List<String> c = new java.util.ArrayList<>(java.util.List.of("zenity", "--file-selection", "--title=" + heading,
                    "--filename=" + start + File.separator));
            if (folder) {
                c.add("--directory");
            } else if ("rom".equalsIgnoreCase(filter)) {
                c.add("--file-filter=Games | *.gb *.gbc *.gba *.nds *.3ds *.cxi *.cci *.sgb");
            } else if ("png".equalsIgnoreCase(filter)) {
                c.add("--file-filter=PNG images | *.png");
            }
            return runTool(c);
        }
        if (onPath("kdialog")) {
            String kind = folder ? "--getexistingdirectory" : "--getopenfilename";
            String pattern = "rom".equalsIgnoreCase(filter) ? "*.gb *.gbc *.gba *.nds *.3ds *.cxi *.cci *.sgb"
                    : "png".equalsIgnoreCase(filter) ? "*.png" : "";
            java.util.List<String> c = new java.util.ArrayList<>(java.util.List.of("kdialog", "--title", heading, kind, start));
            if (!folder && !pattern.isEmpty()) {
                c.add(pattern);
            }
            return runTool(c);
        }
        return null;
    }

    private static boolean onPath(String tool) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (new File(dir, tool).canExecute()) {
                return true;
            }
        }
        return false;
    }

    /** Folder picker for platforms without the PowerShell route (AWT has no folder mode for FileDialog). */
    private static Map<String, Object> chooseFolderSwing(String title, String startDir) {
        if (GraphicsEnvironment.isHeadless()) {
            return error("No graphical display is available on this machine, so the native file dialog cannot open. "
                    + "Type the path by hand instead.");
        }
        String[] chosen = new String[1];
        try {
            EventQueue.invokeAndWait(() -> {
                javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(
                        startDir == null || startDir.isBlank() ? System.getProperty("user.home", "") : startDir);
                chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle(title == null || title.isBlank() ? "Choose a folder" : title);
                javax.swing.JDialog parent = new javax.swing.JDialog();
                parent.setAlwaysOnTop(true);
                if (chooser.showOpenDialog(parent) == javax.swing.JFileChooser.APPROVE_OPTION) {
                    chosen[0] = chooser.getSelectedFile().getAbsolutePath();
                }
                parent.dispose();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("The native file dialog was interrupted.");
        } catch (java.lang.reflect.InvocationTargetException e) {
            return error("The native file dialog could not be shown: " + e.getCause());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("path", chosen[0]);
        return m;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", message);
        return m;
    }
}
