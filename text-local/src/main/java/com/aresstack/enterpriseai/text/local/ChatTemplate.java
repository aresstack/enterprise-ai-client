package com.aresstack.enterpriseai.text.local;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Die Chat-Vorlage eines Modells ({@code chat_template} aus {@code tokenizer_config.json} oder
 * {@code chat_template.jinja}) in einem kleinen Jinja2-Interpreter in reinem Java, mit denselben Einstellungen wie
 * transformers ({@code trim_blocks}, {@code lstrip_blocks}, {@code break}/{@code continue}): Ausgaben, {@code if},
 * {@code for} mit {@code loop}, {@code set} (auch {@code namespace}), Filter wie {@code trim}, {@code tojson},
 * {@code length}, Tests wie {@code is defined}, Methoden wie {@code strip()} und {@code raise_exception}. Was die
 * Vorlage darüber hinaus nutzt, endet in einer {@link TemplateException}; der Aufrufer fällt dann zurück.
 */
final class ChatTemplate {

    /** Der eine undefinierte Wert (Vergleich per Identität). */
    private static final Undefined UNDEFINED = new Undefined();

    private final List<Node> body;

    private ChatTemplate(List<Node> body) {
        this.body = body;
    }

    static ChatTemplate parse(String source) throws TemplateException {
        List<Token> tokens = Lexer.tokenize(source);
        Parser parser = new Parser(tokens);
        List<Node> nodes = parser.parseUntil();
        if (parser.position < tokens.size()) {
            throw new TemplateException("Unerwartetes Tag: " + tokens.get(parser.position).text);
        }
        return new ChatTemplate(nodes);
    }

    String render(Map<String, Object> variables) throws TemplateException {
        Scope scope = new Scope(null);
        scope.values.putAll(Builtins.globals());
        scope.values.putAll(variables);
        StringBuilder out = new StringBuilder();
        try {
            for (Node node : body) {
                node.render(scope, out);
            }
        } catch (LoopSignal signal) {
            throw new TemplateException("break/continue außerhalb einer Schleife");
        }
        return out.toString();
    }

    /** Die Vorlage nutzt etwas, das der Interpreter nicht kann, oder bricht mit {@code raise_exception} ab. */
    static final class TemplateException extends Exception {
        private static final long serialVersionUID = 1L;

        TemplateException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------- Werte

    /** Jinja-Undefined: falsch, leer ausgegeben, Attribute wieder undefiniert. */
    static final class Undefined {

        Undefined() {
        }

        @Override
        public String toString() {
            return "";
        }
    }

    /** Aufrufbares (Funktion, gebundene Methode). */
    interface Callable {
        Object call(List<Object> args, Map<String, Object> kwargs) throws TemplateException;
    }

    /** {@code namespace(...)}: veränderliche Attribute über Schleifen hinweg. */
    static final class Namespace {
        final Map<String, Object> values = new LinkedHashMap<String, Object>();
    }

    private static final class Scope {
        final Scope parent;
        final Map<String, Object> values = new HashMap<String, Object>();

        Scope(Scope parent) {
            this.parent = parent;
        }

        Object get(String name) {
            for (Scope scope = this; scope != null; scope = scope.parent) {
                if (scope.values.containsKey(name)) {
                    return scope.values.get(name);
                }
            }
            return UNDEFINED;
        }

        void set(String name, Object value) {
            values.put(name, value);
        }
    }

    private static final class LoopSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final boolean isBreak;

        LoopSignal(boolean isBreak) {
            super(null, null, false, false);
            this.isBreak = isBreak;
        }
    }

    // ---------------------------------------------------------------- Lexer

    private enum TokenType { TEXT, OUTPUT, BLOCK }

    private static final class Token {
        final TokenType type;
        String text;

        Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private static final class Lexer {
        static List<Token> tokenize(String source) throws TemplateException {
            List<Token> tokens = new ArrayList<Token>();
            int position = 0;
            boolean stripNextLeading = false;
            boolean trimNextNewline = false;
            while (position < source.length()) {
                int open = nextOpen(source, position);
                String text = open < 0 ? source.substring(position) : source.substring(position, open);
                if (trimNextNewline) {
                    if (text.startsWith("\r\n")) {
                        text = text.substring(2);
                    } else if (text.startsWith("\n")) {
                        text = text.substring(1);
                    }
                }
                if (stripNextLeading) {
                    text = stripLeading(text);
                }
                stripNextLeading = false;
                trimNextNewline = false;
                if (open < 0) {
                    addText(tokens, text);
                    break;
                }
                char kind = source.charAt(open + 1);
                String close = kind == '{' ? "}}" : kind == '%' ? "%}" : "#}";
                int contentStart = open + 2;
                boolean stripBefore = contentStart < source.length() && source.charAt(contentStart) == '-';
                boolean keepBefore = contentStart < source.length() && source.charAt(contentStart) == '+';
                if (stripBefore || keepBefore) {
                    contentStart++;
                }
                int end = findClose(source, contentStart, close);
                if (end < 0) {
                    throw new TemplateException("Tag ohne Ende ab Position " + open);
                }
                boolean stripAfter = end > contentStart && source.charAt(end - 1) == '-';
                String content = source.substring(contentStart, stripAfter ? end - 1 : end).trim();
                if (stripBefore) {
                    text = stripTrailing(text);
                } else if (kind != '{' && !keepBefore) {
                    text = lstripBlock(text);
                }
                addText(tokens, text);
                if (kind == '{') {
                    tokens.add(new Token(TokenType.OUTPUT, content));
                } else if (kind == '%') {
                    tokens.add(new Token(TokenType.BLOCK, content));
                    trimNextNewline = true;
                } else {
                    trimNextNewline = true;
                }
                stripNextLeading = stripAfter;
                position = end + 2;
            }
            return tokens;
        }

        private static int nextOpen(String source, int from) {
            int index = source.indexOf('{', from);
            while (index >= 0 && index + 1 < source.length()) {
                char next = source.charAt(index + 1);
                if (next == '{' || next == '%' || next == '#') {
                    return index;
                }
                index = source.indexOf('{', index + 1);
            }
            return -1;
        }

        /** Ende des Tags, Zeichenketten in Tags überspringen. */
        private static int findClose(String source, int from, String close) {
            char quote = 0;
            for (int i = from; i < source.length() - 1; i++) {
                char c = source.charAt(i);
                if (quote != 0) {
                    if (c == '\\') {
                        i++;
                    } else if (c == quote) {
                        quote = 0;
                    }
                    continue;
                }
                if ((c == '\'' || c == '"') && !close.equals("#}")) {
                    quote = c;
                    continue;
                }
                if (source.startsWith(close, i)) {
                    return i;
                }
            }
            return -1;
        }

        private static void addText(List<Token> tokens, String text) {
            if (!text.isEmpty()) {
                tokens.add(new Token(TokenType.TEXT, text));
            }
        }

        private static String stripLeading(String text) {
            int i = 0;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            return text.substring(i);
        }

        private static String stripTrailing(String text) {
            int i = text.length();
            while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
                i--;
            }
            return text.substring(0, i);
        }

        /** lstrip_blocks: Leerzeichen und Tabs vor dem Tag am Zeilenanfang entfernen. */
        private static String lstripBlock(String text) {
            int i = text.length();
            while (i > 0 && (text.charAt(i - 1) == ' ' || text.charAt(i - 1) == '\t')) {
                i--;
            }
            if (i == 0 || text.charAt(i - 1) == '\n') {
                return text.substring(0, i);
            }
            return text;
        }
    }

    // ---------------------------------------------------------------- Knoten

    private abstract static class Node {
        abstract void render(Scope scope, StringBuilder out) throws TemplateException;
    }

    private static final class TextNode extends Node {
        final String text;

        TextNode(String text) {
            this.text = text;
        }

        @Override
        void render(Scope scope, StringBuilder out) {
            out.append(text);
        }
    }

    private static final class OutputNode extends Node {
        final Expr expr;

        OutputNode(Expr expr) {
            this.expr = expr;
        }

        @Override
        void render(Scope scope, StringBuilder out) throws TemplateException {
            out.append(Values.str(expr.eval(scope)));
        }
    }

    private static final class IfNode extends Node {
        final List<Expr> conditions = new ArrayList<Expr>();
        final List<List<Node>> branches = new ArrayList<List<Node>>();
        List<Node> otherwise;

