package com.aresstack.enterpriseai.integration.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest die Textantwort von {@code search_knowledge} (AP20) so weit, wie der Testagent sie braucht: je Treffer die
 * Kopfzeile {@code [n] Titel – Überschrift} und den Textauszug hinter {@code Text:}. Alles andere (Id, Chunk,
 * Quelle, Score) wird überlesen.
 */
final class SearchKnowledgeOutput {

    private static final Pattern HEAD = Pattern.compile("^\\[(\\d+)\\] (.*)$");
    private static final String TEXT_MARKER = "Text:";

    private SearchKnowledgeOutput() {
    }

    static List<Hit> parse(String output) {
        if (output == null || output.isEmpty()) {
            return Collections.emptyList();
        }
        List<Hit> hits = new ArrayList<Hit>();
        String title = null;
        StringBuilder snippet = null;
        boolean inText = false;
        for (String line : output.split("\n", -1)) {
            Matcher head = HEAD.matcher(line);
            if (head.matches()) {
                flush(hits, title, snippet);
                title = head.group(2).trim();
                snippet = new StringBuilder();
                inText = false;
                continue;
            }
            if (title == null) {
                continue;
            }
            if (TEXT_MARKER.equals(line.trim())) {
                inText = true;
                continue;
            }
            if (inText) {
                if (line.trim().isEmpty() || line.startsWith("… ")) {
                    inText = false;
                    continue;
                }
                if (snippet.length() > 0) {
                    snippet.append(' ');
                }
                snippet.append(line.trim());
            }
        }
        flush(hits, title, snippet);
        return hits;
    }

    private static void flush(List<Hit> hits, String title, StringBuilder snippet) {
        if (title != null) {
            hits.add(new Hit(title, snippet == null ? "" : snippet.toString()));
        }
    }

    static final class Hit {
        private final String title;
        private final String snippet;

        Hit(String title, String snippet) {
            this.title = title;
            this.snippet = snippet;
        }

        String title() {
            return title;
        }

        String snippet() {
            return snippet;
        }

        @Override
        public String toString() {
            return "[" + title + "] " + snippet;
        }
    }
}
