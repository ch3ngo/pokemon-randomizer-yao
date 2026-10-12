package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  ShopPriceLimits.java - what prices each generation can actually store. --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

/**
 * Item prices are stored differently in every generation, so a requested price has to be adapted
 * before it is written. The limits below follow how the ROM handlers write prices.
 * <ul>
 *     <li>Gen 1: three-byte decimal, up to 999999.</li>
 *     <li>Gen 2, 3 and 4: a 16-bit value, up to 65535.</li>
 *     <li>Gen 5, 6 and 7: the price divided by 10 in a 16-bit value, so prices are multiples of 10
 *     (a requested 1 would be stored as 0, i.e. free) and the maximum is 655350.</li>
 * </ul>
 */
public final class ShopPriceLimits {

    private ShopPriceLimits() {
    }

    public static boolean pricesAreMultiplesOfTen(int generation) {
        return generation >= 5;
    }

    public static int minPrice(int generation) {
        return pricesAreMultiplesOfTen(generation) ? 10 : 1;
    }

    public static int maxPrice(int generation) {
        return switch (generation) {
            case 1 -> 999_999;
            case 2, 3, 4 -> 65_535;
            default -> 655_350;
        };
    }

    /**
     * Returns the price that will actually be stored for a requested price. Never fails: the value is
     * clamped to the generation's range first (so extreme inputs cannot overflow), then, where prices
     * are multiples of 10, rounded up so that it never becomes free.
     */
    public static int normalize(int generation, int requested) {
        int min = minPrice(generation);
        int max = maxPrice(generation);
        int price = Math.min(Math.max(requested, min), max);
        if (pricesAreMultiplesOfTen(generation)) {
            price = Math.min(((price + 9) / 10) * 10, max);
        }
        return price;
    }
}
