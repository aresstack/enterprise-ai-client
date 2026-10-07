package com.aresstack.enterpriseai.app.ui.agent;

/** Die Betriebsarten der Shell. */
public enum ShellMode {
    /** Normaler Chat mit dem Enterprise-Modell (ohne ACP und MCP). */
    CHAT("Chat"),
    /** Optionaler Agent-Modus: ein externer Agent über ACP. */
    AGENT("Agent");

    private final String label;

    ShellMode(String label) {
        this.label = label;
    }

    /** Beschriftung in der Oberfläche. */
    public String label() {
        return label;
    }
}
