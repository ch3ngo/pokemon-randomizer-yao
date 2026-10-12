package com.uprfvx.random.yao;

import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.Species;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EditArgumentsTest {

    private static Species mon(int number, String name) {
        Species sp = new Species(number);
        sp.setName(name);
        return sp;
    }

    private static final List<Species> ENGLISH = List.of(mon(133, "EEVEE"), mon(197, "UMBREON"), mon(129, "MAGIKARP"),
            mon(130, "GYARADOS"), mon(25, "PIKACHU"), mon(26, "RAICHU"));
    private static final List<Species> SPANISH = List.of(mon(133, "EEVEE"), mon(197, "UMBREON"), mon(129, "MAGIKARP"));

    private static List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i <= 90; i++) {
            items.add(i == 0 ? null : i == 81 ? new Item(81, "Moon Stone") : i == 82 ? new Item(82, "Piedra Lunar") : new Item(i, "Item" + i));
        }
        return items;
    }

    private static List<Move> moves() {
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            Move m = new Move();
            m.number = i;
            m.name = i == 3 ? "Ancient Power" : "Move" + i;
            moves.add(i == 0 ? null : m);
        }
        return moves;
    }

    @Test
    public void floorAndScaleParseAndRepair() {
        YaoSettings yao = new YaoSettings();
        EditArguments.applyFloor(yao, "300");
        assertEquals(300, yao.getBoostFloorBst());
        EditArguments.applyFloor(yao, "lots");
        assertEquals(300, yao.getBoostFloorBst(), "a bad value changes nothing");
        EditArguments.applyScale(yao, "50:20");
        assertEquals(50, yao.getEvolutionScaleMinLevel());
        assertEquals(20, yao.getEvolutionScalePercent());
        EditArguments.applyScale(yao, "x:y");
        assertEquals(20, yao.getEvolutionScalePercent());
        EditArguments.applyScale(yao, "1:99");   // repaired to 2:90
        assertEquals(2, yao.getEvolutionScaleMinLevel());
        assertEquals(90, yao.getEvolutionScalePercent());
    }

    @Test
    public void stoneEvolutionByNameInEnglishAndSpanish() {
        YaoSettings en = new YaoSettings();
        EditArguments.applyEdits(en, ENGLISH, items(), moves(), List.of("Eevee:Umbreon:stone:Moon Stone"));
        assertEquals(List.of(new YaoSettings.EvolutionEdit(133, 197, EvolutionType.STONE, 81)), en.getEvolutionEdits());

        YaoSettings es = new YaoSettings();
        EditArguments.applyEdits(es, SPANISH, items(), moves(), List.of("Eevee:Umbreon:piedra:Piedra Lunar"));
        assertTrue(es.getEvolutionEdits().isEmpty(), "the Spanish word for the method is not understood");
        assertFalse(es.getWarnings().isEmpty());

        YaoSettings es2 = new YaoSettings();
        EditArguments.applyEdits(es2, SPANISH, items(), moves(), List.of("Eevee:Umbreon:stone:piedra lunar"));
        assertEquals(82, es2.getEvolutionEdits().get(0).extra(), "item names come from the ROM's language");
    }

    @Test
    public void levelAndOtherMethods() {
        YaoSettings yao = new YaoSettings();
        EditArguments.applyEdits(yao, ENGLISH, items(), moves(), List.of(
                "Magikarp:Gyarados:level:20",
                "Pikachu:Gyarados:level-day:30",
                "Eevee:Umbreon:happiness-night",
                "Pikachu:Raichu:trade-item:81",
                "Pikachu:Eevee:with-move:Ancient Power"));
        List<YaoSettings.EvolutionEdit> edits = yao.getEvolutionEdits();
        assertEquals(EvolutionType.LEVEL, edits.stream().filter(e -> e.fromSpecies() == 129).findFirst().orElseThrow().type());
        assertTrue(edits.stream().anyMatch(e -> e.type() == EvolutionType.LEVEL_DAY && e.extra() == 30), yao.getWarnings() + " " + edits);
        assertTrue(edits.stream().anyMatch(e -> e.type() == EvolutionType.HAPPINESS_NIGHT && e.extra() == 0), yao.getWarnings() + " " + edits);
        assertTrue(edits.stream().anyMatch(e -> e.type() == EvolutionType.WITH_MOVE && e.extra() == 3), yao.getWarnings() + " " + edits);
    }

    @Test
    public void badEvolutionArgumentsAreSkippedNotThrown() {
        YaoSettings yao = new YaoSettings();
        assertDoesNotThrow(() -> EditArguments.applyEdits(yao, ENGLISH, items(), moves(), Arrays.asList(
                "Eevee", "Eevee:Umbreon", "Eevee:Umbreon:flying", "Eevee:Umbreon:stone", "Eevee:Umbreon:stone:Nothing",
                "Eevee:Umbreon:level", "Eevee:Umbreon:level:abc", "Eevee:Eevee:level:5", "", null, "::::", "NONE:x:y")));
        assertTrue(yao.getEvolutionEdits().isEmpty());
        assertFalse(yao.getWarnings().isEmpty());
    }

    // ---- stat edits ----

    @Test
    public void quickStatEditsResolveByNameAndNumber() {
        YaoSettings yao = new YaoSettings();
        EditArguments.applyStatEdits(yao, ENGLISH, List.of("Magikarp:quick:25", "25:quick:-10"));
        YaoSettings.StatEdit karp = yao.getStatEdits().stream().filter(e -> e.speciesNumber() == 129).findFirst().orElseThrow();
        assertEquals(YaoSettings.StatEditMode.QUICK, karp.mode());
        assertEquals(25, karp.percent());
        YaoSettings.StatEdit pikachu = yao.getStatEdits().stream().filter(e -> e.speciesNumber() == 25).findFirst().orElseThrow();
        assertEquals(-10, pikachu.percent());
    }

    @Test
    public void fineTuneStatEditsParseAllSixNumbers() {
        YaoSettings yao = new YaoSettings();
        EditArguments.applyStatEdits(yao, ENGLISH, List.of("Magikarp:fine:20:10:55:15:20:80"));
        YaoSettings.StatEdit karp = yao.getStatEdits().get(0);
        assertEquals(YaoSettings.StatEditMode.FINE_TUNE, karp.mode());
        assertEquals(20, karp.hp());
        assertEquals(10, karp.attack());
        assertEquals(55, karp.defense());
        assertEquals(15, karp.spatk());
        assertEquals(20, karp.spdef());
        assertEquals(80, karp.speed());
    }

    @Test
    public void badStatEditArgumentsAreSkippedNotThrown() {
        YaoSettings yao = new YaoSettings();
        assertDoesNotThrow(() -> EditArguments.applyStatEdits(yao, ENGLISH, Arrays.asList(
                "Magikarp", "Magikarp:quick", "Magikarp:quick:abc", "Magikarp:fine:1:2:3", "Magikarp:banana:1",
                "Nothing:quick:10", "", null)));
        assertTrue(yao.getStatEdits().isEmpty());
        assertFalse(yao.getWarnings().isEmpty());
    }

    @Test
    public void removalsResolveBothPokemon() {
        YaoSettings yao = new YaoSettings();
        EditArguments.applyRemovals(yao, ENGLISH, List.of("Magikarp:Gyarados", "Eevee", "Nothing:Eevee"));
        assertEquals(List.of(new YaoSettings.EvolutionRemoval(129, 130)), yao.getEvolutionRemovals());
        assertEquals(2, yao.getWarnings().size());
    }

    @Test
    public void methodNamesAreFriendly() {
        assertEquals(EvolutionType.STONE, EditArguments.parseMethod("Item"));
        assertEquals(EvolutionType.LEVEL, EditArguments.parseMethod("level up"));
        assertEquals(EvolutionType.HAPPINESS, EditArguments.parseMethod("friendship"));
        assertEquals(EvolutionType.TRADE_ITEM, EditArguments.parseMethod("trade-item"));
        assertEquals(EvolutionType.LEVEL_WITH_DARK, EditArguments.parseMethod("LEVEL_WITH_DARK"));
        assertNull(EditArguments.parseMethod("none"));
        assertNull(EditArguments.parseMethod("banana"));
        assertNull(EditArguments.parseMethod(null));
    }

    // ---- settings round trip ----

    @Test
    public void evolutionSettingsRoundTrip() {
        YaoSettings yao = new YaoSettings();
        yao.addEvolutionEdit(133, 197, EvolutionType.STONE, 81);
        yao.addEvolutionEdit(129, 130, EvolutionType.LEVEL, 20);
        yao.addEvolutionRemoval(1, 2);
        yao.setEvolutionLevelScale(50, 20);
        YaoSettings back = YaoSettings.fromBase64(yao.toBase64());
        assertEquals(yao.getEvolutionEdits(), back.getEvolutionEdits());
        assertEquals(yao.getEvolutionRemovals(), back.getEvolutionRemovals());
        assertEquals(50, back.getEvolutionScaleMinLevel());
        assertEquals(20, back.getEvolutionScalePercent());
        assertFalse(back.isDefault());
    }

    @Test
    public void unreadableEvolutionLinesAreSkippedWithAWarning() {
        YaoSettings yao = YaoSettings.fromText("evo.0=1:2:BANANA:5\nevo.1=1:2:STONE\nevo.2=3:4:STONE:81\n"
                + "evoremove.0=x\nevoscale=oops\n");
        assertEquals(1, yao.getEvolutionEdits().size());
        assertEquals(4, yao.getWarnings().size());
    }

    @Test
    public void invalidEvolutionEditsAreDroppedWhenBuilt() {
        YaoSettings yao = new YaoSettings();
        assertFalse(yao.addEvolutionEdit(1, 2, EvolutionType.NONE, 0));
        assertFalse(yao.addEvolutionEdit(1, 2, EvolutionType.ITEM, 0));
        assertFalse(yao.addEvolutionEdit(1, 2, null, 0));
        assertFalse(yao.addEvolutionEdit(0, 2, EvolutionType.LEVEL, 5));
        assertFalse(yao.addEvolutionRemoval(3, 3));
        assertEquals(5, yao.getWarnings().size());
    }
}
