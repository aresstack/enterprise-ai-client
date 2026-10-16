package com.aresstack.enterpriseai.astu.acropolis.source;

/**
 * Outcome of "Verbindung testen" for a source draft (#137): whether the configured source answered, and a message for
 * the user. The message names the kind of failure and the adapter's own error text, which adapters keep free of
 * credentials; it never contains setting values.
 */
public final class SourceConnectionCheck {

    private final boolean successful;
    private final String message;

    private SourceConnectionCheck(boolean successful, String message) {
        this.successful = successful;
        this.message = message == null ? "" : message;
    }

    public static SourceConnectionCheck success(String message) {
        return new SourceConnectionCheck(true, message);
    }

    public static SourceConnectionCheck failure(String message) {
        return new SourceConnectionCheck(false, message);
    }

    public boolean successful() {
        return successful;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return (successful ? "success: " : "failure: ") + message;
    }
}
