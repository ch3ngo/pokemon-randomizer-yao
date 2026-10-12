package com.uprfvx.random.yao.guide;

import com.uprfvx.random.web.Json;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.Type;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Added for YAO (12/10/2026): the guide's one EN/ES dictionary must be complete. Every key has both languages, a
 * Spanish text may only equal the English one when the key is explicitly allowed, every key the page asks for
 * exists, and the data driven keys (evolution methods, types, move categories) cover everything the game data can
 * contain.
 */
public class GuideStringsTest {

    private static String read(String resource) throws Exception {
        try (var in = HtmlGuideWriter.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dictionary() throws Exception {
        return Json.parseObject(read(HtmlGuideWriter.STRINGS_RESOURCE));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> strings() throws Exception {
        return (Map<String, Map<String, Object>>) (Map<?, ?>) dictionary().get("strings");
    }

    @SuppressWarnings("unchecked")
    private static Set<String> allowSame() throws Exception {
        return new HashSet<>((List<String>) (List<?>) dictionary().get("allowSame"));
    }

    private static String template() throws Exception {
        return read("/com/uprfvx/random/yao/guide/guide.html");
    }

    @Test
    public void everyKeyHasBothLanguagesAndSpanishIsReallyTranslated() throws Exception {
        Set<String> allowed = allowSame();
        for (Map.Entry<String, Map<String, Object>> e : strings().entrySet()) {
            String en = String.valueOf(e.getValue().get("en"));
            String es = String.valueOf(e.getValue().get("es"));
            assertFalse(en.isBlank() || en.equals("null"), "empty English text for " + e.getKey());
            assertFalse(es.isBlank() || es.equals("null"), "empty Spanish text for " + e.getKey());
            if (en.equals(es)) {
                assertTrue(allowed.contains(e.getKey()), e.getKey() + " is identical in both languages; translate it "
                        + "or add it to allowSame");
            }
        }
        for (String key : allowed) {
            assertTrue(strings().containsKey(key), "allowSame names an unknown key: " + key);
        }
    }

    @Test
    public void spanishDoesNotUseMachineLiteralWords() throws Exception {
        for (Map.Entry<String, Map<String, Object>> e : strings().entrySet()) {
            String es = String.valueOf(e.getValue().get("es")).toLowerCase(Locale.ROOT);
            assertFalse(es.contains("randomizador"), e.getKey());
            assertFalse(es.contains("aleatorizador"), e.getKey());
        }
    }

    @Test
    public void noDashCharactersThatTheProjectForbids() throws Exception {
        assertFalse(read(HtmlGuideWriter.STRINGS_RESOURCE).contains("\u2014"), "em dash in strings");
        assertFalse(template().contains("\u2014"), "em dash in template");
    }

    @Test
    public void everyKeyThePageAsksForExists() throws Exception {
        String html = template();
        Set<String> keys = strings().keySet();
        Matcher m = Pattern.compile("\\b(?:t|has)\\(\"([A-Za-z0-9_]+)\"").matcher(html);
        int found = 0;
        while (m.find()) {
            found++;
            assertTrue(keys.contains(m.group(1)), "the page uses a missing string key: " + m.group(1));
        }
        assertTrue(found > 20, "the key scan found almost nothing, the regex is probably wrong");
        // tab labels are listed in an array of pairs
        Matcher tabs = Pattern.compile("\\[\"[a-z]{2}\",\"(tab[A-Za-z]+)\"\\]").matcher(html);
        int tabCount = 0;
        while (tabs.find()) {
            tabCount++;
            assertTrue(keys.contains(tabs.group(1)), "missing tab key " + tabs.group(1));
        }
        assertEquals(10, tabCount);
    }

    @Test
    public void everyStaticKeyIsUsedByThePage() throws Exception {
        String html = template();
        for (String key : strings().keySet()) {
            boolean dynamic = key.startsWith("evo") && !key.equals("evo") && !key.equals("evoUnknown")
                    || key.startsWith("type") && !key.equals("type")
                    || key.startsWith("cat") && !key.equals("cat")
                    || key.startsWith("tag") || key.startsWith("m_") || key.startsWith("v_");
            if (dynamic || key.startsWith("tab")) {
                continue;
            }
            assertTrue(html.contains("\"" + key + "\""), "unused string key (remove it or use it): " + key);
        }
    }

    @Test
    public void dataDrivenKeysCoverEveryEvolutionTypeTypeAndCategory() throws Exception {
        Set<String> keys = strings().keySet();
        for (EvolutionType type : EvolutionType.values()) {
            assertTrue(keys.contains("evo" + type.name()), "no text for evolution method " + type);
        }
        for (Type type : Type.values()) {
            assertTrue(keys.contains("type" + type.name()), "no text for type " + type);
        }
        for (MoveCategory category : MoveCategory.values()) {
            assertTrue(keys.contains("cat" + category.name()), "no text for move category " + category);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void areaAndShopTermsAreRealTranslations() throws Exception {
        for (String list : new String[]{"areaTerms", "shopTerms"}) {
            List<List<String>> terms = (List<List<String>>) (List<?>) dictionary().get(list);
            assertFalse(terms.isEmpty(), list);
            for (List<String> pair : terms) {
                assertEquals(2, pair.size(), list);
                assertFalse(pair.get(0).isBlank() || pair.get(1).isBlank(), list + " has an empty entry");
                assertNotEquals(pair.get(0), pair.get(1), list + " entry is not translated: " + pair.get(0));
            }
        }
    }

    @Test
    public void theTemplateStaysOneOfflineFile() throws Exception {
        String html = template();
        // the only thing fetched from the network is the optional web font stylesheet, loaded without blocking
        Matcher m = Pattern.compile("(?:src|href)=\"(https?:[^\"]+)\"").matcher(html);
        while (m.find()) {
            assertTrue(m.group(1).startsWith("https://fonts.googleapis.com"), "external resource: " + m.group(1));
        }
        assertFalse(html.contains("<script src="), "no external scripts");
        assertTrue(html.contains("@media print"), "has a print stylesheet");
        assertTrue(html.contains("prefers-reduced-motion"), "respects reduced motion");
        assertTrue(html.contains("data-theme=\"light\""), "has a light theme");
    }

    @Test
    public void theStringsAreInjectedIntoThePageAndNoPlaceholderIsLeft() throws Exception {
        String page = HtmlGuideWriter.injectStrings(
                HtmlGuideWriter.inject(template(), "{\"meta\":{},\"original\":{},\"randomized\":{}}"),
                HtmlGuideWriter.loadStrings());
        assertFalse(page.contains("/*__GUIDE_DATA__*/"));
        assertFalse(page.contains("/*__GUIDE_STRINGS__*/"));
        assertTrue(page.contains("\"Gu\u00eda de Pok\u00e9mon Randomizer YAO\"") || page.contains("Guía de Pokémon Randomizer YAO"));
        assertFalse(page.contains("</script><"), "the strings must not be able to close the script element");
    }
}
