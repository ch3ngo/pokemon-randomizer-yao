package com.uprfvx.random.cli;

import com.uprfvx.random.GameRandomizer;
import com.uprfvx.random.Settings;
import com.uprfvx.random.yao.CodePatchDisclaimer;
import com.uprfvx.random.yao.EditArguments;
import com.uprfvx.random.yao.ShopItemArguments;
import com.uprfvx.random.yao.guide.GuideDisclaimer;
import com.uprfvx.random.yao.guide.HtmlGuideWriter;
import com.uprfvx.random.yao.YaoSettings;
import com.uprfvx.romio.romhandlers.FeatureAvailability;
import com.uprfvx.romio.romhandlers.RomIntegrityReport;
import com.uprfvx.random.customnames.CustomNamesSet;
import com.uprfvx.random.gui.CPGSelection;
import com.uprfvx.romio.gamedata.PlayerCharacterType;
import com.uprfvx.romio.graphics.packs.CustomPlayerGraphics;
import com.uprfvx.romio.graphics.packs.GraphicsPack;
import com.uprfvx.romio.romhandlers.Abstract3DSRomHandler;
import com.uprfvx.romio.romhandlers.AbstractDSRomHandler;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romio.RomOpener;
import filefunctions.FileNameFunctions;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

public class CliRandomizer {

    // TODO: Why is this class fully static? It gives bad vibes since it's also in Java. Is it really fine like this?

    private final static ResourceBundle bundle = java.util.ResourceBundle.getBundle("com/uprfvx/random/gui/Bundle");

    private final static RomOpener romOpener = new RomOpener();

    // Modified from FVX (10/10/2026): the CLI never told the ROM opener that extra memory was available, so 3DS
    // games could not be opened from the command line at all (the GUI sets this when started through its
    // launcher). The usage text says to pass -Xmx4096M, so check what the JVM was really given.
    private final static long MIN_MEMORY_FOR_3DS = 3_800_000_000L;

    static {
        romOpener.setExtraMemoryAvailable(Runtime.getRuntime().maxMemory() >= MIN_MEMORY_FOR_3DS);
    }

