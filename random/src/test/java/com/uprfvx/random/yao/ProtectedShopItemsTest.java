package com.uprfvx.random.yao;

import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Shop;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ProtectedShopItemsTest {

    private static final Item POKE_BALL = new Item(ItemIDs.pokeBall, "Poke Ball");
    private static final Item POTION = new Item(ItemIDs.potion, "Potion");
    private static final Item FIRE_STONE = new Item(82, "Fire Stone");
    private static final Item REVIVE = new Item(ItemIDs.revive, "Revive");
    private static final Item NUGGET = new Item(110, "Nugget");

    private static Shop shop(Item... items) {
        Shop s = new Shop();
        s.setItems(new ArrayList<>(List.of(items)));
        s.setName("shop");
        return s;
    }

    private static List<Shop> copy(List<Shop> shops) {
        List<Shop> out = new ArrayList<>();
        shops.forEach(s -> out.add(new Shop(s)));
        return out;
    }

    @Test
    public void protectedItemsReturnToTheirOriginalSlots() {
        List<Shop> before = List.of(shop(POKE_BALL, POTION, FIRE_STONE), shop(REVIVE, POKE_BALL));
        List<Shop> after = copy(List.of(shop(NUGGET, REVIVE, FIRE_STONE), shop(POTION, NUGGET)));

        int restored = ProtectedShopItems.restore(before, after, Set.of(ItemIDs.pokeBall, ItemIDs.potion));

        assertEquals(3, restored);
        assertEquals(List.of(POKE_BALL, POTION, FIRE_STONE), after.get(0).getItems());
        assertEquals(List.of(POTION, POKE_BALL), after.get(1).getItems()); // slot 0 was Revive: not protected
    }

    @Test
    public void slotsThatAlreadyHoldTheirItemAreNotCounted() {
        List<Shop> before = List.of(shop(POKE_BALL, FIRE_STONE));
        List<Shop> after = copy(before);
        assertEquals(0, ProtectedShopItems.restore(before, after, Set.of(ItemIDs.pokeBall)));
    }

    @Test
    public void nothingProtectedChangesNothing() {
        List<Shop> before = List.of(shop(POKE_BALL));
        List<Shop> after = copy(List.of(shop(NUGGET)));
        assertEquals(0, ProtectedShopItems.restore(before, after, Set.of()));
        assertEquals(List.of(NUGGET), after.get(0).getItems());
    }

    @Test
    public void shopsThatDoNotLineUpAreLeftAloneInsteadOfRiskingTheWrongSlot() {
        List<Shop> before = List.of(shop(POKE_BALL, POTION));
        List<Shop> afterOtherSize = copy(List.of(shop(NUGGET)));
        assertEquals(0, ProtectedShopItems.restore(before, afterOtherSize, Set.of(ItemIDs.pokeBall)));
        assertEquals(List.of(NUGGET), afterOtherSize.get(0).getItems());

        List<Shop> afterOtherCount = copy(List.of(shop(NUGGET, NUGGET), shop(NUGGET)));
        assertEquals(0, ProtectedShopItems.restore(before, afterOtherCount, Set.of(ItemIDs.pokeBall)));
    }

    @Test
    public void nullInputsNeverThrow() {
        assertDoesNotThrow(() -> ProtectedShopItems.restore(null, null, null));
        assertEquals(0, ProtectedShopItems.restore(null, List.of(), Set.of(1)));
        assertEquals(0, ProtectedShopItems.restore(List.of(), null, Set.of(1)));
    }

    @Test
    public void basicsCoverBallsPotionsHealsAndRepels() {
        for (int id : new int[]{ItemIDs.pokeBall, ItemIDs.greatBall, ItemIDs.ultraBall, ItemIDs.potion, ItemIDs.superPotion,
                ItemIDs.hyperPotion, ItemIDs.maxPotion, ItemIDs.fullRestore, ItemIDs.antidote, ItemIDs.fullHeal,
                ItemIDs.repel, ItemIDs.escapeRope}) {
            assertTrue(ProtectedShopItems.BASICS.contains(id), "item " + id);
        }
        // evolution stones and Rare Candy are NOT basics: they are meant to be randomized or configured
        assertFalse(ProtectedShopItems.BASICS.contains(82));
        assertFalse(ProtectedShopItems.BASICS.contains(ItemIDs.rareCandy));
        assertFalse(ProtectedShopItems.BASICS.contains(ItemIDs.revive));
    }

    // --- settings ---

    @Test
    public void settingsRoundTripAndEffectiveSet() {
        YaoSettings yao = new YaoSettings();
        assertTrue(yao.effectiveProtectedShopItems().isEmpty());
        yao.setKeepBasicShopItems(true);
        yao.addProtectedShopItem(82);
        assertFalse(yao.isDefault());
        YaoSettings back = YaoSettings.fromBase64(yao.toBase64());
        assertTrue(back.isKeepBasicShopItems());
        assertEquals(Set.of(82), back.getProtectedShopItems());
        assertTrue(back.effectiveProtectedShopItems().containsAll(ProtectedShopItems.BASICS));
        assertTrue(back.effectiveProtectedShopItems().contains(82));
    }

    @Test
    public void invalidProtectedItemsAreDroppedWithAWarning() {
        YaoSettings yao = new YaoSettings();
        assertFalse(yao.addProtectedShopItem(0));
        assertFalse(yao.addProtectedShopItem(-4));
        YaoSettings fromText = YaoSettings.fromText("shopkeep.0=abc\nshopkeep.1=82\n");
        assertEquals(Set.of(82), fromText.getProtectedShopItems());
        assertEquals(1, fromText.getWarnings().size());
    }

    @Test
    public void protectedItemsAreResolvedByNameOrIdAndNeverThrow() {
        // like real item lists, the index of an item is its id
        List<Item> items = new ArrayList<>();
        for (int i = 0; i <= ItemIDs.pokeBall; i++) {
            items.add(i == ItemIDs.pokeBall ? POKE_BALL : null);
        }
        YaoSettings yao = new YaoSettings();
        ShopItemArguments.applyProtected(yao, items, List.of("4", "Poke Ball", "Nothing Here", "", "-3"));
        assertEquals(Set.of(ItemIDs.pokeBall), yao.getProtectedShopItems());
        assertFalse(yao.getWarnings().isEmpty());
    }
}
