package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Gemeinsame Prüfungen der Query-Typen. */
final class QueryLimits {

    static final int DEFAULT_MAX_RESULTS = 10;
    static final int MAX_RESULTS_LIMIT = 1000;

    private QueryLimits() {
    }

    static int maxResults(int requested) {
        if (requested <= 0) {
            return DEFAULT_MAX_RESULTS;
        }
        return Math.min(requested, MAX_RESULTS_LIMIT);
    }

    static Set<KnowledgeSourceId> sources(Collection<KnowledgeSourceId> sources) {
        Set<KnowledgeSourceId> copy = new LinkedHashSet<KnowledgeSourceId>();
        if (sources != null) {
            for (KnowledgeSourceId source : sources) {
                if (source == null) {
                    throw new IllegalArgumentException("Quellenfilter enthält null");
                }
                copy.add(source);
            }
        }
        return Collections.unmodifiableSet(copy);
    }
}
