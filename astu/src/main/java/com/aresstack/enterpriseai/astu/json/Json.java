package com.aresstack.enterpriseai.astu.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict JSON text (RFC 8259) to plain Java values and back, without a library: objects become
 * {@code Map<String, Object>} (insertion order kept), arrays {@code List<Object>}, strings {@code String}, numbers
 * {@code BigDecimal}, {@code true}/{@code false} {@code Boolean} and {@code null} {@code null}. Parsed containers are
 * unmodifiable. For small structured payloads (fact payloads, model answers in a fixed JSON form), not for streams.
 */
public final class Json {

    /** Deepest nesting accepted; deeper text is rejected instead of overflowing the stack. */
    public static final int MAX_DEPTH = 64;

    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    /**
     * Parse one JSON value; only whitespace may surround it.
     *
     * @throws JsonException if {@code text} is no valid JSON
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("no JSON text", 0);
        }
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.value(0);
        parser.skipWhitespace();
        if (parser.position != text.length()) {
            throw new JsonException("text after the JSON value", parser.position);
        }
        return value;
    }

    /**
     * Parse a JSON object.
     *
     * @throws JsonException if {@code text} is no valid JSON or no object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("JSON value is no object", 0);
        }
        return (Map<String, Object>) value;
    }

    /** The JSON text of {@code value} (maps, iterables, strings, numbers, booleans and {@code null}), compact. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    /** {@code value} as JSON string literal with quotes. */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        quote(value, out);
        return out.toString();
    }

    // ------------------------------------------------------------------ typed access

    /** The member {@code name} of {@code object} if it is a string, else {@code null}. */
    public static String string(Map<String, Object> object, String name) {
        Object value = object == null ? null : object.get(name);
        return value instanceof String ? (String) value : null;
    }

    /** The member {@code name} of {@code object} if it is an object, else {@code null}. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> object, String name) {
        Object value = object == null ? null : object.get(name);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    /** The member {@code name} of {@code object} if it is an array, else an empty list. */
    @SuppressWarnings("unchecked")
    public static List<Object> array(Map<String, Object> object, String name) {
        Object value = object == null ? null : object.get(name);
        return value instanceof List ? (List<Object>) value : Collections.emptyList();
    }

    /** The member {@code name} of {@code object} if it is an integral number in int range, else {@code null}. */
    public static Integer integer(Map<String, Object> object, String name) {
        Object value = object == null ? null : object.get(name);
        return integer(value);
    }

    /** {@code value} if it is an integral number in int range, else {@code null}. */
    public static Integer integer(Object value) {
        if (!(value instanceof BigDecimal)) {
            return null;
        }
        try {
            return ((BigDecimal) value).intValueExact();
        } catch (ArithmeticException notIntegral) {
            return null;
        }
    }

    /** The member {@code name} of {@code object} if it is a boolean, else {@code fallback}. */
    public static boolean bool(Map<String, Object> object, String name, boolean fallback) {
        Object value = object == null ? null : object.get(name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    // ------------------------------------------------------------------ parsing

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw new JsonException("JSON nested deeper than " + MAX_DEPTH, position);
        }
        if (position >= text.length()) {
            throw new JsonException("unexpected end of JSON text", position);
        }
        char c = text.charAt(position);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                literal("true");
                return Boolean.TRUE;
            case 'f':
                literal("false");
                return Boolean.FALSE;
            case 'n':
                literal("null");
                return null;
            default:
                if (c == '-' || c >= '0' && c <= '9') {
                    return number();
                }
                throw new JsonException("unexpected character '" + c + "'", position);
        }
    }

    private Map<String, Object> object(int depth) {
        position++; // '{'
        Map<String, Object> members = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return Collections.unmodifiableMap(members);
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonException("object member name expected", position);
            }
            int at = position;
            String name = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = value(depth + 1);
            if (members.containsKey(name)) {
                throw new JsonException("duplicate object member \"" + name + "\"", at);
            }
            members.put(name, value);
            skipWhitespace();
            char next = peek();
            position++;
            if (next == '}') {
                return Collections.unmodifiableMap(members);
            }
            if (next != ',') {
                throw new JsonException("',' or '}' expected", position - 1);
            }
        }
    }

    private List<Object> array(int depth) {
        position++; // '['
        List<Object> values = new ArrayList<Object>();
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return Collections.unmodifiableList(values);
        }
        while (true) {
            skipWhitespace();
            values.add(value(depth + 1));
            skipWhitespace();
            char next = peek();
            position++;
            if (next == ']') {
                return Collections.unmodifiableList(values);
            }
            if (next != ',') {
                throw new JsonException("',' or ']' expected", position - 1);
            }
        }
    }

    private String string() {
        position++; // '"'
        StringBuilder value = new StringBuilder();
        while (true) {
            if (position >= text.length()) {
                throw new JsonException("unterminated string", position);
            }
            char c = text.charAt(position++);
            if (c == '"') {
                return value.toString();
            }
            if (c < 0x20) {
                throw new JsonException("control character in string", position - 1);
            }
            if (c != '\\') {
                value.append(c);
                continue;
            }
            if (position >= text.length()) {
                throw new JsonException("unterminated escape", position);
            }
            char escape = text.charAt(position++);
            switch (escape) {
                case '"':
                case '\\':
                case '/':
                    value.append(escape);
                    break;
                case 'b':
                    value.append('\b');
                    break;
                case 'f':
                    value.append('\f');
                    break;
                case 'n':
                    value.append('\n');
                    break;
                case 'r':
                    value.append('\r');
                    break;
                case 't':
                    value.append('\t');
                    break;
                case 'u':
                    if (position + 4 > text.length()) {
                        throw new JsonException("short unicode escape", position);
                    }
                    try {
                        value.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    } catch (NumberFormatException e) {
                        throw new JsonException("invalid unicode escape", position);
                    }
                    position += 4;
                    break;
                default:
                    throw new JsonException("invalid escape '\\" + escape + "'", position - 1);
            }
        }
    }

    private BigDecimal number() {
        int start = position;
        if (peek() == '-') {
            position++;
        }
        if (peek() == '0') {
            position++;
        } else if (isDigit(peek())) {
            while (isDigit(peek())) {
                position++;
            }
        } else {
            throw new JsonException("digit expected", position);
        }
        if (peek() == '.') {
            position++;
            if (!isDigit(peek())) {
                throw new JsonException("digit expected after '.'", position);
            }
            while (isDigit(peek())) {
                position++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            position++;
            if (peek() == '+' || peek() == '-') {
                position++;
            }
            if (!isDigit(peek())) {
                throw new JsonException("digit expected in exponent", position);
            }
            while (isDigit(peek())) {
                position++;
            }
        }
        return new BigDecimal(text.substring(start, position));
    }

    private void literal(String word) {
        if (!text.startsWith(word, position)) {
            throw new JsonException("invalid literal", position);
        }
        position += word.length();
    }

    private void expect(char c) {
        if (peek() != c) {
            throw new JsonException("'" + c + "' expected", position);
        }
        position++;
    }

    private char peek() {
        return position < text.length() ? text.charAt(position) : '\0';
    }

    private void skipWhitespace() {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            position++;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    // ------------------------------------------------------------------ writing

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            quote((String) value, out);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof BigDecimal) {
            out.append(((BigDecimal) value).toPlainString());
        } else if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte) {
            out.append(value);
        } else if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                throw new IllegalArgumentException("JSON has no " + number);
            }
            out.append(BigDecimal.valueOf(number).toPlainString());
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable) {
            out.append('[');
            boolean first = true;
            for (Object element : (Iterable<?>) value) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(element, out);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("no JSON value: " + value.getClass().getName());
        }
    }

    private static void quote(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
