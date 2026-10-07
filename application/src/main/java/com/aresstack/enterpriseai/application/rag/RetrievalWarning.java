package com.aresstack.enterpriseai.application.rag;

/**
 * Ein Suchpfad ist ausgefallen, das Ergebnis stammt nur aus dem anderen. Die Meldung stammt aus den
 * Port-Ausnahmen, die laut Vertrag keine Secrets enthalten; sie ist für Anzeige und Log gedacht.
 */
public final class RetrievalWarning {

    private final RetrievalPath path;
    private final String message;

    public RetrievalWarning(RetrievalPath path, String message) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        this.path = path;
        this.message = message == null ? "" : message;
    }

    public RetrievalPath path() {
        return path;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return "RetrievalWarning{" + path + ": " + message + "}";
    }
}
