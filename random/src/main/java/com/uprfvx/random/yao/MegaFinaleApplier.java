package com.uprfvx.random.yao;

/*----------------------------------------------------------------------------*/
/*--  MegaFinaleApplier.java - the last Pokemon of trainers is a Mega.      --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Gives the last Pokemon of every trainer in scope the mega stone of one of its Mega Evolutions, so the last Pokemon
 * of the fight is a Mega. Data only: the trainer's last Pokemon gets the stone as its held item. A Pokemon without a
 * Mega Evolution is either replaced by one that has one (keeping its level, and its moves are reset to what the new
 * Pokemon would know) or left as it is, as chosen. A trainer that already has a Pokemon holding a mega stone is left
 * alone, because a trainer can Mega Evolve only one Pokemon per battle.
 *
 * <p>Modified (12/10/2026): the old single-choice scope (leaders and league / boss / all) became a {@link Selection}
 * of independent tiers that can be combined (gym leaders, league, evil-team bosses, other bosses, everyone), plus a
 * {@code fromLevel}: a trainer only qualifies if its highest-level Pokemon (in the final team, since this runs after
 * the other trainer changes) is at that level or higher, which lets a run start the finales at, say, the third gym.
 * The tiers are decided by {@link TrainerGroups}, shared with the battle style rules, including the three games that
 * give the Champion role to a tagged Rival battle instead of a CHAMPION tag (formerly handled in this class).
 *
 * <p>Runs after the other trainer changes, so it sees the final team. Whether a trainer Mega Evolves in battle with only
 * the held stone is the game's own rule for trainers that already have megas (the Gen 6 mega bosses); it is not
 * something this program changes.
 */
public final class MegaFinaleApplier {

    private MegaFinaleApplier() {
    }

    /**
     * Which trainers qualify. {@code everyone} covers every trainer, whatever the other flags say.
     * {@code fromLevel} 0 means from the start.
     */
    public record Selection(boolean gymLeaders, boolean league, boolean evilBosses, boolean otherBosses,
                            boolean everyone, int fromLevel) {
        public boolean isOff() {
            return !gymLeaders && !league && !evilBosses && !otherBosses && !everyone;
        }

        boolean includes(TrainerGroups.Group tier) {
            if (everyone) {
                return true;
            }
            if (tier == null) {
                return false;
            }
            return switch (tier) {
                case GYM_LEADERS -> gymLeaders;
                case LEAGUE -> league;
                case EVIL_BOSSES -> evilBosses;
                case OTHER_BOSSES -> otherBosses;
                case EVERYONE -> false;
            };
        }
    }

    /**
     * Applies the change in place and returns the notes for the log.
     *
     * @param generation the game's generation (as {@code RomHandler.generationOfPokemon()}), used only to find the
     *                    Champion in the games that tag it as a Rival battle instead of CHAMPION; pass 0 if
     *                    unknown (no special case will match).
     * @param romType     the game's ROM type/variant within its generation (as {@code RomHandler.getROMType()}),
     *                    same purpose as {@code generation}; pass -1 if unknown.
     */
    public static List<String> apply(List<Trainer> trainers, SpeciesSet megaForms, Selection selection,
                                     boolean replaceWithoutMega, int generation, int romType, Random random) {
        List<String> notes = new ArrayList<>();
        if (selection == null || selection.isOff() || trainers == null || megaForms == null) {
            return notes;
        }
        // base species that have at least one mega form that needs a stone, in a fixed order
        Map<Species, List<Species>> megasByBase = new LinkedHashMap<>();
        List<Species> sorted = new ArrayList<>();
        for (Species mega : megaForms) {
            if (mega.isMegaEvolution() && mega.needsMegaEvolutionItem()) {
                sorted.add(mega);
            }
        }
        sorted.sort(Comparator.comparingInt(Species::getNumber).thenComparingInt(Species::getFormeNumber));
        for (Species mega : sorted) {
            megasByBase.computeIfAbsent(mega.getBaseForme(), k -> new ArrayList<>()).add(mega);
        }
        List<Species> replaceable = new ArrayList<>(megasByBase.keySet());

        Map<TrainerGroups.Group, Integer> givenByTier = new EnumMap<>(TrainerGroups.Group.class);
        int given = 0;
        int replaced = 0;
        int alreadyMega = 0;
        int noMega = 0;
        int belowLevel = 0;
        for (Trainer trainer : trainers) {
            if (trainer == null || trainer.getPokemon() == null || trainer.getPokemon().isEmpty()) {
                continue;
            }
            TrainerGroups.Group tier = TrainerGroups.tierOf(trainer, generation, romType);
            if (!selection.includes(tier)) {
                continue;
            }
            if (selection.fromLevel() > 0 && trainer.getHighestLevel() < selection.fromLevel()) {
                belowLevel++;
                continue;
            }
            boolean hasMega = false;
            for (TrainerPokemon tp : trainer.getPokemon()) {
                if (tp.hasMegaStone() || tp.canMegaEvolve()) {
                    hasMega = true;
                    break;
                }
            }
            if (hasMega) {
                alreadyMega++;
                continue;
            }
            TrainerPokemon last = trainer.getPokemon().get(trainer.getPokemon().size() - 1);
            List<Species> options = megasByBase.get(last.getSpecies().getBaseForme());
            if (options == null) {
                if (!replaceWithoutMega || replaceable.isEmpty()) {
                    noMega++;
                    continue;
                }
                Species replacement = replaceable.get(random.nextInt(replaceable.size()));
                last.getSpeciesHolder().setSpecies(replacement);
                last.setResetMoves(true);
                options = megasByBase.get(replacement);
                replaced++;
            }
            Species mega = options.get(options.size() == 1 ? 0 : random.nextInt(options.size()));
            last.setHeldItem(mega.getMegaEvolutionItem());
            last.setHasMegaStone(true);
            given++;
            givenByTier.merge(tier == null ? TrainerGroups.Group.EVERYONE : tier, 1, Integer::sum);
        }
        StringBuilder perTier = new StringBuilder();
        for (Map.Entry<TrainerGroups.Group, Integer> e : givenByTier.entrySet()) {
            perTier.append(perTier.length() == 0 ? "" : ", ").append(e.getValue()).append(' ').append(e.getKey().label());
        }
        notes.add("Mega finale: " + given + " trainer(s) now end with a Mega Evolution"
                + (perTier.length() > 0 ? " (" + perTier + ")" : "")
                + (replaced > 0 ? "; " + replaced + " of them had their last Pokemon replaced by one that has a Mega" : "")
                + (alreadyMega > 0 ? "; " + alreadyMega + " already had a Mega and were left alone" : "")
                + (noMega > 0 ? "; " + noMega + " ended with a Pokemon that has no Mega and were left as they are" : "")
                + (belowLevel > 0 ? "; " + belowLevel + " were skipped because their strongest Pokemon is below level "
                + selection.fromLevel() : "")
                + ".");
        return notes;
    }
}
