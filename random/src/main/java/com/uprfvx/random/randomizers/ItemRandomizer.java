package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.random.yao.ShopPriceLimits;
import com.uprfvx.random.yao.YaoSettings;
import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.PickupItem;
import com.uprfvx.romio.gamedata.Shop;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.*;
import java.util.stream.Collectors;

public class ItemRandomizer extends Randomizer {

    // TODO: the whole deal with Mega Stones
    // Mega Stones should get treated better. There currently *is* some logic for dealing with them,
    // in that they are the only "unique items" when Balanced Item Prices make them buyable.
    // But whether they show up as (non-random) field items, and the behavior of the Shop and Pickup
    // randomization... it is unclear and varies from game to game.
    // Ultimately, there is a need of design direction - how *should* Mega Stones be treated -
    // which I'm not taking charge of at the time being.
    // Why? Because it requires 3DS hacking which is tricky as is, more so when your computer
    // can't run said games in an emulator.
    // And the current situation isn't dire enough to keep a major refactor branch from rejoining
    // with the main branch, for yet who-knows-how-many months. So here we are!

    private boolean fieldChangesMade;
    private boolean shopChangesMade;
    private final List<String> customShopNotes = new ArrayList<>();
    private boolean pickupChangesMade;

    public ItemRandomizer(RomHandler romHandler, Settings settings, Random random) {
        super(romHandler, settings, random);
    }

    /**
     * Returns whether any changes have been made to Field Items.
     */
    public boolean isFieldChangesMade() {
        return fieldChangesMade;
    }

    /**
     * Returns whether any changes have been made to Shop Items.
     */
    public boolean isShopChangesMade() {
        return shopChangesMade;
    }

    /**
     * Returns whether any changes have been made to Pickup Items.
     */
    public boolean isPickupChangesMade() {
        return pickupChangesMade;
    }

    public void randomizeFieldItems() {
        // TMs and non-TMs must end up at the same indices. Complicates the algorithm somewhat.
        List<Item> current = romHandler.getFieldItems();

        Stack<Item> tms = new Stack<>();
        Stack<Item> nonTMs = new Stack<>();
        for (Item item : current) {
            (item.isTM() ? tms : nonTMs).push(item);
        }

        switch (settings.getFieldItemsMod()) {
            case SHUFFLE:
                Collections.shuffle(tms, random);
                Collections.shuffle(nonTMs, random);
                break;
            case RANDOM:
            case RANDOM_EVEN:
                randomizeTMFieldItems(tms);
                randomizeNonTMFieldItems(nonTMs);
                break;
        }

        List<Item> combined = new ArrayList<>(current.size());
        for (Item item : current) {
            combined.add((item.isTM() ? tms : nonTMs).pop());
        }

        romHandler.setFieldItems(combined);
        fieldChangesMade = true;
    }

    /**
     * Randomizes TM field items, by modifying the input list.
     */
    private void randomizeTMFieldItems(List<Item> tms) {
        List<Item> allTMs = romHandler.getItems().stream()
                .filter(Objects::nonNull).filter(Item::isTM)
                .collect(Collectors.toList());
        Set<Item> requiredTMs = romHandler.getRequiredFieldTMs();

        int neededTMAmount = tms.size();

        Set<Item> newTMs = new HashSet<>(requiredTMs);
        while (newTMs.size() < neededTMAmount && newTMs.size() < allTMs.size()) {
            newTMs.add(allTMs.get(random.nextInt(allTMs.size()))); // duplicates get automatically ignored by the Set
        }
        if (newTMs.size() != neededTMAmount) {
            throw new RandomizationException("Could not randomize TM field items, too many TMs requested.");
        }

        tms.clear();
        tms.addAll(newTMs);
        Collections.shuffle(tms, random);
    }

