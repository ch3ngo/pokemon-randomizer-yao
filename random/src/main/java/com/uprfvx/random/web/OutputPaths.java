package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  OutputPaths.java - works out where a randomized game is really saved. --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Added for YAO (12/10/2026). The save dialog hands the program whatever the user typed: a name with no extension,
 * the wrong extension, an existing folder, or (for a 3DS game) a file name. The program used to pass that on
 * as it was, so a 3DS game could end up in a folder named like a file, and the path shown in the interface could
 * differ from the file the randomizer really wrote. This class decides the one real destination up front, and
 * lists what it had to change as warnings, so the user is told instead of left with a game that cannot be found
 * or played. Pure logic apart from creating the destination's parent folders; table tested in OutputPathsTest.
 *
 * <p>Two kinds of destination exist:
 * <ul>
 *   <li>a game file: GB, GBC, GBA and DS games always, and the 3DS games when saved as one CXI file (the only
 *   3DS form that can be opened on its own in an emulator);</li>
 *   <li>a LayeredFS folder for the 3DS games: {@code <folder>/luma/titles/<title id>/} holding only what changed
 *   (code.bin and romfs files). It only works on top of the same version of the game installed in the
 *   emulator or console, and needs Luma3DS (or the emulator's mods folder) to be used.</li>
 * </ul>
 */
final class OutputPaths {

    private OutputPaths() {
    }

    /** How a 3DS game is saved. */
    enum ThreeDsFormat {
        /** A LayeredFS folder, laid out as {@code luma/titles/<title id>/}. */
        FOLDER,
        /** One CXI file, runs on its own in an emulator. */
        CXI;

        static ThreeDsFormat parse(String text) {
            return text != null && text.strip().equalsIgnoreCase("cxi") ? CXI : FOLDER;
        }
    }

    /** What to give the command line randomizer, and what to tell the user. */
    static final class Resolved {
        /** The path for the command line's {@code -o}. */
        final String cliOutput;
        /** True when the command line must be given {@code -d} (a LayeredFS folder). */
        final boolean directory;
        /** The file or folder the user should look for when the run is finished. */
        final File location;
        /** The existing thing that would be replaced, or null. The interface asks before replacing it. */
        final File existing;
        /** Everything that was changed from what was typed, in plain words. */
        final List<String> warnings;
        /** Where the log and the guide are written (next to the file, or next to the folder). */
        final String companionBase;

        private final File folderToCreate;

        Resolved(String cliOutput, boolean directory, File location, File existing, List<String> warnings,
                 String companionBase, File folderToCreate) {
            this.folderToCreate = folderToCreate;
            this.cliOutput = cliOutput;
            this.directory = directory;
            this.location = location;
            this.existing = existing;
            this.warnings = warnings;
            this.companionBase = companionBase;
        }
    }

    /**
     * Creates the folders the destination needs. Separate from {@link #resolve} (which only looks) so that nothing
     * is created on disk while the user is still being asked whether to replace an existing result.
     *
     * @throws IllegalArgumentException with a message fit for the user when a folder cannot be made
     */
    static void prepare(Resolved resolved) {
        makeFolder(resolved.folderToCreate);
    }

    /** Extensions of game files; a typed name that ends in one of these is treated as having an extension. */
    static final Set<String> GAME_EXTENSIONS = Set.of("gb", "gbc", "sgb", "gba", "nds", "3ds", "cci", "cxi", "cia");

    private static final String FORBIDDEN = "<>:\"|?*";

    /**
     * @param chosen        what the user typed or picked
     * @param sourceRom     the original game, which is never overwritten
     * @param threeDs       whether the game is a 3DS game
     * @param romExtension  the extension of this game's own files without a dot (gba, nds, gbc, cxi...)
     * @param format        for a 3DS game, folder or CXI; ignored for other games
     * @param titleId       the 3DS title id (sixteen hex digits) or null when unknown, used to find a folder that
     *                      would be replaced
     * @throws IllegalArgumentException with a message fit for the user when no destination can be made
     */
    static Resolved resolve(String chosen, File sourceRom, boolean threeDs, String romExtension, ThreeDsFormat format,
                            String titleId) {
        String typed = chosen == null ? "" : chosen.strip();
        if (typed.length() >= 2 && (typed.startsWith("\"") && typed.endsWith("\"") || typed.startsWith("'") && typed.endsWith("'"))) {
            typed = typed.substring(1, typed.length() - 1).strip();
        }
        if (typed.isEmpty()) {
            throw new IllegalArgumentException("Choose where to save the randomized game.");
        }
        File typedFile = new File(typed).getAbsoluteFile();
        String leaf = typedFile.getName();
        if (leaf.isEmpty()) {
            // a drive or a root, for example "C:\"
            typedFile = new File(typedFile, defaultStem(sourceRom)).getAbsoluteFile();
            leaf = typedFile.getName();
        }
        for (char c : FORBIDDEN.toCharArray()) {
            if (leaf.indexOf(c) >= 0) {
                throw new IllegalArgumentException("The name \"" + leaf + "\" has a character a file name cannot have ("
                        + c + "). Use letters, numbers, spaces, dashes and dots.");
            }
        }
        List<String> warnings = new ArrayList<>();
        File source = sourceRom.getAbsoluteFile();
        if (threeDs && format != ThreeDsFormat.CXI) {
            return resolveFolder(typedFile, source, titleId, warnings);
        }
        String extension = (threeDs ? "cxi" : romExtension == null || romExtension.isBlank() ? "gba" : romExtension)
                .toLowerCase(Locale.ROOT);
        return resolveFile(typedFile, source, extension, warnings);
    }

    // ------------------------------------------------------------------ a game file

    private static Resolved resolveFile(File typedFile, File source, String extension, List<String> warnings) {
        File file;
        if (typedFile.isDirectory()) {
            file = new File(typedFile, defaultStem(source) + "." + extension);
            warnings.add("\"" + typedFile + "\" is a folder, so the game is saved inside it as \"" + file.getName() + "\".");
        } else {
            String name = typedFile.getName();
            String typedExtension = extensionOf(name);
            String stem = name;
            if (typedExtension != null && GAME_EXTENSIONS.contains(typedExtension)) {
                stem = name.substring(0, name.length() - typedExtension.length() - 1);
                if (!typedExtension.equals(extension)) {
                    warnings.add("This game is saved as a ." + extension + " file, so the extension ." + typedExtension
                            + " was changed to ." + extension + ".");
                }
            }
            stem = cleanStem(stem, source);
            file = new File(typedFile.getParentFile(), stem + "." + extension);
        }
        if (sameFile(file, source)) {
            File renamed = new File(file.getParentFile(), stripExtension(file.getName()) + " randomized." + extension);
            warnings.add("That would replace the original game, so it is saved as \"" + renamed.getName() + "\" instead.");
            file = renamed;
        }
        File existing = file.exists() ? file : null;
        return new Resolved(file.getPath(), false, file, existing, warnings, file.getPath(), file.getParentFile());
    }

    // ------------------------------------------------------------------ a 3DS LayeredFS folder

    private static Resolved resolveFolder(File typedFile, File source, String titleId, List<String> warnings) {
        File folder;
        if (typedFile.isDirectory()) {
            File sourceFolder = source.getParentFile();
            boolean holdsTheGame = sourceFolder != null && sameFile(typedFile, sourceFolder);
            String[] entries = typedFile.list();
            boolean busy = entries != null && entries.length > 0 && !new File(typedFile, "luma").isDirectory();
            if (holdsTheGame || busy) {
                folder = new File(typedFile, defaultStem(source));
                warnings.add("\"" + typedFile + "\" already has other files in it" + (holdsTheGame ? " (including your original game)" : "")
                        + ", so the randomized game is saved in a new folder inside it called \"" + folder.getName() + "\".");
            } else {
                folder = typedFile;
            }
        } else {
            String name = typedFile.getName();
            String typedExtension = extensionOf(name);
            String stem = name;
            if (typedExtension != null && GAME_EXTENSIONS.contains(typedExtension)) {
                stem = name.substring(0, name.length() - typedExtension.length() - 1);
                warnings.add("A 3DS game is saved as a folder (or as one .cxi file), so the extension ." + typedExtension
                        + " was dropped and the folder is called \"" + cleanStem(stem, source) + "\".");
            }
            folder = new File(typedFile.getParentFile(), cleanStem(stem, source));
            if (folder.isFile()) {
                File alternative = new File(folder.getParentFile(), folder.getName() + " (layeredfs)");
                warnings.add("A file called \"" + folder.getName() + "\" already exists, so the folder is called \""
                        + alternative.getName() + "\".");
                folder = alternative;
            }
        }
        if (sameFile(folder, source)) {
            folder = new File(folder.getParentFile(), folder.getName() + " (layeredfs)");
        }
        File titles = new File(new File(folder, "luma"), "titles");
        File existing = null;
        if (titleId != null && !titleId.isBlank()) {
            File candidate = new File(titles, titleId);
            if (candidate.exists()) {
                existing = candidate;
            }
        }
        return new Resolved(titles.getPath(), true, folder, existing, warnings, folder.getPath(), titles);
    }

    // ------------------------------------------------------------------ helpers

    private static void makeFolder(File folder) {
        if (folder == null) {
            throw new IllegalArgumentException("The folder to save in is not valid.");
        }
        if (folder.isDirectory()) {
            return;
        }
        if (folder.exists()) {
            throw new IllegalArgumentException("\"" + folder + "\" is a file, not a folder.");
        }
        try {
            java.nio.file.Files.createDirectories(folder.toPath());
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("The folder to save in could not be created: " + folder + " (" + e.getMessage() + ")");
        }
    }

    /** The extension of a file name in lower case without the dot, or null when there is none. */
    static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String name) {
        String ext = extensionOf(name);
        return ext == null ? name : name.substring(0, name.length() - ext.length() - 1);
    }

    /** Removes what Windows does not allow at the end of a name, and falls back to a default for an empty one. */
    private static String cleanStem(String stem, File source) {
        String cleaned = stem.strip();
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? defaultStem(source) : cleaned;
    }

    /** The name used when none is given: the original's name plus " randomized". */
    static String defaultStem(File source) {
        return stripExtension(source.getName()) + " randomized";
    }

    /**
     * Added for YAO (13/10/2026, web UI feedback): where the log and the guide are written. Used to be
     * {@code Resolved.companionBase} itself, which for a game file was the output file's full path INCLUDING its
     * extension, so the log and the guide ended up named exactly like the randomized game, extension and all
     * ("Omega Ruby randomized.3ds.guide.html"). Named instead from the real game name read off the ROM (for
     * example "Pokemon Omega Ruby"), next to wherever the result was saved, so the name is the same regardless of
     * what the user typed as the output name and never carries a game file's extension.
     *
     * @param location the resolved destination (a file or, for a 3DS LayeredFS folder, the folder itself)
     * @param romName  the game's own name ({@code RomHandler.getROMName()}), or blank/null when unknown
     * @param source   the original game, used only for the fallback name when {@code romName} is unusable
     */
    static String companionBase(File location, String romName, File source) {
        File parent = location.getParentFile();
        return new File(parent, cleanStem(stripForbidden(romName), source)).getPath();
    }

    /** Removes characters a file name cannot have (Windows-forbidden ones, plus the path separators). */
    private static String stripForbidden(String name) {
        String result = name == null ? "" : name;
        for (char c : (FORBIDDEN + "/\\").toCharArray()) {
            result = result.replace(String.valueOf(c), "");
        }
        return result;
    }

    /** Same file or folder, ignoring case (Windows and macOS file systems do not tell "A.gba" from "a.gba"). */
    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().toString().equalsIgnoreCase(b.getCanonicalFile().toString());
        } catch (IOException e) {
            return a.getAbsoluteFile().toString().equalsIgnoreCase(b.getAbsoluteFile().toString());
        }
    }
}