    private static boolean performDirectRandomization(String sourceRomFilePath, String destinationRomFilePath,
                                                      Settings settings, long seed,
                                                      String cpgName, PlayerCharacterType cpgType,
                                                      boolean saveAsDirectory,
                                                      String updateFilePath, boolean saveLog,
                                                      List<String> shopItemArguments, String htmlGuidePath,
                                                      List<String> protectShopItemArguments,
                                                      String boostFloorArgument,
                                                      String boostPercentArgument,
                                                      List<String> evolutionArguments,
                                                      List<String> removeEvolutionArguments,
                                                      String evolutionScaleArgument,
                                                      List<String> statEditArguments) {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream log;
        try {
            log = new PrintStream(baos, false, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log = new PrintStream(baos);
        }

        final PrintStream verboseLog = log;

        try {
            File romFile = new File(sourceRomFilePath);

            RomOpener.Results results = romOpener.openRomFile(romFile);
            if (results.wasOpeningSuccessful()) {
                RomHandler romHandler = results.getRomHandler();
                CustomPlayerGraphics cpg = prepareCPG(romHandler, cpgName, cpgType);

                // Modified from YAO (10/10/2026): options that patch the game's code need the notice to be accepted,
                // and are only offered when this game can take them.
                YaoSettings yaoSettings = settings.getYaoSettings();
                if (yaoSettings.needsCodePatchConsent()) {
                    boolean anyAvailable = false;
                    if (yaoSettings.getShinyChancePermille() > 0) {
                        FeatureAvailability shiny = romHandler.checkShinyChance();
                        anyAvailable |= shiny.available();
                        if (!shiny.available()) {
                            printWarning("--shiny-chance cannot be used with this game: " + shiny.reason());
                        }
                    }
                    if (yaoSettings.getLockeMode() != YaoSettings.LockeMode.NONE) {
                        FeatureAvailability centers = romHandler.checkCentersSkipFainted();
                        anyAvailable |= centers.available();
                        if (!centers.available()) {
                            printWarning("Pokemon Centers that do not revive fainted Pokemon cannot be used with this "
                                    + "game: " + centers.reason());
                        }
                    }
                    if (anyAvailable && !yaoSettings.isCodePatchesAccepted()) {
                        printWarning(CodePatchDisclaimer.forDefaultLocale());
                        printWarning("These code patches will NOT be applied. To accept and apply them, run again with "
                                + "--accept-code-patches.");
                    }
                }

                // Modified from YAO (10/10/2026): say what is different about a modified ROM and what it may affect.
                RomIntegrityReport integrityReport = romHandler.getIntegrityReport();
                if (!integrityReport.isClean()) {
                    printWarning(integrityReport.describe());
                }

                // Modified from YAO (10/10/2026): --shop-item arguments are resolved against this game's items.
                // Modified from YAO (10/10/2026): stat floor and custom evolutions, resolved against this game's names.
                java.util.List<com.uprfvx.romio.gamedata.Species> speciesList =
                        new java.util.ArrayList<>(romHandler.getSpeciesSetInclFormes());
                if (boostFloorArgument != null) {
                    EditArguments.applyFloor(settings.getYaoSettings(), boostFloorArgument);
                }
                if (boostPercentArgument != null) {
                    EditArguments.applyPercent(settings.getYaoSettings(), boostPercentArgument);
                }
                if (!removeEvolutionArguments.isEmpty()) {
                    EditArguments.applyRemovals(settings.getYaoSettings(), speciesList, removeEvolutionArguments);
                }
                if (!evolutionArguments.isEmpty()) {
                    EditArguments.applyEdits(settings.getYaoSettings(), speciesList, romHandler.getItems(),
                            romHandler.getMoves(), evolutionArguments);
                }
                if (evolutionScaleArgument != null) {
                    EditArguments.applyScale(settings.getYaoSettings(), evolutionScaleArgument);
                }
                if (!statEditArguments.isEmpty()) {
                    EditArguments.applyStatEdits(settings.getYaoSettings(), speciesList, statEditArguments);
                }
                if (!protectShopItemArguments.isEmpty()) {
                    ShopItemArguments.applyProtected(settings.getYaoSettings(), romHandler.getItems(),
                            protectShopItemArguments);
                }
                if (!shopItemArguments.isEmpty()) {
                    ShopItemArguments.apply(settings.getYaoSettings(), romHandler.getItems(),
                            romHandler.getShopPrices(), shopItemArguments);
                }

                if (updateFilePath != null && (romHandler.generationOfPokemon() == 6 || romHandler.generationOfPokemon() == 7)) {
                    romHandler.loadGameUpdate(updateFilePath);
                    if (!saveAsDirectory) {
                        printWarning("Forcing save as directory since a game update was supplied.");
                    }
                    saveAsDirectory = true;
                }
                if (saveAsDirectory && romHandler.generationOfPokemon() != 6 && romHandler.generationOfPokemon() != 7) {
                    saveAsDirectory = false;
                    printWarning("Saving as directory does not make sense for non-3DS games, ignoring \"-d\" flag...");
                }

                CliRandomizer.displaySettingsWarnings(settings, romHandler);

                File fh = new File(destinationRomFilePath);
                if (!saveAsDirectory) {
                    List<String> extensions = new ArrayList<>(Arrays.asList("sgb", "gbc", "gba", "nds", "cxi"));
                    extensions.remove(romHandler.getDefaultExtension());

                    fh = FileNameFunctions.fixFilename(fh, romHandler.getDefaultExtension(), extensions);
                    if (romHandler instanceof AbstractDSRomHandler || romHandler instanceof Abstract3DSRomHandler) {
                        String currentFN = romHandler.loadedFilename();
                        if (currentFN.equals(fh.getAbsolutePath())) {
                            printError(bundle.getString("GUI.cantOverwriteDS"));
                            return false;
                        }
                    }
                }

                String filename = fh.getAbsolutePath();

                GameRandomizer randomizer = new GameRandomizer(settings, cpg, romHandler, bundle, saveAsDirectory);
                // Modified from FVX (10/10/2026): the CLI used to ignore the result and always
                // report success, even when randomization threw and no ROM was written.
                GameRandomizer.Results randomizationResults = randomizer.randomize(filename, verboseLog, seed);
                verboseLog.close();
                if (!randomizationResults.wasSaveSuccessful()) {
                    Exception failure = randomizationResults.getException();
                    printError("Randomization failed: " + failure);
                    failure.printStackTrace();
                    return false;
                }
                // Modified from FVX (12/10/2026): a failure while writing the log used to be swallowed, leaving a log
                // that stops in the middle with nobody told.
                if (!randomizationResults.wasLogSuccessful()) {
                    printWarning("The log is incomplete, writing it failed: " + randomizationResults.getLogException());
                }
                byte[] out = baos.toByteArray();
                if (saveLog) {
                    try {
                        FileOutputStream fos = new FileOutputStream(filename + ".log");
                        fos.write(0xEF);
                        fos.write(0xBB);
                        fos.write(0xBF);
                        fos.write(out);
                        fos.close();
                    } catch (IOException e) {
                        printWarning("Could not write log.");
                    }
                }
                writeHtmlGuide(htmlGuidePath, romFile, romHandler, settings, seed, new String(out, java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("Randomized successfully!");
                // this is the only successful exit, everything else will return false at the end of the function
                return true;

            } else {
                printError("Could not load " + romFile.getAbsolutePath() + "; " + results.getFailType());
                if (results.getFailType() == RomOpener.FailType.EXTRA_MEMORY_NOT_AVAILABLE) {
                    printError("3DS games need more memory. Run again with -Xmx4096M or more, for example: "
                            + "java -Xmx6g -jar PokemonRandomizerYAO.jar cli ...");
                }
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Modified from YAO (10/10/2026): the optional HTML guide. Only written when asked for with --html-guide,
     * after showing the notice that it reveals the game. A problem here never fails the randomization.
     */
    private static void writeHtmlGuide(String path, File sourceRom, RomHandler randomized, Settings settings, long seed,
                                       String logText) {
        if (path == null) {
            return;
        }
        printWarning(GuideDisclaimer.forDefaultLocale());
        try {
            RomOpener.Results original = romOpener.openRomFile(sourceRom);
            if (!original.wasOpeningSuccessful()) {
                printWarning("The guide could not be written: the original ROM could not be reopened.");
                return;
            }
            List<String> notes = new ArrayList<>(settings.getYaoSettings().getRunNotes());
            notes.addAll(settings.getYaoSettings().getWarnings());
            // Modified from YAO (12/10/2026): read the wild data the way the randomization did (see GuideDataCollector)
            // Modified for YAO (12/10/2026): the page also holds the log and the options that differ from the defaults.
            HtmlGuideWriter.Extras extras = new HtmlGuideWriter.Extras(logText,
                    com.uprfvx.random.web.SettingsSummary.nativeChanges(settings, randomized),
                    com.uprfvx.random.web.SettingsSummary.extras(settings.getYaoSettings(), randomized));
            HtmlGuideWriter.write(java.nio.file.Path.of(path), original.getRomHandler(), randomized, seed, notes,
                    settings.isUseTimeBasedEncounters(), extras);
            System.out.println("Guide written to " + path);
        } catch (Exception | StackOverflowError e) {
            printWarning("The guide could not be written: " + e);
        }
    }

    private static void displaySettingsWarnings(Settings settings, RomHandler romHandler) {
        Settings.TweakForROMFeedback feedback = settings.tweakForRom(romHandler);
        if (feedback.isChangedStarter() && settings.getStartersMod() == Settings.StartersMod.CUSTOM) {
            printWarning(bundle.getString("GUI.starterUnavailable"));
        }
        if (settings.isUpdatedFromOldVersion()) {
            printWarning(bundle.getString("GUI.settingsFileOlder"));
        }
        // Modified from YAO (10/10/2026): tell the user what was repaired or ignored in the YAO settings.
        settings.getYaoSettings().getWarnings().forEach(CliRandomizer::printWarning);
    }

    private static CustomPlayerGraphics prepareCPG(RomHandler romHandler, String name, PlayerCharacterType type) {
        if (name == null) {
            return null;
        }
        GraphicsPack pack = null;
        List<GraphicsPack> packs = CPGSelection.getAllCPGPacks(romHandler);
        for (GraphicsPack gp : packs) {
            if (gp.getName().equals(name)) {
                pack = gp;
                break;
            }
        }
        if (pack == null) {
            printWarning("Could not find CPG with the name \"" + name + "\". No CPG will be used.");
            return null;
        }

        if (!romHandler.hasMultiplePlayerCharacters() && type != PlayerCharacterType.PC1) {
            printWarning("PCG type \"" + type + "\" is not valid for the given ROM. Using "
                    + PlayerCharacterType.PC1 + " instead.");
            type = PlayerCharacterType.PC1;
        }
        return new CustomPlayerGraphics(pack, type);
    }

    /**
     * Modified from YAO (10/10/2026): writes the walking sprite sheets of the player in the game, as templates to draw
     * over, into a folder.
     */
    private static boolean exportPlayerSprites(String romPath, String folder) {
        try {
            RomOpener.Results results = romOpener.openRomFile(new File(romPath));
            if (!results.wasOpeningSuccessful()) {
                printError("Could not load " + romPath + "; " + results.getFailType());
                return false;
            }
            RomHandler rom = results.getRomHandler();
            FeatureAvailability available = rom.checkPlayerSprites();
            if (!available.available()) {
                printError("This game cannot have its player sprites replaced: " + available.reason());
                return false;
            }
            File dir = new File(folder);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                printError("Could not create the folder " + folder);
                return false;
            }
            for (boolean female : new boolean[]{false, true}) {
                java.awt.image.BufferedImage sheet = rom.exportPlayerSprites(female);
                if (sheet == null) {
                    printError("The sprite sheet could not be read.");
                    return false;
                }
                File out = new File(dir, female ? "player-girl.png" : "player-boy.png");
                javax.imageio.ImageIO.write(sheet, "png", out);
                System.out.println("Wrote " + out.getAbsolutePath());
            }
            java.nio.file.Files.writeString(new File(dir, "README.txt").toPath(),
                    "These are the walking sprites of the player in your game, exported by Pokemon Randomizer YAO.\n"
                            + "Each sheet is 8 frames per row, every frame 32 by 32 pixels, in the order the game stores them.\n"
                            + "Draw over them keeping the same size and the same frame positions, and use at most 15 colours plus\n"
                            + "transparency (more are reduced to the 15 most used). Save as PNG and give the file to the program\n"
                            + "with --player-sprite (boy) and --player-sprite-female (girl), or in the web interface.\n"
                            + "Some frames are shared by the game: if two shared frames are drawn differently, the first wins.\n"
                            + "Do not share these exported images: they are the game's own artwork.\n");
            return true;
        } catch (Exception e) {
            printError("The sprites could not be exported: " + e);
            return false;
        }
    }

    public static int invoke(String[] args) {
        String sourceRomFilePath = null;
        String outputRomFilePath = null;

        Settings settings;
        long seed = -1;
        String settingsFilePath = null;
        String settingsString = null;
        String seedString = null;

        String cpgName = null;
        String cpgTypeString = null;
        PlayerCharacterType cpgType = null;

        boolean saveAsDirectory = false;
        String updateFilePath = null;
        boolean saveLog = false;
        List<String> shopItemArguments = new ArrayList<>();
        String shinyChanceArgument = null;
        boolean acceptCodePatches = false;
        String htmlGuidePath = null;
        String lockeArgument = null;
        boolean keepBasicShopItems = false;
        List<String> protectShopItemArguments = new ArrayList<>();
        List<String> evolutionArguments = new ArrayList<>();
        List<String> removeEvolutionArguments = new ArrayList<>();
        String boostFloorArgument = null;
        String boostPercentArgument = null;
        String evolutionScaleArgument = null;
        List<String> statEditArguments = new ArrayList<>();
        String eggBoxArgument = null;
        String eggLegendaryArgument = null;
        String eggStagesArgument = null;
        boolean eggBasicOnly = false;
        String megaFinaleArgument = null;
        String playerSpriteArgument = null;
        String playerSpriteFemaleArgument = null;
        String exportSpritesFolder = null;
        boolean megaFinaleReplace = false;
        boolean megaFinaleEvilBosses = false;
        String megaFromLevelArgument = null;
        List<String> battleStyleRuleArguments = new ArrayList<>();
        String fullyEvolvedArgument = null;
        String trainerItemsArgument = null;
        boolean regularTrainerItems = false;
        boolean noIncrementalMovesets = false;
        boolean stoneEvolutionsAlsoLevel = false;
        boolean eggDuplicates = false;

        List<String> allowedFlags = Arrays.asList("-i", "-o", "-s", "-S", "-z", "-c", "-d", "-u", "-l", "-h", "--help",
                "--shop-item", "--shiny-chance", "--accept-code-patches", "--html-guide", "--locke",
                "--keep-basic-shop-items", "--protect-shop-item", "--boost-floor", "--boost-percent", "--evolution",
                "--remove-evolution", "--evolution-level-scale", "--stat-edit", "--egg-box", "--egg-legendary", "--egg-stages",
                "--egg-basic-only", "--egg-duplicates", "--mega-finale", "--mega-finale-replace",
                "--mega-finale-evil-bosses", "--mega-from-level", "--battle-style-rule",
                "--fully-evolved-by-level", "--trainer-items", "--regular-trainers-keep-items", "--stone-evolutions-level", "--no-incremental-movesets", "--player-sprite", "--player-sprite-female", "--export-player-sprites");
        for (int i = 0; i < args.length; i++) {
            if (allowedFlags.contains(args[i])) {
                switch (args[i]) {
                    case "-i":
                        sourceRomFilePath = args[i + 1];
                        break;
                    case "-o":
                        outputRomFilePath = args[i + 1];
                        break;
                    case "-s":
                        settingsFilePath = args[i + 1];
                        break;
                    case "-S":
                        settingsString = args[i + 1];
                        break;
                    case "-z":
                        seedString = args[i + 1];
                        break;
                    case "-c":
                        cpgName = args[i + 1];
                        cpgTypeString = args[i + 2];
                        break;
                    case "-d":
                        saveAsDirectory = true;
                        break;
                    case "-u":
                        updateFilePath = args[i + 1];
                        break;
                    case "-l":
                        saveLog = true;
                        break;
                    case "--evolution":
                    case "--remove-evolution":
                    case "--boost-floor":
                    case "--boost-percent":
                    case "--evolution-level-scale":
                        if (i + 1 < args.length) {
                            switch (args[i]) {
                                case "--evolution" -> evolutionArguments.add(args[i + 1]);
                                case "--remove-evolution" -> removeEvolutionArguments.add(args[i + 1]);
                                case "--boost-floor" -> boostFloorArgument = args[i + 1];
                                case "--boost-percent" -> boostPercentArgument = args[i + 1];
                                default -> evolutionScaleArgument = args[i + 1];
                            }
                        } else {
                            printWarning(args[i] + " needs a value; ignored.");
                        }
                        break;
                    case "--keep-basic-shop-items":
                        keepBasicShopItems = true;
                        break;
                    // Added (13/10/2026): trainer battle item rules and incremental trainer movesets.
                    case "--trainer-items":
                        if (i + 1 < args.length) {
                            trainerItemsArgument = args[i + 1];
                        } else {
                            printWarning("--trainer-items needs tiered or unchanged; ignored.");
                        }
                        break;
                    case "--regular-trainers-keep-items":
                        regularTrainerItems = true;
                        break;
                    case "--no-incremental-movesets":
                        noIncrementalMovesets = true;
                        break;
                    case "--protect-shop-item":
                        if (i + 1 < args.length) {
                            protectShopItemArguments.add(args[i + 1]);
                        } else {
                            printWarning("--protect-shop-item needs an item; ignored.");
                        }
                        break;
                    case "--stat-edit":
                        if (i + 1 < args.length) {
                            statEditArguments.add(args[i + 1]);
                        } else {
                            printWarning("--stat-edit needs a value like \"Magikarp:quick:25\"; ignored.");
                        }
                        break;
                    case "--egg-box":
                        if (i + 1 < args.length) {
                            eggBoxArgument = args[i + 1];
                        } else {
                            printWarning("--egg-box needs a number of eggs; ignored.");
                        }
                        break;
                    case "--egg-legendary":
                        if (i + 1 < args.length) {
                            eggLegendaryArgument = args[i + 1];
                        } else {
                            printWarning("--egg-legendary needs any, none or only; ignored.");
                        }
                        break;
                    case "--egg-stages":
                        if (i + 1 < args.length) {
                            eggStagesArgument = args[i + 1];
                        } else {
                            printWarning("--egg-stages needs a list such as 1,2,3; ignored.");
                        }
                        break;
                    case "--player-sprite":
                        playerSpriteArgument = i + 1 < args.length ? args[i + 1] : null;
                        break;
                    case "--player-sprite-female":
                        playerSpriteFemaleArgument = i + 1 < args.length ? args[i + 1] : null;
                        break;
                    case "--export-player-sprites":
                        exportSpritesFolder = i + 1 < args.length ? args[i + 1] : null;
                        break;
                    case "--mega-finale":
                        if (i + 1 < args.length) {
                            megaFinaleArgument = args[i + 1];
                        } else {
                            printWarning("--mega-finale needs none, leaders, boss, all or a list of tiers; ignored.");
                        }
                        break;
                    // Added (12/10/2026): the mega finale start level, battle style rules and the independent
                    // "fully evolved by level".
                    case "--stone-evolutions-level":
                        stoneEvolutionsAlsoLevel = true;
                        break;
                    case "--mega-from-level":
                    case "--battle-style-rule":
                    case "--fully-evolved-by-level":
                        if (i + 1 < args.length) {
                            switch (args[i]) {
                                case "--mega-from-level" -> megaFromLevelArgument = args[i + 1];
                                case "--battle-style-rule" -> battleStyleRuleArguments.add(args[i + 1]);
                                default -> fullyEvolvedArgument = args[i + 1];
                            }
                        } else {
                            printWarning(args[i] + " needs a value; ignored.");
                        }
                        break;
                    case "--mega-finale-replace":
                        megaFinaleReplace = true;
                        break;
                    case "--mega-finale-evil-bosses":
                        megaFinaleEvilBosses = true;
                        break;
                    case "--egg-basic-only":
                        eggBasicOnly = true;
                        break;
                    case "--egg-duplicates":
                        eggDuplicates = true;
                        break;
                    case "--locke":
                        if (i + 1 < args.length) {
                            lockeArgument = args[i + 1];
                        } else {
                            printWarning("--locke needs none, soft or full; ignored.");
                        }
                        break;
                    case "--html-guide":
                        if (i + 1 < args.length) {
                            htmlGuidePath = args[i + 1];
                        } else {
                            printWarning("--html-guide needs a file path; ignored.");
                        }
                        break;
                    case "--accept-code-patches":
                        acceptCodePatches = true;
                        break;
                    case "--shiny-chance":
                        if (i + 1 < args.length) {
                            shinyChanceArgument = args[i + 1];
                        } else {
                            printWarning("--shiny-chance needs a number from 0 to 100; ignored.");
                        }
                        break;
                    case "--shop-item":
                        if (i + 1 < args.length) {
                            shopItemArguments.add(args[i + 1]);
                        } else {
                            printWarning("--shop-item needs a value like \"Rare Candy:1\"; ignored.");
                        }
                        break;
                    case "-h":
                    case "--help":
                        printUsage();
                        return 0;
                    default:
                        break;
                }
            }
        }

        if (exportSpritesFolder != null && sourceRomFilePath != null) {
            return exportPlayerSprites(sourceRomFilePath, exportSpritesFolder) ? 0 : 1;
        }
        if (sourceRomFilePath == null) {
            return usageError("Missing required argument: -i (source ROM path)");
        }

        if (outputRomFilePath == null) {
            return usageError("Missing required argument: -o (path for output ROM)");
        }

        // check that everything is readable/writable as appropriate
        if (!new File(sourceRomFilePath).exists()) {
            return usageError("Could not read source ROM file");
        }

        // java will return false for a non-existent file, have to check the parent directory
        if (!new File(outputRomFilePath).getAbsoluteFile().getParentFile().canWrite()) {
            return usageError("Destination ROM path not writable");
        }

        if (settingsFilePath == null && settingsString == null) {
            return usageError("Missing settings argument");
        }
        if (settingsFilePath != null) {
            if (settingsString != null) {
                printWarning("Both settings file path (-s) and settings string (-S) were given. " +
                        "The settings file path will be used.");
            }
            if (!new File(settingsFilePath).exists()) {
                return usageError("Could not read settings file");
            }
            try {
                File fh = new File(settingsFilePath);
                FileInputStream fis = new FileInputStream(fh);
                settings = Settings.readFromFileFormat(fis);
                fis.close();
            } catch (Exception e) {
                e.printStackTrace(System.out);
                return usageError("Invalid settings file.");
            }
        } else {
            try {
                settings = Settings.fromString(settingsString);
            } catch (IllegalArgumentException e) {
                e.printStackTrace(System.out);
                return usageError("Invalid settings string.");
            }
        }

        // Modified from YAO (10/10/2026): an unreadable value is ignored with a warning, out of range is clamped.
        if (shinyChanceArgument != null) {
            try {
                // a decimal comma is accepted too, for users whose keyboards and locales use one
                settings.getYaoSettings().setShinyChancePercent(
                        Double.parseDouble(shinyChanceArgument.strip().replace(',', '.')));
            } catch (NumberFormatException e) {
                printWarning("--shiny-chance \"" + shinyChanceArgument + "\" is not a number; ignored.");
            }
        }

        if (lockeArgument != null) {
            YaoSettings.LockeMode mode = YaoSettings.parseLocke(lockeArgument);
            if (mode == null) {
                printWarning("--locke \"" + lockeArgument + "\" is not none, soft or full; ignored.");
            } else {
                settings.getYaoSettings().setLockeMode(mode);
            }
        }
        if (eggBoxArgument != null) {
            try {
                settings.getYaoSettings().setEggBoxCount(Integer.parseInt(eggBoxArgument.strip()));
            } catch (NumberFormatException e) {
                printWarning("--egg-box \"" + eggBoxArgument + "\" is not a whole number; ignored.");
            }
        }
        if (eggLegendaryArgument != null) {
            switch (eggLegendaryArgument.strip().toLowerCase(java.util.Locale.ROOT)) {
                case "any" -> settings.getYaoSettings().setEggBoxLegendary(YaoSettings.EggLegendary.ANY);
                case "none" -> settings.getYaoSettings().setEggBoxLegendary(YaoSettings.EggLegendary.NONE);
                case "only" -> settings.getYaoSettings().setEggBoxLegendary(YaoSettings.EggLegendary.ONLY);
                default -> printWarning("--egg-legendary \"" + eggLegendaryArgument + "\" is not any, none or only; ignored.");
            }
        }
        if (eggStagesArgument != null) {
            List<Integer> stages = new ArrayList<>();
            for (String part : eggStagesArgument.split(",")) {
                try {
                    stages.add(Integer.parseInt(part.strip()));
                } catch (NumberFormatException e) {
                    printWarning("--egg-stages: \"" + part + "\" is not a number; ignored.");
                }
            }
            settings.getYaoSettings().setEggBoxStages(stages);
        }
        if (playerSpriteArgument != null || playerSpriteFemaleArgument != null) {
            settings.getYaoSettings().setPlayerSprites(playerSpriteArgument, playerSpriteFemaleArgument);
        }
        if (megaFinaleArgument != null || megaFinaleEvilBosses || megaFromLevelArgument != null) {
            YaoSettings yao = settings.getYaoSettings();
            boolean gym = yao.isMegaGymLeaders(), league = yao.isMegaLeague(), evil = yao.isMegaEvilBosses();
            boolean other = yao.isMegaOtherBosses(), everyone = yao.isMegaEveryone();
            int fromLevel = yao.getMegaFromLevel();
            if (megaFinaleArgument != null) {
                // a list of tiers (gym, league, evil, other, everyone), or the older words none/leaders/boss/all
                gym = league = evil = other = everyone = false;
                for (String part : megaFinaleArgument.split(",")) {
                    switch (part.strip().toLowerCase(java.util.Locale.ROOT)) {
                        case "none", "off", "" -> {
                        }
                        case "gym", "gymleaders", "gym-leaders" -> gym = true;
                        case "league", "elite", "elitefour" -> league = true;
                        case "evil", "evilbosses", "evil-bosses" -> evil = true;
                        case "other", "otherbosses", "other-bosses" -> other = true;
                        case "everyone", "all" -> everyone = true;
                        case "leaders", "leaders_and_league" -> {
                            gym = true;
                            league = true;
                        }
                        case "boss", "bosses" -> {
                            gym = true;
                            league = true;
                            evil = true;
                        }
                        default -> printWarning("--mega-finale: \"" + part + "\" is not none, gym, league, evil, other, "
                                + "everyone, leaders, boss or all; ignored.");
                    }
                }
            }
            if (megaFinaleEvilBosses) {
                evil = true;
            }
            if (megaFromLevelArgument != null) {
                try {
                    fromLevel = Integer.parseInt(megaFromLevelArgument.strip());
                } catch (NumberFormatException e) {
                    printWarning("--mega-from-level \"" + megaFromLevelArgument + "\" is not a number; ignored.");
                }
            }
            yao.setMegaFinaleTiers(gym, league, evil, other, everyone, fromLevel);
        }
        if (megaFinaleReplace) {
            settings.getYaoSettings().setMegaFinaleReplace(true);
        }
        for (String rule : battleStyleRuleArguments) {
            String[] parts = rule.split(":");
            com.uprfvx.random.yao.TrainerGroups.Group group = parts.length == 3
                    ? com.uprfvx.random.yao.TrainerGroups.Group.parse(parts[0]) : null;
            YaoSettings.RuleStyle style = parts.length == 3 ? YaoSettings.parseRuleStyle(parts[1]) : null;
            Integer chance = null;
            if (parts.length == 3) {
                try {
                    chance = Integer.parseInt(parts[2].strip());
                } catch (NumberFormatException ignored) {
                    // reported below
                }
            }
            if (group == null || style == null || chance == null) {
                printWarning("--battle-style-rule \"" + rule + "\" is not <group>:<style>:<chance>, for example "
                        + "gymLeaders:double:10; ignored.");
            } else {
                settings.getYaoSettings().addBattleStyleRule(group, style, chance);
            }
        }
        if (stoneEvolutionsAlsoLevel) {
            settings.getYaoSettings().setStoneEvolutionsAlsoLevel(true);
        }
        if (fullyEvolvedArgument != null) {
            try {
                settings.getYaoSettings().setFullyEvolvedByLevel(Integer.parseInt(fullyEvolvedArgument.strip()));
            } catch (NumberFormatException e) {
                printWarning("--fully-evolved-by-level \"" + fullyEvolvedArgument + "\" is not a number; ignored.");
            }
        }
        if (trainerItemsArgument != null) {
            settings.getYaoSettings().setTrainerItemMode(trainerItemsArgument);
        }
        if (regularTrainerItems) {
            settings.getYaoSettings().setRegularTrainersKeepItems(true);
        }
        if (noIncrementalMovesets) {
            settings.getYaoSettings().setIncrementalMovesets(false);
        }
        if (eggBasicOnly) {
            settings.getYaoSettings().setEggBoxBasicOnly(true);
        }
        if (eggDuplicates) {
            settings.getYaoSettings().setEggBoxDuplicates(true);
        }
        if (keepBasicShopItems) {
            settings.getYaoSettings().setKeepBasicShopItems(true);
        }
        settings.getYaoSettings().setCodePatchesAccepted(acceptCodePatches);

        if (seedString != null) {
            try {
                seed = Long.parseLong(seedString);
            } catch (NumberFormatException e) {
                return usageError("Invalid seed - could not parse as long");
            }
        } else {
            // Modified from FVX (12/10/2026): with no -z the seed stayed at its -1 placeholder, so every run of the
            // command line (and of the web interface, which uses it) with the same settings produced the same game.
            seed = com.uprfvx.random.random.SeedPicker.pickSeed();
        }

        if (cpgName != null) {
            try {
                cpgType = PlayerCharacterType.valueOf(cpgTypeString);
            } catch (IllegalArgumentException e) {
                return usageError("Invalid CPG type");
            }
        }

        boolean processResult = CliRandomizer.performDirectRandomization(
                sourceRomFilePath,
                outputRomFilePath,
                settings,
                seed,
                cpgName, cpgType,
                saveAsDirectory,
                updateFilePath,
                saveLog,
                shopItemArguments,
                htmlGuidePath,
                protectShopItemArguments,
                boostFloorArgument, boostPercentArgument,
                evolutionArguments, removeEvolutionArguments, evolutionScaleArgument,
                statEditArguments
        );
        if (!processResult) {
            return usageError("Randomization failed");
        }
        return 0;
    }

    private static int usageError(String text) {
        printError(text);
        printUsage();
        return 1;
    }

    private static void printError(String text) {
        System.out.println("ERROR: " + text);
    }

    private static void printWarning(String text) {
        System.out.println("WARNING: " + text);
    }

    private static void printUsage() {
        System.out.println("Usage: java [-Xmx4096M] -jar PokemonRandomizerYAO.jar cli -i <path to source ROM> -o <path for output ROM>\n" +
                           "       {-s <path to settings file> | -S <settings string> } [options]");
        System.out.println("Optional flags: ");
        System.out.println("-Xmx4096M          : Increase the amount of RAM available to Java. Required for 3DS games.");
        System.out.println("-z <seed>          : Use the given seed.");
        System.out.println("-c <name> <type>   : Use a Custom Player Graphics. \"name\" must match a CPG defined in the\n" +
                           "                     data folder. \"type\" denotes which player character will be replaced,\n" +
                           "                     and must be either PC1 or PC2.");
        System.out.println("-d                 : Save 3DS game as directory (LayeredFS).");
        System.out.println("-u <path to update>: Apply the given 3DS game update before randomization.");
        System.out.println("-l                 : Generate a detailed log file.");
        System.out.println("--boost-floor <bst>\n" +
                           "                   : Raise every fully evolved Pokemon below this base stat total up to it (for\n" +
                           "                     example 300), spread over its stats as they are. To raise or lower one chosen\n" +
                           "                     Pokemon's stats instead, use --stat-edit.");
        System.out.println("--boost-percent <0-100>\n" +
                           "                   : Needs --boost-floor. Instead of lifting the weak, fully evolved Pokemon (below the\n" +
                           "                     floor) up to it, boost each by this percent of its own total.");
        System.out.println("--evolution <from>:<to>:<method>[:<number>]\n" +
                           "                   : Make <from> evolve into <to>, replacing how it evolves now or adding it. Methods:\n" +
                           "                     level, stone (item), trade, trade-item, happiness and the other FVX evolution\n" +
                           "                     types. <number> is the level, or the item or move (name or number). Example:\n" +
                           "                     --evolution \"Eevee:Umbreon:stone:Moon Stone\". Can be repeated. Unsafe edits\n" +
                           "                     (overlaps, loops, too many evolutions) are skipped with a note.");
        System.out.println("--remove-evolution <from>:<to>\n" +
                           "                   : Take away that evolution. Can be repeated.");
        System.out.println("--evolution-level-scale <minLevel>:<percent>\n" +
                           "                   : Lower every level-up evolution of <minLevel> or more by <percent> (for example\n" +
                           "                     50:20). Evolutions you set yourself are never scaled.");
        System.out.println("--stat-edit <pokemon>:quick:<percent>\n" +
                           "                   : Raise or lower this Pokemon's whole stat spread by a percentage (-90 to 300),\n" +
                           "                     keeping its shape. This is the way to boost (or weaken) one chosen Pokemon's\n" +
                           "                     stats; for an automatic floor across every fully evolved Pokemon instead, use\n" +
                           "                     --boost-floor. Name in the ROM's language or number.\n" +
                           "--stat-edit <pokemon>:fine:<hp>:<atk>:<def>:<spa>:<spd>:<spe>\n" +
                           "                   : Set this Pokemon's six base stats to exact values, for example\n" +
                           "                     \"Magikarp:fine:20:10:55:15:20:80\". Each value is clamped to the real minimum\n" +
                           "                     and maximum any Pokemon in the loaded game has for that stat. Either form of\n" +
                           "                     --stat-edit can be repeated (one per Pokemon); runs after --boost-floor and\n" +
                           "                     after any whole-dex base stat randomization, so it has the final say for that\n" +
                           "                     Pokemon.");
        System.out.println("--keep-basic-shop-items\n" +
                           "                   : When shops are randomized or shuffled, keep Poke Balls, potions, status heals,\n" +
                           "                     repels and Escape Rope exactly where they are.");
        System.out.println("--protect-shop-item <item>\n" +
                           "                   : Also keep this item (id or name in the ROM's language) in place when shops\n" +
                           "                     are randomized. Can be repeated.");
        System.out.println("--locke <none|soft|full>   (EXPERIMENTAL, beta: may not fully work, will be finished in the next release)\n" +
                           "                   : Challenge rules. soft: Pokemon Centers do not revive fainted Pokemon.\n" +
                           "                     full: also removes Revive, Max Revive, Revival Herb and Sacred Ash from shops,\n" +
                           "                     field items and pickup. The Pokemon Center part needs --accept-code-patches\n" +
                           "                     and is available for HeartGold, SoulSilver, Black 2 and White 2.");
        System.out.println("--player-sprite <png>, --player-sprite-female <png>\n" +
                           "                   : Replace the walking sprites of the player (HeartGold, SoulSilver, Black 2).\n" +
                           "--export-player-sprites <folder>\n" +
                           "                   : With -i only: write the game's own player sprite sheets as templates.");
        // "(EXPERIMENTAL)" tag removed and the game list corrected to include Sun/Moon/Ultra Sun/Ultra Moon
        // (modified, 11/10/2026): this is a finished, supported feature, and Gen 7 also has Mega Evolutions.
        System.out.println("--mega-finale <none|leaders|boss|all|list>\n" +
                           "                   : Or a comma separated list of tiers to combine: gym, league, evil, other,\n" +
                           "                     everyone (for example gym,league,evil). Same as below otherwise.\n" +
                           "                   : The last Pokemon of the chosen trainers holds a mega stone, so it is a Mega\n" +
                           "                     Evolution. \"leaders\" means true gym leaders, the real Elite Four, the\n" +
                           "                     Champion and Uber-tier trainers only; \"boss\" is every boss trainer (also\n" +
                           "                     includes evil-team bosses); \"all\" is every trainer. Only for games with Mega\n" +
                           "                     Evolutions (X, Y, Omega Ruby, Alpha Sapphire, Sun, Moon, Ultra Sun, Ultra\n" +
                           "                     Moon). A trainer that already has a Mega is left alone.\n" +
                           "--mega-finale-replace\n" +
                           "                   : Replace a last Pokemon that has no Mega by one that has.\n" +
                           "--mega-finale-evil-bosses\n" +
                           "                   : With --mega-finale leaders or boss, also include evil-team leader trainers\n" +
                           "                     (Team Rocket/Magma/Aqua/Galactic/Plasma/Flare/Skull+Aether). No effect with\n" +
                           "                     \"all\" (already included) or \"none\".");
        System.out.println("--mega-from-level <n>\n" +
                           "                   : Only trainers whose strongest Pokemon is at least this level get the mega\n" +
                           "                     finale (0 = from the start).\n" +
                           "--battle-style-rule <group>:<style>:<chance>\n" +
                           "                   : Each trainer of the group (gymLeaders, league, evilBosses, otherBosses,\n" +
                           "                     everyone) has this percent chance (1 to 100) to fight a double, triple or\n" +
                           "                     rotation battle, for example gymLeaders:double:10. Can be repeated (up to 8;\n" +
                           "                     the first matching rule that succeeds wins). Runs after the battle style\n" +
                           "                     setting. Gen 3 to 7 (triple and rotation: Gen 5 and 6 only).\n" +
                           "--trainer-items <tiered|unchanged>\n" +
                           "                   : tiered (default) limits the battle items trainers carry: ordinary trainers\n" +
                           "                     none, gym leaders only the potion that fits the stage of the game, Full\n" +
                           "                     Restore only for the Elite Four (from level 35) and other bosses (from 45).\n" +
                           "                     Revives are removed. Gen 3 to 7. unchanged leaves the game's own items.\n" +
                           "--regular-trainers-keep-items\n" +
                           "                   : Ordinary trainers keep Potion, Super Potion and cheap cures.\n" +
                           "--no-incremental-movesets\n" +
                           "                   : Better movesets may pick the strongest moves at any level (old behaviour).\n" +
                           "--stone-evolutions-level\n" +
                           "                   : Pokemon with exactly one evolution, and that one by stone, can also evolve by\n" +
                           "                     leveling (Gen 3 to 7). Pokemon with several evolutions are left alone.\n" +
                           "--fully-evolved-by-level <n>\n" +
                           "                   : Bring level evolutions down so every Pokemon is fully evolved by this level\n" +
                           "                     (15 to 100; 0 is off). Independent of Make Evolutions Easier.");
        System.out.println("--egg-box <count>\n" +
                           "                   : The game hands over this many randomized eggs (1 to 90; 0 is off), delivered to\n" +
                           "                     the party and then the PC. Available for Emerald (English) so far.\n" +
                           "--egg-legendary <any|none|only>   : Which eggs may be legendary Pokemon (default any).\n" +
                           "--egg-stages <list>               : Only evolution lines of these lengths, for example 3 or 2,3.\n" +
                           "--egg-basic-only                  : Only Pokemon that do not evolve from anything.\n" +
                           "--egg-duplicates                  : The same species may appear more than once.");
        System.out.println("--html-guide <file>\n" +
                           "                   : Also write a guide to the randomized game as one HTML file (English and\n" +
                           "                     Spanish, spoiler-safe). It reveals the whole game: it can be cheating if you\n" +
                           "                     compete against someone.");
        System.out.println("--accept-code-patches\n" +
                           "                   : Accept the notice that some options (the shiny chance) patch the game's\n" +
                           "                     program code. Without it those options are shown the notice and skipped.");
        System.out.println("--shiny-chance <0-100>\n" +
                           "                   : Make this percentage of all Pokemon shiny, in steps of 0.1 (for example 12.5\n" +
                           "                     or 0,1). Gen 4 so far. 100 makes every\n" +
                           "                     Pokemon shiny, including trainers' Pokemon. 0 leaves the game unchanged.");
        System.out.println("--shop-item <item>:<price>\n" +
                           "                   : Sell an extra item in every regular shop at the given price (e.g. add Rare\n" +
                           "                     Candy at a cheap price). <item> is an item id or its name in the ROM's\n" +
                           "                     language. Can be repeated. Prices the game cannot store are adjusted (Gen 5\n" +
                           "                     to 7 use multiples of 10).");
        System.out.println("-h --help          : Print usage/help info.");
    }
}
