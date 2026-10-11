package com.aresstack.enterpriseai.astu.acropolis.agent;

/**
 * Ein Werkzeugaufruf des Agenten, wie die Oberfläche ihn zeigt: Kennung, Titel, Art ({@code search}, {@code read},
 * {@code think} …), Zustand, Eingabe und Ergebnis als kurze Texte. Updates derselben Kennung ergänzen einander; ein
 * leeres Feld heißt „unverändert“.
 */
public final class AgentToolActivity {

    /** Lebenszyklus eines Aufrufs. */
    public enum Status { RUNNING, COMPLETED, FAILED }

    private final String id;
    private final String title;
    private final String kind;
    private final Status status;
    private final String input;
    private final String output;

    public AgentToolActivity(String id, String title, String kind, Status status, String input, String output) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.kind = kind == null ? "" : kind;
        this.status = status == null ? Status.RUNNING : status;
        this.input = input == null ? "" : input;
        this.output = output == null ? "" : output;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String kind() {
        return kind;
    }

    public Status status() {
        return status;
    }

    public String input() {
        return input;
    }

    public String output() {
        return output;
    }

    /** Ein Denkschritt des Modells (grüne Gedankenblase), kein Werkzeug (gelbe Blase). */
    public boolean isThinking() {
        return "think".equals(kind);
    }

    @Override
    public String toString() {
        return "AgentToolActivity[" + id + ", " + kind + ", " + status + "]";
    }
}
