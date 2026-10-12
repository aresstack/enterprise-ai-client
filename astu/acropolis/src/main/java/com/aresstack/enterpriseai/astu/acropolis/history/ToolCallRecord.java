package com.aresstack.enterpriseai.astu.acropolis.history;

/**
 * Gespeicherter Werkzeugaufruf einer Antwort ({@code knowledge_search}, {@code knowledge_read},
 * {@code read_attachment} …): Name, Argumente des Modells, ob er gelang, und der Anfang des Ergebnisses (gekürzt,
 * die volle Länge steht daneben). Für die Diagnose (Chat-Export); geht nicht zurück in den Modellverlauf.
 */
public final class ToolCallRecord {

    /** Längstes gespeichertes Argument bzw. längster Ergebnisanfang in Zeichen. */
    public static final int MAX_ARGUMENTS = 4000;
    public static final int MAX_RESULT = 2000;

    private String name;
    private String arguments;
    private boolean succeeded;
    private String result;
    private int resultLength;
    /** Zeitpunkt, zu dem das Ergebnis vorlag. */
    private long finishedAt;

    /** Für die Deserialisierung. */
    public ToolCallRecord() {
    }

    public ToolCallRecord(String name, String arguments, boolean succeeded, String result,
                          long finishedAt) {
        this.name = name;
        this.arguments = cut(arguments, MAX_ARGUMENTS);
        this.succeeded = succeeded;
        this.result = cut(result, MAX_RESULT);
        this.resultLength = result == null ? 0 : result.length();
        this.finishedAt = finishedAt;
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    public String getName() {
        return name != null ? name : "";
    }

    public String getArguments() {
        return arguments != null ? arguments : "";
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public String getResult() {
        return result != null ? result : "";
    }

    public int getResultLength() {
        return resultLength;
    }

    public long getFinishedAt() {
        return finishedAt;
    }
}
