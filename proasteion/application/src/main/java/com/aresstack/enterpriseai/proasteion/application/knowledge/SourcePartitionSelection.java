package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.proasteion.application.state.ApplicationStateService;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Welche Bereiche einer Quelle (Confluence: Spaces) der Benutzer unter Wissensquellen angehakt hat, wie in
 * MainframeMate je Bereich ein Häkchen: {@code source.<n>.spaces.<schlüssel>=true|false} in der
 * {@code application-state.json}, nicht in der Konfiguration. Ein Bereich ohne gemerkten Wert ist angehakt, neue
 * Spaces sind also dabei. Wirkt ohne Neustart: Indexierung und Suche fragen bei jedem Lauf {@link #excluded} ab.
 */
public final class SourcePartitionSelection {

    private final ApplicationStateService state;

    public SourcePartitionSelection(ApplicationStateService state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.state = state;
    }

    /** Die abgehakten Bereiche der Quelle; leer = alle. */
    public Set<String> excluded(SourceAddress sourceId) {
        Set<String> excluded = new LinkedHashSet<String>();
        String prefix = prefix(sourceId.value());
        for (Map.Entry<String, String> entry : state.withPrefix(prefix).entrySet()) {
            if ("false".equalsIgnoreCase(entry.getValue())) {
                excluded.add(entry.getKey().substring(prefix.length()));
            }
        }
        return excluded;
    }

    public boolean isSelected(String sourceId, String key) {
        return state.getBoolean(prefix(sourceId) + key, true);
    }

    public void setSelected(String sourceId, String key, boolean selected) {
        state.putAndSave(prefix(sourceId) + key, Boolean.toString(selected));
    }

    private static String prefix(String sourceId) {
        return "source." + sourceId + ".spaces.";
    }
}
