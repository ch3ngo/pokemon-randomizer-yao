package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  TrainerBattleItemRules.java - which battle items trainers may carry.  --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Added for YAO (13/10/2026, web UI feedback item 49). The up to four battle items a trainer's AI may use (Potion,
 * Full Restore, X Attack...) were never touched by the randomizer. This limits them, per trainer tier and stage of the
 * game (the highest level in the trainer's team), and only ever lowers or removes items, it never adds one:
 * <ul>
 *   <li>Ordinary trainers use no items at all (unless "regular trainers keep items" is on, then only Potion and
 *   Super Potion and cheap status cures).</li>
 *   <li>Gym leaders use the HP potion that fits the stage: Potion below level 20, Super Potion below 35, Hyper
 *   Potion below 50, Max Potion from 50. Never Full Restore.</li>
 *   <li>The Elite Four and Champion may use Full Restore from level 35. Evil-team bosses and other bosses (rivals,
 *   strong admins) from level 45. Below that they follow the same potion stages as gym leaders.</li>
 *   <li>Revives are removed for everybody. Status cures other than Full Heal stay for any boss, Full Heal and X
 *   items stay for bosses from level 30 (gym leaders from 40).</li>
 * </ul>
 * Pure logic over {@link Trainer#getBattleItems()}, table tested in TrainerBattleItemRulesTest. Games whose handler
 * does not read battle items leave them null and are skipped.
 */
public final class TrainerBattleItemRules {

    private TrainerBattleItemRules() {
    }

    private static final int[] HP_LINE = {ItemIDs.potion, ItemIDs.superPotion, ItemIDs.hyperPotion, ItemIDs.maxPotion,
            ItemIDs.fullRestore};
    private static final Set<Integer> CURES = Set.of(ItemIDs.antidote, ItemIDs.burnHeal, ItemIDs.iceHeal,
            ItemIDs.awakening, ItemIDs.paralyzeHeal);
    private static final Set<Integer> REVIVES = Set.of(ItemIDs.revive, ItemIDs.maxRevive);

    /** 0 Potion .. 3 Max Potion, 4 Full Restore, or -1 for anything that is not an HP item of that line. */
    static int hpRank(int itemId) {
        for (int i = 0; i < HP_LINE.length; i++) {
            if (HP_LINE[i] == itemId) {
                return i;
            }
        }
        return -1;
    }

    /** The potion that fits a stage of the game, 0 to 3, from the highest level in the team. */
    static int stageRank(int level) {
        if (level < 20) {
            return 0;
        }
        if (level < 35) {
            return 1;
        }
        if (level < 50) {
            return 2;
        }
        return 3;
    }

    /** The level from which this tier may use Full Restore, or 0 when it never may. */
    static int fullRestoreFrom(TrainerGroups.Group tier) {
        if (tier == null || tier == TrainerGroups.Group.GYM_LEADERS) {
            return 0;
        }
        return tier == TrainerGroups.Group.LEAGUE ? 35 : 45;
    }

    /**
     * What one item becomes: itself, a lower item of the same kind, or 0 (removed).
     *
     * @param tier   null for an ordinary trainer
     * @param level  the highest level in the trainer's team
     */
    static int limit(int itemId, TrainerGroups.Group tier, int level, boolean regularKeep) {
        if (itemId == 0) {
            return 0;
        }
        if (REVIVES.contains(itemId)) {
            return 0;
        }
        int rank = hpRank(itemId);
        if (tier == null) {
            if (!regularKeep) {
                return 0;
            }
            if (rank >= 0) {
                return HP_LINE[Math.min(Math.min(rank, 1), stageRank(level))];
            }
            return CURES.contains(itemId) ? itemId : 0;
        }
        if (rank >= 0) {
            int from = fullRestoreFrom(tier);
            boolean fullRestoreOk = from > 0 && level >= from;
            int allowed = fullRestoreOk ? 4 : stageRank(level);
            return rank <= allowed ? itemId : HP_LINE[allowed];
        }
        if (CURES.contains(itemId)) {
            return itemId;
        }
        // Full Heal and the X items: bosses from level 30, gym leaders from 40
        int minLevel = tier == TrainerGroups.Group.GYM_LEADERS ? 40 : 30;
        return level >= minLevel ? itemId : 0;
    }

    /** Applies the rules to every trainer whose battle items are known. Returns notes for the log. */
    public static List<String> apply(List<Trainer> trainers, boolean regularKeep, int generation, int romType) {
        int removed = 0;
        int lowered = 0;
        int trainersChanged = 0;
        int regularCleared = 0;
        for (Trainer t : trainers) {
            int[] items = t.getBattleItems();
            if (items == null) {
                continue;
            }
            TrainerGroups.Group tier = TrainerGroups.tierOf(t, generation, romType);
            if (tier == null && t.shouldNotGetBuffs()) {
                continue; // scripted or special battles keep what the game gave them
            }
            int level = 1;
            for (TrainerPokemon tp : t.getPokemon()) {
                level = Math.max(level, tp.getLevel());
            }
            boolean changed = false;
            for (int slot = 0; slot < items.length; slot++) {
                int before = items[slot];
                int after = limit(before, tier, level, regularKeep);
                if (after != before) {
                    changed = true;
                    if (after == 0) {
                        removed++;
                    } else {
                        lowered++;
                    }
                    items[slot] = after;
                }
            }
            if (changed) {
                trainersChanged++;
                if (tier == null) {
                    regularCleared++;
                }
            }
        }
        List<String> notes = new ArrayList<>();
        notes.add("Trainer battle items: " + trainersChanged + " trainers changed (" + removed + " items removed, "
                + lowered + " lowered to the potion that fits the stage of the game; " + regularCleared
                + " were ordinary trainers).");
        return notes;
    }
}
