package com.uprfvx.random.yao;

import com.uprfvx.random.GameRandomizer;
import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romio.RomOpener;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Added for YAO (13/10/2026, web UI feedback item 47). ROM based check: with the starter type rule "Single type" and
 * the type of the starters left at "None" (the empty choice of the web interface), the randomizer picks one type
 * itself and every random starter has it. The written game is opened again and its starters read back.
 */
public class StartersSingleTypeNoneRandomizerTest {

    @ParameterizedTest
    @ValueSource(strings = {"Black 2 (S).nds", "SoulSilver (U).nds", "SoulSilver (S).nds", "Emerald (U).gba"})
    public void noneMeansTheProgramPicksOneTypeForAllStarters(String romName) throws Exception {
        String romsPath = System.getProperty("romsPath");
        Assumptions.assumeTrue(romsPath != null, "no romsPath");
        File romFile = new File(romsPath, romName);
        Assumptions.assumeTrue(romFile.isFile(), romName + " is not in roms/");
        RomOpener opener = new RomOpener();
        opener.setExtraMemoryAvailable(Runtime.getRuntime().maxMemory() >= 3_800_000_000L);
        RomHandler rom = opener.openRomFile(romFile).getRomHandler();
        Settings settings = new com.uprfvx.random.web.SettingsSchema().build(Map.of(
                "startersMod", "COMPLETELY_RANDOM", "startersTypeMod", "SINGLE_TYPE", "startersSingleType", ""),
                -1, rom.getROMName());
        assertNull(settings.getStartersSingleType());
        File out = Files.createTempFile("starters-test", romName.substring(romName.lastIndexOf('.'))).toFile();
        out.delete();
        GameRandomizer.Results res = new GameRandomizer(settings, null, rom,
                ResourceBundle.getBundle("com/uprfvx/random/gui/Bundle"), false)
                .randomize(out.getAbsolutePath(), new PrintStream(new java.io.ByteArrayOutputStream()), 11L);
        assertTrue(res.wasSaveSuccessful(), () -> String.valueOf(res.getException()));
        RomHandler back = new RomOpener().openRomFile(out).getRomHandler();
        List<Species> starters = back.getStarters();
        assertFalse(starters.isEmpty());
        java.util.Set<Object> common = null;
        for (Species s : starters) {
            java.util.Set<Object> types = new java.util.HashSet<>();
            types.add(s.getPrimaryType(false));
            if (s.getSecondaryType(false) != null) {
                types.add(s.getSecondaryType(false));
            }
            if (common == null) {
                common = types;
            } else {
                common.retainAll(types);
            }
        }
        assertFalse(common.isEmpty(), romName + ": the starters share no type " + starters);
        System.out.println("STARTERS " + romName + " " + starters);
        out.delete();
    }
}
