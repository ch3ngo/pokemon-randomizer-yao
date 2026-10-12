package com.uprfvx.random.yao;

import com.uprfvx.random.GameRandomizer;
import com.uprfvx.random.Settings;
import com.uprfvx.random.randomizers.ItemRandomizer;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Shop;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romio.RomOpener;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Added for YAO (12/10/2026). ROM based check (runs with the {@code testROMs} task, skipped when the ROM is not in
 * {@code roms/}): shop shuffling and randomizing, with the guarantee options on, never changes a regular Poke Mart
 * (the regular Poke Marts, see ItemRandomizer.isRegularMart) and does change at least one special shop where the game has one.
 */
public class MainShopsUntouchedRandomizerTest {

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("com/uprfvx/random/gui/Bundle");

    private static List<List<Integer>> ids(List<Shop> shops, boolean mainGame) {
        List<List<Integer>> out = new ArrayList<>();
        for (Shop s : shops) {
            if (ItemRandomizer.isRegularMart(s) == mainGame) {
                List<Integer> l = new ArrayList<>();
                for (Item i : s.getItems()) {
                    l.add(i == null ? -1 : i.getId());
                }
                out.add(l);
            }
        }
        return out;
    }

    @ParameterizedTest
    @ValueSource(strings = {"Crystal (U).gbc", "Emerald (U).gba", "FireRed (U).gba", "FireRed (S).gba",
            "Diamond (E).nds", "Diamond (S).nds", "Platinum (U).nds", "Platinum (S).nds",
            "SoulSilver (U).nds", "SoulSilver (S).nds", "Black (U).nds", "Black (S).nds",
            "Black 2 (U).nds", "Black 2 (S).nds", "X (E).3ds", "Omega Ruby (E).3ds", "Sun (E).3ds", "Ultra Sun (E).3ds"})
    public void mainShopsAreNeverRandomized(String romName) throws Exception {
        String romsPath = System.getProperty("romsPath");
        Assumptions.assumeTrue(romsPath != null, "no romsPath");
        File romFile = new File(romsPath, romName);
        Assumptions.assumeTrue(romFile.isFile(), romName + " is not in roms/");
        for (String mode : new String[]{"SHUFFLE", "RANDOM"}) {
            RomOpener opener = new RomOpener();
            opener.setExtraMemoryAvailable(Runtime.getRuntime().maxMemory() >= 3_800_000_000L);
            RomOpener.Results r = opener.openRomFile(romFile);
            assertTrue(r.wasOpeningSuccessful());
            RomHandler rom = r.getRomHandler();
            List<Shop> before = rom.getShops();
            List<List<Integer>> mainBefore = ids(before, true);
            long randomizable = before.stream().filter(sh -> sh.isSpecialShop()).count();
            System.out.println("SHOPCOUNT " + romName + " randomizable=" + randomizable + " main=" + mainBefore.size() + " total=" + before.size());
            List<List<Integer>> otherBefore = ids(before, false);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("shopItemsMod", mode);
            if (mode.equals("RANDOM")) {
                values.put("guaranteeEvolutionItems", true);
                values.put("guaranteeXItems", true);
            }
            Settings s = new com.uprfvx.random.web.SettingsSchema().build(values, -1, rom.getROMName());
            boolean threeDs = romName.endsWith(".3ds");
            File out = threeDs ? Files.createTempDirectory("main-shops-test").toFile()
                    : Files.createTempFile("main-shops-test", romName.substring(romName.lastIndexOf('.'))).toFile();
            if (!threeDs) {
                out.delete();
            }
            GameRandomizer.Results res = new GameRandomizer(s, null, rom, BUNDLE, threeDs)
                    .randomize(out.getAbsolutePath(), new PrintStream(new java.io.ByteArrayOutputStream()), 99L);
            assertTrue(res.wasSaveSuccessful(), () -> String.valueOf(res.getException()));
            List<Shop> after = rom.getShops();
            assertEquals(mainBefore, ids(after, true), romName + " " + mode + ": a regular Poke Mart changed");
            if (randomizable > 0) {
                assertNotEquals(otherBefore, ids(after, false), romName + " " + mode + ": no other shop changed");
            }
        }
    }
}
