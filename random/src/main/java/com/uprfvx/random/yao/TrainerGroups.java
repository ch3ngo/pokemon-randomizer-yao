package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  TrainerGroups.java - which trainers belong to which boss tier.        --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.constants.Gen7Constants;
import com.uprfvx.romio.gamedata.Trainer;

import java.util.Locale;

/**
 * The trainer tiers shared by the mega finale and the battle style rules (12/10/2026), so both pick the same trainers.
 * A trainer is in exactly one of the four named tiers (checked in this order: gym leaders, league, evil-team bosses,
 * other bosses); {@link Group#EVERYONE} matches all trainers.
 */
public final class TrainerGroups {

    private TrainerGroups() {
    }

    public enum Group {
        GYM_LEADERS("gymLeaders", "gym leaders"),
        LEAGUE("league", "Elite Four, Champion and Uber"),
        EVIL_BOSSES("evilBosses", "evil-team bosses"),
        OTHER_BOSSES("otherBosses", "other bosses (rivals and strong admins)"),
        EVERYONE("everyone", "all other trainers");

        private final String key;
        private final String label;

        Group(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        /** Null if the text is not one of the keys (case and punctuation ignored). */
        public static Group parse(String text) {
            if (text == null) {
                return null;
            }
            String t = text.strip().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            for (Group g : values()) {
                if (g.key.toLowerCase(Locale.ROOT).equals(t)) {
                    return g;
                }
            }
            return null;
        }
    }

    /**
     * Red/Blue/Yellow/Green, FireRed/LeafGreen and Sun/Moon/Ultra Sun/Ultra Moon give the Champion role to a tagged
     * Rival battle instead of CHAMPION. Moved here from MegaFinaleApplier (12/10/2026) unchanged. Red/Blue/Yellow/
     * Green: the final Rival battle, RIVAL8. FireRed/LeafGreen: RIVAL8 and the post-game rematch RIVAL9. Sun/Moon:
     * Kukui at Mount Lanakila, RIVAL3; Ultra Sun/Ultra Moon: the same trainer, RIVAL2.
     */
    static boolean isMissingChampionTagTrainer(Trainer trainer, int generation, int romType) {
        String tag = trainer.getTag();
        if (tag == null) {
            return false;
        }
        if (generation == 1) {
            return tag.startsWith("RIVAL8-");
        }
        if (generation == 3 && romType == Gen3Constants.RomType_FRLG) {
            return tag.startsWith("RIVAL8-") || tag.startsWith("RIVAL9-");
        }
        if (generation == 7) {
            if (romType == Gen7Constants.Type_SM) {
                return tag.startsWith("RIVAL3-");
            }
            if (romType == Gen7Constants.Type_USUM) {
                return tag.startsWith("RIVAL2-");
            }
        }
        return false;
    }

    /**
     * The one named tier this trainer is in, or {@code null} if it is in none of them (an ordinary trainer).
     */
    public static Group tierOf(Trainer trainer, int generation, int romType) {
        if (trainer.isGymLeader()) {
            return Group.GYM_LEADERS;
        }
        if (trainer.isLeagueTier() || isMissingChampionTagTrainer(trainer, generation, romType)) {
            return Group.LEAGUE;
        }
        if (trainer.isEvilTeamBoss()) {
            return Group.EVIL_BOSSES;
        }
        if ((trainer.isBoss() || trainer.isImportant()) && !trainer.shouldNotGetBuffs()) {
            return Group.OTHER_BOSSES;
        }
        return null;
    }

    /** True if the trainer is in the group ({@link Group#EVERYONE} always matches). */
    public static boolean matches(Trainer trainer, Group group, int generation, int romType) {
        return group == Group.EVERYONE || tierOf(trainer, generation, romType) == group;
    }
}