    /**
     * Randomizes non-TM field items, by modifying the input list.
     */
    private void randomizeNonTMFieldItems(List<Item> nonTMs) {

        boolean banBadItems = settings.isBanBadRandomFieldItems();
        boolean uniqueItems = !settings.isBalanceShopPrices();
        boolean evenItems = settings.getFieldItemsMod() == Settings.FieldItemsMod.RANDOM_EVEN;

        List<Item> possible = new ArrayList<>(banBadItems ? romHandler.getNonBadItems() : romHandler.getAllowedItems());
        possible.removeIf(Item::isTM);
        Set<Item> uniqueNoSellItems = uniqueItems ? romHandler.getMegaStones() : new HashSet<>();

        int neededNonTMCount = nonTMs.size();
        nonTMs.clear();

        // Completely different algorithms whether items are "evenly distributed", or entirely random
        // Though both of them are simple enough to be best left uncommented. Just read them.
        if (evenItems) {
            Stack<Item> remaining = new Stack<>();
            Collections.shuffle(remaining, random);
            for (int i = 0; i < neededNonTMCount; i++) {
                if (remaining.isEmpty()) {
                    remaining.addAll(possible);
                    Collections.shuffle(remaining, random);
                }
                Item chosen = remaining.pop();
                nonTMs.add(chosen);
                if (uniqueNoSellItems.contains(chosen)) {
                    possible.remove(chosen);
                }
            }

        } else {
            for (int i = 0; i < neededNonTMCount; i++) {
                Item chosen = possible.get(random.nextInt(possible.size()));
                nonTMs.add(chosen);
                if (uniqueNoSellItems.contains(chosen)) {
                    possible.remove(chosen);
                }
            }
        }

    }

    /** A copy of the shops as they are now (for protected items, see ProtectedShopItems). Null if unsupported. */
    public List<Shop> snapshotShops() {
        List<Shop> shops = romHandler.hasShopSupport() ? romHandler.getShops() : null;
        return shops == null ? null : deepCopy(shops);
    }

    /**
     * Puts the protected items back in their original slots after the shops were randomized or shuffled.
     * Modified from FVX (10/10/2026). Returns how many slots were restored.
     */
    public int restoreProtectedShopItems(List<Shop> before, java.util.Set<Integer> protectedIds) {
        List<Shop> after = deepCopy(romHandler.getShops());
        int restored = com.uprfvx.random.yao.ProtectedShopItems.restore(before, after, protectedIds);
        if (restored > 0) {
            romHandler.setShops(after);
        }
        return restored;
    }

    public void shuffleShopItems() {
        List<Shop> shops = romHandler.getShops();
        if (shops == null) return;

        List<Item> allItems = new ArrayList<>();
        for (Shop shop : shops) {
            if (!isRandomizableShop(shop)) {
                continue; // temporary
            }
            allItems.addAll(shop.getItems());
        }
        Collections.shuffle(allItems, random);

        Iterator<Item> allItemsIter = allItems.iterator();

        for (Shop shop : shops) {
            if (!isRandomizableShop(shop)) {
                continue;
            }
            ListIterator<Item> shopIter = shop.getItems().listIterator();
            while (shopIter.hasNext()) {
                shopIter.next();
                shopIter.set(allItemsIter.next()); // assumes allItemsIter will always have a next
            }
        }

        romHandler.setShops(shops);
        shopChangesMade = true;
    }

    /**
     * Added (modified from FVX, 12/10/2026, user decision, corrected 12/10/2026): the regular Poke Marts (the
     * progression shops every town has, see {@link #isRegularMart}) are never shuffled or randomized in any
     * generation. Only FVX's "special" shops are: the secondary shops (balls and mail), department store floors,
     * herb, incense, vitamin and X item shops. Note that {@link Shop#isMainGame()} does NOT mean "regular Poke Mart":
     * in the data it only separates special shops reachable in the main story from post game ones, so it is not used
     * to decide what may be randomized.
     */
    private static boolean isRandomizableShop(Shop shop) {
        return shop.isSpecialShop();
    }

