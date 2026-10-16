package com.aresstack.enterpriseai.searchrun.api;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What one live search of the internal knowledge sources actually received: query, time, the sources asked, the
 * sources that failed and every candidate in merged order. Immutable; a run with candidates and no full-text load is
 * complete. A total reported by a source is deliberately not part of the run: only received candidates count.
 */
public final class KnowledgeSearchRun {

    private final SearchRunId id;
    private final String query;
    private final Instant startedAt;
    private final List<SourceAddress> sources;
    private final Map<SourceAddress, String> failures;
    private final List<KnowledgeCandidate> candidates;

    public KnowledgeSearchRun(SearchRunId id, String query, Instant startedAt, List<SourceAddress> sources,
                              Map<SourceAddress, String> failures, List<KnowledgeCandidate> candidates) {
        if (id == null || startedAt == null) {
            throw new IllegalArgumentException("id and startedAt must not be null");
        }
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query must not be empty");
        }
        List<KnowledgeCandidate> ordered = candidates == null
                ? Collections.<KnowledgeCandidate>emptyList() : candidates;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).number() != i + 1) {
                throw new IllegalArgumentException("candidates must be numbered 1..n in order");
            }
        }
        this.id = id;
        this.query = query.trim();
        this.startedAt = startedAt;
        this.sources = Collections.unmodifiableList(new ArrayList<SourceAddress>(
                sources == null ? Collections.<SourceAddress>emptyList() : sources));
        this.failures = Collections.unmodifiableMap(new LinkedHashMap<SourceAddress, String>(
                failures == null ? Collections.<SourceAddress, String>emptyMap() : failures));
        this.candidates = Collections.unmodifiableList(new ArrayList<KnowledgeCandidate>(ordered));
    }

    public SearchRunId id() {
        return id;
    }

    public String query() {
        return query;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** Every source the run asked, including the ones that failed. */
    public List<SourceAddress> sources() {
        return sources;
    }

    /** Sources whose search failed, with a short, user-readable reason. */
    public Map<SourceAddress, String> failures() {
        return failures;
    }

    /** Received candidates in merged order, numbered from 1. */
    public List<KnowledgeCandidate> candidates() {
        return candidates;
    }

    /** Candidate {@code number} (one-based), if the run has it. */
    public Optional<KnowledgeCandidate> candidate(int number) {
        return number >= 1 && number <= candidates.size()
                ? Optional.of(candidates.get(number - 1)) : Optional.<KnowledgeCandidate>empty();
    }

    @Override
    public String toString() {
        return "KnowledgeSearchRun{" + id + ", " + candidates.size() + " candidates}";
    }
}
