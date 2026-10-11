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

    /** Höchstens so viele Treffer je Teilsuche und insgesamt. */
    static final int HITS_PER_QUERY = 8;
    static final int MAX_HITS = 40;

    private final KnowledgePages pages;
    private final List<String> plan = new ArrayList<String>();
    private java.util.function.Consumer<String> onQuery;

    KnowledgeSearchProvider(KnowledgePages pages) {
        this.pages = pages;
    }

    /**
     * Die Suchen aus Konzept und Weidezaun ({@code research_search_plan}): statt des ganzen Auftrags als eine Anfrage
     * sucht der Lauf je Thema; die Treffer kommen abwechselnd (je Suche der beste zuerst) und ohne Dubletten.
     */
    void setPlan(List<String> queries, java.util.function.Consumer<String> listener) {
        plan.clear();
        if (queries != null) {
            for (String query : queries) {
                if (query != null && !query.trim().isEmpty()) {
                    plan.add(query.trim());
                }
            }
        }
        onQuery = listener;
    }

    @Override
    public SearchProviderId getProviderId() {
        return SearchProviderId.KNOWLEDGE_SEARCH;
    }

    @Override
    public SearchProviderResult search(SearchProviderRequest request) {
        List<String> queries = plan.isEmpty() ? java.util.Collections.singletonList(request.getQuery()) : plan;
        int perQuery = plan.isEmpty() ? request.getRequestedResultCount() : HITS_PER_QUERY;
        List<List<KnowledgePages.Hit>> perSearch = new ArrayList<List<KnowledgePages.Hit>>();
        for (String query : queries) {
            if (onQuery != null) {
                onQuery.accept(query);
            }
            try {
                perSearch.add(pages.search(query, perQuery));
            } catch (ToolInvoker.ToolFailure failed) {
                if (queries.size() == 1) {
                    throw new SearchProviderTemporaryException(SearchProviderId.KNOWLEDGE_SEARCH,
                            "knowledge_search failed: " + failed.getMessage());
                }
                perSearch.add(java.util.Collections.<KnowledgePages.Hit>emptyList());
            } catch (ToolInvoker.EndpointUnavailable unavailable) {
                throw new SearchProviderTemporaryException(SearchProviderId.KNOWLEDGE_SEARCH,
                        "knowledge_search unavailable: " + unavailable.getMessage());
            }
        }
        List<SearchHit> result = new ArrayList<SearchHit>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        int limit = plan.isEmpty() ? Integer.MAX_VALUE : MAX_HITS;
        int rank = 0;
        for (int round = 0; result.size() < limit; round++) {
            boolean any = false;
            for (int index = 0; index < perSearch.size() && result.size() < limit; index++) {
                List<KnowledgePages.Hit> hits = perSearch.get(index);
                if (round >= hits.size()) {
                    continue;
                }
                any = true;
                KnowledgePages.Hit hit = hits.get(round);
                if (!seen.add(hit.url)) {
                    continue;
                }
                rank++;
                result.add(new SearchHit(SearchProviderId.KNOWLEDGE_SEARCH, SearchEngine.PROVIDER_DEFAULT,
                        queries.get(index), rank, hit.url, hit.title, hit.snippet, "", rank, null));
            }
            if (!any) {
                break;
            }
        }
        return new SearchProviderResult(SearchProviderId.KNOWLEDGE_SEARCH, SearchEngine.PROVIDER_DEFAULT, result);
    }

    @Override
    public SearchProviderAvailability getAvailability() {
        return SearchProviderAvailability.AVAILABLE;
    }
}
