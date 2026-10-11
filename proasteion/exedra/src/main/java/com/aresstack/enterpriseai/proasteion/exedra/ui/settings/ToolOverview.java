package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import java.util.Collections;
import java.util.List;

/**
 * Was der Reiter „Tools“ anzeigt: die zur Laufzeit registrierten Werkzeuge, bei jedem Aufruf frisch aus der zentralen
 * Registry der Anwendung (auf dem EDT, ohne Netz).
 */
public interface ToolOverview {

    List<ToolOverviewEntry> tools();

    /** Ohne laufende Anwendung (erster Start, Tests): keine Werkzeuge. */
    static ToolOverview none() {
        return Collections::emptyList;
    }
}
