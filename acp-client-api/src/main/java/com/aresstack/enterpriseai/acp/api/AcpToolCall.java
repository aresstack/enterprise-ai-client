package com.aresstack.enterpriseai.acp.api;

/**
 * Ein Werkzeugaufruf des Agenten (ACP {@code tool_call} / {@code tool_call_update}), neutral: Kennung, Titel, Art,
 * Zustand und je ein kurzer Text für Eingabe und Ergebnis. Ein Update trägt nur, was sich geändert hat; fehlende
 * Felder sind leer und der Empfänger behält dann den bekannten Wert derselben Kennung.
 */
public final class AcpToolCall {

    /** Zustand des Aufrufs (ACP {@code ToolCallStatus}). */
    public enum Status { PENDING, IN_PROGRESS, COMPLETED, FAILED }

    /**
     * {@code _meta}-Schlüssel eines {@code tool_call}, der eine eigene Art jenseits der ACP-{@code ToolKind} trägt
     * (z. B. {@code research} für die Recherche-Karte); auf der Leitung steht dann {@code kind: other}.
     */
    public static final String META_TOOL_KIND = "aresstack/toolKind";

    private final String id;
    private final String title;
    private final String kind;
    private final Status status;
    private final String input;
    private final String output;

    public AcpToolCall(String id, String title, String kind, Status status, String input, String output) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.kind = kind == null ? "" : kind;
        this.status = status;
        this.input = input == null ? "" : input;
        this.output = output == null ? "" : output;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    /** ACP {@code ToolKind} in Kleinbuchstaben ({@code search}, {@code read}, {@code think} …), leer wenn unbekannt. */
    public String getKind() { return kind; }
    /** {@code null}, wenn das Update keinen Zustand nennt. */
    public Status getStatus() { return status; }
    public String getInput() { return input; }
    public String getOutput() { return output; }

    public boolean isFinished() {
        return status == Status.COMPLETED || status == Status.FAILED;
    }

    @Override
    public String toString() {
        // Ohne Eingabe und Ergebnis: die können Inhalte aus Quellen tragen.
        return "AcpToolCall[" + id + ", " + kind + ", " + status + "]";
    }
}
