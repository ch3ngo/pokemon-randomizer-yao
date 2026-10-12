package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  ProtectedShopItems.java - keep chosen items fixed when shops change.  --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Shop;

import java.util.List;
import java.util.Set;

/**
 * Lets the shops be randomized or shuffled while chosen items stay exactly where they were: the usual
 * supplies (Poke Balls, potions, status heals, repels, Escape Rope) or any items the user picks. The
 * randomizer changes the shops as it always does and afterwards every slot that originally held a protected
 * item gets it back. Shops never change size during randomization, so slots line up one to one.
 *
 * <p>A protected slot takes precedence over everything else the shop randomizer does there, including its
 * "guarantee evolution items" option for that slot.
 */
public final class ProtectedShopItems {

    private ProtectedShopItems() {
    }

    /** The everyday supplies protected by the "keep the basics" switch. */
    public static final Set<Integer> BASICS = Set.of(
            ItemIDs.pokeBall, ItemIDs.greatBall, ItemIDs.ultraBall,
            ItemIDs.potion, ItemIDs.antidote, ItemIDs.burnHeal, ItemIDs.iceHeal, ItemIDs.awakening,
            ItemIDs.paralyzeHeal, ItemIDs.fullHeal, ItemIDs.fullRestore, ItemIDs.maxPotion,
            ItemIDs.hyperPotion, ItemIDs.superPotion,
            ItemIDs.repel, ItemIDs.superRepel, ItemIDs.maxRepel, ItemIDs.escapeRope);

    /**
     * Puts every protected item back in the slot it came from.
     *
     * @param before the shops as they were before randomization
     * @param after  the shops after randomization; modified in place
     * @return how many slots were restored. Shops that do not line up (a different count or size) are left
     * alone rather than risking writing to the wrong slot.
     */
    public static int restore(List<Shop> before, List<Shop> after, Set<Integer> protectedIds) {
        if (before == null || after == null || protectedIds == null || protectedIds.isEmpty()
                || before.size() != after.size()) {
            return 0;
        }
        int restored = 0;
        for (int i = 0; i < before.size(); i++) {
            List<Item> was = before.get(i).getItems();
            List<Item> now = after.get(i).getItems();
            if (was == null || now == null || was.size() != now.size()) {
                continue;
            }
            for (int j = 0; j < was.size(); j++) {
                Item original = was.get(j);
                if (original != null && protectedIds.contains(original.getId()) && !original.equals(now.get(j))) {
                    now.set(j, original);
                    restored++;
                }
            }
        }
        return restored;
    }
}
