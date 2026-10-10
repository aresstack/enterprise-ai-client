package com.aresstack.enterpriseai.domain.source;

/**
 * Eine konfigurierte (oder im Dialog entworfene) Wissensquelle: ID, Quelltyp, Häkchen und die Einstellungen des
 * Adapters. Die ID ist hier noch Text, weil ein Entwurf ungültig sein darf; geprüft wird sie beim Speichern.
 */
public final class SourceDefinition {

    private final String id;
    private final String typeId;
    private final boolean enabled;
    private final SourceSettings settings;

    public SourceDefinition(String id, String typeId, boolean enabled, SourceSettings settings) {
        this.id = id == null ? "" : id.trim();
        this.typeId = typeId == null ? "" : typeId.trim();
        this.enabled = enabled;
        this.settings = settings == null ? SourceSettings.empty() : settings;
    }

    /** Kurzname der Quelle ({@code source.<id>.*}); im Entwurf eventuell leer oder ungültig. */
    public String id() {
        return id;
    }

    /** {@link KnowledgeSourceType#id()} des Adapters, z. B. {@code mediawiki}. */
    public String typeId() {
        return typeId;
    }

    /** Angehakt: wird indexiert und im Chat durchsucht. */
    public boolean enabled() {
        return enabled;
    }

    public SourceSettings settings() {
        return settings;
    }

    public SourceDefinition withId(String value) {
        return new SourceDefinition(value, typeId, enabled, settings);
    }

    public SourceDefinition withEnabled(boolean value) {
        return new SourceDefinition(id, typeId, value, settings);
    }

    public SourceDefinition withSettings(SourceSettings value) {
        return new SourceDefinition(id, typeId, enabled, value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SourceDefinition)) {
            return false;
        }
        SourceDefinition that = (SourceDefinition) other;
        return id.equals(that.id) && typeId.equals(that.typeId) && enabled == that.enabled
                && settings.equals(that.settings);
    }

    @Override
    public int hashCode() {
        return (id.hashCode() * 31 + typeId.hashCode()) * 31 + settings.hashCode() + (enabled ? 1 : 0);
    }

    @Override
    public String toString() {
        return "SourceDefinition[" + id + ", " + typeId + (enabled ? "" : ", abgewählt") + ", " + settings + "]";
    }
}
