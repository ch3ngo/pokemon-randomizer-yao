package com.uprfvx.random.yao;

import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Species;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class EggBoxTest {

    private static Species mon(int number, String name) {
        Species species = new Species(number);
        species.setName(name);
        return species;
    }

    private static void evolve(Species from, Species to) {
        Evolution evolution = new Evolution(from, to, EvolutionType.LEVEL, 16);
        from.getEvolutionsFrom().add(evolution);
        to.getEvolutionsTo().add(evolution);
    }

    /** A line of three (1,2,3), a line of two (4,5), one that does not evolve (6) and a legendary (150 Mewtwo). */
    private static List<Species> pool() {
        Species a = mon(1, "A"), b = mon(2, "B"), c = mon(3, "C"), d = mon(4, "D"), e = mon(5, "E"), f = mon(6, "F"),
                mewtwo = mon(150, "MEWTWO");
        evolve(a, b);
        evolve(b, c);
        evolve(d, e);
        return new ArrayList<>(List.of(a, b, c, d, e, f, mewtwo));
    }

    private static YaoSettings eggs(int count) {
        YaoSettings yao = new YaoSettings();
        yao.setEggBoxCount(count);
        return yao;
    }

    private static Set<Integer> numbers(EggBoxSelector.Result result) {
        return result.eggs().stream().map(Species::getNumber).collect(Collectors.toSet());
    }

    @Test
    public void lineLengthsAndBasicStage() {
        List<Species> pool = pool();
        assertEquals(3, EggBoxSelector.lineLength(pool.get(0)));
        assertEquals(3, EggBoxSelector.lineLength(pool.get(1)));
        assertEquals(3, EggBoxSelector.lineLength(pool.get(2)));
        assertEquals(2, EggBoxSelector.lineLength(pool.get(3)));
        assertEquals(1, EggBoxSelector.lineLength(pool.get(5)));
        assertTrue(EggBoxSelector.isBasic(pool.get(0)));
        assertFalse(EggBoxSelector.isBasic(pool.get(1)));
    }

    @Test
    public void aCycleInTheEvolutionsDoesNotLoopForever() {
        Species a = mon(1, "A"), b = mon(2, "B");
        evolve(a, b);
        evolve(b, a);
        assertDoesNotThrow(() -> EggBoxSelector.lineLength(a));
        assertDoesNotThrow(() -> EggBoxSelector.pick(List.of(a, b), eggs(2), new Random(1)));
    }

    @Test
    public void nothingIsPickedWhenTheBoxIsOff() {
        assertTrue(EggBoxSelector.pick(pool(), eggs(0), new Random(1)).eggs().isEmpty());
        assertTrue(EggBoxSelector.pick(null, eggs(3), new Random(1)).eggs().isEmpty());
    }

    @Test
    public void legendaryRule() {
        YaoSettings none = eggs(10);
        none.setEggBoxLegendary(YaoSettings.EggLegendary.NONE);
        assertFalse(numbers(EggBoxSelector.pick(pool(), none, new Random(1))).contains(150));
        YaoSettings only = eggs(10);
        only.setEggBoxLegendary(YaoSettings.EggLegendary.ONLY);
        assertEquals(Set.of(150), numbers(EggBoxSelector.pick(pool(), only, new Random(1))));
    }

    @Test
    public void lineLengthAndBasicFilters() {
        YaoSettings three = eggs(10);
        three.setEggBoxStages(List.of(3));
        assertEquals(Set.of(1, 2, 3), numbers(EggBoxSelector.pick(pool(), three, new Random(1))));
        three.setEggBoxBasicOnly(true);
        assertEquals(Set.of(1), numbers(EggBoxSelector.pick(pool(), three, new Random(1))));
        YaoSettings twoOrOne = eggs(10);
        twoOrOne.setEggBoxStages(List.of(1, 2));
        twoOrOne.setEggBoxLegendary(YaoSettings.EggLegendary.NONE);
        assertEquals(Set.of(4, 5, 6), numbers(EggBoxSelector.pick(pool(), twoOrOne, new Random(1))));
    }

    @Test
    public void tooFewPokemonGivesASmallerBoxWithANote() {
        YaoSettings yao = eggs(20);
        yao.setEggBoxLegendary(YaoSettings.EggLegendary.ONLY);
        EggBoxSelector.Result result = EggBoxSelector.pick(pool(), yao, new Random(1));
        assertEquals(1, result.eggs().size());
        assertFalse(result.notes().isEmpty());
    }

    @Test
    public void duplicatesOnlyWhenAsked() {
        YaoSettings yao = eggs(20);
        yao.setEggBoxLegendary(YaoSettings.EggLegendary.ONLY);
        yao.setEggBoxDuplicates(true);
        EggBoxSelector.Result result = EggBoxSelector.pick(pool(), yao, new Random(1));
        assertEquals(20, result.eggs().size());
        assertEquals(Set.of(150), numbers(result));
        YaoSettings unique = eggs(7);
        assertEquals(7, numbers(EggBoxSelector.pick(pool(), unique, new Random(1))).size());
    }

    @Test
    public void nothingPassingTheFiltersGivesNoEggsAndANote() {
        YaoSettings yao = eggs(5);
        yao.setEggBoxStages(List.of(2));
        yao.setEggBoxBasicOnly(true);
        yao.setEggBoxLegendary(YaoSettings.EggLegendary.ONLY);
        EggBoxSelector.Result result = EggBoxSelector.pick(pool(), yao, new Random(1));
        assertTrue(result.eggs().isEmpty());
        assertFalse(result.notes().isEmpty());
    }

    @Test
    public void theSameSeedGivesTheSameEggs() {
        List<Species> big = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            big.add(mon(i, "P" + i));
        }
        List<Integer> first = EggBoxSelector.pick(big, eggs(12), new Random(42)).eggs().stream().map(Species::getNumber).toList();
        List<Integer> second = EggBoxSelector.pick(new ArrayList<>(big), eggs(12), new Random(42)).eggs().stream().map(Species::getNumber).toList();
        assertEquals(first, second);
        assertNotEquals(first, EggBoxSelector.pick(big, eggs(12), new Random(43)).eggs().stream().map(Species::getNumber).toList());
    }

    @Test
    public void settingsAreRepairedAndRoundTrip() {
        YaoSettings yao = new YaoSettings();
        yao.setEggBoxCount(500);
        assertEquals(YaoSettings.MAX_EGG_BOX, yao.getEggBoxCount());
        yao.setEggBoxCount(-2);
        assertEquals(0, yao.getEggBoxCount());
        yao.setEggBoxCount(12);
        yao.setEggBoxLegendary(YaoSettings.EggLegendary.NONE);
        yao.setEggBoxStages(List.of(3, 9, 2));
        yao.setEggBoxBasicOnly(true);
        yao.setEggBoxDuplicates(true);
        assertEquals(Set.of(2, 3), yao.getEggBoxStages());
        assertFalse(yao.isDefault());
        YaoSettings back = YaoSettings.fromBase64(yao.toBase64());
        assertEquals(12, back.getEggBoxCount());
        assertEquals(YaoSettings.EggLegendary.NONE, back.getEggBoxLegendary());
        assertEquals(Set.of(2, 3), back.getEggBoxStages());
        assertTrue(back.isEggBoxBasicOnly());
        assertTrue(back.isEggBoxDuplicates());
        assertEquals(12, yao.copy().getEggBoxCount());
    }

    @Test
    public void unreadableEggLinesAreSkippedWithAWarning() {
        YaoSettings yao = YaoSettings.fromText("eggbox.count=abc\neggbox.legendary=maybe\neggbox.stages=2,x\neggbox.count=4\n");
        assertEquals(4, yao.getEggBoxCount());
        assertEquals(3, yao.getWarnings().size());
        assertEquals(Set.of(2), yao.getEggBoxStages());
    }
}
