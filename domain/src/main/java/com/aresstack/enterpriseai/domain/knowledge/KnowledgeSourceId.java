package com.aresstack.enterpriseai.domain.knowledge;

import java.util.regex.Pattern;

/**
 * Identität einer konfigurierten Wissensquelle (z. B. {@code wiki-intranet} oder {@code confluence-dc}).
 *
 * <p>Die ID ist ein frei gewählter, stabiler Schlüssel aus der Konfiguration, kein technischer Endpunkt: Sie
 * enthält weder URL noch Zugangsdaten und darf deshalb geloggt und indexiert werden. Erlaubt sind
 * ASCII-Buchstaben, Ziffern sowie {@code . _ : -}, beginnend mit Buchstabe oder Ziffer, höchstens 128 Zeichen.
 */
public final class KnowledgeSourceId {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final String value;

    private KnowledgeSourceId(String value) {
        this.value = value;
    }

    public static KnowledgeSourceId of(String value) {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Ungültige Knowledge-Source-ID: " + quote(value)
                    + " (erlaubt: [A-Za-z0-9][A-Za-z0-9._:-]{0,127})");
        }
        return new KnowledgeSourceId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof KnowledgeSourceId && value.equals(((KnowledgeSourceId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }

    static String quote(String value) {
        return value == null ? "null" : "'" + value + "'";
    }
}
