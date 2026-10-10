package com.aresstack.enterpriseai.application.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liest die Argumente eines Werkzeugaufrufs: ein flaches JSON-Objekt mit Text-, Zahl-, Wahrheits- oder
 * {@code null}-Werten. Verschachtelte Werte werden als Rohtext übernommen. Reicht für die Werkzeugschemata dieses
 * Clients und hält den Kern frei von JSON-Bibliotheken.
 */
public final class ToolArguments {

    private final Map<String, String> values;

    private ToolArguments(Map<String, String> values) {
        this.values = values;
    }

    /** @throws IllegalArgumentException wenn {@code json} kein JSON-Objekt ist */
    public static ToolArguments parse(String json) {
        Parser parser = new Parser(json == null ? "{}" : json);
        return new ToolArguments(parser.object());
    }

    /** Der Wert als Text oder {@code null}, wenn er fehlt oder {@code null} ist. */
    public String text(String name) {
        String value = values.get(name);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /** Der Wert als Pflichttext. */
    public String required(String name) {
        String value = text(name);
        if (value == null) {
            throw new IllegalArgumentException("Argument " + name + " fehlt");
        }
        return value;
    }

    /** Der Wert als ganze Zahl oder {@code fallback}. */
    public int integer(String name, int fallback) {
        String value = text(name);
        if (value == null) {
            return fallback;
        }
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Argument " + name + " ist keine Zahl");
        }
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        Map<String, String> object() {
            Map<String, String> result = new LinkedHashMap<String, String>();
            skip();
            expect('{');
            skip();
            if (peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skip();
                String key = string();
                skip();
                expect(':');
                skip();
                result.put(key, value());
                skip();
                char next = next();
                if (next == '}') {
                    return result;
                }
                if (next != ',') {
                    throw error();
                }
            }
        }

        private String value() {
            char c = peek();
            if (c == '"') {
                return string();
            }
            if (c == '{' || c == '[') {
                int start = pos;
                skipNested();
                return text.substring(start, pos);
            }
            int start = pos;
            while (pos < text.length() && ",}] \t\r\n".indexOf(text.charAt(pos)) < 0) {
                pos++;
            }
            String literal = text.substring(start, pos);
            return "null".equals(literal) ? null : literal;
        }

        private void skipNested() {
            int depth = 0;
            boolean inString = false;
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (inString) {
                    if (c == '\\') {
                        pos++;
                    } else if (c == '"') {
                        inString = false;
                    }
                } else if (c == '"') {
                    inString = true;
                } else if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    if (--depth == 0) {
                        return;
                    }
                }
            }
            throw error();
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escaped = next();
                switch (escaped) {
                    case 'n':
                        out.append('\n');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw error();
                        }
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        out.append(escaped);
                }
            }
        }

        private void skip() {
            while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw error();
            }
            return text.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw error();
            }
        }

        private IllegalArgumentException error() {
            return new IllegalArgumentException("Argumente sind kein gültiges JSON-Objekt");
        }
    }
}
