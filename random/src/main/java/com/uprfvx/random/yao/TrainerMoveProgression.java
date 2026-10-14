package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  TrainerMoveProgression.java - incremental trainer movesets.           --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Added for YAO (13/10/2026, web UI feedback item 48). Redesigned (14/10/2026, user feedback) around a target power
 * curve instead of a ceiling, aiming at a competitive game from the first gym: trainers use good moves from the start
 * and the Elite Four mostly 65 to 95 power moves with a rare 120 or 150. Used for trainers only, never wild, static,
 * gift or trade Pokemon.
 * <ul>
 *   <li><b>Centre power by level.</b> {@code 45 + 0.8 * level}, capped at 82, plus a tier bonus (gym leaders +2, evil-team and other bosses +3, Elite Four and Champion +4, final value capped at 85).
 *   Level 5: 49, level 15: 57, level 30: 69, Elite Four at 55: 85.
 *   Power means the number shown in game: all hits of a multi-hit move count, charge and recharge moves are not
 *   halved (Hyper Beam is 150).</li>
 *   <li><b>Weights, not a band.</b> {@link #applyWeights} repeats each move in the pool according to how far its power
 *   is from the centre: the core band (0.75 to 1.1 times the centre) is the most likely, 1.1 to 1.4 half as likely,
 *   above that rare (the exceptions). Below 0.75 times the centre moves are half as likely early and fade to the
 *   minimum between level 25 and 50.</li>
 *   <li><b>Hard limits ({@link #narrow}).</b> A ceiling far above the curve (a 250 power self-destruct move never shows
 *   up at level 20, a 100+ power move still can at level 5). A floor of 0.5 times the centre early, rising to 0.75 from
 *   level 45 (the Elite Four's weakest moves are the Fang moves, never Tackle), only while four stronger moves remain.</li>
 *   <li><b>Right category.</b> A Pokemon whose Attack is at least 1.25x its Special Attack (or the reverse at 0.8)
 *   only gets attacking moves of its better category, as long as two remain. Mixed attackers keep both.</li>
 *   <li><b>Status moves</b> are allowed for every Pokemon at every level, at the lowest weight, so Swords Dance or
 *   Spore appear on some Pokemon, not all. A set has at most two status moves and at least two attacking moves when the
 *   pool has them, so it can have two, three or four attacks ({@link #isStatus}).</li>
 * </ul>
 * Type is never looked at here: STAB, ability and stat synergies stay entirely with {@code TrainerMovesetRandomizer}
 * and the weights multiply them. Pure logic, tested in TrainerMoveProgressionTest.
 */
public final class TrainerMoveProgression {

    public static final int MAX_STATUS_MOVES = 2;
    public static final int MIN_ATTACKING_MOVES = 2;

    private TrainerMoveProgression() {
    }

    /** The power an average good move has for a Pokemon of this level and trainer tier. */
    public static double centerPower(int level, TrainerGroups.Group tier) {
        double base = Math.min(45 + 0.8 * level, 82);
        double bonus = tier == TrainerGroups.Group.LEAGUE ? 4
                : tier == TrainerGroups.Group.EVIL_BOSSES || tier == TrainerGroups.Group.OTHER_BOSSES ? 3
                : tier == TrainerGroups.Group.GYM_LEADERS ? 2 : 0;
        return Math.min(base + bonus, 85);
    }

    /** The highest power an attacking move may have at all, far above the centre, see the class comment. */
    public static double powerCeiling(int level, TrainerGroups.Group tier) {
        return 1.9 * centerPower(level, tier) + Math.max(0, 45 - level) * 1.2;
    }

    /** The power as displayed in game: every hit of a multi-hit move counts, charge and recharge are not halved. */
    public static double effectivePower(Move mv) {
        return mv.power * Math.max(1, mv.hitCount);
    }

    private static boolean isAttack(Move mv) {
        return mv.category != MoveCategory.STATUS && mv.power > 1;
    }

    public static boolean isStatus(Move mv) {
        return mv.category == MoveCategory.STATUS;
    }

    /**
     * Hard limits only, no weighting. See {@link #applyWeights} for the likelihoods.
     *
     * @param pool          the moves the Pokemon could use (not changed)
     * @param level         the Pokemon's level
     * @param tier          the trainer's tier, null for an ordinary trainer
     * @param atkSpatkRatio Attack divided by Special Attack, with ability effects
     * @return the narrowed pool, never empty if the pool was not
     */
    public static List<Move> narrow(List<Move> pool, int level, TrainerGroups.Group tier, double atkSpatkRatio) {
        List<Move> result = new ArrayList<>(pool);
        if (result.isEmpty()) {
            return result;
        }
        double centre = centerPower(level, tier);
        double ceiling = powerCeiling(level, tier);

        // 1. ceiling
        List<Move> attacks = new ArrayList<>();
        for (Move mv : result) {
            if (isAttack(mv)) {
                attacks.add(mv);
            }
        }
        List<Move> tooStrong = new ArrayList<>();
        for (Move mv : attacks) {
            if (effectivePower(mv) > ceiling) {
                tooStrong.add(mv);
            }
        }
        if (!attacks.isEmpty() && tooStrong.size() == attacks.size()) {
            // Everything exceeds the ceiling: keep the four weakest so the picker has real choices.
            List<Move> sorted = new ArrayList<>(attacks);
            sorted.sort((a, b) -> Double.compare(effectivePower(a), effectivePower(b)));
            tooStrong.removeAll(sorted.subList(0, Math.min(4, sorted.size())));
        }
        result.removeAll(tooStrong);

        // 2. floor: from 0.5 times the centre early to 0.75 from level 45, only if four stronger attacks remain
        double floorFactor = 0.5 + 0.25 * Math.max(0, Math.min(1, (level - 25) / 20.0));
        double floor = floorFactor * centre;
        List<Move> filler = new ArrayList<>();
        int attackCount = 0;
        for (Move mv : result) {
            if (isAttack(mv)) {
                attackCount++;
                if (effectivePower(mv) < floor) {
                    filler.add(mv);
                }
            }
        }
        if (attackCount - filler.size() >= 4) {
            result.removeAll(filler);
        }

        // 3. right category
        MoveCategory wanted = atkSpatkRatio >= 1.25 ? MoveCategory.PHYSICAL
                : atkSpatkRatio <= 0.8 ? MoveCategory.SPECIAL : null;
        if (wanted != null) {
            long matching = result.stream().filter(mv -> isAttack(mv) && mv.category == wanted).count();
            if (matching >= 2) {
                result.removeIf(mv -> isAttack(mv) && mv.category != wanted);
            }
        }

        return result.isEmpty() ? new ArrayList<>(pool) : result;
    }

    /** How many times a move is repeated in the pick pool, always at least 1. Package-visible for the tests. */
    static int weightOf(Move mv, int level, TrainerGroups.Group tier) {
        if (isStatus(mv)) {
            return 1;
        }
        if (!isAttack(mv)) {
            return 2; // fixed damage moves have no power to compare
        }
        double ratio = effectivePower(mv) / centerPower(level, tier);
        double lowFade = Math.max(0, Math.min(1, (level - 25) / 25.0));
        double w;
        if (ratio < 0.75) {
            w = 2 * (1 - lowFade);
        } else if (ratio < 1.1) {
            w = 4;
        } else if (ratio < 1.4) {
            w = 2;
        } else {
            w = 1;
        }
        return Math.max(1, (int) Math.round(w));
    }

    /**
     * Repeats every occurrence of each move by its weight, so a move that already appears twice because of a STAB or
     * synergy bias stays proportionally more likely. The list order of first appearance is kept.
     */
    public static List<Move> applyWeights(List<Move> pool, int level, TrainerGroups.Group tier) {
        List<Move> out = new ArrayList<>();
        for (Move mv : pool) {
            int w = weightOf(mv, level, tier);
            for (int i = 0; i < w; i++) {
                out.add(mv);
            }
        }
        return out;
    }
}
