package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  BattleStyleRules.java - a chance of double/triple/rotation battles    --*/
/*--  for chosen trainer groups.                                            --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.BattleStyle;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Applies {@link YaoSettings.BattleStyleRule}s after the native battle style step. For each trainer the rules are
 * checked in listed order; the first rule whose group contains the trainer and whose own random roll succeeds sets the
 * trainer's battle style. A trainer with no succeeding rule keeps what the native step (or the game) gave it.
 *
 * <p>Same safety as the native step ({@code TrainerPokemonRandomizer.modifyBattleStyle}): trainers that are always
 * part of a multi battle, and trainers flagged as not to be buffed (first rival battle and the like), are never
 * touched; a team smaller than the style needs is padded with copies of its first Pokemon. Styles the game cannot
 * store are skipped with a note: only the games whose ROM handler writes a battle style are supported (Gen 3 to 7),
 * and triple and rotation battles only exist in Gen 5 and 6 (see {@link #supportedStyles(int)}).
 */
public final class BattleStyleRules {

    private BattleStyleRules() {
    }

    /** The styles the game of this generation can store for a trainer, none for Gen 1 and 2. */
    public static Set<YaoSettings.RuleStyle> supportedStyles(int generation) {
        if (generation < 3) {
            return EnumSet.noneOf(YaoSettings.RuleStyle.class);
        }
        if (generation == 5 || generation == 6) {
            return EnumSet.allOf(YaoSettings.RuleStyle.class);
        }
        return EnumSet.of(YaoSettings.RuleStyle.DOUBLE);
    }

    private static BattleStyle.Style toStyle(YaoSettings.RuleStyle style) {
        return switch (style) {
            case DOUBLE -> BattleStyle.Style.DOUBLE_BATTLE;
            case TRIPLE -> BattleStyle.Style.TRIPLE_BATTLE;
            case ROTATION -> BattleStyle.Style.ROTATION_BATTLE;
        };
    }

    private static int requiredPokemon(YaoSettings.RuleStyle style) {
        return style == YaoSettings.RuleStyle.DOUBLE ? 2 : 3;
    }

    /** Applies the rules in place and returns the log notes. */
    public static List<String> apply(List<Trainer> trainers, List<YaoSettings.BattleStyleRule> rules, int generation,
                                     int romType, Random random) {
        List<String> notes = new ArrayList<>();
        if (rules == null || rules.isEmpty() || trainers == null) {
            return notes;
        }
        Set<YaoSettings.RuleStyle> supported = supportedStyles(generation);
        if (supported.isEmpty()) {
            notes.add("Battle style rules: NOT applied. This game has no double battles to give to trainers.");
            return notes;
        }
        List<YaoSettings.BattleStyleRule> usable = new ArrayList<>();
        for (YaoSettings.BattleStyleRule rule : rules) {
            if (supported.contains(rule.style())) {
                usable.add(rule);
            } else {
                notes.add("Battle style rule skipped: " + rule.style().name().toLowerCase() + " battles do not exist in this game ("
                        + rule.group().key() + ", " + rule.chance() + "%).");
            }
        }
        int[] hits = new int[usable.size()];
        int[] padded = new int[usable.size()];
        for (Trainer trainer : trainers) {
            if (trainer == null || trainer.getPokemon() == null || trainer.getPokemon().isEmpty()
                    || trainer.getMultiBattleStatus() == Trainer.MultiBattleStatus.ALWAYS || trainer.shouldNotGetBuffs()) {
                continue;
            }
            for (int i = 0; i < usable.size(); i++) {
                YaoSettings.BattleStyleRule rule = usable.get(i);
                if (!TrainerGroups.matches(trainer, rule.group(), generation, romType)) {
                    continue;
                }
                if (random.nextInt(100) >= rule.chance()) {
                    continue;
                }
                trainer.setCurrBattleStyle(new BattleStyle(BattleStyle.Modification.SINGLE_STYLE, toStyle(rule.style())));
                boolean grew = false;
                while (trainer.getPokemon().size() < requiredPokemon(rule.style())) {
                    trainer.getPokemon().add(new TrainerPokemon(trainer.getPokemon().getFirst()));
                    grew = true;
                }
                trainer.setForcedDoubleBattle(true);
                hits[i]++;
                if (grew) {
                    padded[i]++;
                }
                break;
            }
        }
        for (int i = 0; i < usable.size(); i++) {
            YaoSettings.BattleStyleRule rule = usable.get(i);
            notes.add("Battle style rule " + (i + 1) + ": " + hits[i] + " trainer(s) of " + rule.group().label()
                    + " now fight " + rule.style().name().toLowerCase() + " battles (" + rule.chance() + "% chance each)"
                    + (padded[i] > 0 ? "; " + padded[i] + " of them were given extra copies of their first Pokemon to fill the team" : "")
                    + ".");
        }
        return notes;
    }
}
