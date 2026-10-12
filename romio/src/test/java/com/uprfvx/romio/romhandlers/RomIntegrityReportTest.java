package com.uprfvx.romio.romhandlers;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RomIntegrityReportTest {

    private static final Map<Integer, Long> OVERLAYS = Map.of(1, 11L, 12, 12L);
    private static final Map<String, Long> FILES = Map.of("Text", 1L, "PokemonStats", 2L, "WildPokemon", 3L);

    private static RomIntegrityReport ds(int gen, long arm9Actual, Map<Integer, Long> ovl, Map<String, Long> files) {
        return RomIntegrityReport.forDSGame(gen, 100L, arm9Actual, OVERLAYS, ovl, FILES, files);
    }

    @Test
    public void identicalRomIsClean() {
        RomIntegrityReport r = ds(5, 100L, OVERLAYS, FILES);
        assertTrue(r.isClean());
        assertTrue(r.getAffectedFeatures().isEmpty());
        assertEquals("", r.describe());
    }

    @Test
    public void onlyArm9DifferingNamesArm9AndTheOptionsThatUseIt() {
        RomIntegrityReport r = ds(5, 999L, OVERLAYS, FILES);
        assertFalse(r.isClean());
        assertEquals(1, r.getDifferences().size());
        assertEquals(RomIntegrityReport.Kind.ARM9, r.getDifferences().get(0).kind());
        assertTrue(r.getAffectedFeatures().contains("TM and HM moves"));
        assertTrue(r.getAffectedFeatures().contains("forgettable HMs"));
        // options that do not touch the ARM9 are not listed
        assertFalse(r.getAffectedFeatures().contains("wild encounters"));
        assertTrue(r.describe().contains("ARM9"));
        assertTrue(r.describe().contains("unmodified retail dump"));
    }

    @Test
    public void differentGenerationsListDifferentOptions() {
        assertTrue(ds(4, 999L, OVERLAYS, FILES).getAffectedFeatures().contains("egg moves"));
        assertFalse(ds(5, 999L, OVERLAYS, FILES).getAffectedFeatures().contains("egg moves"));
    }

    @Test
    public void dataFileDifferenceListsTheMatchingOption() {
        Map<String, Long> files = Map.of("Text", 1L, "PokemonStats", 2L, "WildPokemon", 777L);
        RomIntegrityReport r = ds(4, 100L, OVERLAYS, files);
        assertEquals(1, r.getDifferences().size());
        assertEquals(java.util.List.of("wild encounters"), r.getAffectedFeatures());
    }

    @Test
    public void unknownFileKeyIsStillNamed() {
        RomIntegrityReport r = RomIntegrityReport.forDSGame(4, 100L, 100L, Map.of(), Map.of(),
                Map.of("SomethingNew", 1L), Map.of("SomethingNew", 2L));
        assertTrue(r.getAffectedFeatures().get(0).contains("SomethingNew"));
    }

    @Test
    public void missingComponentCountsAsDifferent() {
        RomIntegrityReport r = ds(5, 100L, Map.of(1, 11L), FILES); // overlay 12 missing
        assertEquals(java.util.Set.of("Overlay 12"), r.differingNames());
    }

    @Test
    public void overlayDifferenceMentionsCodePatchingOptions() {
        RomIntegrityReport r = ds(4, 100L, Map.of(1, 11L, 12, 999L), FILES);
        assertEquals(java.util.Set.of("Overlay 12"), r.differingNames());
        assertTrue(r.getAffectedFeatures().get(0).contains("extra game code"));
    }

    @Test
    public void wholeRomReportForGameBoyAndGba() {
        assertTrue(RomIntegrityReport.wholeRom(2, 5L, 5L).isClean());
        RomIntegrityReport r = RomIntegrityReport.wholeRom(3, 5L, 6L);
        assertFalse(r.isClean());
        assertEquals(RomIntegrityReport.Kind.WHOLE_ROM, r.getDifferences().get(0).kind());
        assertFalse(r.getAffectedFeatures().isEmpty());
    }

    @Test
    public void manyDifferencesAreAbbreviatedInTheText() {
        Map<String, Long> expected = new java.util.HashMap<>();
        Map<String, Long> actual = new java.util.HashMap<>();
        for (int i = 0; i < 20; i++) {
            expected.put("File" + i, 1L);
            actual.put("File" + i, 2L);
        }
        RomIntegrityReport r = RomIntegrityReport.forDSGame(5, 1L, 1L, Map.of(), Map.of(), expected, actual);
        assertEquals(20, r.getDifferences().size());
        assertTrue(r.describe().contains("and 12 more"));
    }

    @Test
    public void black2AndWhite2DoNotListShopItemsButBlackAndWhite1Do() {
        RomIntegrityReport bw2 = RomIntegrityReport.forDSGame(5, "BW2", 100L, 999L, OVERLAYS, OVERLAYS, FILES, FILES);
        RomIntegrityReport bw1 = RomIntegrityReport.forDSGame(5, "BW", 100L, 999L, OVERLAYS, OVERLAYS, FILES, FILES);
        assertTrue(bw2.getAffectedFeatures().stream().noneMatch(f -> f.startsWith("shop items")));
        assertTrue(bw1.getAffectedFeatures().stream().anyMatch(f -> f.startsWith("shop items")));
        assertTrue(bw2.getAffectedFeatures().contains("TM and HM moves"));
    }
}
