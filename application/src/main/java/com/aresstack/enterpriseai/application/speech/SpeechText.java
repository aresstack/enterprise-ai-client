package com.aresstack.enterpriseai.application.speech;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bereitet eine Markdown-Antwort zum Vorlesen auf: Codeblöcke und Mermaid-Diagramme fallen weg, Links werden zu
 * ihrem Text, Formatierungszeichen verschwinden. {@link #chunks} teilt in Absätze und Sätze (wie die
 * Absatz-/Satz-Körnung aus askai arch, ohne NLP-Modelle), damit das erste Stück schnell erklingt.
 */
public final class SpeechText {

    /** Obergrenze je Stück; längere Sätze werden an Kommas oder Leerzeichen geteilt. */
    static final int MAX_CHUNK_CHARS = 280;

    private static final Pattern FENCE = Pattern.compile("(?s)```.*?(```|$)|~~~.*?(~~~|$)");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]*)`");
    private static final Pattern LINE_MARKERS = Pattern.compile("(?m)^\\s*(#{1,6}\\s+|>\\s?|[-*+]\\s+|\\d+[.)]\\s+)");
    private static final Pattern TABLE_RULE = Pattern.compile("(?m)^\\s*\\|?\\s*:?-{3,}.*$");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|\\*|_|~~)");
    private static final Pattern SPEAKABLE = Pattern.compile("[\\p{L}\\p{N}]");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…:;])\\s+");

    private SpeechText() {
    }

    /** Markdown → vorlesbarer Text (Absätze bleiben durch Leerzeilen getrennt). */
    public static String plainText(String markdown) {
        if (markdown == null) {
            return "";
        }
        String text = FENCE.matcher(markdown).replaceAll("\n\n");
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

    /** Absätze, darin Sätze; leere Stücke fallen weg. */
    public static List<String> chunks(String plainText) {
        List<String> chunks = new ArrayList<String>();
        if (plainText == null) {
            return chunks;
        }
        for (String paragraph : plainText.split("\\n\\s*\\n")) {
            String flat = paragraph.replace('\n', ' ').trim();
            if (flat.isEmpty()) {
                continue;
            }
            for (String sentence : SENTENCE_END.split(flat)) {
                addBounded(chunks, sentence.trim());
            }
        }
        return chunks;
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
