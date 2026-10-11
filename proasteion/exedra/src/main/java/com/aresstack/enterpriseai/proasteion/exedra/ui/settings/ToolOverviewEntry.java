package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

/** Eine Zeile des Reiters „Tools“: nur Werte, keine Registry- oder Port-Typen. */
public final class ToolOverviewEntry {

    private final String name;
    private final String description;
    private final String origin;
    private final String availability;

    /**
     * @param origin       Herkunft bzw. Registry, z. B. „Wissenswerkzeuge (KnowledgeMcpTools)“
     * @param availability wo das Werkzeug angeboten wird, z. B. „Chat + Agent“
     */
    public ToolOverviewEntry(String name, String description, String origin, String availability) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        this.name = name;
        this.description = description == null ? "" : description;
        this.origin = origin == null ? "" : origin;
        this.availability = availability == null ? "" : availability;
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
