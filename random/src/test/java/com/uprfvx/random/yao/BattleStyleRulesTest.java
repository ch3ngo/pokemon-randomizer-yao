package com.uprfvx.random.yao;

import com.uprfvx.romio.gamedata.BattleStyle;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class BattleStyleRulesTest {

    private static Trainer trainer(String tag, int pokemon) {
        Trainer t = new Trainer();
        t.setTag(tag);
        List<TrainerPokemon> list = new ArrayList<>();
        for (int i = 0; i < pokemon; i++) {
            Species s = new Species(i + 1);
            s.setName("P" + i);
            list.add(new TrainerPokemon(s, 20));
        }
        t.setPokemon(list);
        return t;
    }

    private static List<YaoSettings.BattleStyleRule> rule(TrainerGroups.Group g, YaoSettings.RuleStyle s, int chance) {
        return List.of(new YaoSettings.BattleStyleRule(g, s, chance));
    }

    @Test
    public void hundredPercentChangesOnlyTheGroupAndPadsTheTeam() {
        Trainer gym = trainer("GYM1-LEADER", 1);
        Trainer regular = trainer("YOUNGSTER", 2);
        List<String> notes = BattleStyleRules.apply(List.of(gym, regular),
                rule(TrainerGroups.Group.GYM_LEADERS, YaoSettings.RuleStyle.DOUBLE, 100), 5, -1, new Random(1));
        assertEquals(BattleStyle.Style.DOUBLE_BATTLE, gym.getCurrBattleStyle().getStyle());
        assertTrue(gym.isForcedDoubleBattle());
        assertEquals(2, gym.getPokemon().size());
        assertFalse(regular.isForcedDoubleBattle());
        assertTrue(notes.get(0).contains("1 trainer(s) of gym leaders"), notes.get(0));
    }

    @Test
    public void chanceIsDeterministicPerSeedAndRoughlyRight() {
        List<Trainer> a = new ArrayList<>();
        List<Trainer> b = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            a.add(trainer("YOUNGSTER", 2));
            b.add(trainer("YOUNGSTER", 2));
        }
        var rules = rule(TrainerGroups.Group.EVERYONE, YaoSettings.RuleStyle.DOUBLE, 10);
        BattleStyleRules.apply(a, rules, 4, -1, new Random(7));
        BattleStyleRules.apply(b, rules, 4, -1, new Random(7));
        int hitsA = 0;
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).isForcedDoubleBattle(), b.get(i).isForcedDoubleBattle());
            if (a.get(i).isForcedDoubleBattle()) {
                hitsA++;
            }
        }
        assertTrue(hitsA > 60 && hitsA < 140, "hits " + hitsA);
    }

    @Test
    public void firstSucceedingMatchingRuleWins() {
        Trainer gym = trainer("GYM2-LEADER", 3);
        List<YaoSettings.BattleStyleRule> rules = List.of(
                new YaoSettings.BattleStyleRule(TrainerGroups.Group.GYM_LEADERS, YaoSettings.RuleStyle.TRIPLE, 100),
                new YaoSettings.BattleStyleRule(TrainerGroups.Group.EVERYONE, YaoSettings.RuleStyle.DOUBLE, 100));
        BattleStyleRules.apply(List.of(gym), rules, 6, -1, new Random(1));
        assertEquals(BattleStyle.Style.TRIPLE_BATTLE, gym.getCurrBattleStyle().getStyle());
    }

    @Test
    public void unsupportedStylesAndGamesAreSkippedWithANote() {
        Trainer gym = trainer("GYM2-LEADER", 2);
        List<String> notes = BattleStyleRules.apply(List.of(gym),
                rule(TrainerGroups.Group.GYM_LEADERS, YaoSettings.RuleStyle.TRIPLE, 100), 4, -1, new Random(1));
        assertFalse(gym.isForcedDoubleBattle());
        assertTrue(notes.get(0).contains("do not exist"), notes.get(0));
        List<String> gen2 = BattleStyleRules.apply(List.of(gym),
                rule(TrainerGroups.Group.GYM_LEADERS, YaoSettings.RuleStyle.DOUBLE, 100), 2, -1, new Random(1));
        assertFalse(gym.isForcedDoubleBattle());
        assertTrue(gen2.get(0).contains("NOT applied"));
        assertTrue(BattleStyleRules.supportedStyles(1).isEmpty());
        assertEquals(1, BattleStyleRules.supportedStyles(7).size());
        assertEquals(3, BattleStyleRules.supportedStyles(5).size());
    }

    @Test
    public void multiBattleAndUnbuffedTrainersAreNeverTouched() {
        Trainer partner = trainer("GYM1-LEADER", 1);
        partner.setMultiBattleStatus(Trainer.MultiBattleStatus.ALWAYS);
        Trainer firstRival = trainer("RIVAL1-0", 1);
        BattleStyleRules.apply(List.of(partner, firstRival),
                rule(TrainerGroups.Group.EVERYONE, YaoSettings.RuleStyle.DOUBLE, 100), 5, -1, new Random(1));
        assertFalse(partner.isForcedDoubleBattle());
        assertFalse(firstRival.isForcedDoubleBattle());
    }

    @Test
    public void settingsRoundTripClampAndDropBadRules() {
        YaoSettings yao = new YaoSettings();
        assertTrue(yao.addBattleStyleRule(TrainerGroups.Group.GYM_LEADERS, YaoSettings.RuleStyle.DOUBLE, 10));
        assertTrue(yao.addBattleStyleRule(TrainerGroups.Group.LEAGUE, YaoSettings.RuleStyle.ROTATION, 500));
        assertFalse(yao.addBattleStyleRule(null, YaoSettings.RuleStyle.DOUBLE, 10));
        assertEquals(2, yao.getBattleStyleRules().size());
        assertEquals(100, yao.getBattleStyleRules().get(1).chance());
        assertFalse(yao.isDefault());
        YaoSettings back = YaoSettings.fromBase64(yao.toBase64());
        assertEquals(yao.getBattleStyleRules(), back.getBattleStyleRules());
        YaoSettings bad = YaoSettings.fromText("battlerule.0=gymLeaders:double:x\nbattlerule.1=nobody:double:5\nbattlerule.2=gymLeaders:double:5\n");
        assertEquals(1, bad.getBattleStyleRules().size());
        assertEquals(2, bad.getWarnings().size());
        YaoSettings many = new YaoSettings();
        for (int i = 0; i < 12; i++) {
            many.addBattleStyleRule(TrainerGroups.Group.EVERYONE, YaoSettings.RuleStyle.DOUBLE, 5);
        }
        assertEquals(YaoSettings.MAX_BATTLE_STYLE_RULES, many.getBattleStyleRules().size());
    }

    @Test
    public void fullyEvolvedByLevelRoundTripsAndClamps() {
        YaoSettings yao = new YaoSettings();
        yao.setFullyEvolvedByLevel(35);
        assertFalse(yao.isDefault());
        assertEquals(35, YaoSettings.fromBase64(yao.toBase64()).getFullyEvolvedByLevel());
        YaoSettings low = new YaoSettings();
        low.setFullyEvolvedByLevel(3);
        assertEquals(15, low.getFullyEvolvedByLevel());
        assertEquals(1, low.getWarnings().size());
        low.setFullyEvolvedByLevel(0);
        assertEquals(0, low.getFullyEvolvedByLevel());
        assertEquals(1, YaoSettings.fromText("fullyevolved=abc\n").getWarnings().size());
    }
}
