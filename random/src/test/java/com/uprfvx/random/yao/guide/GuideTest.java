package com.uprfvx.random.yao.guide;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GuideTest {

    @Test
    public void jsonEscapesWhatCouldBreakAnHtmlScriptElement() {
        String json = JsonWriter.toJson(Map.of("k", "</script><!-- & \"q\" \\ \n"));
        assertFalse(json.contains("</script>"));
        assertFalse(json.contains("<"));
        assertFalse(json.contains(">"));
        assertFalse(json.contains("&"));
        assertTrue(json.contains("\\u003c"));
        assertTrue(json.contains("\\\"q\\\""));
        assertTrue(json.contains("\\n"));
    }

    @Test
    public void jsonWritesNestedStructuresAndNumbers() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", java.util.Arrays.asList(1, 2.5, "x", true, null));
        m.put("b", new int[]{3, 4});
        m.put("c", Double.NaN);
        m.put("d", 100.0);
        assertEquals("{\"a\":[1,2.5,\"x\",true,null],\"b\":[3,4],\"c\":null,\"d\":100}", JsonWriter.toJson(m));
    }

    @Test
    public void jsonKeepsAccentedNamesAsIs() {
        assertEquals("\"Pokémon ñ\"", JsonWriter.toJson("Pokémon ñ"));
    }

    @Test
    public void injectReplacesTheDataPlaceholder() {
        String out = HtmlGuideWriter.inject("const DATA = /*__GUIDE_DATA__*/null; end", "{\"x\":1}");
        assertEquals("const DATA = {\"x\":1}; end", out);
    }

    @Test
    public void injectFailsClearlyWithoutAPlaceholder() {
        assertThrows(IllegalStateException.class, () -> HtmlGuideWriter.inject("no placeholder", "{}"));
    }

    @Test
    public void disclaimerExistsInBothLanguages() {
        assertNotEquals(GuideDisclaimer.ENGLISH, GuideDisclaimer.SPANISH);
        assertEquals(GuideDisclaimer.SPANISH, GuideDisclaimer.forLanguage("es"));
        assertEquals(GuideDisclaimer.ENGLISH, GuideDisclaimer.forLanguage("en"));
        assertTrue(GuideDisclaimer.ENGLISH.contains("cheating"));
        assertTrue(GuideDisclaimer.SPANISH.contains("trampa"));
    }

    @Test
    public void theTemplateIsPackagedAndHasTheDataPlaceholder() throws Exception {
        try (var in = HtmlGuideWriter.class.getResourceAsStream("/com/uprfvx/random/yao/guide/guide.html")) {
            assertNotNull(in);
            String html = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(html.contains("/*__GUIDE_DATA__*/null"));
            assertFalse(html.contains("\u2014"), "no em dash");
        }
    }
}
