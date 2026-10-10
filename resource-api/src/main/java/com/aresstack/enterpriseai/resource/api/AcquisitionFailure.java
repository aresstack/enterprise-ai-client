package com.aresstack.enterpriseai.resource.api;

/**
 * Die Fehlerart einer gescheiterten Beschaffung über den {@link AcquisitionPort}. Chalcotheca reicht bei einem
 * Fehler nur die Meldung weiter ({@code MediatedResult.error}); damit der Aufrufer trotzdem „nicht gefunden“ von
 * „Quelle nicht erreichbar“ unterscheiden kann, trägt die Meldung die Art in eckigen Klammern vorne, z. B.
 * {@code [NOT_FOUND] wiki:intranet/Seite}. Die Art ist ein Bezeichner ohne Leerzeichen, nie ein Wert der Quelle.
 */
public final class AcquisitionFailure {

    private AcquisitionFailure() {
    }

    public static String describe(String kind, String detail) {
        return "[" + kind + "] " + (detail == null ? "" : detail);
    }

    /** Die Art aus einer Meldung oder {@code ""}, wenn sie keine trägt. */
    public static String kindIn(String message) {
        if (message == null) {
            return "";
        }
        int open = message.indexOf('[');
        int close = open < 0 ? -1 : message.indexOf(']', open);
        if (close <= open + 1) {
            return "";
        }
        String kind = message.substring(open + 1, close);
        for (int i = 0; i < kind.length(); i++) {
            char c = kind.charAt(i);
            if (!(c == '_' || (c >= 'A' && c <= 'Z'))) {
                return "";
            }
        }
        return kind;
    }
}
