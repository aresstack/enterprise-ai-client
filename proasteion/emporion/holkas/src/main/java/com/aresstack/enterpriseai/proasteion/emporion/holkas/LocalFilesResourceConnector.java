package com.aresstack.enterpriseai.proasteion.emporion.holkas;

import com.aresstack.enterpriseai.astu.resource.ResourceScheme;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.util.Collection;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Holkas-Connector für Dateien eines Verzeichnisses, Bookmark {@code file://<quelle>/<relativer Pfad>}; liest über die angebundenen Quellen-Adapter. */
public final class LocalFilesResourceConnector extends KnowledgeSourceResourceConnector {

    public LocalFilesResourceConnector(Supplier<? extends Collection<KnowledgeSourcePort>> sources, LongSupplier clock) {
        super(ResourceScheme.file(), sources, clock);
    }
}
