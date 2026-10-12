package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  LockeModeApplier.java - Locke and Soft-locke challenge rules.         --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.PickupItem;
import com.uprfvx.romio.gamedata.Shop;
import com.uprfvx.romio.romhandlers.PatchResult;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Applies the Locke rules. Two parts, handled independently so that one failing never blocks the other:
 * <ul>
 *     <li><b>Items</b> (Full Locke only, plain data edits): Revive, Max Revive, Revival Herb and Sacred Ash are
 *     taken out of shops (or swapped for a Hyper Potion when the shop cannot shrink), field items and pickup.</li>
 *     <li><b>Pokemon Centers</b> (Locke and Soft-locke, a code patch): healing the party skips fainted Pokemon.
 *     Only done with the code patch notice accepted, and only in games that support it.</li>
 * </ul>
 * Everything is reported as notes for the log, and nothing here throws because of a game's data.
 *
 * <p>Limits worth knowing: items given directly by a script (a gift Revive from a character) cannot be changed,
 * and a Pokemon deposited in a PC box is restored when withdrawn, so a dead Pokemon has to stay boxed for good.
 */
public final class LockeModeApplier {

    private LockeModeApplier() {
    }

    private static final Set<Integer> REVIVE_ITEMS =
            Set.of(ItemIDs.revive, ItemIDs.maxRevive, ItemIDs.revivalHerb, ItemIDs.sacredAsh);

    public static List<String> apply(RomHandler rom, YaoSettings.LockeMode mode, boolean codePatchesAccepted) {
        List<String> notes = new ArrayList<>();
        if (mode == null || mode == YaoSettings.LockeMode.NONE) {
            return notes;
        }
        String label = mode == YaoSettings.LockeMode.FULL ? "Locke mode" : "Soft-locke mode";

        if (mode == YaoSettings.LockeMode.FULL) {
            try {
                removeReviveItems(rom, notes);
            } catch (RuntimeException e) {
                notes.add(label + ": the revive items could not be removed (" + e + ").");
            }
        }

        if (!codePatchesAccepted) {
            notes.add(label + ": Pokemon Centers will still revive fainted Pokemon, because the notice about "
                    + "patching the game's code was not accepted.");
        } else {
            PatchResult result;
            try {
                result = rom.applyCentersSkipFainted();
            } catch (RuntimeException e) {
                result = PatchResult.notApplied("The Pokemon Center change failed (" + e + ").");
            }
            notes.add(label + ": " + (result.applied() ? "applied. " : "Pokemon Centers NOT changed. ") + result.message());
        }
        return notes;
    }

    private static void removeReviveItems(RomHandler rom, List<String> notes) {
        List<Item> allItems = rom.getItems();
        Item replacement = ItemIDs.hyperPotion < allItems.size() ? allItems.get(ItemIDs.hyperPotion) : null;
        if (replacement == null) {
            notes.add("Locke mode (experimental): no replacement item exists in this game, the revive items were left in place.");
            return;
        }

        int shopsRemoved = 0;
        int shopsSwapped = 0;
        if (rom.hasShopSupport()) {
            List<Shop> shops = rom.getShops();
            boolean canShrink = rom.canChangeShopSizes();
            for (Shop shop : shops) {
                List<Item> items = shop.getItems();
                for (int i = items.size() - 1; i >= 0; i--) {
                    if (REVIVE_ITEMS.contains(items.get(i).getId())) {
                        // never leave a shop empty, and keep the size where the game cannot change it
                        if (canShrink && items.size() > 1) {
                            items.remove(i);
                            shopsRemoved++;
                        } else {
                            items.set(i, replacement);
                            shopsSwapped++;
                        }
                    }
                }
            }
            if (shopsRemoved + shopsSwapped > 0) {
                rom.setShops(shops);
            }
        }

        int fieldReplaced = 0;
        try {
            List<Item> field = new ArrayList<>(rom.getFieldItems());
            for (int i = 0; i < field.size(); i++) {
                if (field.get(i) != null && REVIVE_ITEMS.contains(field.get(i).getId())) {
                    field.set(i, replacement);
                    fieldReplaced++;
                }
            }
            if (fieldReplaced > 0) {
                rom.setFieldItems(field);
            }
        } catch (UnsupportedOperationException | NullPointerException e) {
            // this game does not expose field items: nothing to change
        }

        int pickupReplaced = 0;
        try {
            List<PickupItem> current = rom.getPickupItems();
            List<PickupItem> updated = new ArrayList<>();
            for (PickupItem p : current) {
                if (p.getItem() != null && REVIVE_ITEMS.contains(p.getItem().getId())) {
                    PickupItem swapped = new PickupItem(replacement);
                    for (int j = 0; j < PickupItem.PROBABILITY_SLOTS; j++) {
                        swapped.getProbabilities()[j] = p.getProbabilities()[j];
                    }
                    updated.add(swapped);
                    pickupReplaced++;
                } else {
                    updated.add(p);
                }
            }
            if (pickupReplaced > 0) {
                rom.setPickupItems(updated);
            }
        } catch (UnsupportedOperationException | NullPointerException e) {
            // no pickup table in this game
        }

        notes.add("Locke mode (experimental): revive items removed from " + shopsRemoved + " shop slot(s), swapped for a "
                + replacement.getName() + " in " + shopsSwapped + " more, replaced in " + fieldReplaced
                + " field item(s) and " + pickupReplaced + " pickup slot(s). Items handed over by scripts "
                + "(gifts) cannot be changed.");
    }
}
