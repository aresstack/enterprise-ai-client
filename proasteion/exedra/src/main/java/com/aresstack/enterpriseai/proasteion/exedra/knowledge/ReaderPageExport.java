package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.KnowledgeReaderPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;

import java.awt.Component;
import java.util.function.Function;

/**
 * The export button of the reader (#115): the page of the active tab, loaded fresh from its connected source, through
 * the same export as a subtree of the page tree, only without children. Source overviews and pages of sources that
 * are not connected offer no export.
 */
public final class ReaderPageExport implements KnowledgeReaderPanel.PageExporter {

    private final Function<String, KnowledgeSourceRegistration> lookup;
    private final KnowledgeExportController exporter;
    private final Component parent;

    public ReaderPageExport(Function<String, KnowledgeSourceRegistration> lookup, KnowledgeExportController exporter,
                            Component parent) {
        if (lookup == null || exporter == null) {
            throw new IllegalArgumentException("lookup and exporter are required");
        }
        this.lookup = lookup;
        this.exporter = exporter;
        this.parent = parent;
    }

    @Override
    public boolean canExport(ReaderLink link) {
        return link != null && !link.isSource() && lookup.apply(link.sourceId()) != null;
    }

    @Override
    public void export(ReaderLink link) {
        if (!canExport(link)) {
            return;
        }
        KnowledgeSourceRegistration registration = lookup.apply(link.sourceId());
        exporter.exportPage(parent, registration.port(), KnowledgeResourceId.of(link.resourceId()), link.title());
    }
}
