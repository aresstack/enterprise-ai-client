package com.aresstack.enterpriseai.app.ui.settings;

/**
 * Ergebnis der Prüfung „Eintrag in KeePass vorhanden?“: nur ein Befund und ein lesbarer Text, nie das
 * Secret selbst (auch nicht seine Länge).
 */
public final class SecretCheckResult {

    private final boolean success;
    private final String message;

    public SecretCheckResult(boolean success, String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("message must not be empty");
        }
        this.success = success;
        this.message = message.trim();
    }

    public static SecretCheckResult ok(String message) {
        return new SecretCheckResult(true, message);
    }

    public static SecretCheckResult failed(String message) {
        return new SecretCheckResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return (success ? "OK: " : "FEHLER: ") + message;
    }
}
