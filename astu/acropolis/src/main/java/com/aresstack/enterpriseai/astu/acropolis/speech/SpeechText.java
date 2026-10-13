package com.aresstack.enterpriseai.astu.acropolis.speech;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bereitet eine Markdown-Antwort zum Vorlesen auf: Codeblöcke und Mermaid-Diagramme fallen weg, Links werden zu
 * ihrem Text, Formatierungszeichen verschwinden. {@link #chunks(String, SpeechChunking)} teilt nach der gewählten
 * {@link SpeechChunking} (Absatz-/Satz-Körnung aus askai arch; ohne Textanalyse ein einfacher Schnitt an Satzzeichen, mit ihr
 * schneidet {@link TextAnalysisService} die Sätze über OpenNLP).
 */
public final class SpeechText {

    /** Obergrenze je Satz; längere Sätze werden an Kommas oder Leerzeichen geteilt. */
    static final int MAX_CHUNK_CHARS = 280;

    /**
     * Obergrenze je Absatz- oder Antwort-Stück: längere werden an Satzgrenzen geteilt, damit eine Äußerung im
     * Token-Rahmen der Stimme bleibt und das erste Stück nicht unnötig lange rechnet.
     */
    static final int MAX_UNIT_CHARS = 1200;

    private static final Pattern FENCE = Pattern.compile("(?s)```.*?(```|$)|~~~.*?(~~~|$)");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]*)`");
    // Match horizontal whitespace only: \s would also swallow the blank line before a heading or list item and
    // merge the paragraphs that PARAGRAPHS chunking relies on. Drop a table rule together with its own line end.
    private static final Pattern LINE_MARKERS = Pattern.compile(
            "(?m)^[ \\t]*(#{1,6}[ \\t]+|>[ \\t]?|[-*+][ \\t]+|\\d+[.)][ \\t]+)");
    private static final Pattern TABLE_RULE = Pattern.compile("(?m)^[ \\t]*\\|?[ \\t]*:?-{3,}[^\\n]*\\n?");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|\\*|_|~~)");
    private static final Pattern SPEAKABLE = Pattern.compile("[\\p{L}\\p{N}]");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…:;])\\s+");

    /** Ein Bild, auch als Link verpackt ({@code [![alt](bild)](ziel)}): fällt mitsamt Alternativtext weg. */
    private static final Pattern LINKED_IMAGE = Pattern.compile("\\[\\s*!\\[[^\\]]*\\]\\([^)]*\\)\\s*\\]\\([^)]*\\)");
    /** Bild-Referenzen ({@code ![alt][ref]}) sind ebenfalls nur Darstellung. */
    private static final Pattern REFERENCE_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\[[^\\]]*\\]");
    /** Eingebettetes HTML für Grafiken: Bild, SVG, Bildunterschrift, Canvas, Objekt. */
    private static final Pattern HTML_VISUAL = Pattern.compile(
            "(?is)<(svg|figure|figcaption|canvas|object|picture)\\b.*?</\\1\\s*>|<(img|embed|source)\\b[^>]*>");

    private SpeechText() {
    }

    /**
     * Entfernt alles rein Visuelle aus einem Seitentext, bevor er vorgelesen wird (Lesebereich): Bilder samt
     * Alternativtext und Bildunterschrift, eingebettete Grafiken, Diagramm- und Codeblöcke (Mermaid, Draw.io usw.).
     * Überschriften, Absätze, Listen und Tabellen bleiben stehen; {@link #plainText} bereitet das Ergebnis wie im Chat
     * weiter auf.
     */
    public static String withoutVisuals(String markdown) {
        if (markdown == null) {
            return "";
        }
        String text = FENCE.matcher(markdown.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("\n\n");
        text = HTML_VISUAL.matcher(text).replaceAll("");
        text = LINKED_IMAGE.matcher(text).replaceAll("");
        text = IMAGE.matcher(text).replaceAll("");
        text = REFERENCE_IMAGE.matcher(text).replaceAll("");
        return text.replaceAll("\\n{3,}", "\n\n").trim();
    }

    /** Markdown → vorlesbarer Text (Absätze bleiben durch Leerzeilen getrennt). */
    public static String plainText(String markdown) {
        if (markdown == null) {
            return "";
        }
        String text = FENCE.matcher(markdown.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("\n\n");
        text = IMAGE.matcher(text).replaceAll("$1");
        text = LINK.matcher(text).replaceAll("$1");
        text = URL.matcher(text).replaceAll("");
        text = INLINE_CODE.matcher(text).replaceAll("$1");
        text = TABLE_RULE.matcher(text).replaceAll("");
        text = LINE_MARKERS.matcher(text).replaceAll("");
        text = text.replace('|', ' ');
        text = EMPHASIS.matcher(text).replaceAll("");
        return text.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    /** Ein Stück je Satz; leere Stücke fallen weg. */
    public static List<String> chunks(String plainText) {
        return chunks(plainText, SpeechChunking.SENTENCES);
    }

    /** Stücke in der gewählten Einheit, in Lesereihenfolge; leere fallen weg. */
    public static List<String> chunks(String plainText, SpeechChunking chunking) {
        List<String> chunks = new ArrayList<String>();
        if (plainText == null) {
            return chunks;
        }
        List<String> unit = new ArrayList<String>();
        for (String paragraph : plainText.split("\\n\\s*\\n")) {
            String flat = paragraph.replace('\n', ' ').trim();
            if (flat.isEmpty()) {
                continue;
            }
            List<String> sentences = new ArrayList<String>();
            for (String sentence : SENTENCE_END.split(flat)) {
                addBounded(sentences, sentence.trim());
            }
            if (chunking == SpeechChunking.SENTENCES) {
                chunks.addAll(sentences);
            } else {
                unit.addAll(sentences);
                if (chunking != SpeechChunking.ANSWER) {
                    addUnits(chunks, unit);
                    unit.clear();
                }
            }
        }
        addUnits(chunks, unit);
        return chunks;
    }

    /**
     * Ein Stück der Textanalyse, begrenzt auf {@link #MAX_UNIT_CHARS} Zeichen (längere werden an Satzgrenzen geteilt,
     * damit eine Äußerung im Token-Rahmen der Stimme bleibt); ohne sprechbares Zeichen leer.
     */
    static List<String> bounded(String chunk) {
        return chunks(chunk, SpeechChunking.ANSWER);
    }

    /** Fasst Sätze zu Stücken bis {@link #MAX_UNIT_CHARS} zusammen. */
    static void addUnits(List<String> chunks, List<String> sentences) {
        StringBuilder unit = new StringBuilder();
        for (String sentence : sentences) {
            if (unit.length() > 0 && unit.length() + 1 + sentence.length() > MAX_UNIT_CHARS) {
                chunks.add(unit.toString());
                unit.setLength(0);
            }
            if (unit.length() > 0) {
                unit.append(' ');
            }
            unit.append(sentence);
        }
        if (unit.length() > 0) {
            chunks.add(unit.toString());
        }
    }

    private static void addBounded(List<String> chunks, String sentence) {
        String rest = sentence;
        while (rest.length() > MAX_CHUNK_CHARS) {
            int end;
            int comma = rest.lastIndexOf(", ", MAX_CHUNK_CHARS);
            int space = rest.lastIndexOf(' ', MAX_CHUNK_CHARS);
            if (comma >= MAX_CHUNK_CHARS / 3) {
                end = comma + 1;
            } else if (space >= MAX_CHUNK_CHARS / 3) {
                end = space;
            } else {
                end = MAX_CHUNK_CHARS;
            }
            chunks.add(rest.substring(0, end).trim());
            rest = rest.substring(end).trim();
        }
        if (hasSpeakableCharacter(rest)) {
            chunks.add(rest);
        }
    }

    private static boolean hasSpeakableCharacter(String text) {
        return SPEAKABLE.matcher(text).find();
    }
}
