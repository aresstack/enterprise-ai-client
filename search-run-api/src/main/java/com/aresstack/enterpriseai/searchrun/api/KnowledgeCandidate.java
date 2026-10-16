package com.aresstack.enterpriseai.searchrun.api;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One resource a search run received, as an immutable discovery fact. Identity is {@code sourceId + resourceId}; the
 * URL is optional display data. The candidate carries no status: what happened to it later lives in
 * {@link CandidateEvent}s. A candidate is not a source and not a citation.
 */
public final class KnowledgeCandidate {

    private final int number;
    private final SourceAddress sourceId;
    private final KnowledgeResourceId resourceId;
    private final String title;
    private final String snippet;
    private final String url;
    private final KnowledgeRevision revision;
    private final CandidateOrigin origin;
    private final List<CandidateOccurrence> occurrences;

    /**
     * @param number      one-based position in the merged order of the run ("Treffer 18")
     * @param occurrences every place the resource appeared in this run; at least one
     */
    public KnowledgeCandidate(int number, SourceAddress sourceId, KnowledgeResourceId resourceId, String title,
                              String snippet, String url, KnowledgeRevision revision, CandidateOrigin origin,
                              List<CandidateOccurrence> occurrences) {
        if (number < 1) {
            throw new IllegalArgumentException("candidate number must be at least 1: " + number);
        }
        if (sourceId == null || resourceId == null || origin == null) {
            throw new IllegalArgumentException("sourceId, resourceId and origin must not be null");
        }
        if (occurrences == null || occurrences.isEmpty()) {
            throw new IllegalArgumentException("a candidate needs at least one occurrence");
        }
        this.number = number;
        this.sourceId = sourceId;
        this.resourceId = resourceId;
        this.title = title == null ? "" : title.trim();
        this.snippet = snippet == null ? "" : snippet.trim();
        this.url = url == null || url.trim().isEmpty() ? null : url.trim();
        this.revision = revision == null ? KnowledgeRevision.unknown() : revision;
        this.origin = origin;
        this.occurrences = Collections.unmodifiableList(new ArrayList<CandidateOccurrence>(occurrences));
    }

    public int number() {
        return number;
    }

    public SourceAddress sourceId() {
        return sourceId;
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    public String title() {
        return title;
    }

    /** Plain-text excerpt as received; empty when the source returned none. */
    public String snippet() {
        return snippet;
    }

    /** Callable address; {@code null} when the source named none. */
    public String url() {
        return url;
    }

    public KnowledgeRevision revision() {
        return revision;
    }

    public CandidateOrigin origin() {
        return origin;
    }

    public List<CandidateOccurrence> occurrences() {
        return occurrences;
    }

    @Override
    public String toString() {
        return "KnowledgeCandidate{" + number + ", " + resourceId + ", " + origin + "}";
    }
}
