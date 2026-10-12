package com.uprfvx.random.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JsonTest {

    @Test
    public void readsEveryKindOfValue() {
        Map<String, Object> m = Json.parseObject("{\"a\":1,\"b\":-2.5,\"c\":\"x\\ny\\u00e9\\\"\",\"d\":[true,false,null],\"e\":{\"f\":1e2}}");
        assertEquals(1L, m.get("a"));
        assertEquals(-2.5, m.get("b"));
        assertEquals("x\ny\u00e9\"", m.get("c"));
        assertEquals(List.of(true, false), ((List<?>) m.get("d")).subList(0, 2));
        assertNull(((List<?>) m.get("d")).get(2));
        assertEquals(100.0, ((Map<?, ?>) m.get("e")).get("f"));
    }

    @Test
    public void writesWhatItReads() {
        String text = "{\"name\":\"a \\\"b\\\" \\\\ c\",\"list\":[1,2.5,true,null],\"empty\":{}}";
        Object parsed = Json.parse(text);
        assertEquals(parsed, Json.parse(Json.write(parsed)));
    }

    @Test
    public void writesNumbersAndSpecialCharactersSafely() {
        assertEquals("3", Json.write(3.0));
        assertEquals("2.5", Json.write(2.5));
        assertEquals("null", Json.write(Double.NaN));
        assertEquals("\"\\u0001\"", Json.write("\u0001"));
        assertEquals("\"</script>\"", Json.write("</script>"));
        assertEquals("[1,2]", Json.write(new int[]{1, 2}));
        assertEquals("\"X\"", Json.write(Thread.State.valueOf("NEW").name().substring(0, 0) + "X"));
    }

    @Test
    public void badTextIsRefusedWithAnExplanationNotACrash() {
        for (String bad : new String[]{"", "{", "{\"a\"}", "{\"a\":}", "[1,]", "tru", "\"abc", "{\"a\":1} extra", "nul", "{\"a\":\"\\x\"}", "1.2.3"}) {
            assertThrows(Json.JsonException.class, () -> Json.parse(bad), bad);
        }
        assertThrows(Json.JsonException.class, () -> Json.parseObject("[1]"));
        assertThrows(Json.JsonException.class, () -> Json.parse(null));
    }

    @Test
    public void deepNestingCannotOverflowTheStack() {
        String deep = "[".repeat(5000) + "]".repeat(5000);
        assertThrows(Json.JsonException.class, () -> Json.parse(deep));
    }

    @Test
    public void forgivingReaderGivesFallbacks() {
        Json.Obj o = new Json.Obj(Json.parseObject("{\"n\":\"12,5\",\"b\":\"true\",\"i\":3.6,\"s\":7,\"l\":[1],\"o\":{\"x\":1}}"));
        assertEquals(12.5, o.number("n", 0));
        assertTrue(o.bool("b", false));
        assertEquals(4, o.integer("i", 0));
        assertEquals("7", o.string("s", ""));
        assertEquals(1, o.list("l").size());
        assertTrue(o.object("o").has("x"));
        assertEquals(9, o.integer("missing", 9));
        assertEquals("d", o.string("missing", "d"));
        assertTrue(o.list("missing").isEmpty());
        assertFalse(o.object("missing").has("x"));
        assertEquals(5.0, new Json.Obj(Json.parseObject("{\"n\":\"abc\"}")).number("n", 5));
    }
}
