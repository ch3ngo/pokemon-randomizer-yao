package com.uprfvx.random;

/*----------------------------------------------------------------------------*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.random.log.RandomizationLogger;
import com.uprfvx.random.random.RandomSource;
import com.uprfvx.random.random.SeedPicker;
import com.uprfvx.random.randomizers.*;
import com.uprfvx.random.updaters.MoveUpdater;
import com.uprfvx.random.updaters.SpeciesBaseStatUpdater;
import com.uprfvx.random.updaters.TypeEffectivenessUpdater;
import com.uprfvx.random.updaters.Updater;
import com.uprfvx.romio.MiscTweak;
import com.uprfvx.romio.graphics.packs.CustomPlayerGraphics;
import com.uprfvx.romio.romhandlers.Gen1RomHandler;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ResourceBundle;

/**
 * Coordinates the randomization of a game, via a {@link RomHandler}, and various sub-{@link Randomizer}s,
 * and {@link Updater}s.<br>
 * Also passes the results to a {@link RandomizationLogger} and a {@link CheckValueCalculator} for
 * logging/check value calculation.
 * <br><br>
 * Output varies by seed.
 */
public class GameRandomizer {

    public static class Results {

        private Exception e;
        private Exception logE;
        private int checkValue;

        private Results() {}

        public boolean wasSaveSuccessful() {
            return e == null;
        }

        public Exception getException() {
            if (wasSaveSuccessful()) {
                throw new IllegalStateException("Randomization successful; no Exception to be gotten.");
            }
            return e;
        }

        public boolean wasLogSuccessful() {
            return logE == null;
        }

        public Exception getLogException() {
            if (wasLogSuccessful()) {
                throw new IllegalStateException("Logging successful; no Exception to be gotten.");
            }
            return logE;
        }

        public int getCheckValue() {
            return checkValue;
        }
    }

    private final RandomSource randomSource = new RandomSource();

    private final Settings settings;
    private final CustomPlayerGraphics customPlayerGraphics;
    private final RomHandler romHandler;
    private final boolean saveAsDirectory;

    private final RandomizationLogger logger;

    private final SpeciesBaseStatUpdater speciesBSUpdater;
    private final MoveUpdater moveUpdater;
    private final TypeEffectivenessUpdater typeEffUpdater;

    private final IntroPokemonRandomizer introPokeRandomizer;
    private final SpeciesBaseStatRandomizer speciesBSRandomizer;
    private final SpeciesTypeRandomizer speciesTypeRandomizer;
    private final SpeciesAbilityRandomizer speciesAbilityRandomizer;
    private final EvolutionRandomizer evoRandomizer;
    private final StarterRandomizer starterRandomizer;
    private final StaticPokemonRandomizer staticPokeRandomizer;
    private final TradeRandomizer tradeRandomizer;
    private final MoveDataRandomizer moveDataRandomizer;
    private final MoveNameRandomizer moveNameRandomizer;
    private final SpeciesMovesetRandomizer speciesMovesetRandomizer;
    private final TrainerPokemonRandomizer trainerPokeRandomizer;
    private final TrainerMovesetRandomizer trainerMovesetRandomizer;
    private final TrainerNameRandomizer trainerNameRandomizer;
    private final WildEncounterRandomizer wildEncounterRandomizer;
    private final EncounterHeldItemRandomizer encHeldItemRandomizer;
    private final TMTutorMoveRandomizer tmtMoveRandomizer;
    private final TMHMTutorCompatibilityRandomizer tmhmtCompRandomizer;
    private final ItemRandomizer itemRandomizer;
    private final TypeEffectivenessRandomizer typeEffRandomizer;
    private final PaletteRandomizer paletteRandomizer;
    private final MiscTweakRandomizer miscTweakRandomizer;

