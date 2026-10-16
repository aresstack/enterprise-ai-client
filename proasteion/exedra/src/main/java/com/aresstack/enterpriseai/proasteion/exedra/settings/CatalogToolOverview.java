package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.composition.ToolCatalog;
import com.aresstack.enterpriseai.proasteion.application.composition.ToolEntry;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ToolOverview;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ToolOverviewEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Reiter „Tools“ über der zentralen Werkzeug-Registry: jede Abfrage liest {@link ToolCatalog#entries()} neu und
 * übersetzt sie in Werte der Oberfläche. Keine eigene Liste; was im Katalog registriert ist, erscheint.
 */
public final class CatalogToolOverview implements ToolOverview {

    private final ToolCatalog catalog;

    public CatalogToolOverview(ToolCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        this.catalog = catalog;
    }

    @Override
    public List<ToolOverviewEntry> tools() {
        List<ToolEntry> entries = catalog.entries();
        List<ToolOverviewEntry> tools = new ArrayList<ToolOverviewEntry>(entries.size());
        for (ToolEntry entry : entries) {
            tools.add(new ToolOverviewEntry(entry.name(), entry.description(), entry.origin(), entry.availability(),
                    entry.enabled(), entry.required(),
                    catalog.approvals().subRules(entry.name())));
        }
        return tools;
    }
}