        @Override
        void render(Scope scope, StringBuilder out) throws TemplateException {
            for (int i = 0; i < conditions.size(); i++) {
                if (Values.truthy(conditions.get(i).eval(scope))) {
                    renderAll(branches.get(i), scope, out);
                    return;
                }
            }
            if (otherwise != null) {
                renderAll(otherwise, scope, out);
            }
        }
    }

    private static final class ForNode extends Node {
        final List<String> targets;
        final Expr iterable;
        final Expr filter;
        final List<Node> body;
        final List<Node> otherwise;

        ForNode(List<String> targets, Expr iterable, Expr filter, List<Node> body, List<Node> otherwise) {
            this.targets = targets;
            this.iterable = iterable;
            this.filter = filter;
            this.body = body;
            this.otherwise = otherwise;
        }

        @Override
        void render(Scope scope, StringBuilder out) throws TemplateException {
            List<Object> items = Values.iterate(iterable.eval(scope));
            if (filter != null) {
                List<Object> kept = new ArrayList<Object>();
                for (Object item : items) {
                    Scope probe = new Scope(scope);
                    bind(probe, item);
                    if (Values.truthy(filter.eval(probe))) {
                        kept.add(item);
                    }
                }
                items = kept;
            }
            if (items.isEmpty()) {
                if (otherwise != null) {
                    renderAll(otherwise, scope, out);
                }
                return;
            }
            for (int i = 0; i < items.size(); i++) {
                Scope inner = new Scope(scope);
                bind(inner, items.get(i));
                Map<String, Object> loop = new HashMap<String, Object>();
                loop.put("index", (long) i + 1);
                loop.put("index0", (long) i);
                loop.put("revindex", (long) (items.size() - i));
                loop.put("revindex0", (long) (items.size() - i - 1));
                loop.put("first", i == 0);
                loop.put("last", i == items.size() - 1);
                loop.put("length", (long) items.size());
                loop.put("previtem", i > 0 ? items.get(i - 1) : UNDEFINED);
                loop.put("nextitem", i + 1 < items.size() ? items.get(i + 1) : UNDEFINED);
                inner.set("loop", loop);
                try {
                    renderAll(body, inner, out);
                } catch (LoopSignal signal) {
                    if (signal.isBreak) {
                        break;
                    }
                }
            }
        }

        private void bind(Scope scope, Object item) throws TemplateException {
            if (targets.size() == 1) {
                scope.set(targets.get(0), item);
                return;
            }
            List<Object> parts = Values.iterate(item);
            if (parts.size() != targets.size()) {
                throw new TemplateException("Entpacken in " + targets + " passt nicht");
            }
            for (int i = 0; i < targets.size(); i++) {
                scope.set(targets.get(i), parts.get(i));
            }
        }
    }

    private static final class SetNode extends Node {
        final String name;
        final String attribute;
        final Expr value;
        final List<Node> block;

        SetNode(String name, String attribute, Expr value, List<Node> block) {
            this.name = name;
            this.attribute = attribute;
            this.value = value;
            this.block = block;
        }

        @Override
        void render(Scope scope, StringBuilder out) throws TemplateException {
            Object result;
            if (block != null) {
                StringBuilder captured = new StringBuilder();
                renderAll(block, scope, captured);
                result = captured.toString();
            } else {
                result = value.eval(scope);
            }
            if (attribute == null) {
                scope.set(name, result);
                return;
            }
            Object target = scope.get(name);
            if (!(target instanceof Namespace)) {
                throw new TemplateException(name + " ist kein namespace");
            }
            ((Namespace) target).values.put(attribute, result);
        }
    }

    private static final class SignalNode extends Node {
        final boolean isBreak;

        SignalNode(boolean isBreak) {
            this.isBreak = isBreak;
        }

        @Override
        void render(Scope scope, StringBuilder out) {
            throw new LoopSignal(isBreak);
        }
    }

    private static final class MacroNode extends Node {
        final String name;
        final List<String> parameters;
        final List<Expr> defaults;
        final List<Node> body;

        MacroNode(String name, List<String> parameters, List<Expr> defaults, List<Node> body) {
            this.name = name;
            this.parameters = parameters;
            this.defaults = defaults;
            this.body = body;
        }

        @Override
        void render(final Scope scope, StringBuilder out) {
            scope.set(name, new Callable() {
                @Override
                public Object call(List<Object> args, Map<String, Object> kwargs) throws TemplateException {
                    Scope inner = new Scope(scope);
                    for (int i = 0; i < parameters.size(); i++) {
                        String parameter = parameters.get(i);
                        Object value = i < args.size() ? args.get(i)
                                : kwargs.containsKey(parameter) ? kwargs.get(parameter)
                                : defaults.get(i) != null ? defaults.get(i).eval(scope) : UNDEFINED;
                        inner.set(parameter, value);
                    }
                    StringBuilder result = new StringBuilder();
                    renderAll(body, inner, result);
                    return result.toString();
                }
            });
        }
    }

    private static void renderAll(List<Node> nodes, Scope scope, StringBuilder out) throws TemplateException {
        for (Node node : nodes) {
            node.render(scope, out);
        }
    }

    // ---------------------------------------------------------------- Parser der Tags

    private static final class Parser {
        final List<Token> tokens;
        int position;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        /** Knoten bis zu einem der End-Tags (das nicht verbraucht wird); ohne Angabe bis zum Ende. */
        List<Node> parseUntil(String... ends) throws TemplateException {
            List<Node> nodes = new ArrayList<Node>();
            while (position < tokens.size()) {
                Token token = tokens.get(position);
                if (token.type == TokenType.TEXT) {
                    nodes.add(new TextNode(token.text));
                    position++;
                } else if (token.type == TokenType.OUTPUT) {
                    nodes.add(new OutputNode(ExprParser.parse(token.text)));
                    position++;
                } else {
                    String keyword = keyword(token.text);
                    for (String end : ends) {
                        if (end.equals(keyword)) {
                            return nodes;
                        }
                    }
                    position++;
                    nodes.add(block(keyword, token.text));
                }
            }
            if (ends.length > 0) {
                throw new TemplateException("Fehlendes " + ends[ends.length - 1]);
            }
            return nodes;
        }

        private static String keyword(String text) {
            int i = 0;
            while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                i++;
            }
            return text.substring(0, i);
        }

        private String expect(String... keywords) throws TemplateException {
            if (position >= tokens.size()) {
                throw new TemplateException("Vorlage endet zu früh");
            }
            String keyword = keyword(tokens.get(position).text);
            for (String candidate : keywords) {
                if (candidate.equals(keyword)) {
                    return tokens.get(position++).text;
                }
            }
            throw new TemplateException("Erwartet " + java.util.Arrays.toString(keywords) + ", gefunden "
                    + tokens.get(position).text);
        }

