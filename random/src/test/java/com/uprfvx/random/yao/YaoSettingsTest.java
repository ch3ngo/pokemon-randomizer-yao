package com.uprfvx.random.yao;

import com.uprfvx.random.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class YaoSettingsTest {

    private static final String CLEAN_SETTINGS = "src/test/resources/settings/cli_clean.rnqs";

    private static Settings loadClean() throws IOException {
        try (FileInputStream in = new FileInputStream(CLEAN_SETTINGS)) {
            return Settings.readFromFileFormat(in);
        }
    }

    // --- YaoSettings ---

    @Test
    public void defaultHasNoItemsAndIsDefault() {
        YaoSettings yao = new YaoSettings();
        assertTrue(yao.isDefault());
        assertTrue(yao.getCustomShopItems().isEmpty());
    }

    @Test
    public void base64RoundTripKeepsItemsAndOrder() {
        YaoSettings yao = new YaoSettings();
        yao.addCustomShopItem(50, 1);
        yao.addCustomShopItem(80, 2100);
        yao.addCustomShopItem(7, 300);

        YaoSettings back = YaoSettings.fromBase64(yao.toBase64());
        assertEquals(yao.getCustomShopItems(), back.getCustomShopItems());
        assertFalse(back.isDefault());
    }

    @Test
    public void sameItemListedTwiceKeepsTheLastPrice() {
        YaoSettings yao = new YaoSettings();
        yao.addCustomShopItem(50, 1);
        yao.addCustomShopItem(50, 99);
        assertEquals(List.of(new YaoSettings.CustomShopItem(50, 99)), yao.getCustomShopItems());
    }

    @Test
    public void unknownKeysAreIgnoredSoNewerSettingsStayReadable() {
        YaoSettings yao = YaoSettings.fromText("yao.format=1\nshiny.chance=50\nshop.0=50:1\n");
        assertEquals(List.of(new YaoSettings.CustomShopItem(50, 1)), yao.getCustomShopItems());
    }

    @Test
    public void unreadableLinesAreSkippedWithAWarningAndGoodOnesSurvive() {
        YaoSettings yao = YaoSettings.fromText(
                "no equals sign\nshop.0=abc\nshop.1=50:abc\nshop.2=:5\nshop.3=50:7\n");
        assertEquals(List.of(new YaoSettings.CustomShopItem(50, 7)), yao.getCustomShopItems());
        assertEquals(4, yao.getWarnings().size());
    }

    @Test
    public void invalidIdsAreDroppedAndInvalidPricesRepaired() {
        YaoSettings yao = new YaoSettings();
        assertFalse(yao.addCustomShopItem(0, 5));
        assertFalse(yao.addCustomShopItem(-3, 5));
        assertTrue(yao.addCustomShopItem(50, 0));          // becomes 1
        assertTrue(yao.addCustomShopItem(51, -20));        // becomes 1
        assertTrue(yao.addCustomShopItem(52, Integer.MAX_VALUE)); // capped
        assertEquals(List.of(
                new YaoSettings.CustomShopItem(50, 1),
                new YaoSettings.CustomShopItem(51, 1),
                new YaoSettings.CustomShopItem(52, YaoSettings.MAX_REASONABLE_PRICE)), yao.getCustomShopItems());
        assertEquals(5, yao.getWarnings().size());
    }

    @Test
    public void newerFormatStillReadsWhatItUnderstandsAndWarns() {
        YaoSettings yao = YaoSettings.fromText("yao.format=99\nshop.0=50:1\n");
        assertEquals(List.of(new YaoSettings.CustomShopItem(50, 1)), yao.getCustomShopItems());
        assertFalse(yao.getWarnings().isEmpty());
    }

    @Test
    public void damagedBase64FallsBackToDefaults() {
        YaoSettings yao = YaoSettings.fromBase64("this is !!! not base64");
        assertTrue(yao.isDefault());
        assertEquals(1, yao.getWarnings().size());
    }

    @Test
    public void tooManyItemsAreDroppedNotRejected() {
        YaoSettings yao = new YaoSettings();
        for (int i = 1; i <= YaoSettings.MAX_CUSTOM_SHOP_ITEMS; i++) {
            assertTrue(yao.addCustomShopItem(i, 1));
        }
        assertFalse(yao.addCustomShopItem(1000, 1));
        assertEquals(YaoSettings.MAX_CUSTOM_SHOP_ITEMS, yao.getCustomShopItems().size());
        // re-pricing an existing item still works when the list is full
        assertTrue(yao.addCustomShopItem(1, 2));
        assertEquals(2, yao.getCustomShopItems().get(0).price());
    }

    @Test
    public void copyIsIndependent() {
        YaoSettings yao = new YaoSettings();
        yao.addCustomShopItem(50, 1);
        YaoSettings copy = yao.copy();
        copy.addCustomShopItem(51, 2);
        assertEquals(1, yao.getCustomShopItems().size());
        assertEquals(2, copy.getCustomShopItems().size());
    }

    // --- price rules ---

    @Test
    public void pricesAreAdaptedPerGeneration() {
        // Gen 1 to 4 can store a price of 1
        for (int gen = 1; gen <= 4; gen++) {
            assertEquals(1, ShopPriceLimits.normalize(gen, 1), "gen " + gen);
        }
        // Gen 5 to 7 store multiples of 10 and must never round down to free
        for (int gen = 5; gen <= 7; gen++) {
            assertEquals(10, ShopPriceLimits.normalize(gen, 1), "gen " + gen);
            assertEquals(10, ShopPriceLimits.normalize(gen, 10), "gen " + gen);
            assertEquals(20, ShopPriceLimits.normalize(gen, 11), "gen " + gen);
            assertEquals(2100, ShopPriceLimits.normalize(gen, 2100), "gen " + gen);
        }
    }

    @Test
    public void pricesAreClampedToWhatTheGenerationCanStore() {
        assertEquals(999_999, ShopPriceLimits.normalize(1, 5_000_000));
        assertEquals(65_535, ShopPriceLimits.normalize(2, 70_000));
        assertEquals(65_535, ShopPriceLimits.normalize(4, 70_000));
        assertEquals(655_350, ShopPriceLimits.normalize(5, 5_000_000));
        assertEquals(1, ShopPriceLimits.normalize(3, -5));
    }

    @Test
    public void extremePricesNeverOverflowOrFail() {
        for (int gen = 1; gen <= 7; gen++) {
            int max = ShopPriceLimits.maxPrice(gen);
            assertEquals(max, ShopPriceLimits.normalize(gen, Integer.MAX_VALUE), "gen " + gen);
            assertEquals(ShopPriceLimits.minPrice(gen), ShopPriceLimits.normalize(gen, Integer.MIN_VALUE), "gen " + gen);
            assertEquals(ShopPriceLimits.minPrice(gen), ShopPriceLimits.normalize(gen, 0), "gen " + gen);
        }
    }

    // --- Settings integration ---

    @Test
    public void settingsFileWithoutYaoBlockIsStillAPlainUpstreamFile() throws IOException {
        Settings settings = loadClean();
        assertTrue(settings.getYaoSettings().isDefault());
    }

    @Test
    public void settingsFileRoundTripKeepsYaoSettings(@TempDir Path dir) throws IOException {
        Settings settings = loadClean();
        settings.getYaoSettings().addCustomShopItem(50, 1);
        settings.getYaoSettings().addCustomShopItem(80, 2100);

        Path file = dir.resolve("with-yao.rnqs");
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            settings.writeToFileFormat(out);
        }
        Settings back;
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            back = Settings.readFromFileFormat(in);
        }
        assertEquals(settings.getYaoSettings().getCustomShopItems(), back.getYaoSettings().getCustomShopItems());
    }

    @Test
    public void fileWithoutYaoSettingsHasNoExtraBytes(@TempDir Path dir) throws IOException {
        Settings plain = loadClean();
        Path file = dir.resolve("plain.rnqs");
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            plain.writeToFileFormat(out);
        }
        // identical to what upstream would write: 4 version + 4 length + declared length, nothing after
        byte[] bytes = Files.readAllBytes(file);
        int declared = java.nio.ByteBuffer.wrap(bytes, 4, 4).getInt();
        assertEquals(8 + declared, bytes.length);
    }

    @Test
    public void fileWithYaoBlockKeepsTheUpstreamPartIntact(@TempDir Path dir) throws IOException {
        Settings plain = loadClean();
        Path plainFile = dir.resolve("plain.rnqs");
        try (FileOutputStream out = new FileOutputStream(plainFile.toFile())) {
            plain.writeToFileFormat(out);
        }
        Settings extended = loadClean();
        extended.getYaoSettings().addCustomShopItem(50, 1);
        Path extendedFile = dir.resolve("extended.rnqs");
        try (FileOutputStream out = new FileOutputStream(extendedFile.toFile())) {
            extended.writeToFileFormat(out);
        }
        byte[] plainBytes = Files.readAllBytes(plainFile);
        byte[] extendedBytes = Files.readAllBytes(extendedFile);
        assertTrue(extendedBytes.length > plainBytes.length);
        // the first part is byte-identical, so an upstream reader sees exactly the same settings
        assertArrayEquals(plainBytes, java.util.Arrays.copyOf(extendedBytes, plainBytes.length));
    }

    @Test
    public void settingsStringRoundTripKeepsYaoSettings() throws IOException {
        Settings settings = loadClean();
        settings.getYaoSettings().addCustomShopItem(50, 1);

        String string = settings.toString();
        assertTrue(string.indexOf(YaoSettings.STRING_SEPARATOR) > 0);
        Settings back = Settings.fromString(string);
        assertEquals(settings.getYaoSettings().getCustomShopItems(), back.getYaoSettings().getCustomShopItems());
    }

    @Test
    public void settingsStringWithoutYaoSettingsIsUnchangedUpstreamFormat() throws IOException {
        Settings settings = loadClean();
        String string = settings.toString();
        assertEquals(-1, string.indexOf(YaoSettings.STRING_SEPARATOR));
        assertTrue(Settings.fromString(string).getYaoSettings().isDefault());
    }

    @Test
    public void damagedYaoBlockInAFileDoesNotStopTheRestOfTheSettingsLoading(@TempDir Path dir) throws IOException {
        Settings settings = loadClean();
        settings.getYaoSettings().addCustomShopItem(50, 1);
        Path file = dir.resolve("truncated.rnqs");
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            settings.writeToFileFormat(out);
        }
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(bytes, bytes.length - 3)); // cut the YAO block short

        Settings back;
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            back = Settings.readFromFileFormat(in);
        }
        assertTrue(back.getYaoSettings().isDefault());
        assertFalse(back.getYaoSettings().getWarnings().isEmpty());
    }

    @Test
    public void damagedYaoBlockInAStringDoesNotStopTheRestOfTheSettingsLoading() throws IOException {
        Settings plain = loadClean();
        Settings back = Settings.fromString(plain.toString() + YaoSettings.STRING_SEPARATOR + "###damaged###");
        assertTrue(back.getYaoSettings().isDefault());
        assertFalse(back.getYaoSettings().getWarnings().isEmpty());
    }

    // --- shiny chance ---

    @Test
    public void shinyChanceRoundTripsAndMakesTheSettingsNonDefault() {
        YaoSettings yao = new YaoSettings();
        yao.setShinyChancePercent(35);
        assertFalse(yao.isDefault());
        assertEquals(35, YaoSettings.fromBase64(yao.toBase64()).getShinyChancePercent());
    }

    @Test
    public void outOfRangeShinyChanceIsMovedToTheNearestValidOne() {
        YaoSettings yao = new YaoSettings();
        yao.setShinyChancePercent(250);
        assertEquals(100, yao.getShinyChancePercent());
        yao.setShinyChancePercent(-4);
        assertEquals(0, yao.getShinyChancePercent());
        assertEquals(2, yao.getWarnings().size());
    }

    @Test
    public void unreadableShinyChanceInTextIsSkippedWithAWarning() {
        YaoSettings yao = YaoSettings.fromText("shiny.permille=lots\nshop.0=50:1\n");
        assertEquals(0, yao.getShinyChancePercent());
        assertEquals(1, yao.getCustomShopItems().size());
        assertEquals(1, yao.getWarnings().size());
    }

    @Test
    public void zeroShinyChanceIsNotWrittenSoOldSettingsStayIdentical() {
        YaoSettings yao = new YaoSettings();
        yao.addCustomShopItem(50, 1);
        assertFalse(yao.toText().contains("shiny"));
    }

    @Test
    public void shinyChanceSurvivesASettingsStringRoundTrip() throws IOException {
        Settings settings = loadClean();
        settings.getYaoSettings().setShinyChancePercent(100);
        Settings back = Settings.fromString(settings.toString());
        assertEquals(100, back.getYaoSettings().getShinyChancePercent());
    }

    @Test
    public void shinyChanceKeepsOneDecimalAndGoesUpToOneHundred() {
        YaoSettings yao = new YaoSettings();
        yao.setShinyChancePercent(0.1);
        assertEquals(1, yao.getShinyChancePermille());
        assertEquals(0.1, yao.getShinyChancePercent(), 1e-9);
        yao.setShinyChancePercent(12.34);
        assertEquals(123, yao.getShinyChancePermille());
        yao.setShinyChancePercent(99.9);
        assertEquals(999, yao.getShinyChancePermille());
        yao.setShinyChancePercent(100);
        assertEquals(1000, yao.getShinyChancePermille());
        assertEquals(100.0, yao.getShinyChancePercent(), 1e-9);
    }

    @Test
    public void everyTenthOfAPercentRoundTrips() {
        for (int permille = 0; permille <= 1000; permille++) {
            YaoSettings yao = new YaoSettings();
            yao.setShinyChancePercent(permille / 10.0);
            assertEquals(permille, yao.getShinyChancePermille());
            assertEquals(permille, YaoSettings.fromBase64(yao.toBase64()).getShinyChancePermille());
        }
    }

    @Test
    public void notANumberShinyChanceBecomesZeroWithAWarning() {
        YaoSettings yao = new YaoSettings();
        yao.setShinyChancePercent(Double.NaN);
        assertEquals(0, yao.getShinyChancePermille());
        assertEquals(1, yao.getWarnings().size());
    }

    // --- code patch consent ---

    @Test
    public void consentIsNeverSavedSoSharedSettingsCannotAcceptForSomeoneElse() throws IOException {
        YaoSettings yao = new YaoSettings();
        yao.setShinyChancePercent(50);
        yao.setCodePatchesAccepted(true);
        assertFalse(yao.toText().toLowerCase().contains("accept"));
        assertFalse(YaoSettings.fromBase64(yao.toBase64()).isCodePatchesAccepted());

        Settings settings = loadClean();
        settings.setYaoSettings(yao);
        assertFalse(Settings.fromString(settings.toString()).getYaoSettings().isCodePatchesAccepted());
    }

    @Test
    public void consentIsOnlyNeededWhenACodePatchOptionIsChosen() {
        YaoSettings yao = new YaoSettings();
        assertFalse(yao.needsCodePatchConsent());
        yao.addCustomShopItem(50, 1);
        assertFalse(yao.needsCodePatchConsent());
        yao.setShinyChancePercent(0.1);
        assertTrue(yao.needsCodePatchConsent());
    }

    @Test
    public void disclaimerExistsInBothLanguagesAndPicksSpanishForSpanishCodes() {
        assertFalse(CodePatchDisclaimer.ENGLISH.isBlank());
        assertFalse(CodePatchDisclaimer.SPANISH.isBlank());
        assertNotEquals(CodePatchDisclaimer.ENGLISH, CodePatchDisclaimer.SPANISH);
        assertEquals(CodePatchDisclaimer.SPANISH, CodePatchDisclaimer.forLanguage("es"));
        assertEquals(CodePatchDisclaimer.SPANISH, CodePatchDisclaimer.forLanguage("ES-es"));
        assertEquals(CodePatchDisclaimer.ENGLISH, CodePatchDisclaimer.forLanguage("en"));
        assertEquals(CodePatchDisclaimer.ENGLISH, CodePatchDisclaimer.forLanguage(null));
        assertEquals(CodePatchDisclaimer.ENGLISH, CodePatchDisclaimer.forLanguage("fr"));
    }

    // --- Locke mode ---

    @Test
    public void lockeModeRoundTripsAndNeedsConsent() {
        for (YaoSettings.LockeMode mode : YaoSettings.LockeMode.values()) {
            YaoSettings yao = new YaoSettings();
            yao.setLockeMode(mode);
            assertEquals(mode, YaoSettings.fromBase64(yao.toBase64()).getLockeMode());
            assertEquals(mode != YaoSettings.LockeMode.NONE, yao.needsCodePatchConsent());
            assertEquals(mode == YaoSettings.LockeMode.NONE, yao.isDefault());
        }
    }

    @Test
    public void lockeModeParsingIsLenientAndNeverThrows() {
        assertEquals(YaoSettings.LockeMode.FULL, YaoSettings.parseLocke(" FULL "));
        assertEquals(YaoSettings.LockeMode.SOFT, YaoSettings.parseLocke("soft"));
        assertEquals(YaoSettings.LockeMode.NONE, YaoSettings.parseLocke("off"));
        assertNull(YaoSettings.parseLocke("banana"));
        assertNull(YaoSettings.parseLocke(null));
        YaoSettings yao = YaoSettings.fromText("locke=banana\nshop.0=50:1\n");
        assertEquals(YaoSettings.LockeMode.NONE, yao.getLockeMode());
        assertEquals(1, yao.getWarnings().size());
        assertEquals(1, yao.getCustomShopItems().size());
        yao.setLockeMode(null);
        assertEquals(YaoSettings.LockeMode.NONE, yao.getLockeMode());
    }
}
