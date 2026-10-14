package com.uprfvx.random.yao.guide;

import com.uprfvx.romio.gamedata.InGameTrade;
import com.uprfvx.romio.gamedata.StaticEncounter;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romio.RomOpener;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Added for YAO (13/10/2026). ROM based half of {@link GuideLocationsTest} (runs with the {@code testROMs} task,
 * skipped when a game is not in {@code roms/}): every static and trade of a real game gets an answer, the known
 * places are right in both languages, and the coverage per game is printed.
 */
public class GuideLocationsRomRandomizerTest {

    private final GuideLocations table = GuideLocations.get();


    private static RomHandler open(String romName) {
        String romsPath = System.getProperty("romsPath");
        Assumptions.assumeTrue(romsPath != null, "no romsPath");
        File file = new File(romsPath, romName);
        Assumptions.assumeTrue(file.isFile(), romName + " is not in roms/");
        RomOpener opener = new RomOpener();
        opener.setExtraMemoryAvailable(Runtime.getRuntime().maxMemory() >= 3_800_000_000L);
        RomOpener.Results r = opener.openRomFile(file);
        assertTrue(r.wasOpeningSuccessful(), "could not open " + romName);
        return r.getRomHandler();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Crystal (U).gbc", "Emerald (U).gba", "FireRed (U).gba", "FireRed (S).gba", "Diamond (E).nds",
            "Platinum (U).nds", "Platinum (S).nds", "SoulSilver (U).nds", "SoulSilver (S).nds", "Black (U).nds",
            "Black (S).nds", "Black 2 (U).nds", "Black 2 (S).nds", "X (E).3ds", "Omega Ruby (E).3ds", "Sun (E).3ds",
            "Ultra Sun (E).3ds"})
    public void everyStaticAndTradeOfARealGameGetsAnAnswerAndMostAreKnown(String romName) {
        RomHandler rom = open(romName);
        assertTrue(table.covers(rom), romName + " has no section");
        List<GuideLocations.Found> statics = table.forStatics(rom);
        List<StaticEncounter> list = rom.getStaticPokemon();
        assertEquals(list.size(), statics.size());
        long known = statics.stream().filter(f -> f.known).count();
        List<GuideLocations.Found> trades = table.forTrades(rom);
        List<InGameTrade> tradeList = rom.getInGameTrades();
        assertEquals(tradeList.size(), trades.size());
        long knownTrades = trades.stream().filter(f -> f.known).count();
        // the coverage is printed so the test report shows it per game
        System.out.printf("LOCATIONS %s: statics %d of %d known, trades %d of %d known%n", romName, known, statics.size(),
                knownTrades, trades.size());
        for (int i = 0; i < statics.size(); i++) {
            GuideLocations.Found f = statics.get(i);
            assertFalse(f.en.isBlank() || f.es.isBlank());
            if (!f.known) {
                System.out.printf("LOCATIONS   no place: static %d %s L%d%n", i, list.get(i).getSpecies().getName(), list.get(i).getLevel());
            }
        }
        for (int i = 0; i < trades.size(); i++) {
            if (!trades.get(i).known) {
                System.out.printf("LOCATIONS   no place: trade %d %s for %s%n", i, tradeList.get(i).getRequestedSpecies() == null ? "any"
                        : tradeList.get(i).getRequestedSpecies().getName(), tradeList.get(i).getGivenSpecies().getName());
            }
        }
        // every trade of the games with a section is known, trades are the part this table can fully cover
        if (!romName.startsWith("X ") && !romName.startsWith("Omega") && !romName.startsWith("Black 2") && !romName.startsWith("SoulSilver")) {
            assertEquals(trades.size(), knownTrades, "some trade of " + romName + " has no place");
        }
    }

    @Test
    public void crystalKnownPlacesAreEnglishAndFireRedSpanishIsSpanish() {
        RomHandler crystal = open("Crystal (U).gbc");
        List<StaticEncounter> list = crystal.getStaticPokemon();
        List<GuideLocations.Found> statics = table.forStatics(crystal);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getSpecies().getName().equals("LAPRAS")) {
                assertEquals("Union Cave", statics.get(i).en);
                assertEquals("Cueva Unión", statics.get(i).es);
            }
        }
        List<GuideLocations.Found> trades = table.forTrades(crystal);
        assertEquals("Goldenrod City", trades.get(0).en);
        assertEquals("Ciudad Trigal", trades.get(0).es);

        RomHandler frlg = open("FireRed (S).gba");
        List<GuideLocations.Found> frTrades = table.forTrades(frlg);
        assertEquals("Route 2", frTrades.get(0).en);
        assertEquals("Ruta 2", frTrades.get(0).es);
    }
}
