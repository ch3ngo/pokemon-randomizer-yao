package com.uprfvx.random.yao;

import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrainerBattleItemRulesTest {

    private static Trainer trainer(String tag, int level, int... items) {
        Trainer t = new Trainer();
        t.setTag(tag);
        Species s = new Species(1);
        s.setName("P");
        t.getPokemon().add(new TrainerPokemon(s, level));
        int[] slots = new int[4];
        System.arraycopy(items, 0, slots, 0, items.length);
        t.setBattleItems(slots);
        return t;
    }

    private static void run(Trainer t, boolean regularKeep) {
        TrainerBattleItemRules.apply(List.of(t), regularKeep, 4, -1);
    }

    @Test
    public void regularTrainerNeverKeepsFullRestoreOrAnythingElseByDefault() {
        Trainer t = trainer("YOUNGSTER", 8, ItemIDs.fullRestore, ItemIDs.potion, ItemIDs.xAttack, ItemIDs.revive);
        run(t, false);
        assertArrayEquals(new int[4], t.getBattleItems());
    }

    @Test
    public void regularTrainerThatKeepsItemsGetsAtMostSuperPotionAndNoFullRestore() {
        Trainer t = trainer("YOUNGSTER", 40, ItemIDs.fullRestore, ItemIDs.hyperPotion, ItemIDs.antidote, ItemIDs.fullHeal);
        run(t, true);
        assertArrayEquals(new int[]{ItemIDs.superPotion, ItemIDs.superPotion, ItemIDs.antidote, 0}, t.getBattleItems());
    }

    @Test
    public void gymLeaderGetsThePotionOfTheStageAndNeverFullRestore() {
        Trainer early = trainer("GYM1-LEADER", 14, ItemIDs.fullRestore, ItemIDs.maxPotion);
        run(early, false);
        assertArrayEquals(new int[]{ItemIDs.potion, ItemIDs.potion, 0, 0}, early.getBattleItems());
        Trainer mid = trainer("GYM4-LEADER", 30, ItemIDs.fullRestore);
        run(mid, false);
        assertEquals(ItemIDs.superPotion, mid.getBattleItems()[0]);
        Trainer late = trainer("GYM8-LEADER", 52, ItemIDs.fullRestore, ItemIDs.potion);
        run(late, false);
        assertArrayEquals(new int[]{ItemIDs.maxPotion, ItemIDs.potion, 0, 0}, late.getBattleItems());
    }

    @Test
    public void itemsAreNeverAddedOrRaised() {
        Trainer gym = trainer("GYM8-LEADER", 60, ItemIDs.potion);
        run(gym, false);
        assertArrayEquals(new int[]{ItemIDs.potion, 0, 0, 0}, gym.getBattleItems());
    }

    @Test
    public void leagueAndBossesMayUseFullRestoreFromTheirLevel() {
        Trainer elite = trainer("ELITE1", 40, ItemIDs.fullRestore, ItemIDs.revive);
        run(elite, false);
        assertArrayEquals(new int[]{ItemIDs.fullRestore, 0, 0, 0}, elite.getBattleItems());

        Trainer eliteLow = trainer("ELITE1", 30, ItemIDs.fullRestore);
        run(eliteLow, false);
        assertEquals(ItemIDs.superPotion, eliteLow.getBattleItems()[0]);

        Trainer evil = trainer("THEMED:ROCKET-LEADER", 40, ItemIDs.fullRestore);
        run(evil, false);
        assertEquals(ItemIDs.hyperPotion, evil.getBattleItems()[0]);
        Trainer evilHigh = trainer("THEMED:ROCKET-LEADER", 48, ItemIDs.fullRestore);
        run(evilHigh, false);
        assertEquals(ItemIDs.fullRestore, evilHigh.getBattleItems()[0]);

        Trainer rival = trainer("RIVAL3-0", 50, ItemIDs.fullRestore, ItemIDs.xAttack);
        run(rival, false);
        assertArrayEquals(new int[]{ItemIDs.fullRestore, ItemIDs.xAttack, 0, 0}, rival.getBattleItems());
    }

    @Test
    public void xItemsAndFullHealWaitForTheStage() {
        Trainer earlyBoss = trainer("THEMED:AQUA-LEADER", 20, ItemIDs.xAttack, ItemIDs.fullHeal, ItemIDs.paralyzeHeal);
        run(earlyBoss, false);
        assertArrayEquals(new int[]{0, 0, ItemIDs.paralyzeHeal, 0}, earlyBoss.getBattleItems());
        Trainer gym = trainer("GYM5-LEADER", 35, ItemIDs.xAttack);
        run(gym, false);
        assertEquals(0, gym.getBattleItems()[0]);
    }

    @Test
    public void scriptedFirstRivalAndTrainersWithoutBattleItemDataAreSkipped() {
        Trainer first = trainer("RIVAL1-0", 5, ItemIDs.fullRestore);
        run(first, false);
        assertEquals(ItemIDs.fullRestore, first.getBattleItems()[0]);
        Trainer unread = new Trainer();
        unread.setTag("YOUNGSTER");
        assertDoesNotThrow(() -> run(unread, false));
        assertNull(unread.getBattleItems());
    }

    @Test
    public void settingsRoundTripAndFallBack() {
        YaoSettings yao = new YaoSettings();
        assertTrue(yao.isDefault());
        assertEquals(YaoSettings.TrainerItemMode.TIERED, yao.getTrainerItemMode());
        yao.setTrainerItemMode("unchanged");
        yao.setRegularTrainersKeepItems(true);
        yao.setIncrementalMovesets(false);
        assertFalse(yao.isDefault());
        YaoSettings back = YaoSettings.fromBase64(yao.toBase64());
        assertEquals(YaoSettings.TrainerItemMode.UNCHANGED, back.getTrainerItemMode());
        assertTrue(back.isRegularTrainersKeepItems());
        assertFalse(back.isIncrementalMovesets());
        yao.setTrainerItemMode("banana");
        assertEquals(YaoSettings.TrainerItemMode.TIERED, yao.getTrainerItemMode());
        assertFalse(yao.getWarnings().isEmpty());
        // a damaged value in a text block never breaks the rest
        YaoSettings damaged = YaoSettings.fromText("yao.format=1\ntrainer.items=banana\nshopkeep.basics=1\n");
        assertEquals(YaoSettings.TrainerItemMode.TIERED, damaged.getTrainerItemMode());
        assertTrue(damaged.isKeepBasicShopItems());
    }
}
