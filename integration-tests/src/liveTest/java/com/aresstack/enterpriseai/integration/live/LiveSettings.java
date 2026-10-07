package com.aresstack.enterpriseai.integration.live;

import static org.junit.Assume.assumeTrue;

/**
 * Parameter des getrennten Laufs gegen echte Dienste ({@code ./gradlew :integration-tests:liveTest -Dlive.…}).
 * Adressen und Namen kommen als System-Properties, Secrets ausschließlich aus Umgebungsvariablen; fehlt ein
 * Parameter, wird der Test übersprungen (Assume), nie rot. Werte werden nirgends ausgegeben.
 */
final class LiveSettings {

    static final String API_KEY_ENV = "ENTERPRISE_AI_LIVE_API_KEY";
    static final String WIKI_PASSWORD_ENV = "ENTERPRISE_AI_LIVE_WIKI_PASSWORD";
    static final String KEEPASS_PAIRING_ENV = "ENTERPRISE_AI_LIVE_KEEPASS_PAIRING";

    private LiveSettings() {
    }

    static String optional(String key) {
        String value = System.getProperty(key);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    static String required(String key) {
        String value = optional(key);
        assumeTrue("Live-Test übersprungen: -D" + key + " fehlt", value != null);
        return value;
    }

    static int integer(String key, int fallback) {
        String value = optional(key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    static boolean flag(String key) {
        return Boolean.parseBoolean(optional(key));
    }

    /** Ein Secret aus der Umgebung; die Kopie gehört dem Aufrufer (nach Gebrauch löschen). */
    static char[] secret(String environmentVariable) {
        String value = System.getenv(environmentVariable);
        assumeTrue("Live-Test übersprungen: Umgebungsvariable " + environmentVariable + " fehlt",
                value != null && !value.isEmpty());
        return value.toCharArray();
    }
}