    public GameRandomizer(Settings settings, CustomPlayerGraphics customPlayerGraphics, RomHandler romHandler,
                          ResourceBundle bundle, boolean saveAsDirectory) {
        this.settings = settings;
        this.customPlayerGraphics = customPlayerGraphics;
        this.romHandler = romHandler;
        this.saveAsDirectory = saveAsDirectory;

        this.speciesBSUpdater = new SpeciesBaseStatUpdater(romHandler);
        this.moveUpdater = new MoveUpdater(romHandler);
        this.typeEffUpdater = new TypeEffectivenessUpdater(romHandler);

        this.introPokeRandomizer = new IntroPokemonRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.speciesBSRandomizer = romHandler.generationOfPokemon() == 1 ?
                new Gen1SpeciesBaseStatRandomizer(romHandler, settings, randomSource.getNonCosmetic()) :
                new SpeciesBaseStatRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.speciesTypeRandomizer = new SpeciesTypeRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.speciesAbilityRandomizer = new SpeciesAbilityRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.evoRandomizer = new EvolutionRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.starterRandomizer = new StarterRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.staticPokeRandomizer = new StaticPokemonRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.tradeRandomizer = new TradeRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.moveDataRandomizer = new MoveDataRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.moveNameRandomizer = new MoveNameRandomizer(romHandler, settings, randomSource.getCosmetic());
        this.speciesMovesetRandomizer = new SpeciesMovesetRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.trainerPokeRandomizer = new TrainerPokemonRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.trainerMovesetRandomizer = new TrainerMovesetRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.trainerNameRandomizer = new TrainerNameRandomizer(romHandler, settings, randomSource.getCosmetic());
        this.wildEncounterRandomizer = new WildEncounterRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.encHeldItemRandomizer = new EncounterHeldItemRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.tmtMoveRandomizer = new TMTutorMoveRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.tmhmtCompRandomizer = new TMHMTutorCompatibilityRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.itemRandomizer = new ItemRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        this.typeEffRandomizer = new TypeEffectivenessRandomizer(romHandler, settings, randomSource.getNonCosmetic());
        switch (romHandler.generationOfPokemon()) {
            case 1:
                this.paletteRandomizer = new Gen1PaletteRandomizer(romHandler, settings, randomSource.getCosmetic());
                break;
            case 2:
                this.paletteRandomizer = new Gen2PaletteRandomizer(romHandler, settings, randomSource.getCosmetic());
                break;
            case 3:
            case 4:
            case 5:
                this.paletteRandomizer = new Gen3to5PaletteRandomizer(romHandler, settings, randomSource.getCosmetic());
                break;
            default:
                this.paletteRandomizer = null;
        }
        this.miscTweakRandomizer = new MiscTweakRandomizer(romHandler, settings, randomSource.getNonCosmetic());

        this.logger = new RandomizationLogger(randomSource, settings, romHandler, bundle,
                speciesBSUpdater, moveUpdater, typeEffUpdater,
                introPokeRandomizer, speciesBSRandomizer, speciesTypeRandomizer, speciesAbilityRandomizer,
                evoRandomizer, starterRandomizer, staticPokeRandomizer, tradeRandomizer, moveDataRandomizer, moveNameRandomizer,
                speciesMovesetRandomizer, trainerPokeRandomizer, trainerMovesetRandomizer, trainerNameRandomizer,
                wildEncounterRandomizer, encHeldItemRandomizer, tmtMoveRandomizer, tmhmtCompRandomizer, itemRandomizer,
                typeEffRandomizer, paletteRandomizer, miscTweakRandomizer);
    }

