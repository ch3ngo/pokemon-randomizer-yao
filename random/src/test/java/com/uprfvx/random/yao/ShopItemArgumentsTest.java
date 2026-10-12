package com.uprfvx.random.yao;

import com.uprfvx.romio.gamedata.Item;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShopItemArgumentsTest {

    // index = item id; id 0 is unused, id 3 is missing in this fake game
    private static final List<Item> ENGLISH = items("-", "Potion", "Rare Candy", null, "Fire Stone", "Super Potion");
    private static final List<Item> SPANISH = items("-", "Poción", "Caramelo Raro", null, "Piedra Fuego", "Superpoción");
    private static final List<Integer> PRICES = Arrays.asList(0, 300, 4800, 0, 2100, 700);

    private static List<Item> items(String... names) {
        List<Item> list = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            list.add(i == 0 || names[i] == null ? null : new Item(i, names[i]));
        }
        return list;
    }

    private static YaoSettings run(List<Item> items, String... args) {
        YaoSettings yao = new YaoSettings();
        ShopItemArguments.apply(yao, items, PRICES, Arrays.asList(args));
        return yao;
    }

    @Test
    public void resolvesByNameInTheRomLanguage() {
        assertEquals(List.of(new YaoSettings.CustomShopItem(2, 1)), run(ENGLISH, "Rare Candy:1").getCustomShopItems());
        assertEquals(List.of(new YaoSettings.CustomShopItem(2, 1)), run(SPANISH, "Caramelo Raro:1").getCustomShopItems());
    }

    @Test
    public void nameMatchingIgnoresCaseAccentsAndPunctuation() {
        assertEquals(1, run(SPANISH, "caramelo-raro:5").getCustomShopItems().size());
        assertEquals(1, run(SPANISH, "POCION:5").getCustomShopItems().size() == 1
                ? 1 : 0); // "pocion" is also contained in "superpocion", but the exact match wins
        assertEquals(1, run(SPANISH, "POCION:5").getCustomShopItems().get(0).itemId());
    }

    @Test
    public void resolvesById() {
        assertEquals(List.of(new YaoSettings.CustomShopItem(4, 2100)), run(ENGLISH, "4:2100").getCustomShopItems());
    }

    @Test
    public void uniquePartialNameIsAccepted() {
        assertEquals(4, run(ENGLISH, "fire:100").getCustomShopItems().get(0).itemId());
    }

    @Test
    public void ambiguousNameIsSkippedWithAWarning() {
        // "stone" is fine but "potion" matches Potion exactly; "ion" matches both potions partially
        YaoSettings yao = run(ENGLISH, "ion:1");
        assertTrue(yao.getCustomShopItems().isEmpty());
        assertEquals(1, yao.getWarnings().size());
    }

    @Test
    public void unknownNameOrIdIsSkippedWithAWarning() {
        YaoSettings yao = run(ENGLISH, "Master Ball:1", "999:1", "3:1", "0:1", ":5");
        assertTrue(yao.getCustomShopItems().isEmpty());
        assertEquals(5, yao.getWarnings().size());
    }

    @Test
    public void unreadablePriceFallsBackToTheVanillaPrice() {
        YaoSettings yao = run(ENGLISH, "Fire Stone:abc", "Rare Candy", "Potion:");
        assertEquals(List.of(
                new YaoSettings.CustomShopItem(4, 2100),
                new YaoSettings.CustomShopItem(2, 4800),
                new YaoSettings.CustomShopItem(1, 300)), yao.getCustomShopItems());
        assertEquals(3, yao.getWarnings().size());
    }

    @Test
    public void badPricesAreRepairedByTheSettings() {
        YaoSettings yao = run(ENGLISH, "Fire Stone:0", "Rare Candy:-7");
        assertEquals(List.of(
                new YaoSettings.CustomShopItem(4, 1),
                new YaoSettings.CustomShopItem(2, 1)), yao.getCustomShopItems());
    }

    @Test
    public void garbageInputNeverThrows() {
        assertDoesNotThrow(() -> run(ENGLISH, "", "   ", ":", "::", "a:b:c", "\u0000", null));
    }
}