        private Node block(String keyword, String text) throws TemplateException {
            String rest = text.substring(keyword.length()).trim();
            if ("if".equals(keyword)) {
                IfNode node = new IfNode();
                node.conditions.add(ExprParser.parse(rest));
                node.branches.add(parseUntil("elif", "else", "endif"));
                while (true) {
                    String tag = expect("elif", "else", "endif");
                    String tagKeyword = keyword(tag);
                    if ("elif".equals(tagKeyword)) {
                        node.conditions.add(ExprParser.parse(tag.substring(4).trim()));
                        node.branches.add(parseUntil("elif", "else", "endif"));
                    } else if ("else".equals(tagKeyword)) {
                        node.otherwise = parseUntil("endif");
                        expect("endif");
                        return node;
                    } else {
                        return node;
                    }
                }
            }
            if ("for".equals(keyword)) {
                int in = findKeyword(rest, " in ");
                if (in < 0) {
                    throw new TemplateException("for ohne in: " + text);
                }
                List<String> targets = new ArrayList<String>();
                for (String target : rest.substring(0, in).split(",")) {
                    String name = target.trim();
                    if (name.startsWith("(")) {
                        name = name.substring(1);
                    }
                    if (name.endsWith(")")) {
                        name = name.substring(0, name.length() - 1);
                    }
                    targets.add(name.trim());
                }
                String source = rest.substring(in + 4).trim();
                Expr filter = null;
                int condition = findKeyword(source, " if ");
                if (condition >= 0) {
                    filter = ExprParser.parse(source.substring(condition + 4));
                    source = source.substring(0, condition);
                }
                if (source.endsWith(" recursive")) {
                    throw new TemplateException("rekursive Schleifen werden nicht unterstützt");
                }
                Expr iterable = ExprParser.parse(source);
                List<Node> body = parseUntil("else", "endfor");
                List<Node> otherwise = null;
                if ("else".equals(keyword(expect("else", "endfor")))) {
                    otherwise = parseUntil("endfor");
                    expect("endfor");
                }
                return new ForNode(targets, iterable, filter, body, otherwise);
            }
            if ("set".equals(keyword)) {
                int equals = findAssignment(rest);
                String target = equals < 0 ? rest : rest.substring(0, equals).trim();
                String name = target;
                String attribute = null;
                int dot = target.indexOf('.');
                if (dot > 0) {
                    name = target.substring(0, dot).trim();
                    attribute = target.substring(dot + 1).trim();
                }
                if (equals < 0) {
                    List<Node> block = parseUntil("endset");
                    expect("endset");
                    return new SetNode(name, attribute, null, block);
                }
                return new SetNode(name, attribute, ExprParser.parse(rest.substring(equals + 1)), null);
            }
            if ("break".equals(keyword) || "continue".equals(keyword)) {
                return new SignalNode("break".equals(keyword));
            }
            if ("generation".equals(keyword)) {
                List<Node> inner = parseUntil("endgeneration");
                expect("endgeneration");
                IfNode always = new IfNode();
                always.conditions.add(new Literal(Boolean.TRUE));
                always.branches.add(inner);
                return always;
            }
            if ("macro".equals(keyword)) {
                int open = rest.indexOf('(');
                int close = rest.lastIndexOf(')');
                if (open < 0 || close < open) {
                    throw new TemplateException("macro ohne Parameterliste: " + text);
                }
                String name = rest.substring(0, open).trim();
                List<String> parameters = new ArrayList<String>();
                List<Expr> defaults = new ArrayList<Expr>();
                String list = rest.substring(open + 1, close).trim();
                if (!list.isEmpty()) {
                    for (String parameter : splitTopLevel(list)) {
                        int equals = parameter.indexOf('=');
                        parameters.add((equals < 0 ? parameter : parameter.substring(0, equals)).trim());
                        defaults.add(equals < 0 ? null : ExprParser.parse(parameter.substring(equals + 1)));
                    }
                }
                List<Node> body = parseUntil("endmacro");
                expect("endmacro");
                return new MacroNode(name, parameters, defaults, body);
            }
            throw new TemplateException("Tag " + keyword + " wird nicht unterstützt");
        }

