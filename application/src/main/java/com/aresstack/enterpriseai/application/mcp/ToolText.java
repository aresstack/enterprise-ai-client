package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.Collection;
import java.util.Locale;

/**
 * Textbausteine der Wissenswerkzeuge: Kürzen mit Kennzeichnung, einzeilige Kopffelder, Formatierung von Revision
 * und Scores. Alle Methoden sind frei von Zustand.
 */
final class ToolText {

    /** Zeichen, die am Ende einer gekürzten Antwort für den Marker reserviert bleiben. */
    static final int MARKER_RESERVE = 64;

    private ToolText() {
    }

    /**
     * Kürzt {@code text} auf höchstens {@code maxChars} Zeichen und hängt einen Marker an, der die Zahl der
     * ausgelassenen Zeichen nennt. Das Ergebnis ist nie länger als {@code maxChars}.
     */
    static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        int cut = Math.max(0, maxChars - MARKER_RESERVE);
        cut = adjustCut(text, cut);
        String marker = "\n… [gekürzt: " + (text.length() - cut) + " Zeichen ausgelassen]";
        if (cut + marker.length() > maxChars) {
            cut = adjustCut(text, Math.max(0, maxChars - marker.length()));
            marker = "\n… [gekürzt: " + (text.length() - cut) + " Zeichen ausgelassen]";
        }
        return text.substring(0, cut) + marker;
    }

    /** Textausschnitt für einen Treffer: hart gekürzt mit Auslassungszeichen. */
    static String snippet(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        int cut = adjustCut(text, Math.max(0, maxChars - 2));
        return text.substring(0, cut) + " …";
    }

    /** Für Kopfzeilen: Zeilenumbrüche und Steuerzeichen werden zu Leerzeichen, Länge begrenzt. */
    static String oneLine(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        StringBuilder line = new StringBuilder(Math.min(value.length(), maxChars));
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            line.append(Character.isISOControl(c) ? ' ' : c);
        }
        return snippet(line.toString().trim(), maxChars);
    }

    static String revision(KnowledgeRevision revision) {
        if (revision == null || !revision.isKnown()) {
            return "unbekannt";
        }
        StringBuilder text = new StringBuilder();
        if (revision.modifiedAt().isPresent()) {
            text.append(revision.modifiedAt().get());
        }
        if (!revision.version().isEmpty()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append("Version ").append(oneLine(revision.version(), 80));
        }
        return text.toString();
    }

    static String score(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    static String join(Collection<KnowledgeSourceId> ids) {
        StringBuilder text = new StringBuilder();
        for (KnowledgeSourceId id : ids) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(id.value());
        }
        return text.toString();
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Kein Schnitt mitten in einem Surrogatpaar. */
    private static int adjustCut(String text, int cut) {
        if (cut > 0 && cut < text.length() && Character.isHighSurrogate(text.charAt(cut - 1))) {
            return cut - 1;
        }
        return cut;
    }
}
