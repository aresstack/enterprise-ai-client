package com.aresstack.enterpriseai.app.ui.settings;

/**
 * Ein Schritt des Verbindungstests („Verbindung zum KI-Dienst prüfen“): Titel, Ausgang und eine Zeile Detail.
 * Reine Daten für die Oberfläche; die Details enthalten nie Secrets (der Test nennt den API-Key nicht und
 * maskiert Tokens in fremden Texten).
 */
public final class ConnectionCheckStep {

    /** Ausgang eines Schritts; nur {@link #FAILED} beendet den Test. */
    public enum Status {
        OK, WARNING, FAILED, INFO
    }

    private final String title;
    private final Status status;
    private final String detail;

    private ConnectionCheckStep(String title, Status status, String detail) {
        if (title == null || title.trim().isEmpty() || status == null) {
            throw new IllegalArgumentException("title and status must not be empty");
        }
        this.title = title.trim();
        this.status = status;
        this.detail = detail == null ? "" : detail.trim();
    }

    public static ConnectionCheckStep ok(String title, String detail) {
        return new ConnectionCheckStep(title, Status.OK, detail);
    }

    public static ConnectionCheckStep warning(String title, String detail) {
        return new ConnectionCheckStep(title, Status.WARNING, detail);
    }

    public static ConnectionCheckStep failed(String title, String detail) {
        return new ConnectionCheckStep(title, Status.FAILED, detail);
    }

    public static ConnectionCheckStep info(String title, String detail) {
        return new ConnectionCheckStep(title, Status.INFO, detail);
    }

    public String title() {
        return title;
    }

    public Status status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public boolean isFailure() {
        return status == Status.FAILED;
    }

    @Override
    public String toString() {
        return title + ": " + status + (detail.isEmpty() ? "" : " - " + detail);
    }
}