    /**
     * A regular Poke Mart: not a special shop and not a TM shop. These are the shops the custom shop items go to
     * and that randomization never touches.
     */
    public static boolean isRegularMart(Shop shop) {
        if (shop.isSpecialShop() || shop.getItems() == null || shop.getItems().isEmpty()) {
            return false;
        }
        return shop.getItems().stream().noneMatch(i -> i != null && i.isTM());
    }

    /** True when this game has at least one shop that shop randomization is allowed to change. */
    public boolean hasRandomizableShops() {
        List<Shop> shops = romHandler.hasShopSupport() ? romHandler.getShops() : null;
        return shops != null && shops.stream().anyMatch(ItemRandomizer::isRandomizableShop);
    }

    public void randomizeShopItems() {
        Set<Item> possible = setupPossible();
        Set<Item> guaranteed = setupGuaranteed();

        List<Shop> shops = deepCopy(romHandler.getShops());

        List<Item> newItems = setupNewItems(possible, guaranteed, shops);
        // Modified from FVX (11/10/2026): a shop slot that originally held a TM now gets another TM,
        // never a regular item. See setupNewTMItems for why this matters even though none of the
        // currently supported games' randomizable ("special") shops happen to hold a TM today.
        List<Item> newTMItems = setupNewTMItems(shops);

        placeNewItems(newItems, newTMItems, shops, guaranteed);

        romHandler.setShops(shops);
        shopChangesMade = true;
    }

    private Set<Item> setupPossible() {
        Set<Item> possible = new HashSet<>(settings.isBanBadRandomShopItems() ?
                romHandler.getNonBadItems() : romHandler.getAllowedItems());
        possible.removeIf(Item::isTM);
        if (settings.isBanRegularShopItems()) {
            possible.removeAll(romHandler.getRegularShopItems());
        }
        if (settings.isBanOPShopItems()) {
            possible.removeAll(romHandler.getOPShopItems());
        }

        return possible;
    }

    private Set<Item> setupGuaranteed() {
        Set<Item> guaranteed = new HashSet<>();
        if (settings.isGuaranteeEvolutionItems()) {
            guaranteed.addAll(romHandler.getEvolutionItems());
        }
        if (settings.isGuaranteeXItems()) {
            guaranteed.addAll(romHandler.getXItems());
        }
        return guaranteed;
    }

    private List<Shop> deepCopy(List<Shop> original) {
        List<Shop> copy = new ArrayList<>(original.size());
        original.forEach(shop -> copy.add(new Shop(shop)));
        return copy;
    }

    private List<Item> setupNewItems(Set<Item> possible, Set<Item> guaranteed, List<Shop> shops) {
        List<Item> newItems = new ArrayList<>(guaranteed);

        int shopItemCount = shops.stream().filter(ItemRandomizer::isRandomizableShop).mapToInt(s -> s.getItems().size()).sum();
        // Guaranteed items now have to fit in the randomizable shops (the main shops are never touched), so keep
        // only as many as there are free non-TM slots.
        int freeSlots = shopItemCount - countTMSlots(shops);
        if (newItems.size() > freeSlots) {
            newItems = new ArrayList<>(newItems.subList(0, Math.max(0, freeSlots)));
            guaranteed.retainAll(newItems);
        }
        shopItemCount -= guaranteed.size();
        // TM slots are no longer drawn from this pool (modified from FVX, 11/10/2026): they get their own
        // pool of TMs, see setupNewTMItems.
        shopItemCount -= countTMSlots(shops);

        possible.removeAll(guaranteed);

        Stack<Item> remaining = new Stack<>();
        Collections.shuffle(remaining, random);
        for (int i = 0; i < shopItemCount; i++) {
            if (remaining.isEmpty()) {
                remaining.addAll(possible);
                Collections.shuffle(remaining, random);
            }
            newItems.add(remaining.pop());
        }
        return newItems;
    }

