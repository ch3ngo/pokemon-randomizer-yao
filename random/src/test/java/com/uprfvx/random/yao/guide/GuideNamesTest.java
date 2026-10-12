package com.uprfvx.random.yao.guide;

import com.uprfvx.romio.constants.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Added for YAO (12/10/2026): the bundled English and Spanish names table (guide-names.json) is complete for every
 * id the games in scope (Gen 1 to 7) can show, has no forbidden dash characters, and the helpers behave.
 */
public class GuideNamesTest {

    private final GuideNames names = GuideNames.get();

    private static void assertComplete(String what, Map<String, String[]> table, int maxId) {
        for (int id = 1; id <= maxId; id++) {
            String[] pair = table.get(String.valueOf(id));
            assertNotNull(pair, what + " " + id + " has no entry");
            assertFalse(pair[0].isBlank() || pair[1].isBlank(), what + " " + id + " has a blank name");
        }
    }

    @Test
    public void everySpeciesMoveAndAbilityOfGenerationsOneToSevenHasBothNames() {
        assertComplete("species", names.speciesTable(), 807);
        assertComplete("move", names.moveTable(), 742);
        assertComplete("ability", names.abilityTable(), 233);
    }

    @Test
    public void spanishNamesAreRealTranslationsNotCopiesOfTheEnglishOnes() {
        // Pokemon names are mostly identical in both languages, moves and abilities are not
        int same = 0;
        for (String[] pair : names.moveTable().values()) {
            if (pair[0].equals(pair[1])) {
                same++;
            }
        }
        assertTrue(same < names.moveTable().size() * 0.2, "too many untranslated move names: " + same);
        same = 0;
        for (String[] pair : names.abilityTable().values()) {
            if (pair[0].equals(pair[1])) {
                same++;
            }
        }
        assertTrue(same < names.abilityTable().size() * 0.2, "too many untranslated ability names: " + same);
        assertEquals("Fire Blast", names.moveTable().get("126")[0]);
        assertEquals("Llamarada", names.moveTable().get("126")[1]);
        assertEquals("Intimidate", names.abilityTable().get("22")[0]);
        assertEquals("Intimidación", names.abilityTable().get("22")[1]);
    }

    @Test
    public void everyGenerationThatHasItemsHasAnItemTable() {
        for (String gen : new String[]{"2", "3", "4", "5", "6", "7"}) {
            Map<String, String[]> table = names.itemTables().get(gen);
            assertNotNull(table, "no item table for generation " + gen);
            assertTrue(table.size() > 150, "generation " + gen + " item table is too small: " + table.size());
            for (Map.Entry<String, String[]> e : table.entrySet()) {
                assertFalse(e.getValue()[0].isBlank() || e.getValue()[1].isBlank(), "blank item name " + gen + ":" + e.getKey());
            }
        }
        // Poke Ball and Potion have the same ids as in PokeAPI for Gen 4 onwards, a cheap sanity check of the id keyed tables
        assertEquals("Poke Ball", names.itemTables().get("4").get("4")[0].replace("é", "e"));
    }

    @Test
    public void everyRouteAndMostPlaceTagsOfTheStoryOrderListsHaveBothNames() {
        Set<String> tags = new LinkedHashSet<>();
        tags.addAll(Gen1Constants.locationTagsTraverseOrder);
        tags.addAll(Gen2Constants.locationTagsTraverseOrder);
        for (int type = 0; type < 4; type++) {
            tags.addAll(Gen3Constants.getLocationTagsTraverseOrder(type));
            tags.addAll(Gen4Constants.getLocationTagsTraverseOrder(type));
            tags.addAll(Gen5Constants.getLocationTagsTraverseOrder(type));
            tags.addAll(Gen6Constants.getLocationTagsTraverseOrder(type));
            tags.addAll(Gen7Constants.getLocationTagsTraverseOrder(type));
        }
        int known = 0;
        List<String> missing = new ArrayList<>();
        for (String tag : tags) {
            String[] pair = names.place(tag);
            if (pair != null) {
                known++;
                assertFalse(pair[0].isBlank() || pair[1].isBlank(), tag);
            } else {
                missing.add(tag);
                assertFalse(tag.matches("ROUTE \\d+"), "a numbered route has no name: " + tag);
            }
        }
        // the rest keeps its English name with translated generic words, see the page's placeEs
        assertTrue(known >= tags.size() * 0.7, "only " + known + " of " + tags.size() + " places named; missing " + missing);
    }

