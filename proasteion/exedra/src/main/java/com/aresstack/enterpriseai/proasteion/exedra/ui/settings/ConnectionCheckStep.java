package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

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
    private final boolean temporary;

    private ConnectionCheckStep(String title, Status status, String detail, boolean temporary) {
        if (title == null || title.trim().isEmpty() || status == null) {
            throw new IllegalArgumentException("title and status must not be empty");
        }
        this.title = title.trim();
        this.status = status;
        this.detail = detail == null ? "" : detail.trim();
        this.temporary = temporary;
    }

    private ConnectionCheckStep(String title, Status status, String detail) {
        this(title, status, detail, false);
    }

    public static ConnectionCheckStep of(String title, Status status, String detail) {
        return new ConnectionCheckStep(title, status, detail);
    }

    /** Derselbe Schritt mit anderem Detail; die Einstufung „vorübergehend“ bleibt. */
    public ConnectionCheckStep withDetail(String newDetail) {
        return new ConnectionCheckStep(title, status, newDetail, temporary);
    }

    /**
     * Derselbe Schritt, als vorübergehende Störung eingestuft (KeePass kurz nicht erreichbar, Verbindungsabbruch,
     * Zeitüberschreitung, HTTP 5xx): ein späterer Versuch kann gelingen, ohne dass jemand etwas ändert.
     */
    public ConnectionCheckStep asTemporary() {
        return new ConnectionCheckStep(title, status, detail, true);
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

    /** Ob die Störung vorübergehend ist ({@link #asTemporary()}); ohne Bedeutung für {@link Status#OK}. */
    public boolean isTemporary() {
        return temporary;
    }

    @Override
    public String toString() {
        return title + ": " + status + (detail.isEmpty() ? "" : " - " + detail);
    }
}
