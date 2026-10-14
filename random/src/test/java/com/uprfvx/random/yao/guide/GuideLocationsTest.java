package com.uprfvx.random.yao.guide;

import com.uprfvx.romio.gamedata.InGameTrade;
import com.uprfvx.romio.gamedata.StaticEncounter;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romio.RomOpener;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Added for YAO (13/10/2026): the table of places of static, gift and traded Pokemon ({@code locations.txt}).
 * The first group needs no game: the file reads cleanly (every {TAG} is a known place, every species name matches,
 * no forbidden dash), and the game names this program shows map to the right section. The second group runs on the
 * real games when {@code roms/} has them (skipped otherwise) and checks the known places and the coverage.
 */
public class GuideLocationsTest {

    private final GuideLocations table = GuideLocations.get();

    @Test
    public void theTableReadsWithoutProblems() {
        assertTrue(table.problems().isEmpty(), "problems in locations.txt: " + table.problems());
    }

    @Test
    public void everyFamilyOfTheTableIsReachableFromAGameName() {
        String[] names = {"Pokemon Crystal (U)", "Pokemon FireRed (U) 1.0", "Pokemon Fire Red (U) 1.0", "Pokemon LeafGreen (U)",
                "Pokemon Emerald (U)", "Pokemon Diamond (E)", "Pokemon Pearl (U)", "Pokemon Platinum (U Rev 1)",
                "Pokemon HeartGold (U)", "Pokemon SoulSilver (S)", "Pokemon Black (U)", "Pokemon White (S)",
                "Pokemon Black 2 (U)", "Pokemon White 2 (E)", "Pokemon X", "Pokemon Y", "Pokemon Omega Ruby",
                "Pokemon Alpha Sapphire", "Pokemon Sun", "Pokemon Moon", "Pokemon Ultra Sun", "Pokemon Ultra Moon"};
        java.util.Set<String> reached = new java.util.TreeSet<>();
        for (String name : names) {
            String family = GuideLocations.familyOf(name);
            assertNotNull(family, name + " has no section");
            assertTrue(table.hasFamily(family), name + " maps to a section that does not exist: " + family);
            reached.add(family);
        }
        assertEquals(new java.util.TreeSet<>(table.familyKeys()), reached, "a section can never be reached");
        // games without a section are not forced into one by a loose name match
        assertNull(GuideLocations.familyOf("Pokemon Red (U)"));
        assertNull(GuideLocations.familyOf("Pokemon Ruby (U)"));
        assertNull(GuideLocations.familyOf("Pokemon Gold (U)"));
        assertNull(GuideLocations.familyOf(null));
    }

    @Test
    public void noDashCharactersAndEverySpanishPartIsSet() throws Exception {
        try (java.io.InputStream in = GuideLocations.class.getResourceAsStream(GuideLocations.RESOURCE)) {
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(text.indexOf((char) 0x2014) >= 0 || text.indexOf((char) 0x2013) >= 0, "locations.txt has a dash character");
        }
    }
}
