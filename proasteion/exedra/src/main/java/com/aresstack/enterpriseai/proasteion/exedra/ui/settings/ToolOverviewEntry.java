package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

/** Eine Zeile des Reiters „Tools“: nur Werte, keine Registry- oder Port-Typen. */
public final class ToolOverviewEntry {

    private final String name;
    private final String description;
    private final String origin;
    private final String availability;
    private final boolean enabled;

    /** Wie unten, aktiv. */
    public ToolOverviewEntry(String name, String description, String origin, String availability) {
        this(name, description, origin, availability, true);
    }

    /**
     * @param origin       Herkunft bzw. Registry, z. B. „Wissenswerkzeuge (KnowledgeMcpTools)“
     * @param availability wo das Werkzeug angeboten wird, z. B. „Chat + Agent“
     * @param enabled      aktiv laut Registry (gespeicherter Stand)
     */
    public ToolOverviewEntry(String name, String description, String origin, String availability, boolean enabled) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        this.name = name;
        this.description = description == null ? "" : description;
        this.origin = origin == null ? "" : origin;
        this.availability = availability == null ? "" : availability;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String origin() {
        return origin;
    }

    public String availability() {
        return availability;
    }
}
