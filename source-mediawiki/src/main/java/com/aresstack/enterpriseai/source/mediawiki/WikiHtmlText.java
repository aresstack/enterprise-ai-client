package com.aresstack.enterpriseai.source.mediawiki;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Überführt das von {@code action=parse} gelieferte HTML in indexierbaren Klartext (paketintern).
 *
 * <p>Die Bereinigung (Inhaltsverzeichnis, Bearbeiten-Links, Skripte, Styles, unsichtbare Elemente, Bilder
 * samt Rahmen) ist aus MainframeMate {@code HtmlPostProcessor} übernommen. Statt des dortigen
 * Regex-Tag-Strippings ({@code WikiSourceScanner.stripHtml}) läuft die Textgewinnung über den jsoup-Baum und
 * erhält Struktur für das Chunking: Überschriften als Markdown-Zeilen ({@code ## Titel}), Absätze durch
 * Leerzeilen getrennt, Listenpunkte mit {@code - }, Tabellenzeilen mit {@code |} zwischen den Zellen.
 */
final class WikiHtmlText {

    private WikiHtmlText() {
    }

    static String toText(String html) {
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);
        clean(doc);
        TextBuilder out = new TextBuilder();
        renderChildren(doc.body(), out);
        return out.result();
    }

    private static void clean(Document doc) {
        doc.select("#toc, .toc, .mw-toc, .mw-editsection, script, style, noscript").remove();
        doc.select(".mw-empty-elt, .noprint, .mw-indicators, .mw-jump-link, .catlinks").remove();
        doc.select(".thumb, .thumbinner, figure, .mw-file-element, img").remove();
        doc.select("sup.reference").remove();
        doc.select("[style~=(?i)display:\\s*none]").remove();
    }

    private static void renderChildren(Element parent, TextBuilder out) {
        for (Node child : parent.childNodes()) {
            if (child instanceof TextNode) {
                out.inline(((TextNode) child).text());
            } else if (child instanceof Element) {
                render((Element) child, out);
            }
        }
    }

    private static void render(Element el, TextBuilder out) {
        String tag = el.tagName().toLowerCase(java.util.Locale.ROOT);
        if (tag.length() == 2 && tag.charAt(0) == 'h' && tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
            String text = el.text().trim();
            if (!text.isEmpty()) {
                out.block();
                out.line(repeat('#', tag.charAt(1) - '0') + " " + text);
                out.block();
            }
        } else if ("br".equals(tag)) {
            out.newline();
        } else if ("li".equals(tag)) {
            out.newline();
            out.inline("- ");
            renderChildren(el, out);
            out.newline();
        } else if ("table".equals(tag)) {
            out.block();
            for (Element row : el.select("tr")) {
                StringBuilder line = new StringBuilder();
                for (Element cell : row.select("> th, > td")) {
                    String text = cell.text().trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    if (line.length() > 0) {
                        line.append(" | ");
                    }
                    line.append(text);
                }
                if (line.length() > 0) {
                    out.line(line.toString());
                }
            }
            out.block();
        } else if ("pre".equals(tag)) {
            out.block();
            out.raw(el.wholeText());
            out.block();
        } else if (isBlock(tag)) {
            out.block();
            renderChildren(el, out);
            out.block();
        } else {
            renderChildren(el, out);
        }
    }

    private static boolean isBlock(String tag) {
        return "p".equals(tag) || "div".equals(tag) || "section".equals(tag) || "blockquote".equals(tag)
                || "ul".equals(tag) || "ol".equals(tag) || "dl".equals(tag) || "dt".equals(tag)
                || "dd".equals(tag) || "center".equals(tag) || "figcaption".equals(tag) || "hr".equals(tag);
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    /** Sammelt Text zeilenweise; normalisiert Leerraum innerhalb von Zeilen und Leerzeilen zwischen Blöcken. */
    private static final class TextBuilder {

        private final StringBuilder text = new StringBuilder();
        private final StringBuilder current = new StringBuilder();

        void inline(String value) {
            current.append(value);
        }

        void line(String value) {
            newline();
            current.append(value);
            newline();
        }

        void raw(String value) {
            newline();
            for (String l : value.split("\r?\n", -1)) {
                appendLine(rtrim(l));
            }
        }

        void newline() {
            String normalized = current.toString().replaceAll("[ \\t\\u00A0]+", " ").trim();
            current.setLength(0);
            if (!normalized.isEmpty()) {
                appendLine(normalized);
            }
        }

        void block() {
            newline();
            if (text.length() > 0 && !endsWithBlankLine()) {
                text.append('\n');
            }
        }

        private void appendLine(String value) {
            text.append(value).append('\n');
        }

        private boolean endsWithBlankLine() {
            int n = text.length();
            return n >= 2 && text.charAt(n - 1) == '\n' && text.charAt(n - 2) == '\n';
        }

        String result() {
            newline();
            return text.toString().replaceAll("\n{3,}", "\n\n").trim();
        }

        private static String rtrim(String s) {
            int end = s.length();
            while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
                end--;
            }
            return s.substring(0, end);
        }
    }
}
