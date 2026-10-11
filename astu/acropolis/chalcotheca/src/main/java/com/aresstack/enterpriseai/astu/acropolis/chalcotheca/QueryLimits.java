package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

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

    static Set<SourceAddress> sources(Collection<SourceAddress> sources) {
        Set<SourceAddress> copy = new LinkedHashSet<SourceAddress>();
        if (sources != null) {
            for (SourceAddress source : sources) {
                if (source == null) {
                    throw new IllegalArgumentException("Quellenfilter enthält null");
                }
                copy.add(source);
            }
        }
        return Collections.unmodifiableSet(copy);
    }
}
