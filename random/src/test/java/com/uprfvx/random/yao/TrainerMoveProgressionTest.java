package com.uprfvx.random.yao;

import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.StatChangeMoveType;
import com.uprfvx.romio.gamedata.StatChangeType;
import com.uprfvx.romio.gamedata.StatusType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrainerMoveProgressionTest {

    private static int next = 1;

    private static Move attack(String name, int power, MoveCategory category) {
        Move m = new Move();
        m.name = name;
        m.number = next++;
        m.power = power;
        m.category = category;
        m.hitratio = 100;
        return m;
    }

    private static Move statChange(String name, StatChangeMoveType type, int stages) {
        Move m = new Move();
        m.name = name;
        m.number = next++;
        m.category = MoveCategory.STATUS;
        m.statChangeMoveType = type;
        m.statChanges[0] = new Move.StatChange();
        m.statChanges[0].type = StatChangeType.ATTACK;
        m.statChanges[0].stages = stages;
        m.hitratio = 100;
        return m;
    }

    private static Move inflicter(String name, StatusType status, double accuracy) {
        Move m = new Move();
        m.name = name;
        m.number = next++;
        m.category = MoveCategory.STATUS;
        m.statusType = status;
        m.hitratio = accuracy;
        return m;
    }

    private static List<Move> attackPool() {
        return new ArrayList<>(List.of(
                attack("Tackle", 35, MoveCategory.PHYSICAL), attack("Bite", 60, MoveCategory.PHYSICAL),
                attack("Earthquake", 100, MoveCategory.PHYSICAL), hyperBeam(),
                attack("Flamethrower", 90, MoveCategory.SPECIAL)));
    }

    private static Move hyperBeam() {
        Move m = attack("Hyper Beam", 150, MoveCategory.PHYSICAL);
        m.isRechargeMove = true;
        return m;
    }

    private static int count(List<Move> weighted, String name) {
        return (int) weighted.stream().filter(m -> m.name.equals(name)).count();
    }

    @Test
    public void centreGrowsWithLevelAndTierAndIsCapped() {
        assertEquals(49, TrainerMoveProgression.centerPower(5, null), 0.001);
        assertEquals(81, TrainerMoveProgression.centerPower(45, null), 0.001);
        assertEquals(82, TrainerMoveProgression.centerPower(80, null), 0.001);
        assertEquals(85, TrainerMoveProgression.centerPower(55, TrainerGroups.Group.LEAGUE), 0.001);
        assertTrue(TrainerMoveProgression.centerPower(30, TrainerGroups.Group.LEAGUE)
                > TrainerMoveProgression.centerPower(30, TrainerGroups.Group.EVIL_BOSSES));
        assertTrue(TrainerMoveProgression.centerPower(30, TrainerGroups.Group.EVIL_BOSSES)
                > TrainerMoveProgression.centerPower(30, TrainerGroups.Group.GYM_LEADERS));
    }

    @Test
    public void aHundredPowerMoveIsStillPossibleAtLevelFiveButSelfDestructIsNot() {
        List<Move> pool = new ArrayList<>(List.of(attack("Tackle", 35, MoveCategory.PHYSICAL),
                attack("Earthquake", 100, MoveCategory.PHYSICAL), attack("Explosion", 250, MoveCategory.PHYSICAL),
                attack("Scratch", 40, MoveCategory.PHYSICAL)));
        List<String> names = TrainerMoveProgression.narrow(pool, 5, null, 1.0).stream().map(m -> m.name)
                .toList();
        assertTrue(names.contains("Earthquake"), names.toString());
        assertFalse(names.contains("Explosion"), names.toString());
    }

    @Test
    public void earlyGameWeightsFavourLowMovesAndKeepStrongOnesRare() {
        Move tackle = attack("Tackle", 35, MoveCategory.PHYSICAL);
        Move bite = attack("Bite", 60, MoveCategory.PHYSICAL);
        Move earthquake = attack("Earthquake", 100, MoveCategory.PHYSICAL);
        assertEquals(2, TrainerMoveProgression.weightOf(tackle, 5, null));
        assertEquals(2, TrainerMoveProgression.weightOf(bite, 5, null));
        assertEquals(1, TrainerMoveProgression.weightOf(earthquake, 5, null));
    }

    @Test
    public void eliteFourMostlyGetsMidMovesAndRarelyTheHugeOnes() {
        TrainerGroups.Group e4 = TrainerGroups.Group.LEAGUE;
        Move fang = attack("Ice Fang", 65, MoveCategory.PHYSICAL);
        Move brick = attack("Brick Break", 75, MoveCategory.PHYSICAL);
        Move eq = attack("Earthquake", 100, MoveCategory.PHYSICAL);
        Move beam = hyperBeam();
        assertEquals(4, TrainerMoveProgression.weightOf(fang, 55, e4));
        assertEquals(4, TrainerMoveProgression.weightOf(brick, 55, e4));
        assertEquals(2, TrainerMoveProgression.weightOf(eq, 55, e4));
        assertEquals(1, TrainerMoveProgression.weightOf(beam, 55, e4));
    }

    @Test
    public void lateGameDropsFillerButKeepsTheFangs() {
        TrainerGroups.Group e4 = TrainerGroups.Group.LEAGUE;
        List<Move> pool = new ArrayList<>(List.of(attack("Tackle", 35, MoveCategory.PHYSICAL),
                attack("Quick Attack", 40, MoveCategory.PHYSICAL), attack("Ice Fang", 65, MoveCategory.PHYSICAL),
                attack("Brick Break", 75, MoveCategory.PHYSICAL), attack("Earthquake", 100, MoveCategory.PHYSICAL),
                attack("Crunch", 80, MoveCategory.PHYSICAL), hyperBeam()));
        List<String> names = TrainerMoveProgression.narrow(pool, 55, e4, 1.0).stream().map(m -> m.name).toList();
        assertFalse(names.contains("Tackle") || names.contains("Quick Attack"), names.toString());
        assertTrue(names.contains("Ice Fang") && names.contains("Hyper Beam"), names.toString());
    }

    @Test
    public void weightsMultiplyAnExistingBiasInsteadOfReplacingIt() {
        Move brick = attack("Brick Break", 75, MoveCategory.PHYSICAL);
        List<Move> biased = new ArrayList<>(List.of(brick, brick));
        assertEquals(8, count(TrainerMoveProgression.applyWeights(biased, 55, TrainerGroups.Group.LEAGUE),
                "Brick Break"));
    }

    @Test
    public void statusMovesAreRareInThePoolAndNeverFloodASet() {
        Move swords = statChange("Swords Dance", StatChangeMoveType.NO_DAMAGE_USER, 2);
        assertEquals(1, TrainerMoveProgression.weightOf(swords, 55, TrainerGroups.Group.LEAGUE));
        assertTrue(TrainerMoveProgression.isStatus(swords));
        assertEquals(2, TrainerMoveProgression.MAX_STATUS_MOVES);
        assertEquals(2, TrainerMoveProgression.MIN_ATTACKING_MOVES);
    }

    @Test
    public void chargeMovesAreNotHalvedAndMultiHitCountsEveryHit() {
        assertEquals(150, TrainerMoveProgression.effectivePower(hyperBeam()), 0.001);
        Move fury = attack("Fury Attack", 15, MoveCategory.PHYSICAL);
        fury.hitCount = 3.5;
        assertEquals(52.5, TrainerMoveProgression.effectivePower(fury), 0.001);
    }

    @Test
    public void physicalAttackerGetsPhysicalMovesWhenTwoExist() {
        List<Move> out = TrainerMoveProgression.narrow(attackPool(), 60, TrainerGroups.Group.LEAGUE, 2.0);
        assertTrue(out.stream().noneMatch(m -> m.category == MoveCategory.SPECIAL));
        // a mixed attacker keeps both
        List<Move> mixed = TrainerMoveProgression.narrow(attackPool(), 60, TrainerGroups.Group.LEAGUE, 1.0);
        assertTrue(mixed.stream().anyMatch(m -> m.category == MoveCategory.SPECIAL));
        // only one physical move: the rule is skipped, never leaves nothing to choose from
        List<Move> few = new ArrayList<>(List.of(attack("Slam", 80, MoveCategory.PHYSICAL),
                attack("Surf", 90, MoveCategory.SPECIAL), attack("Thunderbolt", 90, MoveCategory.SPECIAL)));
        assertEquals(3, TrainerMoveProgression.narrow(few, 40, TrainerGroups.Group.LEAGUE, 3.0).size());
    }

    @Test
    public void everythingOverTheCeilingKeepsUpToFourWeakest() {
        List<Move> big = new ArrayList<>(List.of(
                attack("D", 380, MoveCategory.PHYSICAL), attack("E", 400, MoveCategory.PHYSICAL),
                attack("F", 360, MoveCategory.PHYSICAL), attack("G", 310, MoveCategory.PHYSICAL),
                attack("H", 320, MoveCategory.PHYSICAL), attack("I", 330, MoveCategory.PHYSICAL)));
        List<Move> out = TrainerMoveProgression.narrow(big, 5, null, 1.0);
        assertEquals(4, out.size());
        assertTrue(out.stream().allMatch(m -> m.power <= 360));
    }

    @Test
    public void statusMovesStayInThePoolForEveryoneAtEveryLevel() {
        Move growl = statChange("Growl", StatChangeMoveType.NO_DAMAGE_TARGET, -1);
        Move swords = statChange("Swords Dance", StatChangeMoveType.NO_DAMAGE_USER, 2);
        Move hypnosis = inflicter("Hypnosis", StatusType.SLEEP, 60);
        List<Move> pool = new ArrayList<>(List.of(growl, swords, hypnosis,
                attack("Bite", 60, MoveCategory.PHYSICAL), attack("Slam", 80, MoveCategory.PHYSICAL)));
        for (int level : new int[]{5, 30, 60}) {
            for (TrainerGroups.Group tier : new TrainerGroups.Group[]{null, TrainerGroups.Group.LEAGUE}) {
                assertEquals(5, TrainerMoveProgression.narrow(pool, level, tier, 1.0).size());
            }
        }
    }

    @Test
    public void neverReturnsAnEmptyPoolFromANonEmptyOne() {
        List<Move> onlyBad = new ArrayList<>(List.of(statChange("Growl", StatChangeMoveType.NO_DAMAGE_TARGET, -1)));
        assertEquals(1, TrainerMoveProgression.narrow(onlyBad, 50, TrainerGroups.Group.LEAGUE, 1.0).size());
        assertTrue(TrainerMoveProgression.narrow(new ArrayList<>(), 50, null, 1.0).isEmpty());
    }
}
