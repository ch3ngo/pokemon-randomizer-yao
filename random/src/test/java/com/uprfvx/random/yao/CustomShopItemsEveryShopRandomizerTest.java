package com.uprfvx.random.yao;

import com.uprfvx.random.Settings;
import com.uprfvx.random.randomizers.ItemRandomizer;
import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Shop;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romio.RomOpener;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.uprfvx.random.GameRandomizer;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ResourceBundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Added for YAO (13/10/2026, web UI feedback item 46). ROM based check (runs with the {@code testROMs} task, skipped
 * when the ROM is not in {@code roms/}): custom shop items must be sold by EVERY regular Poke Mart from the start of
 * the game, including the very first ones (which only sell a couple of everyday supplies) and in the games whose
 * shops cannot grow.
 */
public class CustomShopItemsEveryShopRandomizerTest {

    @ParameterizedTest
    @ValueSource(strings = {"Crystal (U).gbc", "Emerald (U).gba", "FireRed (U).gba",
            "Diamond (E).nds", "Platinum (U).nds", "SoulSilver (U).nds", "SoulSilver (S).nds",
            "Black (U).nds", "Black 2 (U).nds", "Black 2 (S).nds",
            "X (E).3ds", "Omega Ruby (E).3ds", "Sun (E).3ds", "Ultra Sun (E).3ds"})
    public void everyRegularMartSellsEveryCustomItem(String romName) throws Exception {
        String romsPath = System.getProperty("romsPath");
        Assumptions.assumeTrue(romsPath != null, "no romsPath");
        File romFile = new File(romsPath, romName);
        Assumptions.assumeTrue(romFile.isFile(), romName + " is not in roms/");
        RomOpener opener = new RomOpener();
        opener.setExtraMemoryAvailable(Runtime.getRuntime().maxMemory() >= 3_800_000_000L);
        RomOpener.Results r = opener.openRomFile(romFile);
        assertTrue(r.wasOpeningSuccessful());
        RomHandler rom = r.getRomHandler();
        Assumptions.assumeTrue(rom.hasShopSupport());

        List<Shop> before = rom.getShops();
        for (Shop shop : before) {
            System.out.println("SHOP " + romName + " regular=" + ItemRandomizer.isRegularMart(shop) + " special="
                    + shop.isSpecialShop() + " [" + shop.getName() + "] " + shop.getItems());
        }
        // Rare Candy exists in every game; the second item is only used when the game has it.
        int[] wanted = rom.generationOfPokemon() == 1 ? new int[]{ItemIDs.rareCandy}
                : new int[]{ItemIDs.rareCandy, ItemIDs.maxRevive};
        List<YaoSettings.CustomShopItem> custom = new ArrayList<>();
        for (int id : wanted) {
            custom.add(new YaoSettings.CustomShopItem(id, 100));
        }
        ItemRandomizer randomizer = new ItemRandomizer(rom, new Settings(), new Random(1));
        randomizer.addCustomItemsToShops(custom);
        randomizer.getCustomShopNotes().forEach(n -> System.out.println("NOTE " + romName + ": " + n));

        List<Item> allItems = rom.getItems();
        List<Shop> after = rom.getShops();
        boolean canGrow = rom.canChangeShopSizes();
        Item pokeBall = allItems.get(ItemIDs.pokeBall);
        int index = -1;
        for (Shop shop : after) {
            index++;
            if (!ItemRandomizer.isRegularMart(shop)) {
                continue;
            }
            List<Item> usable = new ArrayList<>();
            for (int id : wanted) {
                Item item = allItems.get(id);
                if (item != null && item.isAllowed()) {
                    usable.add(item);
                }
            }
            long present = usable.stream().filter(shop.getItems()::contains).count();
            // Shops that can grow take every item. Fixed size shops keep their Poke Ball and give every other slot
            // to a custom item, so they hold min(items, slots - 1) of them (all slots when there is no Poke Ball).
            int sizeBefore = before.get(index).getItems().size();
            boolean hadBall = before.get(index).getItems().contains(pokeBall);
            long expected = canGrow ? usable.size() : Math.min(usable.size(), sizeBefore - (hadBall ? 1 : 0));
            assertTrue(present >= expected, romName + ": regular shop [" + shop.getName() + "] " + shop.getItems()
                    + " holds " + present + " custom item(s), expected at least " + expected);
            if (!canGrow && sizeBefore >= 2) {
                assertTrue(shop.getItems().contains(pokeBall) || !hadBall, romName + " [" + shop.getName() + "] lost its Poke Ball");
            }
        }
    }

    /** The written game file is opened again and its regular shops are read back from the file itself. */
    @ParameterizedTest
    @ValueSource(strings = {"Black 2 (S).nds", "SoulSilver (S).nds", "Emerald (U).gba", "Crystal (U).gbc"})
    public void customItemsSurviveWritingAndReopeningTheGame(String romName) throws Exception {
        String romsPath = System.getProperty("romsPath");
        Assumptions.assumeTrue(romsPath != null, "no romsPath");
        File romFile = new File(romsPath, romName);
        Assumptions.assumeTrue(romFile.isFile(), romName + " is not in roms/");
        RomOpener opener = new RomOpener();
        opener.setExtraMemoryAvailable(Runtime.getRuntime().maxMemory() >= 3_800_000_000L);
        RomHandler rom = opener.openRomFile(romFile).getRomHandler();
        Settings settings = new com.uprfvx.random.web.SettingsSchema().build(java.util.Map.of(), -1, rom.getROMName());
        settings.getYaoSettings().addCustomShopItem(ItemIDs.rareCandy, 150);
        settings.getYaoSettings().addCustomShopItem(ItemIDs.maxRevive, 400);
        File out = Files.createTempFile("custom-shops-test", romName.substring(romName.lastIndexOf('.'))).toFile();
        out.delete();
        GameRandomizer.Results res = new GameRandomizer(settings, null, rom,
                ResourceBundle.getBundle("com/uprfvx/random/gui/Bundle"), false)
                .randomize(out.getAbsolutePath(), new PrintStream(new java.io.ByteArrayOutputStream()), 7L);
        assertTrue(res.wasSaveSuccessful(), () -> String.valueOf(res.getException()));
        RomOpener.Results reopened = new RomOpener().openRomFile(out);
        assertTrue(reopened.wasOpeningSuccessful());
        RomHandler back = reopened.getRomHandler();
        List<Item> items = back.getItems();
        int regular = 0;
        for (Shop shop : back.getShops()) {
            if (ItemRandomizer.isRegularMart(shop)) {
                regular++;
                assertTrue(shop.getItems().contains(items.get(ItemIDs.rareCandy)), romName + " [" + shop.getName() + "] " + shop.getItems());
                assertTrue(shop.getItems().contains(items.get(ItemIDs.maxRevive)), romName + " [" + shop.getName() + "] " + shop.getItems());
            }
        }
        assertTrue(regular > 0);
        assertEquals(150, back.getShopPrices().get(ItemIDs.rareCandy));
        out.delete();
    }
}
