package com.aresstack.enterpriseai.proasteion.application.composition;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Ein Werkzeug des {@link ToolCatalog} zum Anzeigen: Name, Beschreibung, Herkunft, Kanäle und Zustand; ohne Handler. */
public final class ToolEntry {

    private final String name;
    private final String description;
    private final String origin;
    private final Set<ToolChannel> channels;
    private final boolean enabled;
    private final boolean required;

    ToolEntry(String name, String description, String origin, Set<ToolChannel> channels, boolean enabled,
              boolean required) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.origin = origin;
        this.channels = Collections.unmodifiableSet(EnumSet.copyOf(channels));
        this.enabled = enabled;
        this.required = required;
    }

    /** Pflichtwerkzeug: immer aktiv, im Reiter „Tools“ nicht abwählbar. */
    public boolean required() {
        return required;
    }

    /** Aktiv, also Chat bzw. Agenten angeboten; abgeschaltet bleibt es registriert. */
    public boolean enabled() {
        return enabled;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    /** Wer das Werkzeug registriert hat, z. B. „Wissenswerkzeuge“. */
    public String origin() {
        return origin;
    }

    public Set<ToolChannel> channels() {
        return channels;
    }

    /** Die Kanäle als Text in fester Reihenfolge, z. B. „Chat + Agent“. */
    public String availability() {
        StringBuilder text = new StringBuilder();
        for (ToolChannel channel : channels) {
            text.append(text.length() == 0 ? "" : " + ").append(channel.label());
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return "ToolEntry[" + name + ", " + origin + ", " + availability() + (enabled ? "" : ", aus")
                + (required ? ", Pflicht" : "") + "]";
    }
}
