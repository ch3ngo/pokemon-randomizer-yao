package com.uprfvx.random.yao;

import com.uprfvx.random.GameRandomizer;
import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romio.RomOpener;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Added for YAO (14/10/2026). ROM based check: with completely random movesets and better trainer movesets, the
 * Pokemon in the ROM get one randomized learnset and the trainers pick from a different one. Read back from the
 * written game: few of the moves trainers hold are on the species' own (player side) learnset. If the trainers used
 * the same learnset, most of their moves would be on it.
 */
public class TrainerSeparateLearnsetsRandomizerTest {

    @ParameterizedTest
    @ValueSource(strings = {"Emerald (U).gba", "SoulSilver (U).nds", "Black 2 (S).nds"})
    public void trainersDoNotUseThePlayersRandomizedLearnsets(String romName) throws Exception {
        String romsPath = System.getProperty("romsPath");
        Assumptions.assumeTrue(romsPath != null, "no romsPath");
        File romFile = new File(romsPath, romName);
        Assumptions.assumeTrue(romFile.isFile(), romName + " is not in roms/");
        RomOpener opener = new RomOpener();
        opener.setExtraMemoryAvailable(Runtime.getRuntime().maxMemory() >= 3_800_000_000L);
        RomHandler rom = opener.openRomFile(romFile).getRomHandler();
        Settings settings = new com.uprfvx.random.web.SettingsSchema().build(Map.of(
                "movesetsMod", "COMPLETELY_RANDOM", "betterBossTrainerMovesets", true,
                "betterImportantTrainerMovesets", true, "betterRegularTrainerMovesets", true),
                -1, rom.getROMName());
        assertEquals(Settings.MovesetsMod.COMPLETELY_RANDOM, settings.getMovesetsMod());
        File out = Files.createTempFile("separate-test", romName.substring(romName.lastIndexOf('.'))).toFile();
        out.delete();
        GameRandomizer.Results res = new GameRandomizer(settings, null, rom,
                ResourceBundle.getBundle("com/uprfvx/random/gui/Bundle"), false)
                .randomize(out.getAbsolutePath(), new PrintStream(new java.io.ByteArrayOutputStream()), 5L);
        assertTrue(res.wasSaveSuccessful(), () -> String.valueOf(res.getException()));
        RomHandler back = new RomOpener().openRomFile(out).getRomHandler();
        Map<Integer, List<MoveLearnt>> learnt = back.getMovesLearnt();
        int total = 0;
        int onLearnset = 0;
        for (Trainer t : back.getTrainers()) {
            for (TrainerPokemon tp : t.getPokemon()) {
                if (tp.isResetMoves()) {
                    continue;
                }
                Set<Integer> own = new HashSet<>();
                for (MoveLearnt ml : learnt.get(tp.getSpecies().getNumber())) {
                    own.add(ml.move);
                }
                for (int mv : tp.getMoves()) {
                    if (mv != 0) {
                        total++;
                        if (own.contains(mv)) {
                            onLearnset++;
                        }
                    }
                }
            }
        }
        assertTrue(total > 100, romName + ": no custom trainer moves read back");
        double share = onLearnset / (double) total;
        System.out.println("SEPARATE " + romName + " trainer moves on the player-side learnset: " + onLearnset + "/"
                + total + " = " + share);
        assertTrue(share < 0.4, romName + ": trainers seem to share the player's learnsets, share " + share);
        out.delete();
    }
}
