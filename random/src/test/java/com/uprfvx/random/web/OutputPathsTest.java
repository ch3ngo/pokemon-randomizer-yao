package com.uprfvx.random.web;

import com.uprfvx.random.web.OutputPaths.Resolved;
import com.uprfvx.random.web.OutputPaths.ThreeDsFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Added for YAO (12/10/2026): table tests for the pure path resolution behind the web run. Every case says what the
 * user typed and what has to come out, so a change in the rules shows up here first.
 */
public class OutputPathsTest {

    @TempDir
    Path tmp;

    File out;
    File gba;
    File threeDsGame;

    @BeforeEach
    public void setUp() throws Exception {
        out = Files.createDirectory(tmp.resolve("out")).toFile();
        File roms = Files.createDirectory(tmp.resolve("roms")).toFile();
        gba = new File(roms, "Emerald (U).gba");
        threeDsGame = new File(roms, "Omega Ruby (E).3ds");
        Files.writeString(gba.toPath(), "x");
        Files.writeString(threeDsGame.toPath(), "x");
    }

    private Resolved file(String typed) {
        return OutputPaths.resolve(typed, gba, false, "gba", ThreeDsFormat.FOLDER, null);
    }

    private Resolved cxi(String typed) {
        return OutputPaths.resolve(typed, threeDsGame, true, "cxi", ThreeDsFormat.CXI, "000400000011C400");
    }

    private Resolved folder(String typed) {
        return OutputPaths.resolve(typed, threeDsGame, true, "cxi", ThreeDsFormat.FOLDER, "000400000011C400");
    }

    private String p(String name) {
        return new File(out, name).getPath();
    }

    // ------------------------------------------------------------------ game files

    @Test
    public void aNameWithoutAnExtensionGetsTheGamesExtension() {
        Resolved r = file(p("My game"));
        assertEquals(p("My game.gba"), r.location.getPath());
        assertEquals(r.location.getPath(), r.cliOutput);
        assertFalse(r.directory);
        assertEquals(List.of(), r.warnings);
        assertNull(r.existing);
    }

    @Test
    public void theRightExtensionIsKeptInAnyCase() {
        assertEquals(p("My game.gba"), file(p("My game.gba")).location.getPath());
        assertEquals(p("My game.gba"), file(p("My game.GBA")).location.getPath());
        assertEquals(List.of(), file(p("My game.GBA")).warnings);
    }

    @Test
    public void aWrongGameExtensionIsReplacedWithAWarning() {
        Resolved r = file(p("My game.nds"));
        assertEquals(p("My game.gba"), r.location.getPath());
        assertEquals(1, r.warnings.size());
        assertTrue(r.warnings.get(0).contains(".nds") && r.warnings.get(0).contains(".gba"));
    }

    @Test
    public void aDotInTheNameThatIsNotAGameExtensionIsPartOfTheName() {
        assertEquals(p("v1.2 final.gba"), file(p("v1.2 final")).location.getPath());
        assertEquals(p("Emerald (U).randomized.gba"), file(p("Emerald (U).randomized")).location.getPath());
    }

    @Test
    public void aTrailingDotOrSpaceIsDropped() {
        assertEquals(p("My game.gba"), file(p("My game.")).location.getPath());
    }

    @Test
    public void quotesAroundThePathAreIgnored() {
        assertEquals(p("My game.gba"), file("\"" + p("My game") + "\"").location.getPath());
    }

    @Test
    public void anExistingFolderGetsTheGameInsideIt() {
        Resolved r = file(out.getPath());
        assertEquals(new File(out, "Emerald (U) randomized.gba").getPath(), r.location.getPath());
        assertEquals(1, r.warnings.size());
    }

    @Test
    public void theOriginalGameIsNeverTheDestination() {
        Resolved r = file(gba.getPath());
        assertNotEquals(gba.getAbsolutePath(), r.location.getAbsolutePath());
        assertEquals(gba.getParentFile(), r.location.getParentFile());
        assertEquals("Emerald (U) randomized.gba", r.location.getName());
        assertEquals(1, r.warnings.size());
        // typed without the extension it is the same game
        Resolved noExtension = file(new File(gba.getParentFile(), "Emerald (U)").getPath());
        assertEquals("Emerald (U) randomized.gba", noExtension.location.getName());
    }

    @Test
    public void anExistingResultIsReportedSoTheInterfaceCanAsk() throws Exception {
        File existing = new File(out, "Done.gba");
        Files.writeString(existing.toPath(), "old");
        Resolved r = file(p("Done"));
        assertEquals(existing, r.existing);
    }

