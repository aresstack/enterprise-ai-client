package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Eine Zeile des Reiters „Tools“: nur Werte, keine Registry- oder Port-Typen. */
public final class ToolOverviewEntry {

    private final String name;
    private final String description;
    private final String origin;
    private final String availability;
    private final boolean enabled;
    private final boolean required;
    private final List<String> approvalSubRules;

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
        this(name, description, origin, availability, enabled, false);
    }

    /** @param required Pflichtwerkzeug: immer aktiv, das Häkchen lässt sich nicht abwählen */
    public ToolOverviewEntry(String name, String description, String origin, String availability, boolean enabled,
                             boolean required) {
        this(name, description, origin, availability, enabled, required, Collections.<String>emptyList());
    }

    /**
     * @param approvalSubRules actions or {@code action/scope} combinations with their own approval rule, e.g.
     *                         {@code delete/project}; empty: only one policy for the whole tool
     */
    public ToolOverviewEntry(String name, String description, String origin, String availability, boolean enabled,
                             boolean required, List<String> approvalSubRules) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        this.name = name;
        this.description = description == null ? "" : description;
        this.origin = origin == null ? "" : origin;
        this.availability = availability == null ? "" : availability;
        this.enabled = enabled || required;
        this.required = required;
        this.approvalSubRules = approvalSubRules == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(approvalSubRules));
    }

    public List<String> approvalSubRules() {
        return approvalSubRules;
    }

    public boolean required() {
        return required;
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