    @Test
    public void formeSuffixPatternsKeepTheBaseName() {
        for (Map.Entry<String, String> e : names.formeTable().entrySet()) {
            assertTrue(e.getValue().contains("{b}"), e.getKey());
        }
        GuideNames.Resolver unused = null;
        assertNotNull(names.formeTable().get("-Mega"));
        assertNotNull(names.formeTable().get("-Alolan"));
    }

    @Test
    public void shoutedTextIsFixedAndNormalTextIsKept() {
        assertEquals("Mr. Mime", GuideNames.fixShouting("MR. MIME"));
        assertEquals("Ho-Oh", GuideNames.fixShouting("HO-OH"));
        assertEquals("Porygon-Z", GuideNames.fixShouting("PORYGON-Z"));
        assertEquals("V. Cortante", GuideNames.fixShouting("V. CORTANTE"));
        assertEquals("M\u00e1x. PP", GuideNames.fixShouting("M\u00c1X. PP"));
        assertEquals("TM01", GuideNames.fixShouting("TM01"));
        assertEquals("Poci\u00f3n de Vida", GuideNames.fixShouting("POCI\u00d3N DE VIDA"));
        assertEquals("Mt. Silver", GuideNames.fixShouting("MT. SILVER"));
        assertEquals("Mr. Mime", GuideNames.fixShouting("Mr. Mime"));
        assertEquals("EXP. Share", GuideNames.fixShouting("EXP. Share"));
        assertEquals("", GuideNames.fixShouting(null));
        assertEquals("pokeball", GuideNames.norm("POKé BALL"));
    }

    @Test
    public void noForbiddenDashCharactersInTheNames() throws Exception {
        try (var in = GuideNames.class.getResourceAsStream(GuideNames.RESOURCE)) {
            assertNotNull(in);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(text.contains("\u2014"), "em dash in the names table");
        }
    }

    @Test
    public void slotChancesAreOnlyWrittenForWellKnownTablesAndAddUpTo100() {
        for (int gen = 1; gen <= 7; gen++) {
            for (String method : List.of("grass", "surf", "rocksmash", "oldrod", "goodrod", "superrod", "fishing")) {
                for (int slots = 1; slots <= 12; slots++) {
                    double[] c = GuideWild.slotChances(gen, method, slots);
                    if (c != null) {
                        assertEquals(slots, c.length, gen + " " + method);
                        double sum = 0;
                        for (double d : c) {
                            sum += d;
                        }
                        assertEquals(100.0, sum, 0.35, gen + " " + method + " " + slots);
                    }
                }
            }
        }
        assertNull(GuideWild.slotChances(7, "grass", 10), "Gen 7 chances are not known");
        assertNull(GuideWild.slotChances(5, "doubles", 12));
    }

    @Test
    public void shopsAreOrderedByBadgesThenByTheTownInTheStoryOrder() {
        List<String> order = List.of("ROUTE 101", "OLDALE TOWN", "PETALBURG CITY", "SLATEPORT CITY");
        double badges0 = GuideOrder.shopKey("Primary 0 Badges", order, 5);
        double badges3 = GuideOrder.shopKey("Primary 3 Badges", order, 0);
        double oldale = GuideOrder.shopKey("Oldale Poke Mart (Before Pokedex)", order, 9);
        double slateport = GuideOrder.shopKey("Slateport Vitamins", order, 1);
        double unknown = GuideOrder.shopKey("Mystery Shop", order, 2);
        assertTrue(badges0 < badges3 && badges3 < oldale && oldale < slateport && slateport < unknown);
    }
}
