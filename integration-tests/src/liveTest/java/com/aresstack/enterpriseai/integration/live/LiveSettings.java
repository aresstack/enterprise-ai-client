package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.embedding.openai.BearerTokenSource;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Parameter des getrennten Laufs gegen echte Dienste ({@code ./gradlew :integration-tests:liveTest -Dlive.…}).
 * Adressen und Namen kommen als System-Properties, Secrets ausschließlich aus Umgebungsvariablen; fehlt ein
 * Parameter, wird der Test übersprungen (Assume), nie rot. Werte werden nirgends ausgegeben.
 *
 * <p>Die Stufen der Live-Verifikation (1 bis 7) wählt der Gradle-Task über {@code -Dlive.stage}; jede Ausgabe
 * eines Live-Tests beginnt mit {@code [live] Stufe N:} ({@link #report(int, String)}), damit der Auftraggeber
 * genau diese Zeilen zurückmelden kann.
 */
final class LiveSettings {

    static final String API_KEY_ENV = "ENTERPRISE_AI_LIVE_API_KEY";
    static final String WIKI_PASSWORD_ENV = "ENTERPRISE_AI_LIVE_WIKI_PASSWORD";
    static final String KEEPASS_PAIRING_ENV = "ENTERPRISE_AI_LIVE_KEEPASS_PAIRING";

    /** Ein Testkörper, der Exceptions werfen darf (für {@link #withoutSecretLeak}). */
    interface Body {
        void run() throws Exception;
    }

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

    /** @return der Wert oder {@code null}, wenn der Parameter fehlt */
    static Integer optionalInteger(String key) {
        String value = optional(key);
        return value == null ? null : Integer.valueOf(Integer.parseInt(value));
    }

    static boolean flag(String key) {
        return Boolean.parseBoolean(optional(key));
    }

    /** @return ob die Umgebungsvariable gesetzt und nicht leer ist (ohne den Wert zu lesen) */
    static boolean hasSecret(String environmentVariable) {
        String value = System.getenv(environmentVariable);
        return value != null && !value.isEmpty();
    }

    /** Ein Secret aus der Umgebung; die Kopie gehört dem Aufrufer (nach Gebrauch löschen). */
    static char[] secret(String environmentVariable) {
        assumeTrue("Live-Test übersprungen: Umgebungsvariable " + environmentVariable + " fehlt",
                hasSecret(environmentVariable));
        return System.getenv(environmentVariable).toCharArray();
    }

    /** Der API-Key der Enterprise-API, je Request frisch aus der Umgebung gelesen (nie in einem Feld). */
    static BearerTokenSource apiKey() {
        assumeTrue("Live-Test übersprungen: Umgebungsvariable " + API_KEY_ENV + " fehlt", hasSecret(API_KEY_ENV));
        return () -> System.getenv(API_KEY_ENV).toCharArray();
    }

    /** Base-URL für {@code /embeddings}: {@code live.embedding.baseUrl}, sonst {@code live.chat.baseUrl}. */
    static String embeddingBaseUrl() {
        String baseUrl = optional("live.embedding.baseUrl");
        return baseUrl != null ? baseUrl : required("live.chat.baseUrl");
    }

    /** Die eine Ausgabeform der Live-Tests; nie URL, Token, Hostname oder Antworttext hineingeben. */
    static void report(int stage, String message) {
        System.out.println("[live] Stufe " + stage + ": " + message);
    }

    /**
     * Führt den Testkörper aus und prüft bei jeder Exception (auch Assertion-Fehlern), dass ihre Meldung und die
     * Meldungen aller Ursachen keines der Secrets enthalten; sonst wird die Exception durch eine geschwärzte
     * ersetzt. So landet ein Secret nie in der JUnit- oder Gradle-Ausgabe, selbst wenn ein Adapter es wider
     * Erwarten in eine Meldung schreibt.
     */
    static void withoutSecretLeak(Body body, String... environmentVariables) throws Exception {
        try {
            body.run();
        } catch (Throwable thrown) {
            for (String variable : environmentVariables) {
                String secret = System.getenv(variable);
                if (secret == null || secret.isEmpty()) {
                    continue;
                }
                for (Throwable current = thrown; current != null; current = current.getCause()) {
                    String message = current.getMessage();
                    if (message != null && message.contains(secret)) {
                        fail("Secret aus " + variable + " (Länge " + secret.length() + ") taucht in der Meldung einer "
                                + current.getClass().getSimpleName() + " auf; Ursprung: "
                                + thrown.getClass().getSimpleName() + " (Meldung geschwärzt)");
                    }
                }
            }
            if (thrown instanceof Exception) {
                throw (Exception) thrown;
            }
            throw (Error) thrown;
        }
    }
}
