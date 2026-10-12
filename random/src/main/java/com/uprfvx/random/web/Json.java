package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  Json.java - a tiny JSON reader and writer for the local web interface. --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON support. Values are {@code Map<String,Object>}, {@code List<Object>}, {@code String}, {@code Double}
 * or {@code Long} for numbers, {@code Boolean} and {@code null}. Parsing is strict enough for the program's own
 * requests and never recurses more than {@link #MAX_DEPTH} levels, so a hostile request cannot overflow the stack.
 */
public final class Json {

    private Json() {
    }

    public static final int MAX_DEPTH = 64;

    /** Raised for text that is not valid JSON. */
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------- writing

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            sb.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) map).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else if (value instanceof int[] ints) {
            sb.append('[');
            for (int i = 0; i < ints.length; i++) {
                sb.append(i == 0 ? "" : ",").append(ints[i]);
            }
            sb.append(']');
        } else if (value instanceof Enum<?> e) {
            writeString(sb, e.name());
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case ' ' -> sb.append("\\u2028");
                case ' ' -> sb.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- reading

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("Unexpected text after the JSON value at position " + parser.pos);
        }
        return value;
    }

    /** Parses an object; anything else is an error. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("A JSON object was expected.");
        }
        return (Map<String, Object>) value;
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (atEnd()) {
                throw new JsonException("Unexpected end of the JSON text.");
            }
            return text.charAt(pos);
        }

        private void expect(char c) {
            if (peek() != c) {
                throw new JsonException("Expected '" + c + "' at position " + pos + ".");
            }
            pos++;
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new JsonException("The JSON text is nested too deeply.");
            }
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{':
                    return readObject(depth);
                case '[':
                    return readArray(depth);
                case '"':
                    return readString();
                case 't':
                    return readWord("true", Boolean.TRUE);
                case 'f':
                    return readWord("false", Boolean.FALSE);
                case 'n':
                    return readWord("null", null);
                default:
                    return readNumber();
            }
        }

        private Object readWord(String word, Object value) {
            if (!text.startsWith(word, pos)) {
                throw new JsonException("Unexpected text at position " + pos + ".");
            }
            pos += word.length();
            return value;
        }

        private Map<String, Object> readObject(int depth) {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                map.put(key, readValue(depth + 1));
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new JsonException("Expected ',' or '}' at position " + (pos - 1) + ".");
                }
            }
        }

        private List<Object> readArray(int depth) {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue(depth + 1));
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new JsonException("Expected ',' or ']' at position " + (pos - 1) + ".");
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = peek();
                    pos++;
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw new JsonException("Bad \\u escape at position " + pos + ".");
                            }
                            try {
                                sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw new JsonException("Bad \\u escape at position " + pos + ".");
                            }
                            pos += 4;
                        }
                        default -> throw new JsonException("Bad escape at position " + (pos - 1) + ".");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object readNumber() {
            int start = pos;
            while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            String number = text.substring(start, pos);
            if (number.isEmpty()) {
                throw new JsonException("Unexpected character at position " + start + ".");
            }
            try {
                if (number.indexOf('.') < 0 && number.indexOf('e') < 0 && number.indexOf('E') < 0) {
                    return Long.parseLong(number);
                }
                return Double.parseDouble(number);
            } catch (NumberFormatException e) {
                throw new JsonException("Bad number \"" + number + "\" at position " + start + ".");
            }
        }
    }

    // ---------------------------------------------------------------- helpers for reading requests

    /** A small, forgiving reader over a parsed object, used to read requests without casts everywhere. */
    public static final class Obj {
        private final Map<String, Object> map;

        public Obj(Map<String, Object> map) {
            this.map = map == null ? new LinkedHashMap<>() : map;
        }

        public boolean has(String key) {
            return map.containsKey(key) && map.get(key) != null;
        }

        public String string(String key, String fallback) {
            Object v = map.get(key);
            return v instanceof String s ? s : (v instanceof Number || v instanceof Boolean ? String.valueOf(v) : fallback);
        }

        public double number(String key, double fallback) {
            Object v = map.get(key);
            if (v instanceof Number n) {
                return n.doubleValue();
            }
            if (v instanceof String s) {
                try {
                    return Double.parseDouble(s.strip().replace(',', '.'));
                } catch (NumberFormatException e) {
                    return fallback;
                }
            }
            return fallback;
        }

        public int integer(String key, int fallback) {
            return (int) Math.round(number(key, fallback));
        }

        public boolean bool(String key, boolean fallback) {
            Object v = map.get(key);
            if (v instanceof Boolean b) {
                return b;
            }
            if (v instanceof String s) {
                return s.equalsIgnoreCase("true") || s.equals("1");
            }
            if (v instanceof Number n) {
                return n.intValue() != 0;
            }
            return fallback;
        }

        @SuppressWarnings("unchecked")
        public List<Object> list(String key) {
            Object v = map.get(key);
            return v instanceof List ? (List<Object>) v : new ArrayList<>();
        }

        @SuppressWarnings("unchecked")
        public Obj object(String key) {
            Object v = map.get(key);
            return new Obj(v instanceof Map ? (Map<String, Object>) v : null);
        }

        public Map<String, Object> raw() {
            return map;
        }
    }
}
