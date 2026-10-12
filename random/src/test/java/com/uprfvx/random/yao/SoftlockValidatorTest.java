package com.uprfvx.random.yao;

import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Species;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SoftlockValidatorTest {

    private static Species mon(int n) {
        Species s = new Species(n);
        s.setName("P" + n);
        return s;
    }

    private static SoftlockValidator.Data data(List<Integer> required, Set<Species> obtainable, java.util.function.BiPredicate<Species, Integer> learns) {
        return new SoftlockValidator.Data() {
            public List<Integer> requiredMoves() { return required; }
            public Set<Species> obtainable() { return obtainable; }
            public boolean canLearn(Species s, int move) { return learns.test(s, move); }
            public String moveName(int move) { return "Move" + move; }
        };
    }

    @Test
    public void noProblemWhenSomebodyCanLearnEveryRequiredMove() {
        Species a = mon(1), b = mon(2);
        List<String> problems = SoftlockValidator.check(data(List.of(15, 57), new LinkedHashSet<>(List.of(a, b)),
                (s, m) -> (s == a && m == 15) || (s == b && m == 57)));
        assertTrue(problems.isEmpty());
    }

    @Test
    public void aMoveNobodyCanLearnIsReportedByName() {
        Species a = mon(1);
        List<String> problems = SoftlockValidator.check(data(List.of(15, 57), Set.of(a), (s, m) -> m == 15));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Move57"));
    }

    @Test
    public void nothingObtainableIsSaidNotPretendedFine() {
        List<String> problems = SoftlockValidator.check(data(List.of(15), Set.of(), (s, m) -> true));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Could not tell"));
    }

    @Test
    public void noRequiredMovesMeansNoProblems() {
        assertTrue(SoftlockValidator.check(data(List.of(), Set.of(mon(1)), (s, m) -> false)).isEmpty());
    }

    @Test
    public void evolutionsOfObtainablePokemonCountAsObtainable() throws Exception {
        Species a = mon(1), b = mon(2);
        Evolution evo = new Evolution(a, b, EvolutionType.LEVEL, 16);
        a.getEvolutionsFrom().add(evo);
        b.getEvolutionsTo().add(evo);
        java.lang.reflect.Method m = SoftlockValidator.class.getDeclaredMethod("addFamilyAhead", Species.class, Set.class);
        m.setAccessible(true);
        Set<Species> set = new LinkedHashSet<>();
        m.invoke(null, a, set);
        assertEquals(Set.of(a, b), set);
        m.invoke(null, null, set);
        assertEquals(2, set.size());
    }
}