        private static int findKeyword(String text, String keyword) {
            char quote = 0;
            int depth = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quote != 0) {
                    if (c == '\\') {
                        i++;
                    } else if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '\'' || c == '"') {
                    quote = c;
                } else if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == ')' || c == ']' || c == '}') {
                    depth--;
                } else if (depth == 0 && text.startsWith(keyword, i)) {
                    return i;
                }
            }
            return -1;
        }

        private static int findAssignment(String text) {
            char quote = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quote != 0) {
                    if (c == '\\') {
                        i++;
                    } else if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '\'' || c == '"') {
                    quote = c;
                } else if (c == '=' && (i + 1 >= text.length() || text.charAt(i + 1) != '=')
                        && (i == 0 || "!<>=".indexOf(text.charAt(i - 1)) < 0)) {
                    return i;
                }
            }
            return -1;
        }

        private static List<String> splitTopLevel(String text) {
            List<String> parts = new ArrayList<String>();
            int depth = 0;
            char quote = 0;
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quote != 0) {
                    if (c == '\\') {
                        i++;
                    } else if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '\'' || c == '"') {
                    quote = c;
                } else if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == ')' || c == ']' || c == '}') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    parts.add(text.substring(start, i));
                    start = i + 1;
                }
            }
            parts.add(text.substring(start));
            return parts;
        }
    }

    // ---------------------------------------------------------------- Ausdrücke

    private abstract static class Expr {
        abstract Object eval(Scope scope) throws TemplateException;
    }

    private static final class Literal extends Expr {
        final Object value;

        Literal(Object value) {
            this.value = value;
        }

        @Override
        Object eval(Scope scope) {
            return value;
        }
    }

    private static final class Name extends Expr {
        final String name;

        Name(String name) {
            this.name = name;
        }

        @Override
        Object eval(Scope scope) {
            return scope.get(name);
        }
    }

    private static final class ListExpr extends Expr {
        final List<Expr> items;

        ListExpr(List<Expr> items) {
            this.items = items;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            List<Object> values = new ArrayList<Object>(items.size());
            for (Expr item : items) {
                values.add(item.eval(scope));
            }
            return values;
        }
    }

    private static final class DictExpr extends Expr {
        final List<Expr> keys;
        final List<Expr> values;

        DictExpr(List<Expr> keys, List<Expr> values) {
            this.keys = keys;
            this.values = values;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            for (int i = 0; i < keys.size(); i++) {
                map.put(Values.str(keys.get(i).eval(scope)), values.get(i).eval(scope));
            }
            return map;
        }
    }

    private static final class Attribute extends Expr {
        final Expr target;
        final String name;

        Attribute(Expr target, String name) {
            this.target = target;
            this.name = name;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            return Values.attribute(target.eval(scope), name);
        }
    }

    private static final class Subscript extends Expr {
        final Expr target;
        final Expr index;

        Subscript(Expr target, Expr index) {
            this.target = target;
            this.index = index;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            return Values.item(target.eval(scope), index.eval(scope));
        }
    }

    private static final class Slice extends Expr {
        final Expr target;
        final Expr start;
        final Expr stop;
        final Expr step;

        Slice(Expr target, Expr start, Expr stop, Expr step) {
            this.target = target;
            this.start = start;
            this.stop = stop;
            this.step = step;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            Object value = target.eval(scope);
            boolean text = value instanceof String;
            List<Object> items = text ? Values.chars((String) value) : Values.iterate(value);
            int size = items.size();
            int stepValue = step == null ? 1 : (int) Values.number(step.eval(scope));
            if (stepValue == 0) {
                throw new TemplateException("Slice-Schritt 0");
            }
            Integer startValue = start == null ? null : (int) Values.number(start.eval(scope));
            Integer stopValue = stop == null ? null : (int) Values.number(stop.eval(scope));
            List<Object> out = new ArrayList<Object>();
            if (stepValue > 0) {
                int from = startValue == null ? 0 : clamp(startValue, size, 0, size);
                int to = stopValue == null ? size : clamp(stopValue, size, 0, size);
                for (int i = from; i < to; i += stepValue) {
                    out.add(items.get(i));
                }
            } else {
                int from = startValue == null ? size - 1 : clamp(startValue, size, -1, size - 1);
                int to = stopValue == null ? -1 : clamp(stopValue, size, -1, size - 1);
                for (int i = from; i > to; i += stepValue) {
                    out.add(items.get(i));
                }
            }
            if (text) {
                StringBuilder joined = new StringBuilder();
                for (Object c : out) {
                    joined.append(c);
                }
                return joined.toString();
            }
            return out;
        }

        private static int clamp(int index, int size, int low, int high) {
            int value = index < 0 ? index + size : index;
            return Math.max(low, Math.min(high, value));
        }
    }

    private static final class Call extends Expr {
        final Expr target;
        final List<Expr> args;
        final Map<String, Expr> kwargs;

        Call(Expr target, List<Expr> args, Map<String, Expr> kwargs) {
            this.target = target;
            this.args = args;
            this.kwargs = kwargs;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            List<Object> values = new ArrayList<Object>(args.size());
            for (Expr arg : args) {
                values.add(arg.eval(scope));
            }
            Map<String, Object> named = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Expr> entry : kwargs.entrySet()) {
                named.put(entry.getKey(), entry.getValue().eval(scope));
            }
            if (target instanceof Attribute) {
                Attribute method = (Attribute) target;
                Object receiver = method.target.eval(scope);
                Object member = Values.attribute(receiver, method.name);
                if (member instanceof Callable) {
                    return ((Callable) member).call(values, named);
                }
                return Builtins.method(receiver, method.name, values);
            }
            Object function = target.eval(scope);
            if (!(function instanceof Callable)) {
                throw new TemplateException("Nicht aufrufbar: " + (target instanceof Name ? ((Name) target).name
                        : "Ausdruck"));
            }
            return ((Callable) function).call(values, named);
        }
    }

    private static final class FilterExpr extends Expr {
        final Expr target;
        final String name;
        final List<Expr> args;
        final Map<String, Expr> kwargs;

        FilterExpr(Expr target, String name, List<Expr> args, Map<String, Expr> kwargs) {
            this.target = target;
            this.name = name;
            this.args = args;
            this.kwargs = kwargs;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            List<Object> values = new ArrayList<Object>(args.size());
            for (Expr arg : args) {
                values.add(arg.eval(scope));
            }
            Map<String, Object> named = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Expr> entry : kwargs.entrySet()) {
                named.put(entry.getKey(), entry.getValue().eval(scope));
            }
            return Builtins.filter(name, target.eval(scope), values, named);
        }
    }

    private static final class TestExpr extends Expr {
        final Expr target;
        final String name;
        final List<Expr> args;
        final boolean negated;

        TestExpr(Expr target, String name, List<Expr> args, boolean negated) {
            this.target = target;
            this.name = name;
            this.args = args;
            this.negated = negated;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            List<Object> values = new ArrayList<Object>(args.size());
            for (Expr arg : args) {
                values.add(arg.eval(scope));
            }
            return Builtins.test(name, target.eval(scope), values) != negated;
        }
    }

    private static final class Unary extends Expr {
        final String operator;
        final Expr operand;

        Unary(String operator, Expr operand) {
            this.operator = operator;
            this.operand = operand;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            Object value = operand.eval(scope);
            if ("not".equals(operator)) {
                return !Values.truthy(value);
            }
            if ("-".equals(operator)) {
                return Values.negate(value);
            }
            return value;
        }
    }

    private static final class Binary extends Expr {
        final String operator;
        final Expr left;
        final Expr right;

        Binary(String operator, Expr left, Expr right) {
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            if ("and".equals(operator)) {
                Object a = left.eval(scope);
                return Values.truthy(a) ? right.eval(scope) : a;
            }
            if ("or".equals(operator)) {
                Object a = left.eval(scope);
                return Values.truthy(a) ? a : right.eval(scope);
            }
            return Values.binary(operator, left.eval(scope), right.eval(scope));
        }
    }

    private static final class Conditional extends Expr {
        final Expr condition;
        final Expr then;
        final Expr otherwise;

        Conditional(Expr condition, Expr then, Expr otherwise) {
            this.condition = condition;
            this.then = then;
            this.otherwise = otherwise;
        }

        @Override
        Object eval(Scope scope) throws TemplateException {
            if (Values.truthy(condition.eval(scope))) {
                return then.eval(scope);
            }
            return otherwise == null ? UNDEFINED : otherwise.eval(scope);
        }
    }

    /** Rekursiver Abstieg nach der Rangfolge von Jinja2. */
    private static final class ExprParser {
        private final List<String> tokens;
        private int position;

        private ExprParser(List<String> tokens) {
            this.tokens = tokens;
        }

        static Expr parse(String text) throws TemplateException {
            ExprParser parser = new ExprParser(ExprLexer.tokenize(text));
            Expr expr = parser.conditional();
            if (parser.position < parser.tokens.size()) {
                throw new TemplateException("Unerwartet in Ausdruck: " + parser.tokens.get(parser.position)
                        + " in " + text);
            }
            return expr;
        }

        private String peek() {
            return position < tokens.size() ? tokens.get(position) : null;
        }

        private boolean accept(String token) {
            if (token.equals(peek())) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(String token) throws TemplateException {
            if (!accept(token)) {
                throw new TemplateException("Erwartet " + token + ", gefunden " + peek());
            }
        }

        private Expr conditional() throws TemplateException {
            Expr value = or();
            if (accept("if")) {
                Expr condition = or();
                Expr otherwise = accept("else") ? conditional() : null;
                return new Conditional(condition, value, otherwise);
            }
            return value;
        }

        private Expr or() throws TemplateException {
            Expr left = and();
            while (accept("or")) {
                left = new Binary("or", left, and());
            }
            return left;
        }

        private Expr and() throws TemplateException {
            Expr left = not();
            while (accept("and")) {
                left = new Binary("and", left, not());
            }
            return left;
        }

        private Expr not() throws TemplateException {
            if (accept("not")) {
                return new Unary("not", not());
            }
            return compare();
        }

        private Expr compare() throws TemplateException {
            Expr left = concat();
            while (true) {
                String token = peek();
                if (token == null) {
                    return left;
                }
                if ("==".equals(token) || "!=".equals(token) || "<".equals(token) || ">".equals(token)
                        || "<=".equals(token) || ">=".equals(token) || "in".equals(token)) {
                    position++;
                    left = new Binary(token, left, concat());
                } else if ("not".equals(token) && "in".equals(position + 1 < tokens.size()
                        ? tokens.get(position + 1) : null)) {
                    position += 2;
                    left = new Unary("not", new Binary("in", left, concat()));
                } else if ("is".equals(token)) {
                    position++;
                    boolean negated = accept("not");
                    String name = peek();
                    if (name == null) {
                        throw new TemplateException("Test fehlt nach is");
                    }
                    position++;
                    List<Expr> args = new ArrayList<Expr>();
                    if (accept("(")) {
                        if (!accept(")")) {
                            do {
                                args.add(conditional());
                            } while (accept(","));
                            expect(")");
                        }
                    } else if (peek() != null && startsPrimary(peek())) {
                        args.add(concat());
                    }
                    left = new TestExpr(left, name, args, negated);
                } else {
                    return left;
                }
            }
        }

        private static boolean startsPrimary(String token) {
            char c = token.charAt(0);
            return c == '\'' || c == '"' || Character.isDigit(c)
                    || ((Character.isLetter(c) || c == '_') && !"and".equals(token) && !"or".equals(token)
                    && !"if".equals(token) && !"else".equals(token) && !"is".equals(token) && !"in".equals(token)
                    && !"not".equals(token));
        }

        private Expr concat() throws TemplateException {
            Expr left = additive();
            while (accept("~")) {
                left = new Binary("~", left, additive());
            }
            return left;
        }

        private Expr additive() throws TemplateException {
            Expr left = multiplicative();
            while (true) {
                if (accept("+")) {
                    left = new Binary("+", left, multiplicative());
                } else if (accept("-")) {
                    left = new Binary("-", left, multiplicative());
                } else {
                    return left;
                }
            }
        }

        private Expr multiplicative() throws TemplateException {
            Expr left = unary();
            while (true) {
                String token = peek();
                if ("*".equals(token) || "/".equals(token) || "//".equals(token) || "%".equals(token)) {
                    position++;
                    left = new Binary(token, left, unary());
                } else {
                    return left;
                }
            }
        }

        private Expr unary() throws TemplateException {
            if (accept("-")) {
                return new Unary("-", unary());
            }
            if (accept("+")) {
                return unary();
            }
            return postfix(primary());
        }

        private Expr postfix(Expr base) throws TemplateException {
            Expr current = base;
            while (true) {
                if (accept(".")) {
                    String name = peek();
                    position++;
                    current = new Attribute(current, name);
                } else if (accept("[")) {
                    Expr start = null;
                    if (!":".equals(peek())) {
                        start = conditional();
                    }
                    if (accept(":")) {
                        Expr stop = null;
                        Expr step = null;
                        if (!":".equals(peek()) && !"]".equals(peek())) {
                            stop = conditional();
                        }
                        if (accept(":") && !"]".equals(peek())) {
                            step = conditional();
                        }
                        expect("]");
                        current = new Slice(current, start, stop, step);
                    } else {
                        expect("]");
                        current = new Subscript(current, start);
                    }
                } else if ("(".equals(peek())) {
                    position++;
                    List<Expr> args = new ArrayList<Expr>();
                    Map<String, Expr> kwargs = new LinkedHashMap<String, Expr>();
                    arguments(args, kwargs);
                    current = new Call(current, args, kwargs);
                } else if (accept("|")) {
                    String name = peek();
                    position++;
                    List<Expr> args = new ArrayList<Expr>();
                    Map<String, Expr> kwargs = new LinkedHashMap<String, Expr>();
                    if (accept("(")) {
                        arguments(args, kwargs);
                    }
                    current = new FilterExpr(current, name, args, kwargs);
                } else {
                    return current;
                }
            }
        }

        /** Nach "(": Argumente bis ")". */
        private void arguments(List<Expr> args, Map<String, Expr> kwargs) throws TemplateException {
            if (accept(")")) {
                return;
            }
            do {
                if (")".equals(peek())) {
                    break;
                }
                String token = peek();
                if (token != null && position + 1 < tokens.size() && "=".equals(tokens.get(position + 1))
                        && Character.isJavaIdentifierStart(token.charAt(0))) {
                    position += 2;
                    kwargs.put(token, conditional());
                } else {
                    args.add(conditional());
                }
            } while (accept(","));
            expect(")");
        }

        private Expr primary() throws TemplateException {
            String token = peek();
            if (token == null) {
                throw new TemplateException("Ausdruck endet zu früh");
            }
            position++;
            char c = token.charAt(0);
            if (c == '\'' || c == '"') {
                StringBuilder text = new StringBuilder(ExprLexer.unquote(token));
                // aneinandergereihte Zeichenketten wie in Python
                while (peek() != null && (peek().charAt(0) == '\'' || peek().charAt(0) == '"')) {
                    text.append(ExprLexer.unquote(tokens.get(position++)));
                }
                return new Literal(text.toString());
            }
            if (Character.isDigit(c)) {
                if (token.contains(".") || token.contains("e") || token.contains("E")) {
                    return new Literal(Double.parseDouble(token));
                }
                return new Literal(Long.parseLong(token));
            }
            if ("(".equals(token)) {
                Expr inner = conditional();
                if (accept(",")) {
                    List<Expr> items = new ArrayList<Expr>();
                    items.add(inner);
                    if (!")".equals(peek())) {
                        do {
                            items.add(conditional());
                        } while (accept(",") && !")".equals(peek()));
                    }
                    expect(")");
                    return new ListExpr(items);
                }
                expect(")");
                return inner;
            }
            if ("[".equals(token)) {
                List<Expr> items = new ArrayList<Expr>();
                if (!accept("]")) {
                    do {
                        if ("]".equals(peek())) {
                            break;
                        }
                        items.add(conditional());
                    } while (accept(","));
                    expect("]");
                }
                return new ListExpr(items);
            }
            if ("{".equals(token)) {
                List<Expr> keys = new ArrayList<Expr>();
                List<Expr> values = new ArrayList<Expr>();
                if (!accept("}")) {
                    do {
                        if ("}".equals(peek())) {
                            break;
                        }
                        keys.add(conditional());
                        expect(":");
                        values.add(conditional());
                    } while (accept(","));
                    expect("}");
                }
                return new DictExpr(keys, values);
            }
            if ("true".equals(token) || "True".equals(token)) {
                return new Literal(Boolean.TRUE);
            }
            if ("false".equals(token) || "False".equals(token)) {
                return new Literal(Boolean.FALSE);
            }
            if ("none".equals(token) || "None".equals(token)) {
                return new Literal(null);
            }
            if (Character.isLetter(c) || c == '_') {
                return new Name(token);
            }
            throw new TemplateException("Unerwartet in Ausdruck: " + token);
        }
    }

    private static final class ExprLexer {
        private static final String[] OPERATORS = {"//", "==", "!=", "<=", ">=", "+", "-", "*", "/", "%", "~", "<",
                ">", "(", ")", "[", "]", "{", "}", ".", ",", ":", "|", "="};

        static List<String> tokenize(String text) throws TemplateException {
            List<String> tokens = new ArrayList<String>();
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                if (c == '\'' || c == '"') {
                    int j = i + 1;
                    while (j < text.length() && text.charAt(j) != c) {
                        if (text.charAt(j) == '\\') {
                            j++;
                        }
                        j++;
                    }
                    if (j >= text.length()) {
                        throw new TemplateException("Zeichenkette ohne Ende: " + text);
                    }
                    tokens.add(text.substring(i, j + 1));
                    i = j + 1;
                    continue;
                }
                if (Character.isDigit(c)) {
                    int j = i;
                    while (j < text.length() && (Character.isDigit(text.charAt(j)) || text.charAt(j) == '_')) {
                        j++;
                    }
                    if (j + 1 < text.length() && text.charAt(j) == '.' && Character.isDigit(text.charAt(j + 1))) {
                        j++;
                        while (j < text.length() && Character.isDigit(text.charAt(j))) {
                            j++;
                        }
                    }
                    tokens.add(text.substring(i, j).replace("_", ""));
                    i = j;
                    continue;
                }
                if (Character.isLetter(c) || c == '_') {
                    int j = i;
                    while (j < text.length() && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) {
                        j++;
                    }
                    tokens.add(text.substring(i, j));
                    i = j;
                    continue;
                }
                String operator = null;
                for (String candidate : OPERATORS) {
                    if (text.startsWith(candidate, i)) {
                        operator = candidate;
                        break;
                    }
                }
                if (operator == null) {
                    throw new TemplateException("Unbekanntes Zeichen '" + c + "' in " + text);
                }
                tokens.add(operator);
                i += operator.length();
            }
            return tokens;
        }

        static String unquote(String token) {
            StringBuilder out = new StringBuilder();
            for (int i = 1; i < token.length() - 1; i++) {
                char c = token.charAt(i);
                if (c != '\\' || i + 1 >= token.length() - 1) {
                    out.append(c);
                    continue;
                }
                char next = token.charAt(++i);
                switch (next) {
                    case 'n':
                        out.append('\n');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case '\'':
                        out.append('\'');
                        break;
                    case '"':
                        out.append('"');
                        break;
                    case 'u':
                        if (i + 4 < token.length()) {
                            out.append((char) Integer.parseInt(token.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                        break;
                    default:
                        out.append('\\').append(next);
                }
            }
            return out.toString();
        }
    }

    // ---------------------------------------------------------------- Werte-Semantik

    static final class Values {
        private Values() {
        }

        static boolean truthy(Object value) {
            if (value == null || value == UNDEFINED) {
                return false;
            }
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value instanceof Number) {
                return ((Number) value).doubleValue() != 0;
            }
            if (value instanceof String) {
                return !((String) value).isEmpty();
            }
            if (value instanceof Collection) {
                return !((Collection<?>) value).isEmpty();
            }
            if (value instanceof Map) {
                return !((Map<?, ?>) value).isEmpty();
            }
            return true;
        }

        static String str(Object value) {
            if (value == null) {
                return "None";
            }
            if (value == UNDEFINED) {
                return "";
            }
            if (value instanceof String) {
                return (String) value;
            }
            return repr(value, false);
        }

        /** Python-Darstellung (für Listen und Dicts in Ausgaben). */
        static String repr(Object value, boolean quoted) {
            if (value == null) {
                return "None";
            }
            if (value instanceof Boolean) {
                return (Boolean) value ? "True" : "False";
            }
            if (value instanceof Double || value instanceof Float) {
                double d = ((Number) value).doubleValue();
                if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e16) {
                    return String.valueOf((long) d) + ".0";
                }
                return String.valueOf(d);
            }
            if (value instanceof Number) {
                return String.valueOf(((Number) value).longValue());
            }
            if (value instanceof String) {
                return quoted ? "'" + ((String) value).replace("\\", "\\\\").replace("'", "\\'") + "'"
                        : (String) value;
            }
            if (value instanceof List) {
                StringBuilder out = new StringBuilder("[");
                boolean first = true;
                for (Object item : (List<?>) value) {
                    if (!first) {
                        out.append(", ");
                    }
                    first = false;
                    out.append(repr(item, true));
                }
                return out.append(']').toString();
            }
            if (value instanceof Map) {
                StringBuilder out = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!first) {
                        out.append(", ");
                    }
                    first = false;
                    out.append(repr(entry.getKey(), true)).append(": ").append(repr(entry.getValue(), true));
                }
                return out.append('}').toString();
            }
            if (value == UNDEFINED) {
                return "";
            }
            return String.valueOf(value);
        }

        static List<Object> chars(String text) {
            List<Object> out = new ArrayList<Object>();
            for (int i = 0; i < text.length(); ) {
                int c = text.codePointAt(i);
                out.add(new String(Character.toChars(c)));
                i += Character.charCount(c);
            }
            return out;
        }

        static List<Object> iterate(Object value) throws TemplateException {
            if (value == null || value == UNDEFINED) {
                return Collections.emptyList();
            }
            if (value instanceof List) {
                return new ArrayList<Object>((List<?>) value);
            }
            if (value instanceof Collection) {
                return new ArrayList<Object>((Collection<?>) value);
            }
            if (value instanceof Map) {
                return new ArrayList<Object>(((Map<?, ?>) value).keySet());
            }
            if (value instanceof String) {
                return chars((String) value);
            }
            throw new TemplateException("Nicht iterierbar: " + repr(value, true));
        }

        static double number(Object value) throws TemplateException {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof Boolean) {
                return (Boolean) value ? 1 : 0;
            }
            throw new TemplateException("Keine Zahl: " + repr(value, true));
        }

        static Object negate(Object value) throws TemplateException {
            if (value instanceof Long || value instanceof Integer) {
                return -((Number) value).longValue();
            }
            return -number(value);
        }

        static Object attribute(Object target, String name) {
            if (target instanceof Namespace) {
                Map<String, Object> values = ((Namespace) target).values;
                return values.containsKey(name) ? values.get(name) : UNDEFINED;
            }
            if (target instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) target;
                if (map.containsKey(name)) {
                    return map.get(name);
                }
            }
            return UNDEFINED;
        }

        static Object item(Object target, Object index) throws TemplateException {
            if (target instanceof Map || target instanceof Namespace) {
                if (target instanceof Namespace) {
                    return attribute(target, str(index));
                }
                Map<?, ?> map = (Map<?, ?>) target;
                Object key = index instanceof String ? index : str(index);
                return map.containsKey(key) ? map.get(key) : UNDEFINED;
            }
            if (target instanceof List || target instanceof String) {
                List<?> list = target instanceof String ? chars((String) target) : (List<?>) target;
                if (!(index instanceof Number)) {
                    return UNDEFINED;
                }
                int i = (int) ((Number) index).longValue();
                if (i < 0) {
                    i += list.size();
                }
                return i >= 0 && i < list.size() ? list.get(i) : UNDEFINED;
            }
            return UNDEFINED;
        }

        static boolean equal(Object a, Object b) {
            if (a == UNDEFINED || b == UNDEFINED) {
                return a == b;
            }
            if (a == null || b == null) {
                return a == b;
            }
            if (a instanceof Number && b instanceof Number) {
                return ((Number) a).doubleValue() == ((Number) b).doubleValue();
            }
            if (a instanceof Boolean && b instanceof Number || a instanceof Number && b instanceof Boolean) {
                return false;
            }
            return a.equals(b);
        }

        static Object binary(String operator, Object a, Object b) throws TemplateException {
            if ("==".equals(operator)) {
                return equal(a, b);
            }
            if ("!=".equals(operator)) {
                return !equal(a, b);
            }
            if ("in".equals(operator)) {
                if (b instanceof String) {
                    return ((String) b).contains(str(a));
                }
                if (b instanceof Map) {
                    return ((Map<?, ?>) b).containsKey(a);
                }
                if (b instanceof Namespace) {
                    return ((Namespace) b).values.containsKey(str(a));
                }
                for (Object item : iterate(b)) {
                    if (equal(a, item)) {
                        return true;
                    }
                }
                return false;
            }
            if ("~".equals(operator)) {
                return str(a) + str(b);
            }
            if ("+".equals(operator)) {
                if (a instanceof String || b instanceof String) {
                    if (a instanceof String && b instanceof String) {
                        return (String) a + b;
                    }
                    if (a == UNDEFINED || b == UNDEFINED) {
                        return str(a) + str(b);
                    }
                    throw new TemplateException("+ von Zeichenkette und " + repr(a instanceof String ? b : a, true));
                }
                if (a instanceof List && b instanceof List) {
                    List<Object> joined = new ArrayList<Object>((List<?>) a);
                    joined.addAll((List<?>) b);
                    return joined;
                }
            }
            if ("<".equals(operator) || ">".equals(operator) || "<=".equals(operator) || ">=".equals(operator)) {
                int compared;
                if (a instanceof String && b instanceof String) {
                    compared = ((String) a).compareTo((String) b);
                } else {
                    compared = Double.compare(number(a), number(b));
                }
                return "<".equals(operator) ? compared < 0 : ">".equals(operator) ? compared > 0
                        : "<=".equals(operator) ? compared <= 0 : compared >= 0;
            }
            if ("*".equals(operator) && a instanceof String && b instanceof Number) {
                StringBuilder out = new StringBuilder();
                for (long i = 0; i < ((Number) b).longValue(); i++) {
                    out.append((String) a);
                }
                return out.toString();
            }
            boolean integral = isIntegral(a) && isIntegral(b);
            double x = number(a);
            double y = number(b);
            if ("+".equals(operator)) {
                return integral ? (Object) ((long) x + (long) y) : (Object) (x + y);
            }
            if ("-".equals(operator)) {
                return integral ? (Object) ((long) x - (long) y) : (Object) (x - y);
            }
            if ("*".equals(operator)) {
                return integral ? (Object) ((long) x * (long) y) : (Object) (x * y);
            }
            if ("/".equals(operator)) {
                return x / y;
            }
            if ("//".equals(operator)) {
                return integral ? (Object) Math.floorDiv((long) x, (long) y) : (Object) Math.floor(x / y);
            }
            if ("%".equals(operator)) {
                if (a instanceof String) {
                    throw new TemplateException("%-Formatierung wird nicht unterstützt");
                }
                return integral ? (Object) Math.floorMod((long) x, (long) y) : (Object) (x - Math.floor(x / y) * y);
            }
            throw new TemplateException("Operator " + operator + " wird nicht unterstützt");
        }

        private static boolean isIntegral(Object value) {
            return value instanceof Long || value instanceof Integer || value instanceof Boolean;
        }
    }

    // ---------------------------------------------------------------- Filter, Tests, Methoden, Globale

    private static final class Builtins {
        private Builtins() {
        }

        static Map<String, Object> globals() {
            Map<String, Object> globals = new HashMap<String, Object>();
            globals.put("raise_exception", new Callable() {
                @Override
                public Object call(List<Object> args, Map<String, Object> kwargs) throws TemplateException {
                    throw new TemplateException("Vorlage: " + (args.isEmpty() ? "Fehler" : Values.str(args.get(0))));
                }
            });
            globals.put("namespace", new Callable() {
                @Override
                public Object call(List<Object> args, Map<String, Object> kwargs) {
                    Namespace namespace = new Namespace();
                    namespace.values.putAll(kwargs);
                    return namespace;
                }
            });
            globals.put("range", new Callable() {
                @Override
                public Object call(List<Object> args, Map<String, Object> kwargs) throws TemplateException {
                    long start = 0;
                    long stop;
                    long step = 1;
                    if (args.size() == 1) {
                        stop = (long) Values.number(args.get(0));
                    } else {
                        start = (long) Values.number(args.get(0));
                        stop = (long) Values.number(args.get(1));
                        if (args.size() > 2) {
                            step = (long) Values.number(args.get(2));
                        }
                    }
                    List<Object> out = new ArrayList<Object>();
                    for (long i = start; step > 0 ? i < stop : i > stop; i += step) {
                        out.add(i);
                    }
                    return out;
                }
            });
            globals.put("strftime_now", new Callable() {
                @Override
                public Object call(List<Object> args, Map<String, Object> kwargs) {
                    String format = args.isEmpty() ? "%Y-%m-%d" : Values.str(args.get(0));
                    return strftime(format, new Date());
                }
            });
            return globals;
        }

        static String strftime(String format, Date date) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < format.length(); i++) {
                char c = format.charAt(i);
                if (c != '%' || i + 1 >= format.length()) {
                    out.append(c);
                    continue;
                }
                char code = format.charAt(++i);
                String pattern;
                switch (code) {
                    case 'Y':
                        pattern = "yyyy";
                        break;
                    case 'm':
                        pattern = "MM";
                        break;
                    case 'd':
                        pattern = "dd";
                        break;
                    case 'b':
                        pattern = "MMM";
                        break;
                    case 'B':
                        pattern = "MMMM";
                        break;
                    case 'H':
                        pattern = "HH";
                        break;
                    case 'M':
                        pattern = "mm";
                        break;
                    case 'S':
                        pattern = "ss";
                        break;
                    case 'A':
                        pattern = "EEEE";
                        break;
                    case 'a':
                        pattern = "EEE";
                        break;
                    case 'y':
                        pattern = "yy";
                        break;
                    default:
                        out.append('%').append(code);
                        continue;
                }
                out.append(new SimpleDateFormat(pattern, Locale.ENGLISH).format(date));
            }
            return out.toString();
        }

        static Object filter(String name, Object value, List<Object> args, Map<String, Object> kwargs)
                throws TemplateException {
            if ("trim".equals(name)) {
                return Values.str(value).trim();
            }
            if ("length".equals(name) || "count".equals(name)) {
                if (value instanceof String) {
                    return (long) ((String) value).codePointCount(0, ((String) value).length());
                }
                if (value instanceof Map) {
                    return (long) ((Map<?, ?>) value).size();
                }
                return (long) Values.iterate(value).size();
            }
            if ("tojson".equals(name)) {
                Object indent = kwargs.containsKey("indent") ? kwargs.get("indent")
                        : args.isEmpty() ? null : args.get(0);
                return JsonText.write(value, indent instanceof Number ? ((Number) indent).intValue() : -1,
                        Values.truthy(kwargs.get("sort_keys")));
            }
            if ("string".equals(name)) {
                return Values.str(value);
            }
            if ("safe".equals(name) || "e".equals(name) || "escape".equals(name)) {
                return value;
            }
            if ("lower".equals(name)) {
                return Values.str(value).toLowerCase(Locale.ROOT);
            }
            if ("upper".equals(name)) {
                return Values.str(value).toUpperCase(Locale.ROOT);
            }
            if ("capitalize".equals(name)) {
                String text = Values.str(value);
                return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT)
                        + text.substring(1).toLowerCase(Locale.ROOT);
            }
            if ("title".equals(name)) {
                return title(Values.str(value));
            }
            if ("first".equals(name)) {
                List<Object> items = Values.iterate(value);
                return items.isEmpty() ? UNDEFINED : items.get(0);
            }
            if ("last".equals(name)) {
                List<Object> items = Values.iterate(value);
                return items.isEmpty() ? UNDEFINED : items.get(items.size() - 1);
            }
            if ("default".equals(name) || "d".equals(name)) {
                boolean bool = args.size() > 1 && Values.truthy(args.get(1)) || Values.truthy(kwargs.get("boolean"));
                boolean missing = value == UNDEFINED || (bool && !Values.truthy(value));
                return missing ? (args.isEmpty() ? "" : args.get(0)) : value;
            }
            if ("join".equals(name)) {
                String separator = args.isEmpty() ? "" : Values.str(args.get(0));
                return joinWith(Values.iterate(value), separator);
            }
            if ("items".equals(name)) {
                return items(value);
            }
            if ("list".equals(name)) {
                return Values.iterate(value);
            }
            if ("reverse".equals(name)) {
                if (value instanceof String) {
                    return new StringBuilder((String) value).reverse().toString();
                }
                List<Object> items = Values.iterate(value);
                Collections.reverse(items);
                return items;
            }
            if ("replace".equals(name)) {
                if (args.size() < 2) {
                    throw new TemplateException("replace braucht zwei Argumente");
                }
                return Values.str(value).replace(Values.str(args.get(0)), Values.str(args.get(1)));
            }
            if ("int".equals(name)) {
                try {
                    return value instanceof Number ? ((Number) value).longValue()
                            : (long) Double.parseDouble(Values.str(value).trim());
                } catch (NumberFormatException e) {
                    return 0L;
                }
            }
            if ("float".equals(name)) {
                try {
                    return value instanceof Number ? ((Number) value).doubleValue()
                            : Double.parseDouble(Values.str(value).trim());
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }
            if ("abs".equals(name)) {
                double number = Values.number(value);
                return value instanceof Long ? (Object) Math.abs(((Long) value)) : (Object) Math.abs(number);
            }
            if ("round".equals(name)) {
                return (double) Math.round(Values.number(value));
            }
            if ("indent".equals(name)) {
                int width = args.isEmpty() ? 4 : (int) Values.number(args.get(0));
                StringBuilder pad = new StringBuilder();
                for (int i = 0; i < width; i++) {
                    pad.append(' ');
                }
                boolean first = args.size() > 1 && Values.truthy(args.get(1)) || Values.truthy(kwargs.get("first"));
                String text = Values.str(value);
                String indented = text.replace("\n", "\n" + pad);
                return first ? pad + indented : indented;
            }
            if ("map".equals(name)) {
                List<Object> out = new ArrayList<Object>();
                Object attribute = kwargs.get("attribute");
                for (Object item : Values.iterate(value)) {
                    if (attribute != null) {
                        out.add(Values.attribute(item, Values.str(attribute)));
                    } else if (!args.isEmpty()) {
                        out.add(filter(Values.str(args.get(0)), item, args.subList(1, args.size()),
                                Collections.<String, Object>emptyMap()));
                    } else {
                        out.add(item);
                    }
                }
                return out;
            }
            if ("selectattr".equals(name) || "rejectattr".equals(name) || "select".equals(name)
                    || "reject".equals(name)) {
                boolean attr = name.endsWith("attr");
                boolean keep = name.startsWith("select");
                List<Object> out = new ArrayList<Object>();
                for (Object item : Values.iterate(value)) {
                    Object subject = item;
                    int offset = 0;
                    if (attr) {
                        subject = Values.attribute(item, Values.str(args.get(0)));
                        offset = 1;
                    }
                    boolean passed;
                    if (args.size() > offset) {
                        passed = test(Values.str(args.get(offset)), subject, args.subList(offset + 1, args.size()));
                    } else {
                        passed = Values.truthy(subject);
                    }
                    if (passed == keep) {
                        out.add(item);
                    }
                }
                return out;
            }
            if ("unique".equals(name)) {
                List<Object> out = new ArrayList<Object>();
                for (Object item : Values.iterate(value)) {
                    boolean seen = false;
                    for (Object existing : out) {
                        seen |= Values.equal(existing, item);
                    }
                    if (!seen) {
                        out.add(item);
                    }
                }
                return out;
            }
            if ("dictsort".equals(name)) {
                List<Object> items = items(value);
                Collections.sort(items, (a, b) -> Values.str(((List<?>) a).get(0))
                        .compareTo(Values.str(((List<?>) b).get(0))));
                return items;
            }
            throw new TemplateException("Filter " + name + " wird nicht unterstützt");
        }

        private static String joinWith(List<Object> items, String separator) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    out.append(separator);
                }
                out.append(Values.str(items.get(i)));
            }
            return out.toString();
        }

        private static List<Object> items(Object value) throws TemplateException {
            List<Object> out = new ArrayList<Object>();
            Map<?, ?> map = value instanceof Namespace ? ((Namespace) value).values
                    : value instanceof Map ? (Map<?, ?>) value : null;
            if (map == null) {
                if (value == UNDEFINED || value == null) {
                    return out;
                }
                throw new TemplateException("items() braucht ein Dict");
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                List<Object> pair = new ArrayList<Object>(2);
                pair.add(entry.getKey());
                pair.add(entry.getValue());
                out.add(pair);
            }
            return out;
        }

        private static String title(String text) {
            StringBuilder out = new StringBuilder(text.length());
            boolean start = true;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                out.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
                start = !Character.isLetterOrDigit(c);
            }
            return out.toString();
        }

        static boolean test(String name, Object value, List<Object> args) throws TemplateException {
            if ("defined".equals(name)) {
                return value != UNDEFINED;
            }
            if ("undefined".equals(name)) {
                return value == UNDEFINED;
            }
            if ("none".equals(name)) {
                return value == null;
            }
            if ("string".equals(name)) {
                return value instanceof String;
            }
            if ("number".equals(name)) {
                return value instanceof Number;
            }
            if ("integer".equals(name)) {
                return value instanceof Long || value instanceof Integer;
            }
            if ("float".equals(name)) {
                return value instanceof Double || value instanceof Float;
            }
            if ("boolean".equals(name)) {
                return value instanceof Boolean;
            }
            if ("true".equals(name)) {
                return Boolean.TRUE.equals(value);
            }
            if ("false".equals(name)) {
                return Boolean.FALSE.equals(value);
            }
            if ("mapping".equals(name)) {
                return value instanceof Map || value instanceof Namespace;
            }
            if ("iterable".equals(name)) {
                return value instanceof Collection || value instanceof Map || value instanceof String;
            }
            if ("sequence".equals(name)) {
                return value instanceof List || value instanceof String || value instanceof Map;
            }
            if ("callable".equals(name)) {
                return value instanceof Callable;
            }
            if ("odd".equals(name)) {
                return ((long) Values.number(value)) % 2 != 0;
            }
            if ("even".equals(name)) {
                return ((long) Values.number(value)) % 2 == 0;
            }
            if ("divisibleby".equals(name)) {
                return ((long) Values.number(value)) % ((long) Values.number(args.get(0))) == 0;
            }
            if ("eq".equals(name) || "equalto".equals(name) || "==".equals(name)) {
                return Values.equal(value, args.get(0));
            }
            if ("ne".equals(name) || "!=".equals(name)) {
                return !Values.equal(value, args.get(0));
            }
            if ("in".equals(name)) {
                return Values.truthy(Values.binary("in", value, args.get(0)));
            }
            if ("lower".equals(name)) {
                return value instanceof String && value.equals(((String) value).toLowerCase(Locale.ROOT));
            }
            if ("upper".equals(name)) {
                return value instanceof String && value.equals(((String) value).toUpperCase(Locale.ROOT));
            }
            throw new TemplateException("Test " + name + " wird nicht unterstützt");
        }

        static Object method(Object receiver, String name, List<Object> args) throws TemplateException {
            if (receiver instanceof String) {
                String text = (String) receiver;
                if ("strip".equals(name)) {
                    return args.isEmpty() ? text.trim() : stripChars(text, Values.str(args.get(0)), true, true);
                }
                if ("lstrip".equals(name)) {
                    return args.isEmpty() ? stripChars(text, null, true, false)
                            : stripChars(text, Values.str(args.get(0)), true, false);
                }
                if ("rstrip".equals(name)) {
                    return args.isEmpty() ? stripChars(text, null, false, true)
                            : stripChars(text, Values.str(args.get(0)), false, true);
                }
                if ("split".equals(name)) {
                    List<Object> parts = new ArrayList<Object>();
                    if (args.isEmpty() || args.get(0) == null) {
                        for (String part : text.trim().split("\\s+")) {
                            if (!part.isEmpty()) {
                                parts.add(part);
                            }
                        }
                        return parts;
                    }
                    String separator = Values.str(args.get(0));
                    long limit = args.size() > 1 ? (long) Values.number(args.get(1)) : -1;
                    int start = 0;
                    int index;
                    while ((limit < 0 || parts.size() < limit)
                            && (index = text.indexOf(separator, start)) >= 0) {
                        parts.add(text.substring(start, index));
                        start = index + separator.length();
                    }
                    parts.add(text.substring(start));
                    return parts;
                }
                if ("rsplit".equals(name) && !args.isEmpty()) {
                    String separator = Values.str(args.get(0));
                    long limit = args.size() > 1 ? (long) Values.number(args.get(1)) : -1;
                    List<Object> parts = new ArrayList<Object>();
                    int end = text.length();
                    int index;
                    while ((limit < 0 || parts.size() < limit)
                            && (index = text.lastIndexOf(separator, end - 1)) >= 0 && end > 0) {
                        parts.add(0, text.substring(index + separator.length(), end));
                        end = index;
                    }
                    parts.add(0, text.substring(0, end));
                    return parts;
                }
                if ("startswith".equals(name)) {
                    return text.startsWith(Values.str(args.get(0)));
                }
                if ("endswith".equals(name)) {
                    return text.endsWith(Values.str(args.get(0)));
                }
                if ("lower".equals(name)) {
                    return text.toLowerCase(Locale.ROOT);
                }
                if ("upper".equals(name)) {
                    return text.toUpperCase(Locale.ROOT);
                }
                if ("title".equals(name)) {
                    return title(text);
                }
                if ("capitalize".equals(name)) {
                    return filter("capitalize", text, args, Collections.<String, Object>emptyMap());
                }
                if ("replace".equals(name)) {
                    return text.replace(Values.str(args.get(0)), Values.str(args.get(1)));
                }
                if ("join".equals(name)) {
                    return joinWith(Values.iterate(args.get(0)), text);
                }
                if ("find".equals(name)) {
                    return (long) text.indexOf(Values.str(args.get(0)));
                }
                if ("count".equals(name)) {
                    String needle = Values.str(args.get(0));
                    long count = 0;
                    for (int i = text.indexOf(needle); i >= 0 && !needle.isEmpty(); i = text.indexOf(needle, i
                            + needle.length())) {
                        count++;
                    }
                    return count;
                }
            }
            if (receiver instanceof Map || receiver instanceof Namespace) {
                Map<?, ?> map = receiver instanceof Namespace ? ((Namespace) receiver).values : (Map<?, ?>) receiver;
                if ("get".equals(name)) {
                    Object key = args.get(0);
                    if (map.containsKey(key)) {
                        return map.get(key);
                    }
                    return args.size() > 1 ? args.get(1) : null;
                }
                if ("items".equals(name)) {
                    return items(receiver);
                }
                if ("keys".equals(name)) {
                    return new ArrayList<Object>(map.keySet());
                }
                if ("values".equals(name)) {
                    return new ArrayList<Object>(map.values());
                }
            }
            if (receiver instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) receiver;
                if ("append".equals(name)) {
                    list.add(args.get(0));
                    return null;
                }
                if ("index".equals(name)) {
                    for (int i = 0; i < list.size(); i++) {
                        if (Values.equal(list.get(i), args.get(0))) {
                            return (long) i;
                        }
                    }
                    throw new TemplateException("index: nicht gefunden");
                }
            }
            if (receiver == UNDEFINED) {
                throw new TemplateException("Methode " + name + " auf undefiniertem Wert");
            }
            throw new TemplateException("Methode " + name + " wird nicht unterstützt");
        }

        private static String stripChars(String text, String chars, boolean left, boolean right) {
            int start = 0;
            int end = text.length();
            while (left && start < end && (chars == null ? Character.isWhitespace(text.charAt(start))
                    : chars.indexOf(text.charAt(start)) >= 0)) {
                start++;
            }
            while (right && end > start && (chars == null ? Character.isWhitespace(text.charAt(end - 1))
                    : chars.indexOf(text.charAt(end - 1)) >= 0)) {
                end--;
            }
            return text.substring(start, end);
        }
    }

    /** {@code tojson} wie transformers: {@code json.dumps(ensure_ascii=False)} mit Pythons Trennzeichen. */
    static final class JsonText {
        private JsonText() {
        }

        static String write(Object value, int indent, boolean sortKeys) {
            StringBuilder out = new StringBuilder();
            write(value, indent, sortKeys, 0, out);
            return out.toString();
        }

        private static void write(Object value, int indent, boolean sortKeys, int depth, StringBuilder out) {
            if (value == null || value == UNDEFINED) {
                out.append("null");
            } else if (value instanceof Boolean) {
                out.append((Boolean) value ? "true" : "false");
            } else if (value instanceof Number) {
                out.append(Values.repr(value, false));
            } else if (value instanceof String) {
                quote((String) value, out);
            } else if (value instanceof Map || value instanceof Namespace) {
                Map<?, ?> map = value instanceof Namespace ? ((Namespace) value).values : (Map<?, ?>) value;
                if (sortKeys) {
                    map = new TreeMap<Object, Object>(map);
                }
                if (map.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append('{');
                Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
                boolean first = true;
                while (entries.hasNext()) {
                    Map.Entry<?, ?> entry = entries.next();
                    if (!first) {
                        out.append(indent >= 0 ? "," : ", ");
                    }
                    first = false;
                    newline(indent, depth + 1, out);
                    quote(String.valueOf(entry.getKey()), out);
                    out.append(": ");
                    write(entry.getValue(), indent, sortKeys, depth + 1, out);
                }
                newline(indent, depth, out);
                out.append('}');
            } else if (value instanceof Collection) {
                Collection<?> items = (Collection<?>) value;
                if (items.isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append('[');
                boolean first = true;
                for (Object item : items) {
                    if (!first) {
                        out.append(indent >= 0 ? "," : ", ");
                    }
                    first = false;
                    newline(indent, depth + 1, out);
                    write(item, indent, sortKeys, depth + 1, out);
                }
                newline(indent, depth, out);
                out.append(']');
            } else {
                quote(String.valueOf(value), out);
            }
        }

        private static void newline(int indent, int depth, StringBuilder out) {
            if (indent < 0) {
                return;
            }
            out.append('\n');
            for (int i = 0; i < indent * depth; i++) {
                out.append(' ');
            }
        }

        private static void quote(String text, StringBuilder out) {
            out.append('"');
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
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
                    case '\b':
                        out.append("\\b");
                        break;
                    case '\f':
                        out.append("\\f");
                        break;
                    default:
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                }
            }
            out.append('"');
        }
    }
}
