package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.ResourceScheme;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.util.Collection;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Holkas-Connector für MediaWiki-Seiten, Bookmark {@code wiki://<site-alias>/<Page_Title>} (corenth Kompendium Kap. 10); liest über die angebundenen Quellen-Adapter. */
public final class WikiResourceConnector extends KnowledgeSourceResourceConnector {

    public WikiResourceConnector(Supplier<? extends Collection<KnowledgeSourcePort>> sources, LongSupplier clock) {
        super(ResourceScheme.wiki(), sources, clock);
    }
}