    public Results randomize(final String filename) {
        return randomize(filename, new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
            }
        }));
    }

    public Results randomize(final String filename, final PrintStream log) {
        long seed = SeedPicker.pickSeed();
        // long seed = 123456789;    // TESTING
        return randomize(filename, log, seed);
    }

    public Results randomize(final String filename, final PrintStream log, long seed) {
        Results results = new Results();
        try {
            final long startTime = System.currentTimeMillis();
            randomSource.seed(seed);

            setupSpeciesRestrictions();
            applyUpdaters();
            applyRandomizers();
            maybeSetCustomPlayerGraphics();

            results.checkValue = new CheckValueCalculator(romHandler, settings).calculate();
            if (romHandler.shouldWriteCheckValue()) {
                romHandler.writeCheckValue(results.checkValue);
            }

            boolean couldSave = romHandler.saveRom(filename, seed, saveAsDirectory);

            try {
                logger.logResults(log, startTime);
            } catch (Exception e) {
                results.logE = e;
            }

            if (!couldSave) {
                results.e = new IOException("Could not save ROM, reason unknown.");
            }
        } catch (Exception e) {
            results.e = e;
        }

        return results;
    }

    private void setupSpeciesRestrictions() {
        romHandler.getRestrictedSpeciesService().setRestrictions(settings.getCurrentRestrictions());
        if (settings.isLimitPokemon()) {
            romHandler.removeEvosForPokemonPool();
        }
    }

    private void applyUpdaters() {
        if (settings.isUpdateTypeEffectiveness()) {
            typeEffUpdater.updateTypeEffectiveness();
        }
        if (settings.isUpdateMoves()) {
            moveUpdater.updateMoves(settings.getUpdateMovesToGeneration());
        }
        if (settings.isUpdateBaseStats()) {
            speciesBSUpdater.updateSpeciesStats(settings.getUpdateBaseStatsToGeneration());
        }
    }

    private void maybeSetCustomPlayerGraphics() {
        // This setting/feature sticks out for being atypical,
        // versus the rest of the randomizer.....
        // But if we consider the GameRandomizer to be
        // "the thing that does all the changes to the ROM, chosen through the UI",
        // then it makes sense that this should be here.
        if (customPlayerGraphics != null) {
            romHandler.setCustomPlayerGraphics(customPlayerGraphics);
        }
    }


    private void applyRandomizers() {

        maybeRandomizeTypeEffectiveness();

        maybeRandomizeMoveData();

        maybeApplyMiscTweaks();

        maybeStandardizeEXPCurves();

        // Applied before anything that can be carried up evolutions, so the new evos are used for that.
        maybeRandomizeEvolutions();

        // Modified from FVX (10/10/2026): the user's own evolution edits, applied right after the game's evolution
        // step so that everything that follows sees them.
        maybeEditEvolutionStructure();

        maybeRandomizeSpeciesBaseStatTotals();

        // Applied after both evo and BST randomization, so the right evos/BSTs are used.
        if (settings.isAdjustEvolutionLevels()) {
            evoRandomizer.adjustEvolutionLevels();
        }

        maybeRandomizeSpeciesTypes();
        maybeRandomizeWildHeldItems();
        maybeRandomizeSpeciesBaseStats();
        maybeApplyStatFloor();
        // Added for YAO (11/10/2026): runs after both whole-dex base stat randomization and the automatic stat
        // floor, so a manual per-species edit is always the final word for that species.
        maybeApplyStatEdits();
        maybeRandomizeSpeciesAbilities();

        maybeApplyEvolutionImprovements();
        maybeEditEvolutionLevels();

        // Applied after species types both some settings and the in-game strings should depend on the new types.
        maybeRandomizeStarters();

        maybeRandomizeMovesets();

        maybeRandomizeTMMoves();
        maybeRandomizeTMHMCompatibility();

        maybeRandomizeMoveTutorMoves();
        maybeRandomizeMoveTutorCompatibility();

        // Applied before trainer randomization so "trainers use local pokémon"
        // may be based on new "local pokémon".
        maybeRandomizeWildPokemon();

        maybeRandomizeTrainerPokemon();
        maybeRandomizeTrainerMovesets();
        maybeFixTrainerZCrystals();

        maybeRandomizeTrainerHeldItems();
        maybeApplyMegaFinale();
        maybeApplyTrainerBattleItemRules();
        maybeRandomizeTrainerNames();

        // Apply metronome only mode now that trainers have been dealt with
        if (settings.getMovesetsMod() == Settings.MovesetsMod.METRONOME_ONLY) {
            speciesMovesetRandomizer.metronomeOnlyMode();
        }

        maybeRandomizeStaticPokemon();
        maybeRandomizeTotemPokemon();

        maybeRandomizeInGameTrades();

        maybeRandomizeFieldItems();
        maybeRandomizeShops();
        maybeRandomizePickupItems();

        maybeRandomizePokemonPalettes();

        maybeRandomizeIntroPokemon();

        maybeCheckForSoftlocks();
        maybeApplyEggBoxes();
        maybeApplyPlayerSprites();
        maybeApplyShinyChance();
        maybeApplyLocke();
    }

    // ---- Modified from FVX (10/10/2026): stat floor and custom evolutions ----

    private com.uprfvx.random.yao.EvolutionEditor evolutionEditor;

    private com.uprfvx.random.yao.EvolutionEditor evolutionEditor() {
        if (evolutionEditor == null) {
            final java.util.Map<Integer, com.uprfvx.romio.gamedata.Species> byNumber = new java.util.HashMap<>();
            for (com.uprfvx.romio.gamedata.Species sp : romHandler.getSpeciesSetInclFormes()) {
                if (sp != null && (!byNumber.containsKey(sp.getNumber()) || sp.isBaseForme())) {
                    byNumber.put(sp.getNumber(), sp);
                }
            }
            final java.util.List<com.uprfvx.romio.gamedata.Item> items = romHandler.getItems();
            final java.util.List<com.uprfvx.romio.gamedata.Move> moves = romHandler.getMoves();
            evolutionEditor = new com.uprfvx.random.yao.EvolutionEditor(new com.uprfvx.random.yao.EvolutionEditor.World() {
                @Override
                public int generation() {
                    return romHandler.generationOfPokemon();
                }

                @Override
                public java.util.Collection<com.uprfvx.romio.gamedata.Species> allSpecies() {
                    return romHandler.getSpeciesSetInclFormes();
                }

                @Override
                public com.uprfvx.romio.gamedata.Species species(int number) {
                    return byNumber.get(number);
                }

                @Override
                public boolean itemExists(int id) {
                    return id > 0 && id < items.size() && items.get(id) != null;
                }

                @Override
                public boolean moveExists(int id) {
                    return id > 0 && id < moves.size() && moves.get(id) != null;
                }
            }, settings.getYaoSettings());
        }
        return evolutionEditor;
    }

    private void maybeEditEvolutionStructure() {
        com.uprfvx.random.yao.YaoSettings yao = settings.getYaoSettings();
        if (yao.getEvolutionEdits().isEmpty() && yao.getEvolutionRemovals().isEmpty()) {
            return;
        }
        try {
            yao.getEvolutionEdits(); // nothing else to prepare
            evolutionEditor().applyStructural().forEach(yao::addRunNote);
        } catch (RuntimeException e) {
            yao.addRunNote("Custom evolutions could not be applied (" + e + ").");
        }
    }

    private void maybeEditEvolutionLevels() {
        com.uprfvx.random.yao.YaoSettings yao = settings.getYaoSettings();
        if (yao.getEvolutionEdits().isEmpty() && yao.getEvolutionScalePercent() == 0
                && !yao.isStoneEvolutionsAlsoLevel()) {
            return;
        }
        try {
            if (yao.isStoneEvolutionsAlsoLevel()) {
                // Added (13/10/2026, Phase 19): runs after the evolution randomization, the user's edits and the
                // "make evolutions easier" steps, and before the level scale so the scale also covers these.
                int fully = yao.getFullyEvolvedByLevel();
                int cap = fully > 0 ? fully : settings.isMakeEvolutionsEasier() ? settings.getMakeEvolutionsEasierLvl() : 0;
                evolutionEditor().addLevelRoutesToStoneEvolutions(cap).forEach(yao::addRunNote);
            }
            evolutionEditor().applyLevels().forEach(yao::addRunNote);
        } catch (RuntimeException e) {
            yao.addRunNote("Evolution levels could not be adjusted (" + e + ").");
        }
    }

    private void maybeApplyStatFloor() {
        com.uprfvx.random.yao.YaoSettings yao = settings.getYaoSettings();
        if (yao.getBoostFloorBst() == 0) {
            return;
        }
        try {
            com.uprfvx.random.yao.StatFloorApplier.apply(romHandler.getSpeciesSetInclFormes(), yao)
                    .forEach(yao::addRunNote);
        } catch (RuntimeException e) {
            yao.addRunNote("The stat floor could not be applied (" + e + ").");
        }
    }

    /** Added for YAO (11/10/2026): the precise, single-Pokemon stat editor, see {@link com.uprfvx.random.yao.PerSpeciesStatEditor}. */
    private void maybeApplyStatEdits() {
        com.uprfvx.random.yao.YaoSettings yao = settings.getYaoSettings();
        if (yao.getStatEdits().isEmpty()) {
            return;
        }
        try {
            com.uprfvx.random.yao.PerSpeciesStatEditor.apply(romHandler.getSpeciesSetInclFormes(), yao)
                    .forEach(yao::addRunNote);
        } catch (RuntimeException e) {
            yao.addRunNote("The stat edits could not be applied (" + e + ").");
        }
    }

    /**
     * Modified from FVX (10/10/2026): Locke and Soft-locke modes. Never fails the randomization, each part
     * reports what it did.
     */
    private void maybeApplyLocke() {
        com.uprfvx.random.yao.YaoSettings yao = settings.getYaoSettings();
        if (yao.getLockeMode() == com.uprfvx.random.yao.YaoSettings.LockeMode.NONE) {
            return;
        }
        try {
            for (String note : com.uprfvx.random.yao.LockeModeApplier.apply(romHandler, yao.getLockeMode(),
                    yao.isCodePatchesAccepted())) {
                yao.addRunNote(note);
            }
        } catch (RuntimeException e) {
            yao.addRunNote("Locke mode could not be applied (" + e + ").");
        }
    }

    /**
     * Modified from FVX (10/10/2026): the adjustable shiny chance. Applied last, and it never fails the
     * randomization: if the game cannot take it, a note explains why and the ROM is left as it was.
     */
    /** Modified from YAO (10/10/2026): replaces the player's walking sprites with the sheets the user chose. */
    private void maybeApplyPlayerSprites() {
        com.uprfvx.random.yao.YaoSettings yao = settings.getYaoSettings();
        if (yao.getPlayerSpriteMale().isEmpty() && yao.getPlayerSpriteFemale().isEmpty()) {
            return;
        }
        com.uprfvx.romio.romhandlers.FeatureAvailability availability = romHandler.checkPlayerSprites();
        if (!availability.available()) {
            yao.addRunNote("Player sprites: NOT applied. " + availability.reason());
            return;
        }
        java.awt.image.BufferedImage male = readSheet(yao.getPlayerSpriteMale(), "boy");
        java.awt.image.BufferedImage female = readSheet(yao.getPlayerSpriteFemale(), "girl");
        if (male == null && female == null) {
            return;
        }
        com.uprfvx.romio.romhandlers.PatchResult result;
        try {
            result = romHandler.applyPlayerSprites(male, female);
        } catch (RuntimeException e) {
            result = com.uprfvx.romio.romhandlers.PatchResult.notApplied("The player's sprites could not be replaced (" + e + ").");
        }
        yao.addRunNote("Player sprites: " + (result.applied() ? "applied. " : "NOT applied. ") + result.message());
    }

    private java.awt.image.BufferedImage readSheet(String path, String who) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.File(path));
            if (image == null) {
                settings.getYaoSettings().addRunNote("Player sprites: the file for the " + who + " is not an image: " + path);
            }
            return image;
        } catch (java.io.IOException | RuntimeException e) {
            settings.getYaoSettings().addRunNote("Player sprites: the file for the " + who + " could not be read (" + e.getMessage() + ").");
            return null;
        }
    }

    /** Modified from YAO (10/10/2026): reports randomizations that may block the game, see {@link com.uprfvx.random.yao.SoftlockValidator}. */
    private void maybeCheckForSoftlocks() {
        try {
            java.util.List<String> problems = com.uprfvx.random.yao.SoftlockValidator.check(
                    com.uprfvx.random.yao.SoftlockValidator.of(romHandler));
            for (String problem : problems) {
                settings.getYaoSettings().addRunNote("Softlock warning: " + problem);
            }
            if (problems.isEmpty()) {
                settings.getYaoSettings().addRunNote("Softlock check: no problem found with the field moves the game needs early on.");
            }
        } catch (RuntimeException e) {
            settings.getYaoSettings().addRunNote("Softlock check could not run (" + e + ").");
        }
    }

    /**
     * Modified from YAO (10/10/2026): the egg box. The species are picked after everything else that can change the
     * Pokemon of the game (so evolution lines and legendary status are final), with the same seeded generator.
     */
    private void maybeApplyEggBoxes() {
        com.uprfvx.random.yao.YaoSettings yao = settings.getYaoSettings();
        if (yao.getEggBoxCount() <= 0) {
            return;
        }
        com.uprfvx.romio.romhandlers.FeatureAvailability availability = romHandler.checkEggBoxes();
        if (!availability.available()) {
            yao.addRunNote("Egg box (" + yao.getEggBoxCount() + " eggs): NOT applied. " + availability.reason());
            return;
        }
        com.uprfvx.random.yao.EggBoxSelector.Result picked = com.uprfvx.random.yao.EggBoxSelector.pick(
                romHandler.getSpeciesSet(), yao, randomSource.getNonCosmetic());
        for (String note : picked.notes()) {
            yao.addRunNote("Egg box: " + note);
        }
        if (picked.eggs().isEmpty()) {
            return;
        }
        com.uprfvx.romio.romhandlers.PatchResult result;
        try {
            result = romHandler.applyEggBoxes(picked.eggs());
        } catch (RuntimeException e) {
            result = com.uprfvx.romio.romhandlers.PatchResult.notApplied("The egg box could not be applied (" + e + ").");
        }
        StringBuilder names = new StringBuilder();
        for (com.uprfvx.romio.gamedata.Species egg : picked.eggs()) {
            names.append(names.length() == 0 ? "" : ", ").append(egg.getFullName());
        }
        yao.addRunNote("Egg box: " + (result.applied() ? "applied. " : "NOT applied. ") + result.message()
                + (result.applied() ? " Eggs: " + names + "." : ""));
    }

    private void maybeApplyShinyChance() {
        double percent = settings.getYaoSettings().getShinyChancePercent();
        if (percent <= 0) {
            return;
        }
        if (!settings.getYaoSettings().isCodePatchesAccepted()) {
            settings.getYaoSettings().addRunNote("Shiny chance " + String.format(java.util.Locale.ROOT, "%.1f", percent)
                    + " percent: NOT applied. It changes the game's program code and the notice about that was not "
                    + "accepted.");
            return;
        }
        com.uprfvx.romio.romhandlers.PatchResult result;
        try {
            result = romHandler.applyShinyChance(percent);
        } catch (RuntimeException e) {
            result = com.uprfvx.romio.romhandlers.PatchResult.notApplied(
                    "The shiny chance could not be applied (" + e + ").");
        }
        settings.getYaoSettings().addRunNote("Shiny chance " + String.format(java.util.Locale.ROOT, "%.1f", percent) + " percent: "
                + (result.applied() ? "applied. " : "NOT applied. ") + result.message());
    }

    private void maybeRandomizeTypeEffectiveness() {
        if (settings.getTypeEffectivenessMod() != Settings.TypeEffectivenessMod.UNCHANGED) {
            switch (settings.getTypeEffectivenessMod()) {
                case RANDOM:
                    typeEffRandomizer.randomizeTypeEffectiveness(false);
                    break;
                case RANDOM_BALANCED:
                    typeEffRandomizer.randomizeTypeEffectiveness(true);
                    break;
                case KEEP_IDENTITIES:
                    typeEffRandomizer.randomizeTypeEffectivenessKeepIdentities();
                    break;
                case INVERSE:
                    typeEffRandomizer.invertTypeEffectiveness(settings.isInverseTypesRandomImmunities());
            }
        }
    }

    private void maybeRandomizeMoveData() {
        if (settings.isRandomizeMovePowers()) {
            moveDataRandomizer.randomizeMovePowers();
        }

        if (settings.isRandomizeMoveAccuracies()) {
            moveDataRandomizer.randomizeMoveAccuracies();
        }

        if (settings.isRandomizeMovePPs()) {
            moveDataRandomizer.randomizeMovePPs();
        }

        if (settings.isRandomizeMoveTypes()) {
            moveDataRandomizer.randomizeMoveTypes();
        }

        if (settings.isRandomizeMoveNames()) {
            moveNameRandomizer.randomizeMoveNames();
        }

        if (settings.isRandomizeMoveCategory() && romHandler.hasPhysicalSpecialSplit()) {
            moveDataRandomizer.randomizeMoveCategory();
        }
    }

    private void maybeApplyMiscTweaks() {
        if (settings.getCurrentMiscTweaks() != MiscTweak.NO_MISC_TWEAKS) {
            miscTweakRandomizer.applyMiscTweaks();
        }
    }

    private void maybeStandardizeEXPCurves() {
        if (settings.isStandardizeEXPCurves()) {
            speciesBSRandomizer.standardizeEXPCurves();
        }
    }

    private void maybeRandomizeSpeciesTypes() {
        if (settings.getSpeciesTypesMod() != Settings.SpeciesTypesMod.UNCHANGED) {
            speciesTypeRandomizer.randomizeSpeciesTypes();
        }
    }

    private void maybeRandomizeWildHeldItems() {
        if (settings.isRandomizeWildPokemonHeldItems()) {
            encHeldItemRandomizer.randomizeWildHeldItems();
        }
    }

    private void maybeRandomizeEvolutions() {
        if (settings.getEvolutionsMod() != Settings.EvolutionsMod.UNCHANGED) {
            evoRandomizer.randomizeEvolutions();
        }
    }

    private void maybeRandomizeSpeciesBaseStatTotals() {
        if (settings.getBSTMod() != Settings.BSTMod.UNCHANGED) {
            speciesBSRandomizer.randomizeBSTs();
        }
    }

    private void maybeRandomizeSpeciesBaseStats() {
        switch (settings.getBaseStatisticsMod()) {
            case SHUFFLE:
                speciesBSRandomizer.shuffleSpeciesStats();
                break;
            case RANDOM:
                speciesBSRandomizer.randomizeSpeciesStats();
        }
    }

    private void maybeRandomizeSpeciesAbilities() {
        if (settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE) {
            speciesAbilityRandomizer.randomizeAbilities();
        }
    }

    private void maybeApplyEvolutionImprovements() {
        boolean useEstimatedLevels = settings.useEstimatedLevelsForEvolutionImprovements();

        // Trade evolutions (etc.) removal
        if (settings.isChangeImpossibleEvolutions()) {
            boolean changeMoveEvos = settings.getMovesetsMod() != Settings.MovesetsMod.UNCHANGED;
            romHandler.removeImpossibleEvolutions(changeMoveEvos, useEstimatedLevels);
        }

        // Easier evolutions
        // Modified from YAO (12/10/2026): "fully evolved by level" is independent of the Make Evolutions Easier
        // checkbox. When set, it condenses level evolutions on its own and replaces the native level.
        int fullyEvolvedLevel = settings.getYaoSettings().getFullyEvolvedByLevel();
        if (fullyEvolvedLevel > 0) {
            romHandler.condenseLevelEvolutions(fullyEvolvedLevel);
            settings.getYaoSettings().addRunNote("Fully evolved by level " + fullyEvolvedLevel
                    + ": level evolutions above that level were brought down to it (middle stages by 75% of it).");
        } else if (settings.isMakeEvolutionsEasier()) {
            romHandler.condenseLevelEvolutions(settings.getMakeEvolutionsEasierLvl());
        }
        if (settings.isMakeEvolutionsEasier()) {
            boolean wildsRandomizer = settings.isRandomizeWildPokemon();
            romHandler.makeEvolutionsEasier(wildsRandomizer, useEstimatedLevels);
        }

        // Remove time-based evolutions
        if (settings.isRemoveTimeBasedEvolutions()) {
            romHandler.removeTimeBasedEvolutions();
        }
    }

    private void maybeRandomizeStarters() {
        if (settings.getStartersMod() != Settings.StartersMod.UNCHANGED) {
            starterRandomizer.randomizeStarters();
        }
        if (settings.isRandomizeStartersHeldItems() && !(romHandler instanceof Gen1RomHandler)) {
            starterRandomizer.randomizeStarterHeldItems();
        }
    }

    private void maybeRandomizeMovesets() {
        // Movesets
        // 1. Randomize movesets
        // 2. Reorder moves by damage
        // Note: "Metronome only" is handled after trainers instead

        if (settings.getMovesetsMod() != Settings.MovesetsMod.UNCHANGED &&
                settings.getMovesetsMod() != Settings.MovesetsMod.METRONOME_ONLY) {
            speciesMovesetRandomizer.randomizeMovesLearnt();
            speciesMovesetRandomizer.randomizeEggMoves();
        }

        if (settings.isReorderDamagingMoves()) {
            speciesMovesetRandomizer.orderDamagingMovesByDamage();
        }
    }

    private void maybeRandomizeTMMoves() {
        if (!(settings.getMovesetsMod() == Settings.MovesetsMod.METRONOME_ONLY)
                && settings.getTmsMod() == Settings.TMsMod.RANDOM) {
            tmtMoveRandomizer.randomizeTMMoves();
        }
    }

    private void maybeRandomizeTMHMCompatibility() {
        // TM/HM compatibility
        // 1. Randomize TM/HM compatibility
        // 2. Ensure levelup move sanity
        // 3. Follow evolutions
        // 4. Full HM compatibility
        // 5. Copy to cosmetic forms

        switch (settings.getTmsHmsCompatibilityMod()) {
            case COMPLETELY_RANDOM:
            case RANDOM_PREFER_TYPE:
                tmhmtCompRandomizer.randomizeTMHMCompatibility();
                break;
            case FULL:
                tmhmtCompRandomizer.fullTMHMCompatibility();
        }

        if (settings.isTmLevelUpMoveSanity()) {
            tmhmtCompRandomizer.ensureTMCompatSanity();
            if (settings.isTmsFollowEvolutions()) {
                tmhmtCompRandomizer.ensureTMEvolutionSanity();
            }
        }

        if (settings.isFullHMCompat()) {
            tmhmtCompRandomizer.fullHMCompatibility();
        }

        // Copy TM/HM compatibility to cosmetic formes if it was changed at all
        if (tmhmtCompRandomizer.isTMHMChangesMade()) {
            tmhmtCompRandomizer.copyTMCompatibilityToCosmeticFormes();
        }
    }

    private void maybeRandomizeMoveTutorMoves() {
        if (romHandler.hasMoveTutors()) {
            if (!(settings.getMovesetsMod() == Settings.MovesetsMod.METRONOME_ONLY)
                    && settings.getMoveTutorMovesMod() == Settings.MoveTutorMovesMod.RANDOM) {
                tmtMoveRandomizer.randomizeMoveTutorMoves();
            }
        }
    }

    private void maybeRandomizeMoveTutorCompatibility() {
        if (romHandler.hasMoveTutors()) {
            // Move Tutor Compatibility
            // 1. Randomize MT compatibility
            // 2. Ensure levelup move sanity
            // 3. Follow evolutions
            // 4. Copy to cosmetic forms

            switch (settings.getMoveTutorsCompatibilityMod()) {
                case COMPLETELY_RANDOM:
                case RANDOM_PREFER_TYPE:
                    tmhmtCompRandomizer.randomizeMoveTutorCompatibility();
                    break;
                case FULL:
                    tmhmtCompRandomizer.fullMoveTutorCompatibility();
            }

            if (settings.isTutorLevelUpMoveSanity()) {
                tmhmtCompRandomizer.ensureMoveTutorCompatSanity();
                if (settings.isTutorFollowEvolutions()) {
                    tmhmtCompRandomizer.ensureMoveTutorEvolutionSanity();
                }
            }

            // Copy move tutor compatibility to cosmetic formes if it was changed at all
            if (tmhmtCompRandomizer.isTutorChangesMade()) {
                tmhmtCompRandomizer.copyMoveTutorCompatibilityToCosmeticFormes();
            }
        }
    }

    private void maybeRandomizeTrainerPokemon() {
        // Trainer Pokemon
        // 1. Modify levels first to get larger level variety if additional Pokemon are added in the next step
        // 2. Add extra Trainer Pokemon with level between lowest and highest original trainer Pokemon
        // 3. Set trainers to be double battles and add extra Pokemon if necessary
        // 4. Modify rivals to carry starters
        // 5. Randomize Trainer Pokemon (or evolve if not randomizing, i.e., UNCHANGED and no additional Pkmn)

        if (settings.isTrainersLevelModified()) {
            trainerPokeRandomizer.applyTrainerLevelModifier();
        }

        boolean additionalPokemonAdded = settings.getAdditionalRegularTrainerPokemon() > 0
                || settings.getAdditionalImportantTrainerPokemon() > 0
                || settings.getAdditionalBossTrainerPokemon() > 0;
        if (additionalPokemonAdded) {
            trainerPokeRandomizer.addTrainerPokemon();
        }

        if (settings.getBattleStyle().isBattleStyleChanged()) {
            trainerPokeRandomizer.modifyBattleStyle();
        }
        maybeApplyBattleStyleRules();

        if ((settings.getTrainersMod() != Settings.TrainersMod.UNCHANGED
                || settings.getStartersMod() != Settings.StartersMod.UNCHANGED)
                && settings.isRivalCarriesStarterThroughout()) {
            trainerPokeRandomizer.makeRivalCarryStarter();
        }

        if (settings.getTrainersMod() != Settings.TrainersMod.UNCHANGED || additionalPokemonAdded) {
            trainerPokeRandomizer.randomizeTrainerPokes();
        } else if (settings.isTrainersEvolveTheirPokemon()) {
            trainerPokeRandomizer.evolveTrainerPokemonAsFarAsLegal();
        }
    }

    private void maybeRandomizeTrainerMovesets() {
        if (settings.isBetterBossTrainerMovesets()
                || settings.isBetterImportantTrainerMovesets()
                || settings.isBetterRegularTrainerMovesets()) {
            trainerMovesetRandomizer.randomizeTrainerMovesets();
        }
    }

    private void maybeFixTrainerZCrystals() {
        // if earlier randomization could have led to unusable Z-crystals, fix them to something usable here
        if (speciesMovesetRandomizer.isChangesMade() || trainerPokeRandomizer.isChangesMade()
                || trainerMovesetRandomizer.isChangesMade()) {
            trainerPokeRandomizer.randomUsableZCrystals();
        }
    }

    private void maybeRandomizeTrainerHeldItems() {
        if (settings.isRandomizeHeldItemsForBossTrainerPokemon()
                || settings.isRandomizeHeldItemsForImportantTrainerPokemon()
                || settings.isRandomizeHeldItemsForRegularTrainerPokemon()) {
            trainerPokeRandomizer.randomizeTrainerHeldItems();
        }
    }

    /** Added for YAO (13/10/2026): limits the battle items trainers carry, see {@link com.uprfvx.random.yao.TrainerBattleItemRules}. */
    private void maybeApplyTrainerBattleItemRules() {
        com.uprfvx.random.yao.YaoSettings yao = settings.getYaoSettings();
        if (yao.getTrainerItemMode() != com.uprfvx.random.yao.YaoSettings.TrainerItemMode.TIERED) {
            return;
        }
        if (!romHandler.canChangeTrainerBattleItems()) {
            return; // Gen 1 and 2 keep the game's own item logic, nothing to note
        }
        try {
            for (String note : com.uprfvx.random.yao.TrainerBattleItemRules.apply(romHandler.getTrainers(),
                    yao.isRegularTrainersKeepItems(), romHandler.generationOfPokemon(), romHandler.getROMType())) {
                yao.addRunNote(note);
            }
        } catch (RuntimeException e) {
            yao.addRunNote("Trainer battle item rules could not be applied (" + e + ").");
        }
    }

    /** Modified from YAO (12/10/2026): chance-based double/triple/rotation battles, see {@link com.uprfvx.random.yao.BattleStyleRules}. */
    private void maybeApplyBattleStyleRules() {
        com.uprfvx.random.yao.YaoSettings yao = settings.getYaoSettings();
        if (yao.getBattleStyleRules().isEmpty()) {
            return;
        }
        try {
            for (String note : com.uprfvx.random.yao.BattleStyleRules.apply(romHandler.getTrainers(),
                    yao.getBattleStyleRules(), romHandler.generationOfPokemon(), romHandler.getROMType(),
                    randomSource.getNonCosmetic())) {
                yao.addRunNote(note);
            }
            romHandler.makeDoubleBattleModePossible();
        } catch (RuntimeException e) {
            yao.addRunNote("Battle style rules could not be applied (" + e + ").");
        }
    }

    /** Modified from YAO (10/10/2026): the last Pokemon of trainers is a Mega, see {@link com.uprfvx.random.yao.MegaFinaleApplier}. */
    private void maybeApplyMegaFinale() {
        com.uprfvx.random.yao.YaoSettings yao = settings.getYaoSettings();
        if (!yao.isMegaFinaleOn()) {
            return;
        }
        if (!romHandler.hasMegaEvolutions()) {
            yao.addRunNote("Mega finale: NOT applied. This game has no Mega Evolutions for trainers to use.");
            return;
        }
        try {
            for (String note : com.uprfvx.random.yao.MegaFinaleApplier.apply(romHandler.getTrainers(),
                    romHandler.getMegaEvolutions(), yao.getMegaFinaleSelection(), yao.isMegaFinaleReplace(),
                    romHandler.generationOfPokemon(), romHandler.getROMType(),
                    randomSource.getNonCosmetic())) {
                yao.addRunNote(note);
            }
        } catch (RuntimeException e) {
            yao.addRunNote("Mega finale could not be applied (" + e + ").");
        }
    }

    private void maybeRandomizeTrainerNames() {
        if (romHandler.canChangeTrainerText()) {
            if (settings.isRandomizeTrainerClassNames()) {
                trainerNameRandomizer.randomizeTrainerClassNames();
            }

            if (settings.isRandomizeTrainerNames()) {
                trainerNameRandomizer.randomizeTrainerNames();
            }
        }
    }

    private void maybeRandomizeStaticPokemon() {
        if (romHandler.canChangeStaticPokemon()) {
            if (settings.getStaticPokemonMod() != Settings.StaticPokemonMod.UNCHANGED) { // Legendary for L
                staticPokeRandomizer.randomizeStaticPokemon();
            } else if (settings.isStaticLevelModified()) {
                staticPokeRandomizer.onlyChangeStaticLevels();
            }
        }
    }

    private void maybeRandomizeTotemPokemon() {
        if (romHandler.hasTotemPokemon()) {
            if (settings.getTotemPokemonMod() != Settings.TotemPokemonMod.UNCHANGED ||
                    settings.getAllyPokemonMod() != Settings.AllyPokemonMod.UNCHANGED ||
                    settings.getAuraMod() != Settings.AuraMod.UNCHANGED ||
                    settings.isRandomizeTotemHeldItems() ||
                    settings.isTotemLevelsModified()) {

                staticPokeRandomizer.randomizeTotemPokemon();
            }
        }
    }

    private void maybeRandomizeWildPokemon() {
        if (settings.isUseMinimumCatchRate()) {
            wildEncounterRandomizer.changeCatchRates();
        }

        if (settings.isRandomizeWildPokemon() || settings.isWildLevelsModified()) {
            wildEncounterRandomizer.randomizeEncounters();
        }
    }

    private void maybeRandomizeInGameTrades() {
        switch (settings.getInGameTradesMod()) {
            case RANDOMIZE_GIVEN:
            case RANDOMIZE_GIVEN_AND_REQUESTED:
                tradeRandomizer.randomizeIngameTrades();
        }
    }

    private void maybeRandomizeFieldItems() {
        switch (settings.getFieldItemsMod()) {
            case SHUFFLE:
            case RANDOM:
            case RANDOM_EVEN:
                itemRandomizer.randomizeFieldItems();
        }
    }

    private void maybeRandomizeShops() {
        // Modified from FVX (10/10/2026): protected items (the basics and any chosen) keep their original slots.
        java.util.Set<Integer> protectedIds = settings.getYaoSettings().effectiveProtectedShopItems();
        boolean randomizing = settings.getShopItemsMod() != Settings.ShopItemsMod.UNCHANGED;
        java.util.List<com.uprfvx.romio.gamedata.Shop> shopsBefore = null;
        if (randomizing && !protectedIds.isEmpty()) {
            try {
                shopsBefore = itemRandomizer.snapshotShops();
            } catch (RuntimeException e) {
                settings.getYaoSettings().addRunNote("Protected shop items could not be applied (" + e + ").");
            }
        }
        if (randomizing && !itemRandomizer.hasRandomizableShops()) {
            settings.getYaoSettings().addRunNote("This game has no shop that shop randomization may change (the regular shops are never "
                    + "randomized), so the shop randomization option did nothing.");
        }
        switch (settings.getShopItemsMod()) {
            case SHUFFLE:
                itemRandomizer.shuffleShopItems();
                break;
            case RANDOM:
                itemRandomizer.randomizeShopItems();
        }
        if (shopsBefore != null) {
            try {
                int restored = itemRandomizer.restoreProtectedShopItems(shopsBefore, protectedIds);
                settings.getYaoSettings().addRunNote("Protected shop items: " + restored
                        + " shop slot(s) kept their original item (" + protectedIds.size() + " protected item types).");
            } catch (RuntimeException e) {
                settings.getYaoSettings().addRunNote("Protected shop items could not be applied (" + e + ").");
            }
        }
        if (settings.isBalanceShopPrices()) {
            romHandler.setBalancedShopPrices();
        }
        if (settings.isAddCheapRareCandiesToShops()) {
            itemRandomizer.addCheapRareCandiesToShops();
        }
        // Modified from FVX (10/10/2026): custom shop items are added last so that shop randomization cannot remove them.
        if (!settings.getYaoSettings().getCustomShopItems().isEmpty()) {
            itemRandomizer.addCustomItemsToShops(settings.getYaoSettings().getCustomShopItems());
        }
    }

    private void maybeRandomizePickupItems() {
        if (settings.getPickupItemsMod() == Settings.PickupItemsMod.RANDOM) {
            itemRandomizer.randomizePickupItems();
        }
    }

    private void maybeRandomizePokemonPalettes() {
        if (settings.getPokemonPalettesMod() == Settings.PokemonPalettesMod.RANDOM) {
            // Modified from FVX (10/10/2026): games without palette support have no palette randomizer. The option
            // can still arrive through a settings file or string, and used to crash the randomization.
            if (paletteRandomizer == null) {
                settings.getYaoSettings().addRunNote("Pokemon palette randomization is not available for this game "
                        + "and was skipped.");
                return;
            }
            paletteRandomizer.randomizePokemonPalettes();
        }
    }

    private void maybeRandomizeIntroPokemon() {
        if (settings.isRandomizeIntroMon()) {
            introPokeRandomizer.randomizeIntroPokemon();
        }
    }
}