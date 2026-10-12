package com.uprfvx.random.yao;

import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.basestats.BaseStats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StatFloorApplierTest {

    private static Species mon(int number, String name, int hp, int atk, int def, int spa, int spd, int spe) {
        Species sp = new Species(number);
        sp.setName(name);
        sp.setBaseStats(new BaseStats(hp, atk, def, spa, spd, spe));
        return sp;
    }

    @Test
    public void targetBstMath() {
        assertEquals(300, StatFloorApplier.targetBst(200, 50, 0));
        assertEquals(240, StatFloorApplier.targetBst(200, 20, 0));
        assertEquals(300, StatFloorApplier.targetBst(200, null, 300));   // floor lifts it
        assertEquals(300, StatFloorApplier.targetBst(280, 5, 300));      // percent first, then the floor
        assertEquals(500, StatFloorApplier.targetBst(500, null, 300));   // never lowered
        assertEquals(500, StatFloorApplier.targetBst(500, 0, 0));
        assertEquals(200, StatFloorApplier.targetBst(200, null, 0));
    }

    @Test
    public void everyStatStaysWithinTheGameCap() {
        Species blissey = mon(242, "BLISSEY", 255, 10, 10, 75, 135, 55); // HP is already at the cap
        YaoSettings yao = new YaoSettings();
        yao.setBoostFloorBst(1000);
        StatFloorApplier.apply(List.of(blissey), yao);
        BaseStats bs = blissey.getBaseStats();
        for (int stat : new int[]{bs.getHp(), bs.getAttack(), bs.getDefense(), bs.getSpatk(), bs.getSpdef(), bs.getSpeed()}) {
            assertTrue(stat >= 0 && stat <= 255);
        }
        assertEquals(255, bs.getHp());
    }

    @Test
    public void theFloorLiftsOnlyThoseBelowIt() {
        Species weak = mon(1, "WEAK", 20, 20, 20, 20, 20, 20);       // 120
        Species mid = mon(2, "MID", 50, 50, 50, 50, 50, 50);         // 300: exactly at the floor
        Species strong = mon(3, "STRONG", 100, 100, 100, 100, 100, 100); // 600
        YaoSettings yao = new YaoSettings();
        yao.setBoostFloorBst(300);
        List<String> notes = StatFloorApplier.apply(List.of(weak, mid, strong), yao);
        assertEquals(300, weak.getBaseStats().getBST());
        assertEquals(300, mid.getBaseStats().getBST());
        assertEquals(600, strong.getBaseStats().getBST());
        assertTrue(String.join(" ", notes).contains("1 Pokemon"));
    }

    // Added (11/10/2026): the automatic floor must skip any Pokemon that still has a further evolution ahead of
    // it (a pre-evolution, e.g. a starter or baby Pokemon), while a fully evolved Pokemon below the floor is
    // still lifted.
    @Test
    public void theFloorSkipsPreEvolutionsButLiftsFullyEvolvedSpecies() {
        Species baby = mon(1, "BABY", 20, 20, 20, 20, 20, 20);       // 120, evolves further
        Species evolved = mon(2, "EVOLVED", 20, 20, 20, 20, 20, 20); // 120, nothing left to evolve into
        Evolution evo = new Evolution(baby, evolved, EvolutionType.LEVEL, 16);
        baby.getEvolutionsFrom().add(evo);
        evolved.getEvolutionsTo().add(evo);

        YaoSettings yao = new YaoSettings();
        yao.setBoostFloorBst(300);
        StatFloorApplier.apply(List.of(baby, evolved), yao);

        assertEquals(120, baby.getBaseStats().getBST(), "a pre-evolution is never touched by the automatic floor");
        assertEquals(300, evolved.getBaseStats().getBST(), "a fully evolved Pokemon below the floor is lifted");
    }

    @Test
    public void aFloorNeverMakesAPokemonWeakerAndNothingHappensWithoutSettings() {
        Species a = mon(1, "A", 80, 80, 80, 80, 80, 80);
        StatFloorApplier.apply(List.of(a), new YaoSettings());
        assertEquals(480, a.getBaseStats().getBST());
        YaoSettings yao = new YaoSettings();
        yao.setBoostFloorBst(300);
        StatFloorApplier.apply(List.of(a), yao);
        assertEquals(480, a.getBaseStats().getBST());
    }

    @Test
    public void badDataNeverThrows() {
        Species a = mon(1, "A", 20, 20, 20, 20, 20, 20);
        YaoSettings yao = new YaoSettings();
        yao.setBoostFloorBst(300);
        List<String> notes = StatFloorApplier.apply(new ArrayList<>(java.util.Arrays.asList(a, null)), yao);
        assertEquals(300, a.getBaseStats().getBST());
        assertFalse(notes.isEmpty());
    }

    // ---- settings ----

    @Test
    public void floorSettingRoundTripsAndIsRepaired() {
        YaoSettings yao = new YaoSettings();
        yao.setBoostFloorBst(99999);        // moved to 1530
        assertEquals(1530, yao.getBoostFloorBst());
        assertFalse(yao.isDefault());

        YaoSettings back = YaoSettings.fromBase64(yao.toBase64());
        assertEquals(yao.getBoostFloorBst(), back.getBoostFloorBst());
    }

    @Test
    public void unreadableFloorLineIsSkippedWithAWarning() {
        YaoSettings yao = YaoSettings.fromText("boost.floor=lots\n");
        assertEquals(0, yao.getBoostFloorBst());
        assertEquals(1, yao.getWarnings().size());
    }
}
