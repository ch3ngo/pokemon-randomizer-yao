package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  ShopItemArguments.java - turns "--shop-item" arguments into items.    --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.Item;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves command line shop items of the form {@code <item>:<price>} against the items of the
 * loaded game. {@code <item>} is a numeric id or a name in the ROM's own language, so
 * {@code "Rare Candy:1"} works on an English ROM and {@code "Caramelo Raro:1"} on a Spanish one.
 *
 * <p>Like the rest of the YAO settings this never fails on bad input. A price that cannot be read
 * falls back to the item's vanilla price, a name that matches nothing (or is ambiguous) is skipped,
 * and every such decision is recorded as a warning on the {@link YaoSettings}.
 */
public final class ShopItemArguments {

    private ShopItemArguments() {
    }

    /**
     * @param items         the game's items, indexed by item id (entries may be null)
     * @param vanillaPrices the game's current item prices, indexed like {@code items}
     * @param arguments     the raw {@code <item>:<price>} strings
     */
    public static void apply(YaoSettings yao, List<Item> items, List<Integer> vanillaPrices, List<String> arguments) {
        for (String raw : arguments) {
            String argument = raw == null ? "" : raw.strip();
            int colon = argument.lastIndexOf(':');
            String itemPart = colon >= 0 ? argument.substring(0, colon).strip() : argument;
            String pricePart = colon >= 0 ? argument.substring(colon + 1).strip() : "";

            Item item = resolveItem(itemPart, items, yao, argument);
            if (item == null) {
                continue;
            }

            Integer price = parsePrice(pricePart);
            if (price == null) {
                int vanilla = item.getId() < vanillaPrices.size() ? vanillaPrices.get(item.getId()) : 0;
                price = Math.max(vanilla, 1);
                yao.addWarning("No usable price in \"" + argument + "\"; using the vanilla price of "
                        + item.getName() + " (" + price + ").");
            }
            yao.addCustomShopItem(item.getId(), price);
        }
    }

    /**
     * Resolves {@code --protect-shop-item <item>} arguments (an id or a name in the ROM's language) and adds
     * them to the protected set. Never fails: an item that cannot be resolved is skipped with a warning.
     */
    public static void applyProtected(YaoSettings yao, List<Item> items, List<String> arguments) {
        for (String raw : arguments) {
            String text = raw == null ? "" : raw.strip();
            Item item = resolveItem(text, items, yao, text);
            if (item != null) {
                yao.addProtectedShopItem(item.getId());
            }
        }
    }

    private static Item resolveItem(String itemPart, List<Item> items, YaoSettings yao, String argument) {
        if (itemPart.isEmpty()) {
            yao.addWarning("Shop item \"" + argument + "\" has no item; skipped.");
            return null;
        }
        Integer id = parsePrice(itemPart);
        if (id != null) {
            Item byId = id > 0 && id < items.size() ? items.get(id) : null;
            if (byId == null) {
                yao.addWarning("Item id " + id + " does not exist in this game; skipped.");
            }
            return byId;
        }

        String wanted = normalize(itemPart);
        List<Item> exact = new ArrayList<>();
        List<Item> partial = new ArrayList<>();
        for (Item item : items) {
            if (item == null || item.getName() == null) {
                continue;
            }
            String name = normalize(item.getName());
            if (name.equals(wanted)) {
                exact.add(item);
            } else if (!wanted.isEmpty() && name.contains(wanted)) {
                partial.add(item);
            }
        }
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.isEmpty() && partial.size() == 1) {
            return partial.get(0);
        }
        if (exact.size() > 1 || partial.size() > 1) {
            yao.addWarning("\"" + itemPart + "\" matches more than one item; use the item id instead. Skipped.");
        } else {
            yao.addWarning("No item called \"" + itemPart + "\" in this game; skipped.");
        }
        return null;
    }

    /** Lower-cases and strips accents, spaces and punctuation, so "Caramelo Raro" equals "caramelo-raro". */
    static String normalize(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Integer parsePrice(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
