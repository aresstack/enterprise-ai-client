package com.aresstack.enterpriseai.proasteion.application.composition;

/**
 * Wo ein Werkzeug des {@link ToolCatalog} angeboten wird. {@link #AGENT} heißt: am MCP-Endpoint jedes Agenten (eigenes
 * Agent-Kommando wie eingebauter Recherche-Agent); {@link #RESEARCH_AGENT} nur am Endpoint des eingebauten
 * Recherche-Agenten.
 */
public enum ToolChannel {

    CHAT("Chat"),
    AGENT("Agent"),
    RESEARCH_AGENT("Recherche-Agent");

    private final String label;

    ToolChannel(String label) {
        this.label = label;
    }

    /** Anzeigename, z. B. „Chat“. */
    public String label() {
        return label;
    }
}
