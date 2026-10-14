package com.uprfvx.random.yao;

import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Species;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EvolutionEditorTest {

    private static final int MOON_STONE = 81;
    private static final int FIRE_STONE = 82;
    private static final int WATER_STONE = 84;

    /** A tiny fake game: Pokemon by number, items 1..120 exist, moves 1..400 exist. */
    private static final class FakeWorld implements EvolutionEditor.World {
        final int gen;
        final Map<Integer, Species> species = new HashMap<>();

        FakeWorld(int gen) {
            this.gen = gen;
        }

        Species add(int number, String name) {
            Species sp = new Species(number);
            sp.setName(name);
            species.put(number, sp);
            return sp;
        }

        void evolve(int from, int to, EvolutionType type, int extra) {
            Species f = species.get(from);
            Species t = species.get(to);
            Evolution evo = new Evolution(f, t, type, extra);
            f.getEvolutionsFrom().add(evo);
            t.getEvolutionsTo().add(evo);
        }

        Evolution evo(int from, int to) {
            for (Evolution e : species.get(from).getEvolutionsFrom()) {
                if (e.getTo().getNumber() == to) {
                    return e;
                }
            }
            return null;
        }

        @Override
        public int generation() {
            return gen;
        }

        @Override
        public Species species(int number) {
            return species.get(number);
        }

        @Override
        public boolean itemExists(int id) {
            return id >= 1 && id <= 120;
        }

        @Override
        public boolean moveExists(int id) {
            return id >= 1 && id <= 400;
        }
    }

    /** Eevee (133) and its Gen 4 line, plus a few spare Pokemon. */
    private static FakeWorld eeveeWorld(int gen) {
        FakeWorld w = new FakeWorld(gen);
        w.add(133, "EEVEE");
        w.add(134, "VAPOREON");
        w.add(135, "JOLTEON");
        w.add(136, "FLAREON");
        w.add(196, "ESPEON");
        w.add(197, "UMBREON");
        w.add(1, "BULBASAUR");
        w.add(2, "IVYSAUR");
        w.add(3, "VENUSAUR");
        w.add(100, "VOLTORB");
        w.evolve(133, 134, EvolutionType.STONE, WATER_STONE);
        w.evolve(133, 135, EvolutionType.STONE, 83);
        w.evolve(133, 136, EvolutionType.STONE, FIRE_STONE);
        w.evolve(133, 196, EvolutionType.HAPPINESS_DAY, 0);
        w.evolve(133, 197, EvolutionType.HAPPINESS_NIGHT, 0);
        w.evolve(1, 2, EvolutionType.LEVEL, 16);
        w.evolve(2, 3, EvolutionType.LEVEL, 32);
        return w;
    }

    private static List<String> structural(FakeWorld w, YaoSettings yao) {
        return new EvolutionEditor(w, yao).applyStructural();
    }

    private static String all(List<String> notes) {
        return String.join(" | ", notes);
    }

    @Test
    public void eeveeIntoUmbreonWithAMoonStoneChangesTheMethodInPlace() {
        FakeWorld w = eeveeWorld(4);
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(133, 197, EvolutionType.STONE, MOON_STONE);
        List<String> notes = structural(w, yao);
        Evolution evo = w.evo(133, 197);
        assertEquals(EvolutionType.STONE, evo.getType());
        assertEquals(MOON_STONE, evo.getExtraInfo());
        assertEquals(5, w.species.get(133).getEvolutionsFrom().size());
        assertEquals(1, w.species.get(197).getEvolutionsTo().size());
        assertTrue(all(notes).contains("now by"), all(notes));
    }

    @Test
    public void aSecondEvolutionWithTheSameStoneIsRefused() {
        FakeWorld w = eeveeWorld(4);
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(133, 197, EvolutionType.STONE, FIRE_STONE); // Flareon already uses it
        List<String> notes = structural(w, yao);
        assertEquals(EvolutionType.HAPPINESS_NIGHT, w.evo(133, 197).getType(), "left untouched");
        assertTrue(all(notes).contains("the same way"), all(notes));
    }

    @Test
    public void dayAndNightAreDifferentWaysButPlainHappinessOverlapsBoth() {
        FakeWorld w = eeveeWorld(4);
        YaoSettings dayNight = new YaoSettings();
        dayNight.addEvolutionEdit(133, 197, EvolutionType.HAPPINESS_NIGHT, 0); // already like this: fine
        assertFalse(all(structural(w, dayNight)).contains("the same way"));

        YaoSettings plain = new YaoSettings();
        plain.addEvolutionEdit(133, 197, EvolutionType.HAPPINESS, 0); // overlaps Espeon's day happiness
        assertTrue(all(structural(w, plain)).contains("the same way"));
        assertEquals(EvolutionType.HAPPINESS_NIGHT, w.evo(133, 197).getType());
    }

    @Test
    public void twoLevelUpEvolutionsOfTheSamePokemonOverlapWhateverTheLevel() {
        FakeWorld w = eeveeWorld(4);
        w.add(4, "OTHER");
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(1, 4, EvolutionType.LEVEL, 30); // Bulbasaur already evolves by level
        assertTrue(all(structural(w, yao)).contains("the same way"));
        assertNull(w.evo(1, 4));
    }

    @Test
    public void aLevelEvolutionAndAStoneEvolutionDoNotOverlap() {
        FakeWorld w = eeveeWorld(4);
        w.add(4, "OTHER");
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(1, 4, EvolutionType.STONE, FIRE_STONE);
        List<String> notes = structural(w, yao);
        assertNotNull(w.evo(1, 4), all(notes));
        assertEquals(2, w.species.get(1).getEvolutionsFrom().size());
        assertEquals(1, w.species.get(4).getEvolutionsTo().size());
    }

    @Test
    public void loopsAndSelfEvolutionAreRefused() {
        FakeWorld w = eeveeWorld(4);
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(3, 1, EvolutionType.STONE, FIRE_STONE); // Venusaur back to Bulbasaur: a loop
        assertTrue(all(structural(w, yao)).contains("loop"));
        assertNull(w.evo(3, 1));
        assertFalse(new YaoSettings().addEvolutionEdit(5, 5, EvolutionType.LEVEL, 10), "self evolution is dropped when read");
    }

    @Test
    public void aPokemonThatAlreadyEvolvesFromSomebodyElseIsRefusedUntilThatIsRemoved() {
        FakeWorld w = eeveeWorld(4);
        YaoSettings refused = new YaoSettings();
        refused.addEvolutionEdit(100, 2, EvolutionType.STONE, FIRE_STONE);
        assertTrue(all(structural(w, refused)).contains("already evolves from"));
        assertNull(w.evo(100, 2));

        YaoSettings fixed = new YaoSettings();
        fixed.addEvolutionRemoval(1, 2);
        fixed.addEvolutionEdit(100, 2, EvolutionType.STONE, FIRE_STONE);
        structural(w, fixed);
        assertNull(w.evo(1, 2), "removed first");
        assertNotNull(w.evo(100, 2), "then added");
        assertEquals(1, w.species.get(2).getEvolutionsTo().size());
    }

    @Test
    public void removalTakesTheEvolutionOutOfBothSides() {
        FakeWorld w = eeveeWorld(4);
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionRemoval(133, 134);
        yao.addEvolutionRemoval(133, 134);    // listed twice: harmless
        yao.addEvolutionRemoval(1, 3);        // does not exist: reported, not an error
        List<String> notes = structural(w, yao);
        assertNull(w.evo(133, 134));
        assertTrue(w.species.get(134).getEvolutionsTo().isEmpty());
        assertTrue(all(notes).contains("nothing to remove"), all(notes));
    }

    @Test
    public void thereIsALimitOnEvolutionsPerPokemonThatDependsOnTheGeneration() {
        for (int[] c : new int[][]{{3, 5}, {4, 7}, {6, 8}}) {
            FakeWorld w = new FakeWorld(c[0]);
            w.add(1, "FROM");
            for (int i = 0; i < c[1]; i++) {
                w.add(10 + i, "TO" + i);
                w.evolve(1, 10 + i, EvolutionType.STONE, 20 + i);
            }
            w.add(99, "ONEMORE");
            YaoSettings yao = new YaoSettings();
            yao.addEvolutionEdit(1, 99, EvolutionType.STONE, 119);
            assertTrue(all(structural(w, yao)).contains("most evolutions"), "gen " + c[0]);
            assertNull(w.evo(1, 99));
        }
    }

    @Test
    public void methodsTheGameCannotStoreAreSkipped() {
        FakeWorld w = eeveeWorld(3);
        w.add(4, "OTHER");
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(1, 4, EvolutionType.FAIRY_AFFECTION, 0); // Gen 6 method in a Gen 3 game
        yao.addEvolutionEdit(1, 4, EvolutionType.STONE_MALE_ONLY, FIRE_STONE); // Gen 4 method
        List<String> notes = structural(w, yao);
        assertNull(w.evo(1, 4));
        assertTrue(all(notes).contains("cannot store"), all(notes));
    }

    @Test
    public void numbersThatMakeNoSenseAreRepairedOrSkipped() {
        FakeWorld w = eeveeWorld(4);
        w.add(4, "OTHER");
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(1, 4, EvolutionType.STONE, 9999); // no such item: skipped
        assertTrue(all(structural(w, yao)).contains("does not exist"));
        assertNull(w.evo(1, 4));

        FakeWorld w2 = eeveeWorld(4);
        YaoSettings lvl = new YaoSettings();
        lvl.addEvolutionEdit(1, 2, EvolutionType.LEVEL, 250); // out of range level: moved to 100
        structural(w2, lvl);
        assertEquals(100, w2.evo(1, 2).getExtraInfo());
    }

    @Test
    public void pokemonThatDoNotExistInTheGameAreReportedNotCrashed() {
        FakeWorld w = eeveeWorld(4);
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(777, 1, EvolutionType.LEVEL, 10);
        yao.addEvolutionRemoval(1, 888);
        assertDoesNotThrow(() -> structural(w, yao));
    }

    @Test
    public void gen1And2OnlyAllowChangingTheLevelOfAnExistingLevelEvolution() {
        for (int gen = 1; gen <= 2; gen++) {
            FakeWorld w = eeveeWorld(gen);
            w.add(4, "OTHER");
            YaoSettings yao = new YaoSettings();
            yao.addEvolutionEdit(1, 2, EvolutionType.LEVEL, 12);           // allowed
            yao.addEvolutionEdit(2, 3, EvolutionType.STONE, FIRE_STONE);   // method change: refused
            yao.addEvolutionEdit(1, 4, EvolutionType.LEVEL, 30);           // a new evolution: refused
            yao.addEvolutionRemoval(133, 134);                             // removal: refused
            structural(w, yao);
            assertEquals(12, w.evo(1, 2).getExtraInfo(), "gen " + gen);
            assertEquals(EvolutionType.LEVEL, w.evo(2, 3).getType(), "gen " + gen);
            assertNull(w.evo(1, 4), "gen " + gen);
            assertNotNull(w.evo(133, 134), "gen " + gen);
        }
    }

    // ---------------------------------------------------------------- levels

    @Test
    public void highEvolutionLevelsAreLoweredByAPercentage() {
        FakeWorld w = eeveeWorld(4);
        w.add(50, "A");
        w.add(51, "B");
        w.add(52, "C");
        w.evolve(50, 51, EvolutionType.LEVEL, 55);
        w.evolve(51, 52, EvolutionType.LEVEL, 49);
        YaoSettings yao = new YaoSettings();
        yao.setEvolutionLevelScale(50, 20);
        List<String> notes = new EvolutionEditor(w, yao).applyLevels();
        assertEquals(44, w.evo(50, 51).getExtraInfo());        // 55 * 0.8
        assertEquals(49, w.evo(51, 52).getExtraInfo());        // below the minimum level: untouched
        assertEquals(16, w.evo(1, 2).getExtraInfo());
        assertEquals(EvolutionType.STONE, w.evo(133, 134).getType(), "only level evolutions are scaled");
        assertTrue(all(notes).contains("1 evolution"), all(notes));
    }

    @Test
    public void thePlayersOwnLevelsAreNeverScaledAndSurviveOtherChanges() {
        FakeWorld w = eeveeWorld(4);
        w.add(50, "A");
        w.add(51, "B");
        w.evolve(50, 51, EvolutionType.LEVEL, 70);
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(50, 51, EvolutionType.LEVEL, 60);
        yao.setEvolutionLevelScale(50, 50);
        EvolutionEditor editor = new EvolutionEditor(w, yao);
        editor.applyStructural();
        assertEquals(60, w.evo(50, 51).getExtraInfo());
        // something else (for example "make evolutions easier") changes it afterwards
        w.evo(50, 51).updateEvolutionMethod(EvolutionType.LEVEL, 30);
        editor.applyLevels();
        assertEquals(60, w.evo(50, 51).getExtraInfo(), "the user's level wins and is not scaled");
    }

    @Test
    public void scaledLevelsNeverGoBelowTwo() {
        FakeWorld w = new FakeWorld(4);
        w.add(1, "A");
        w.add(2, "B");
        w.evolve(1, 2, EvolutionType.LEVEL, 3);
        YaoSettings yao = new YaoSettings();
        yao.setEvolutionLevelScale(2, 90);
        new EvolutionEditor(w, yao).applyLevels();
        assertEquals(2, w.evo(1, 2).getExtraInfo());
    }

    // ---------------------------------------------------------------- the overlap rule itself

    @Test
    public void overlapRuleCases() {
        assertTrue(EvolutionEditor.overlaps(EvolutionType.STONE, 82, EvolutionType.STONE, 82));
        assertFalse(EvolutionEditor.overlaps(EvolutionType.STONE, 82, EvolutionType.STONE, 83));
        assertFalse(EvolutionEditor.overlaps(EvolutionType.STONE_MALE_ONLY, 82, EvolutionType.STONE_FEMALE_ONLY, 82));
        assertTrue(EvolutionEditor.overlaps(EvolutionType.STONE, 82, EvolutionType.STONE_MALE_ONLY, 82));
        assertFalse(EvolutionEditor.overlaps(EvolutionType.LEVEL_DAY, 20, EvolutionType.LEVEL_NIGHT, 20));
        assertTrue(EvolutionEditor.overlaps(EvolutionType.LEVEL, 20, EvolutionType.LEVEL_DAY, 40));
        assertTrue(EvolutionEditor.overlaps(EvolutionType.TRADE, 0, EvolutionType.TRADE, 0));
        assertFalse(EvolutionEditor.overlaps(EvolutionType.TRADE, 0, EvolutionType.TRADE_ITEM, 0));
        assertFalse(EvolutionEditor.overlaps(EvolutionType.LEVEL, 20, EvolutionType.STONE, 20));
    }

    // ---- Phase 19 (13/10/2026): stone evolutions also by level ----

    private static FakeWorld stoneWorld(int gen) {
        FakeWorld w = eeveeWorld(gen);
        w.add(37, "VULPIX");
        w.add(38, "NINETALES");
        w.add(44, "GLOOM");
        w.add(45, "VILEPLUME");
        w.add(182, "BELLOSSOM");
        w.add(43, "ODDISH");
        w.evolve(37, 38, EvolutionType.STONE, FIRE_STONE);
        w.evolve(43, 44, EvolutionType.LEVEL, 21);
        w.evolve(44, 45, EvolutionType.STONE, 85);
        w.evolve(44, 182, EvolutionType.STONE, 86);
        return w;
    }

    @Test
    public void singleStoneEvolutionGainsALevelEvolutionAndKeepsTheStone() {
        FakeWorld w = stoneWorld(4);
        List<String> notes = new EvolutionEditor(w, new YaoSettings()).addLevelRoutesToStoneEvolutions(0);
        assertEquals(2, w.species.get(37).getEvolutionsFrom().size());
        assertEquals(EvolutionType.STONE, w.species.get(37).getEvolutionsFrom().get(0).getType());
        Evolution added = w.species.get(37).getEvolutionsFrom().get(1);
        assertEquals(EvolutionType.LEVEL, added.getType());
        assertEquals(38, added.getTo().getNumber());
        assertEquals(25, added.getExtraInfo()); // 400 / 16 with no stats in the fake game
        assertTrue(all(notes).contains("1 Pokemon"));
    }

    @Test
    public void pokemonWithSeveralEvolutionsAreNeverTouched() {
        FakeWorld w = stoneWorld(4);
        new EvolutionEditor(w, new YaoSettings()).addLevelRoutesToStoneEvolutions(0);
        assertEquals(5, w.species.get(133).getEvolutionsFrom().size());
        assertEquals(2, w.species.get(44).getEvolutionsFrom().size());
    }

    @Test
    public void theLevelIsLimitedByTheFullyEvolvedLevelAndStaysAboveThePreviousStage() {
        FakeWorld w = stoneWorld(4);
        w.add(36, "ROCKET");
        w.evolve(36, 37, EvolutionType.LEVEL, 30);
        new EvolutionEditor(w, new YaoSettings()).addLevelRoutesToStoneEvolutions(20);
        Evolution added = w.species.get(37).getEvolutionsFrom().get(1);
        assertEquals(31, added.getExtraInfo()); // capped to 20, then kept above the previous stage's 30
        FakeWorld w2 = stoneWorld(4);
        new EvolutionEditor(w2, new YaoSettings()).addLevelRoutesToStoneEvolutions(20);
        assertEquals(20, w2.species.get(37).getEvolutionsFrom().get(1).getExtraInfo());
    }

    @Test
    public void speciesTheUserEditedAreLeftAlone() {
        FakeWorld w = stoneWorld(4);
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(37, 38, EvolutionType.STONE, MOON_STONE);
        EvolutionEditor editor = new EvolutionEditor(w, yao);
        editor.applyStructural();
        List<String> notes = editor.addLevelRoutesToStoneEvolutions(0);
        assertEquals(1, w.species.get(37).getEvolutionsFrom().size());
        assertTrue(all(notes).contains("edited by you"));
    }

    @Test
    public void gen1And2AddNothing() {
        FakeWorld w = stoneWorld(2);
        List<String> notes = new EvolutionEditor(w, new YaoSettings()).addLevelRoutesToStoneEvolutions(0);
        assertEquals(1, w.species.get(37).getEvolutionsFrom().size());
        assertTrue(all(notes).contains("cannot store"));
    }

    @Test
    public void settingRoundTripsThroughTheTextAndStringFormats() {
        YaoSettings yao = new YaoSettings();
        assertTrue(yao.isDefault());
        yao.setStoneEvolutionsAlsoLevel(true);
        assertFalse(yao.isDefault());
        assertTrue(YaoSettings.fromBase64(yao.toBase64()).isStoneEvolutionsAlsoLevel());
        assertTrue(yao.copy().isStoneEvolutionsAlsoLevel());
    }
}
