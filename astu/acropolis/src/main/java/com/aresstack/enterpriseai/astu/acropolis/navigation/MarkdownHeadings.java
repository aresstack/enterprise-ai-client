package com.aresstack.enterpriseai.astu.acropolis.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Originalüberschriften der kanonischen Textfassung ({@code KnowledgeDocument}: Überschriften als
 * {@code #}-Zeilen, Code in Zäunen) mit Ebene und Zeichenposition. Dieselbe Erkennung wie
 * {@code PassageSegmentation.blocksOf(String)}; Zeilen in Code-Zäunen sind nie Überschriften.
 */
final class MarkdownHeadings {

    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})[ \\t]+(.*?)[ \\t#]*$");

    /** Eine Überschrift: Ebene 1–6, Text, Beginn der Zeile, Ende der Zeile (exklusiv). */
    static final class Heading {
        final int level;
        final String text;
        final int start;
        final int lineEnd;

        Heading(int level, String text, int start, int lineEnd) {
            this.level = level;
            this.text = text;
            this.start = start;
            this.lineEnd = lineEnd;
        }
    }

    private MarkdownHeadings() {
    }

    static List<Heading> of(String text) {
        List<Heading> headings = new ArrayList<Heading>();
        if (text == null) {
            return headings;
        }
        boolean inCode = false;
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? text.length() : newline;
            String line = text.substring(lineStart, lineEnd);
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inCode = !inCode;
            } else if (!inCode) {
                Matcher heading = HEADING.matcher(line);
                if (heading.matches() && !heading.group(2).trim().isEmpty()) {
                    headings.add(new Heading(heading.group(1).length(), heading.group(2).trim(), lineStart,
                            lineEnd));
                }
            }
            if (newline < 0) {
                break;
            }
            lineStart = newline + 1;
        }
        return headings;
    }
}