    /**
     * Added (modified from FVX, 11/10/2026): a shop slot that originally held a TM should keep selling a
     * TM after randomization, not a regular item. {@link #setupPossible()} excludes TMs from the general
     * pool entirely (TMs are randomized separately, see {@link #randomizeTMFieldItems}), so without this,
     * {@link #placeNewItems} would quietly turn a TM slot into a random non-TM item. In every currently
     * supported game this never actually triggers, since TMs are always sold through their own dedicated,
     * non-special shops (confirmed by inspecting real ROM data for Emerald, SoulSilver, Black 2 and Sun:
     * every special shop has zero TM items, every shop with TM items is not a special shop), but the
     * randomizer should not rely on that coincidence holding for every game it will ever support.
     */
    private List<Item> setupNewTMItems(List<Shop> shops) {
        int tmSlotCount = countTMSlots(shops);
        List<Item> newTMItems = new ArrayList<>(tmSlotCount);
        if (tmSlotCount == 0) {
            return newTMItems;
        }
        List<Item> allTMs = romHandler.getItems().stream()
                .filter(Objects::nonNull).filter(Item::isTM)
                .collect(Collectors.toList());
        Stack<Item> remaining = new Stack<>();
        for (int i = 0; i < tmSlotCount; i++) {
            if (remaining.isEmpty()) {
                remaining.addAll(allTMs);
                Collections.shuffle(remaining, random);
            }
            newTMItems.add(remaining.pop());
        }
        return newTMItems;
    }

    private int countTMSlots(List<Shop> shops) {
        return (int) shops.stream().filter(ItemRandomizer::isRandomizableShop).flatMap(s -> s.getItems().stream())
                .filter(item -> item != null && item.isTM()).count();
    }

    private void placeNewItems(List<Item> newItems, List<Item> newTMItems, List<Shop> shops, Set<Item> guaranteed) {
        // Modified 12/10/2026: only special shops are filled. As in FVX, the guaranteed items go to the special
        // shops that are reachable in the main game (isMainGame), the other special shops are filled first.
        List<Shop> mainGameShops = new ArrayList<>();
        List<Shop> otherShops = new ArrayList<>();
        for (Shop shop : shops) {
            if (!isRandomizableShop(shop)) {
                continue;
            }
            (shop.isMainGame() ? mainGameShops : otherShops).add(shop);
        }

        Collections.shuffle(newItems, random);
        Collections.shuffle(newTMItems, random);
        for (Shop shop : otherShops) {
            for (int i = 0; i < shop.getItems().size(); i++) {
                Item original = shop.getItems().get(i);
                if (original != null && original.isTM()) {
                    shop.getItems().set(i, newTMItems.remove(newTMItems.size() - 1));
                    continue;
                }
                // skip over guaranteed items while others are left, so they end up in the main game shops
                int pick = -1;
                for (int k = 0; k < newItems.size(); k++) {
                    if (!guaranteed.contains(newItems.get(k))) {
                        pick = k;
                        break;
                    }
                }
                if (pick < 0) {
                    pick = 0;
                }
                shop.getItems().set(i, newItems.remove(pick));
            }
        }
        Collections.shuffle(newItems, random);
        for (Shop shop : mainGameShops) {
            for (int i = 0; i < shop.getItems().size(); i++) {
                Item original = shop.getItems().get(i);
                if (original != null && original.isTM()) {
                    shop.getItems().set(i, newTMItems.remove(newTMItems.size() - 1));
                    continue;
                }
                shop.getItems().set(i, newItems.remove(newItems.size() - 1));
            }
        }

        if (!newItems.isEmpty() || !newTMItems.isEmpty()) {
            throw new IllegalStateException("newItems has not been emptied");
        }
    }

