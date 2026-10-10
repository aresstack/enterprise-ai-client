package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.ResourceScheme;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.util.Collection;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Holkas-Connector für Confluence-Seiten und -Anhänge, Bookmark {@code confluence://<site-alias>/page/<content-id>} (corenth Kompendium Kap. 11); liest über die angebundenen Quellen-Adapter. */
public final class ConfluenceResourceConnector extends KnowledgeSourceResourceConnector {

    public ConfluenceResourceConnector(Supplier<? extends Collection<KnowledgeSourcePort>> sources, LongSupplier clock) {
        super(ResourceScheme.confluence(), sources, clock);
    }
}