    @Test
    public void emptyOrImpossibleNamesAreRefusedWithAMessage() {
        assertThrows(IllegalArgumentException.class, () -> file(""));
        assertThrows(IllegalArgumentException.class, () -> file("   "));
        assertThrows(IllegalArgumentException.class, () -> file(null));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> file(p("bad|name")));
        assertTrue(e.getMessage().contains("|"));
        assertThrows(IllegalArgumentException.class, () -> file(p("what?")));
    }

    @Test
    public void resolvingCreatesNothingAndPrepareCreatesTheParentFolders() {
        File deep = new File(new File(out, "new"), "sub");
        Resolved r = file(new File(deep, "Game").getPath());
        assertFalse(deep.exists(), "resolve only looks");
        OutputPaths.prepare(r);
        assertTrue(deep.isDirectory());
    }

    @Test
    public void aParentThatIsAFileIsRefusedWithAMessage() throws Exception {
        File blocker = new File(out, "blocker");
        Files.writeString(blocker.toPath(), "x");
        Resolved r = file(new File(blocker, "Game").getPath());
        assertThrows(IllegalArgumentException.class, () -> OutputPaths.prepare(r));
    }

    // ------------------------------------------------------------------ 3DS as one CXI file

    @Test
    public void aThreeDsGameAsOneFileIsAlwaysCxi() {
        assertEquals(p("OR rand.cxi"), cxi(p("OR rand")).location.getPath());
        assertEquals(p("OR rand.cxi"), cxi(p("OR rand.cxi")).location.getPath());
        Resolved renamed = cxi(p("OR rand.3ds"));
        assertEquals(p("OR rand.cxi"), renamed.location.getPath());
        assertEquals(1, renamed.warnings.size());
        assertFalse(renamed.directory);
    }

    // ------------------------------------------------------------------ 3DS as a LayeredFS folder

    @Test
    public void aThreeDsGameAsAFolderIsAFolderWithTheLumaLayout() {
        Resolved r = folder(p("OR rand"));
        assertTrue(r.directory);
        assertEquals(p("OR rand"), r.location.getPath());
        assertEquals(new File(new File(new File(out, "OR rand"), "luma"), "titles").getPath(), r.cliOutput);
        assertEquals(p("OR rand"), r.companionBase);
        assertEquals(List.of(), r.warnings);
    }

    @Test
    public void aFileLikeNameForAFolderLosesItsExtensionWithAWarning() {
        for (String typed : new String[]{"OR rand.3ds", "OR rand.cxi", "OR rand.CIA"}) {
            Resolved r = folder(p(typed));
            assertEquals(p("OR rand"), r.location.getPath(), typed);
            assertEquals(1, r.warnings.size(), typed);
        }
    }

    @Test
    public void aDottedNameThatIsNotAGameExtensionKeepsItsDot() {
        assertEquals(p("OR v1.2"), folder(p("OR v1.2")).location.getPath());
    }

    @Test
    public void aFileInTheWayMakesTheFolderGetAnotherName() throws Exception {
        Files.writeString(new File(out, "OR rand").toPath(), "x");
        Resolved r = folder(p("OR rand"));
        assertEquals(p("OR rand (layeredfs)"), r.location.getPath());
        assertEquals(1, r.warnings.size());
    }

    @Test
    public void aFolderHoldingTheOriginalGetsASubfolder() {
        Resolved r = folder(threeDsGame.getParentFile().getPath());
        assertEquals(new File(threeDsGame.getParentFile(), "Omega Ruby (E) randomized").getPath(), r.location.getPath());
        assertEquals(1, r.warnings.size());
    }

    @Test
    public void anEmptyExistingFolderIsUsedDirectly() {
        Resolved r = folder(out.getPath());
        assertEquals(out.getPath(), r.location.getPath());
        assertEquals(List.of(), r.warnings);
    }

    @Test
    public void aBusyFolderWithoutLumaGetsASubfolderButOneWithLumaIsReused() throws Exception {
        Files.writeString(new File(out, "notes.txt").toPath(), "x");
        Resolved busy = folder(out.getPath());
        assertEquals(new File(out, "Omega Ruby (E) randomized").getPath(), busy.location.getPath());
        assertEquals(1, busy.warnings.size());
        Files.createDirectory(new File(out, "luma").toPath());
        Resolved reused = folder(out.getPath());
        assertEquals(out.getPath(), reused.location.getPath());
    }

    @Test
    public void anExistingTitleFolderIsReportedAndPrepareBuildsTheLumaFolders() throws Exception {
        File title = new File(new File(new File(new File(out, "OR rand"), "luma"), "titles"), "000400000011C400");
        Files.createDirectories(title.toPath());
        assertEquals(title, folder(p("OR rand")).existing);
        Resolved fresh = folder(p("Another"));
        assertNull(fresh.existing);
        assertFalse(new File(out, "Another").exists());
        OutputPaths.prepare(fresh);
        assertTrue(new File(new File(new File(out, "Another"), "luma"), "titles").isDirectory());
    }

    @Test
    public void theFormatIsReadLeniently() {
        assertEquals(ThreeDsFormat.CXI, ThreeDsFormat.parse("cxi"));
        assertEquals(ThreeDsFormat.CXI, ThreeDsFormat.parse(" CXI "));
        assertEquals(ThreeDsFormat.FOLDER, ThreeDsFormat.parse("folder"));
        assertEquals(ThreeDsFormat.FOLDER, ThreeDsFormat.parse(null));
        assertEquals(ThreeDsFormat.FOLDER, ThreeDsFormat.parse("nonsense"));
    }

    @Test
    public void theHowToTextNamesTheRightStepsForEachKindOfResult() {
        String folderText = WebServer.howToUse(true, true, new File("OR rand"), "000400000011C400");
        assertTrue(folderText.contains("/luma/titles/000400000011C400"));
        assertTrue(folderText.contains("same game version") || folderText.contains("SAME game version"));
        assertTrue(WebServer.howToUse(true, false, new File("OR.cxi"), "x").contains("Load File"));
        assertTrue(WebServer.howToUse(false, false, new File("E.gba"), null).contains("E.gba"));
        assertFalse(folderText.contains("—"));
    }
}
