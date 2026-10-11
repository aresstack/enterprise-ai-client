package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.search.provider.SearchEngine;
import com.aresstack.enterpriseai.research.runtime.search.provider.SearchHit;
import com.aresstack.enterpriseai.research.runtime.search.provider.SearchProvider;
import com.aresstack.enterpriseai.research.runtime.search.provider.SearchProviderAvailability;
import com.aresstack.enterpriseai.research.runtime.search.provider.SearchProviderId;
import com.aresstack.enterpriseai.research.runtime.search.provider.SearchProviderRequest;
import com.aresstack.enterpriseai.research.runtime.search.provider.SearchProviderResult;
import com.aresstack.enterpriseai.research.runtime.search.provider.SearchProviderTemporaryException;

import java.util.ArrayList;
import java.util.List;

/**
 * Der {@link SearchProvider} des Recherche-Laufs: statt einer Websuche (askai arch) die Wissenssuche des Hosts
 * ({@code knowledge_search}), also Confluence und die anderen angehakten Quellen.
 */
final class KnowledgeSearchProvider implements SearchProvider {

    private final KnowledgePages pages;

    KnowledgeSearchProvider(KnowledgePages pages) {
        this.pages = pages;
    }

    @Override
    public SearchProviderId getProviderId() {
        return SearchProviderId.KNOWLEDGE_SEARCH;
    }

    @Override
    public SearchProviderResult search(SearchProviderRequest request) {
        List<KnowledgePages.Hit> hits;
        try {
            hits = pages.search(request.getQuery(), request.getRequestedResultCount());
        } catch (ToolInvoker.ToolFailure failed) {
            throw new SearchProviderTemporaryException(SearchProviderId.KNOWLEDGE_SEARCH,
                    "knowledge_search failed: " + failed.getMessage());
        } catch (ToolInvoker.EndpointUnavailable unavailable) {
            throw new SearchProviderTemporaryException(SearchProviderId.KNOWLEDGE_SEARCH,
                    "knowledge_search unavailable: " + unavailable.getMessage());
        }
        List<SearchHit> result = new ArrayList<SearchHit>();
        int rank = 0;
        for (KnowledgePages.Hit hit : hits) {
            rank++;
            result.add(new SearchHit(SearchProviderId.KNOWLEDGE_SEARCH, SearchEngine.PROVIDER_DEFAULT,
                    request.getQuery(), rank, hit.url, hit.title, hit.snippet, "", rank, null));
        }
        return new SearchProviderResult(SearchProviderId.KNOWLEDGE_SEARCH, SearchEngine.PROVIDER_DEFAULT, result);
    }

    @Override
    public SearchProviderAvailability getAvailability() {
        return SearchProviderAvailability.AVAILABLE;
    }
}
