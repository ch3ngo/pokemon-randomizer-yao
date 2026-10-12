package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  Applicability.java - which randomizer options make sense for a game.  --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Decides which options and groups of options the web interface hides for the game that is loaded, using the same
 * questions the original interface asks the game (does it have megas, shops, tutors, time based encounters and so
 * on). Hiding is only a convenience: a hidden option is never sent as anything but its default, and the randomizer
 * itself ignores options a game cannot use.
 */
final class Applicability {

    final Set<String> hiddenGroups = new LinkedHashSet<>();
    final Set<String> hiddenFields = new LinkedHashSet<>();

    static Applicability of(RomHandler rom) {
        Applicability a = new Applicability();
        int gen = rom.generationOfPokemon();
        a.group(gen >= 3, "abilities");
        a.group(rom.hasTotemPokemon(), "totem");
        a.group(rom.hasPokemonPaletteSupport(), "palettes");
        a.group(rom.hasMoveTutors(), "tutors", "tutorcompat");
        a.group(rom.canChangeStaticPokemon(), "static");
        a.group(rom.hasShopSupport(), "shops");
        a.group(rom.hasTypeEffectivenessSupport(), "typechart");
        a.group(gen >= 3, "pickup");

        boolean megas = rom.hasMegaEvolutions();
        a.fields(megas, "baseStatsFollowMegaEvolutions", "abilitiesFollowMegaEvolutions", "typesFollowMegaEvolutions",
                "swapTrainerMegaEvos", "swapStaticMegaEvos");
        a.fields(rom.hasTimeBasedEncounters(), "useTimeBasedEncounters");
        a.fields(rom.hasTimeBasedEvolutions(), "removeTimeBasedEvolutions");
        a.fields(rom.hasWildAltFormes(), "allowWildAltFormes");
        a.fields(rom.hasStaticAltFormes(), "allowStaticAltFormes");
        a.fields(rom.hasStarterAltFormes(), "allowStarterAltFormes");
        a.fields(rom.supportsStarterHeldItems(), "randomizeStartersHeldItems", "banBadRandomStarterHeldItems");
        a.fields(rom.hasMainGameLegendaries(), "limitMainGameLegendaries");
        a.fields(rom.hasStaticMusicFix(), "correctStaticMusic");
        a.fields(rom.canGiveCustomMovesetsToBossTrainers(), "betterBossTrainerMovesets");
        a.fields(rom.canGiveCustomMovesetsToImportantTrainers(), "betterImportantTrainerMovesets");
        a.fields(rom.canGiveCustomMovesetsToRegularTrainers(), "betterRegularTrainerMovesets");
        a.fields(rom.canAddPokemonToBossTrainers(), "additionalBossTrainerPokemon");
        a.fields(rom.canAddPokemonToImportantTrainers(), "additionalImportantTrainerPokemon");
        a.fields(rom.canAddPokemonToRegularTrainers(), "additionalRegularTrainerPokemon");
        a.fields(rom.canAddHeldItemsToBossTrainers(), "randomizeHeldItemsForBossTrainerPokemon");
        a.fields(rom.canAddHeldItemsToImportantTrainers(), "randomizeHeldItemsForImportantTrainerPokemon");
        a.fields(rom.canAddHeldItemsToRegularTrainers(), "randomizeHeldItemsForRegularTrainerPokemon");
        a.fields(rom.canChangeTrainerText(), "randomizeTrainerNames", "randomizeTrainerClassNames");
        a.fields(rom.supportsFourStartingMoves(), "startWithGuaranteedMoves", "guaranteedMoveCount");
        a.fields(rom.hasPhysicalSpecialSplit(), "randomizeMoveCategory");
        a.fields(rom.canSetIntroPokemon(), "randomizeIntroMon");
        a.fields(gen >= 4, "banIrregularAltFormes");
        a.fields(gen < 7, "updateMoves", "updateMovesToGeneration", "updateBaseStats", "updateBaseStatsToGeneration");
        return a;
    }

    private void group(boolean applies, String... ids) {
        if (!applies) {
            for (String id : ids) {
                hiddenGroups.add(id);
            }
        }
    }

    private void fields(boolean applies, String... keys) {
        if (!applies) {
            for (String key : keys) {
                hiddenFields.add(key);
            }
        }
    }
}
