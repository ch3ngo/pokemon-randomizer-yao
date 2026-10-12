package com.uprfvx.random.yao;

import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.basestats.BaseStats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PerSpeciesStatEditorTest {

    private static Species mon(int number, String name, int hp, int atk, int def, int spa, int spd, int spe) {
        Species sp = new Species(number);
        sp.setName(name);
        sp.setBaseStats(new BaseStats(hp, atk, def, spa, spd, spe));
        return sp;
    }

    @Test
    public void quickModeScalesTheWholeSpreadAndKeepsShape() {
        Species magikarp = mon(129, "MAGIKARP", 20, 10, 55, 15, 20, 80); // BST 200, fast
        YaoSettings yao = new YaoSettings();
        yao.addQuickStatEdit(129, 50);
        PerSpeciesStatEditor.apply(List.of(magikarp), yao);
        BaseStats bs = magikarp.getBaseStats();
        assertEquals(300, bs.getBST());
        assertTrue(bs.getSpeed() > bs.getHp() && bs.getSpeed() > bs.getAttack(), "still the fast one");
    }

    @Test
    public void quickModeCanAlsoLowerStatsUnlikeTheWholeDexBooster() {
        Species a = mon(1, "A", 80, 80, 80, 80, 80, 80); // BST 480
        YaoSettings yao = new YaoSettings();
        yao.addQuickStatEdit(1, -50);
        PerSpeciesStatEditor.apply(List.of(a), yao);
        assertEquals(240, a.getBaseStats().getBST());
    }

    @Test
    public void fineTuneSetsExactStatsWithinRealGameBounds() {
        Species shedinja = mon(292, "SHEDINJA", 1, 90, 45, 30, 30, 40); // the real HP=1 Pokemon
        Species low = mon(2, "LOW", 1, 1, 1, 1, 1, 1);
        Species high = mon(3, "HIGH", 255, 255, 255, 255, 255, 255);
        YaoSettings yao = new YaoSettings();
        // the loaded dex (LOW/HIGH) ranges 1 to 255 on every stat, so every requested value below is well inside
        // range and should be set exactly
        yao.addFineTuneStatEdit(292, 50, 60, 70, 80, 90, 100);
        List<String> notes = PerSpeciesStatEditor.apply(List.of(shedinja, low, high), yao);
        BaseStats bs = shedinja.getBaseStats();
        assertEquals(50, bs.getHp());
        assertEquals(60, bs.getAttack());
        assertEquals(70, bs.getDefense());
        assertEquals(80, bs.getSpatk());
        assertEquals(90, bs.getSpdef());
        assertEquals(100, bs.getSpeed());
        assertTrue(String.join(" ", notes).contains("SHEDINJA"));
    }

    @Test
    public void fineTuneClampsToTheRealMinimumAndMaximumOfTheLoadedGame() {
        // a tiny two-Pokemon "game" where HP only ever ranges from 1 to 10
        Species low = mon(1, "LOW", 1, 50, 50, 50, 50, 50);
        Species high = mon(2, "HIGH", 10, 50, 50, 50, 50, 50);
        YaoSettings yao = new YaoSettings();
        yao.addFineTuneStatEdit(1, 255, 50, 50, 50, 50, 50); // HP 255 requested, but this game's max HP is 10
        PerSpeciesStatEditor.apply(List.of(low, high), yao);
        assertEquals(10, low.getBaseStats().getHp(), "clamped to this game's real maximum HP, not the generic 255 cap");
    }

    @Test
    public void computeBoundsFallsBackToTheGenericRangeWhenNothingIsLoaded() {
        PerSpeciesStatEditor.Bounds bounds = PerSpeciesStatEditor.computeBounds(List.of());
        assertEquals(0, bounds.hp().min());
        assertEquals(255, bounds.hp().max());
    }

    @Test
    public void unknownPokemonAreReportedAndBadDataNeverThrows() {
        Species a = mon(1, "A", 20, 20, 20, 20, 20, 20);
        YaoSettings yao = new YaoSettings();
        yao.addQuickStatEdit(999, 50);
        List<String> notes = PerSpeciesStatEditor.apply(new ArrayList<>(java.util.Arrays.asList(a, null)), yao);
        assertTrue(String.join(" ", notes).contains("#999"), String.join(" ", notes));
    }

    @Test
    public void noEditsProducesNoNotes() {
        Species a = mon(1, "A", 20, 20, 20, 20, 20, 20);
        assertTrue(PerSpeciesStatEditor.apply(List.of(a), new YaoSettings()).isEmpty());
    }

    // ---- settings ----

    @Test
    public void quickEditSettingsRoundTripAndAreRepaired() {
        YaoSettings yao = new YaoSettings();
        assertTrue(yao.addQuickStatEdit(129, 40));
        assertTrue(yao.addQuickStatEdit(129, 60), "listing the same Pokemon again replaces it");
        assertFalse(yao.addQuickStatEdit(0, 10));
        assertTrue(yao.addQuickStatEdit(5, 5000));  // moved to 300
        assertTrue(yao.addQuickStatEdit(6, -200));  // moved to -90
        assertFalse(yao.isDefault());

        List<YaoSettings.StatEdit> edits = yao.getStatEdits();
        assertEquals(3, edits.size());

        YaoSettings back = YaoSettings.fromBase64(yao.toBase64());
        assertEquals(yao.getStatEdits(), back.getStatEdits());
    }

    @Test
    public void fineTuneEditSettingsRoundTripAndAreRepaired() {
        YaoSettings yao = new YaoSettings();
        assertTrue(yao.addFineTuneStatEdit(129, 20, 10, 55, 15, 20, 80));
        assertTrue(yao.addFineTuneStatEdit(5, -10, 999, 50, 50, 50, 50)); // moved into 0-255
        YaoSettings.StatEdit repaired = yao.getStatEdits().stream().filter(e -> e.speciesNumber() == 5).findFirst().orElseThrow();
        assertEquals(0, repaired.hp());
        assertEquals(255, repaired.attack());
        assertFalse(yao.getWarnings().isEmpty());

        YaoSettings back = YaoSettings.fromBase64(yao.toBase64());
        assertEquals(yao.getStatEdits(), back.getStatEdits());
    }

    @Test
    public void unreadableStatEditLinesAreSkippedWithAWarning() {
        YaoSettings yao = YaoSettings.fromText("statedit.0=abc\nstatedit.1=129:quick:30\n"
                + "statedit.2=5:fine:1:2:3:4:5:6\nstatedit.3=5:banana:1\n");
        assertEquals(2, yao.getStatEdits().size());
        assertEquals(2, yao.getWarnings().size());
    }
}
