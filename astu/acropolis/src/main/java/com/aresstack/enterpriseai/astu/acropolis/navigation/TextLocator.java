package com.aresstack.enterpriseai.astu.acropolis.navigation;

/**
 * Findet Passagen in der kanonischen Textfassung (FA-05): verglichen werden nur Buchstaben und Ziffern in
 * Kleinschreibung, so stören weder Markdown-Zeichen ({@code **}, {@code |}, {@code -}) noch zusammengezogener
 * Leerraum. Gesucht wird vorwärts ab einer Position, damit gleichlautende Stellen in Lesereihenfolge zugeordnet
 * werden.
 */
final class TextLocator {

    /** So viele normalisierte Zeichen vom Passagenanfang reichen für einen eindeutigen Treffer. */
    static final int PROBE_LENGTH = 48;
    private static final int SHORT_PROBE_LENGTH = 20;

    private final String text;
    private final String normalized;
    /** Für jedes Zeichen von {@link #normalized} seine Position im Originaltext. */
    private final int[] origin;

    TextLocator(String text) {
        this.text = text == null ? "" : text;
        StringBuilder out = new StringBuilder(this.text.length());
        int[] positions = new int[this.text.length()];
        int count = 0;
        for (int i = 0; i < this.text.length(); i++) {
            char c = this.text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
                positions[count++] = i;
            }
        }
        this.normalized = out.toString();
        this.origin = java.util.Arrays.copyOf(positions, count);
    }

    /** Ein Treffer im Originaltext: Beginn (inklusive) und Ende (exklusiv). */
    static final class Span {
        final int start;
        final int end;

        Span(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * @param from Position im Originaltext, ab der gesucht wird
     * @return die Stelle der Passage oder {@code null}, wenn sie nicht vorkommt
     */
    Span find(String passage, int from) {
        String probe = normalize(passage);
        if (probe.isEmpty() || normalized.isEmpty()) {
            return null;
        }
        int fromIndex = normalizedIndexAt(Math.max(0, from));
        int hit = indexOf(probe, PROBE_LENGTH, fromIndex);
        if (hit < 0) {
            hit = indexOf(probe, SHORT_PROBE_LENGTH, fromIndex);
        }
        if (hit < 0) {
            return null;
        }
        int lastNormalized = Math.min(normalized.length(), hit + probe.length()) - 1;
        return new Span(origin[hit], origin[Math.max(hit, lastNormalized)] + 1);
    }

    private int indexOf(String probe, int length, int fromIndex) {
        String head = probe.length() <= length ? probe : probe.substring(0, length);
        return normalized.indexOf(head, fromIndex);
    }

    /** Index in {@link #normalized} des ersten Zeichens an oder nach {@code position}. */
    private int normalizedIndexAt(int position) {
        int low = 0;
        int high = origin.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (origin[mid] < position) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    /** Die ersten Worte ab {@code start} (höchstens {@code maxChars} Zeichen), als Anker für die Ansicht. */
    String anchorAt(int start, int maxChars) {
        int end = Math.min(text.length(), start + maxChars);
        String slice = text.substring(Math.min(start, text.length()), end);
        int newline = slice.indexOf('\n');
        if (newline > 0) {
            slice = slice.substring(0, newline);
        }
        return slice.trim();
    }
}