    /**
     * Adds the user's custom items to the regular Poke Marts and sets their prices.
     * Modified from FVX (10/10/2026).
     *
     * <p>Unlike {@link #addCheapRareCandiesToShops()} this only touches the regular Poke Marts ({@link #isRegularMart}), never the shops that use another currency, and it never fails the randomization
     * because of a bad value. Items the game does not have or does not allow in shops are skipped,
     * prices are adapted to what the generation can store (Gen 5 to 7 prices are multiples of 10, so a
     * price of 1 becomes 10), and if the game rejects the write the original shops are restored and the
     * write is retried with one item fewer, until it works. When a game cannot enlarge its shops, the items
     * replace the least important existing ones (never everyday supplies) instead of being skipped. Everything skipped, adjusted or dropped is
     * recorded in {@link #getCustomShopNotes()} so it shows up in the log.
     */
    public void addCustomItemsToShops(List<YaoSettings.CustomShopItem> customItems) {
        customShopNotes.clear();
        if (customItems.isEmpty()) {
            return;
        }
        List<Shop> currentShops = romHandler.getShops();
        if (currentShops == null || !romHandler.hasShopSupport()) {
            customShopNotes.add("This game's shops cannot be edited; no custom shop items were added.");
            return;
        }
        // Games whose shops cannot be enlarged (Gen 6 and 7, and a few others): instead of giving up, the custom
        // items take the place of the least important items already in the shop.
        final boolean canGrow = romHandler.canChangeShopSizes();
        if (currentShops.stream().noneMatch(ItemRandomizer::isRegularMart)) {
            customShopNotes.add("No regular shop was found in this game; no custom shop items were added.");
            return;
        }

        // 1. Work out which items can really be added, and at what price.
        int generation = romHandler.generationOfPokemon();
        List<Item> allItems = romHandler.getItems();
        List<Item> usableItems = new ArrayList<>();
        List<Integer> usablePrices = new ArrayList<>();
        for (YaoSettings.CustomShopItem custom : customItems) {
            int id = custom.itemId();
            Item item = id < allItems.size() ? allItems.get(id) : null;
            if (item == null || !item.isAllowed()) {
                customShopNotes.add("Item #" + id + " does not exist in this game or cannot be sold; skipped.");
                continue;
            }
            int price = ShopPriceLimits.normalize(generation, custom.price());
            if (price != custom.price()) {
                customShopNotes.add(item.getName() + ": the requested price " + custom.price()
                        + " cannot be stored in this game, using " + price + " instead.");
            }
            usableItems.add(item);
            usablePrices.add(price);
        }
        if (usableItems.isEmpty()) {
            return;
        }

        // 2. Snapshot the current state so that a failed write can be undone completely.
        List<Shop> originalShops = deepCopy(currentShops);
        List<Integer> originalPrices = new ArrayList<>(romHandler.getShopPrices());

        // 3. Try to write everything; if the game rejects it, restore and retry with one item fewer.
        for (int count = usableItems.size(); count > 0; count--) {
            List<Shop> newShops = deepCopy(originalShops);
            List<Integer> newPrices = new ArrayList<>(originalPrices);
            List<String> attemptNotes = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Item item = usableItems.get(i);
                int shopsChanged = 0;
                int noSlot = 0;
                int mainShops = 0;
                for (Shop shop : newShops) {
                    if (!isRegularMart(shop)) {
                        continue;
                    }
                    mainShops++;
                    if (shop.getItems().contains(item)) {
                        continue;
                    }
                    if (canGrow) {
                        shop.getItems().add(item);
                        shopsChanged++;
                    } else {
                        int slot = replaceableSlot(shop, usableItems);
                        if (slot >= 0) {
                            shop.getItems().set(slot, item);
                            shopsChanged++;
                        } else {
                            noSlot++;
                        }
                    }
                }
                newPrices.set(item.getId(), usablePrices.get(i));
                int alreadyThere = mainShops - shopsChanged - noSlot;
                attemptNotes.add(item.getName() + ": price " + usablePrices.get(i) + ", "
                        + (canGrow ? "added to " : "put in place of another item in ") + shopsChanged
                        + " regular shop(s)" + (alreadyThere > 0 ? ", already sold in " + alreadyThere : "")
                        + " (" + mainShops + " regular shops in total)."
                        + (noSlot > 0 ? " " + noSlot + " shop(s) had no item that could be replaced." : ""));
            }
            try {
                romHandler.setShops(newShops);
                romHandler.setShopPrices(newPrices);
            } catch (RuntimeException e) {
                try {
                    romHandler.setShops(deepCopy(originalShops));
                    romHandler.setShopPrices(new ArrayList<>(originalPrices));
                } catch (RuntimeException restoreFailure) {
                    throw new RandomizationException("Could not restore the shops after failing to add custom "
                            + "shop items: " + restoreFailure.getMessage());
                }
                continue; // try again with one item fewer
            }
            customShopNotes.addAll(attemptNotes);
            for (int i = count; i < usableItems.size(); i++) {
                customShopNotes.add(usableItems.get(i).getName()
                        + ": the game could not hold this many extra items; not added.");
            }
            shopChangesMade = true;
            return;
        }
        customShopNotes.add("The game could not hold any of the custom shop items; the shops were left unchanged.");
    }

    /**
     * For games whose shops cannot grow: the slot a custom item should take in this shop. Looks from the end of
     * the list for an item that is neither one of the everyday supplies nor another custom item. -1 if none.
     */
    private int replaceableSlot(Shop shop, List<Item> customItems) {
        List<Item> items = shop.getItems();
        for (int i = items.size() - 1; i >= 0; i--) {
            Item existing = items.get(i);
            if (!customItems.contains(existing)
                    && !com.uprfvx.random.yao.ProtectedShopItems.BASICS.contains(existing.getId())) {
                return i;
            }
        }
        return -1;
    }

    /** What {@link #addCustomItemsToShops} did, one line each, for the randomization log. */
    public List<String> getCustomShopNotes() {
        return Collections.unmodifiableList(customShopNotes);
    }

    public void addCheapRareCandiesToShops() {
        addRareCandiesToShops();
        makeRareCandiesCheap();
        shopChangesMade = true;
    }

    private void addRareCandiesToShops() {
        List<Item> allItems = romHandler.getItems();
        List<Shop> shops = romHandler.getShops();
        for (Shop sh : shops) {
            sh.getItems().add(allItems.get(ItemIDs.rareCandy));
        }
        romHandler.setShops(shops);
    }

    private void makeRareCandiesCheap() {
        List<Integer> prices = romHandler.getShopPrices();
        prices.set(ItemIDs.rareCandy, 10);
        romHandler.setShopPrices(prices);
    }

    public void randomizePickupItems() {
        boolean banBadItems = settings.isBanBadRandomPickupItems();

        List<Item> possibleItems = new ArrayList<>(banBadItems ? romHandler.getNonBadItems() : romHandler.getAllowedItems());
        if (!romHandler.canTMsBeHeld() || romHandler.isTMsReusable()) {
            // Normally these conditions overlap, but if TMs are made reusable we can get the latter but not the former,
            // and it's still no fun getting the same reusable TM over and over again.
            possibleItems.removeIf(Item::isTM);
        }
        List<PickupItem> currentItems = romHandler.getPickupItems();
        List<PickupItem> newItems = new ArrayList<>();
        for (PickupItem currentItem : currentItems) {
            Item picked = possibleItems.get(random.nextInt(possibleItems.size()));

            PickupItem pickupItem = new PickupItem(picked);
            for (int j = 0; j < PickupItem.PROBABILITY_SLOTS; j++) {
                pickupItem.getProbabilities()[j] = currentItem.getProbabilities()[j];
            }
            newItems.add(pickupItem);
        }

        romHandler.setPickupItems(newItems);
        pickupChangesMade = true;
    }

}
