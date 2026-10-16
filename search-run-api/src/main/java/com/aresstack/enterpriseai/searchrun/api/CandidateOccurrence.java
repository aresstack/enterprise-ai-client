package com.aresstack.enterpriseai.searchrun.api;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

/**
 * Where a candidate appeared in the result list of one source: the source and the zero-based rank in its list. A
 * resource found more than once in a run is one candidate with several occurrences, so deduplication loses nothing.
 */
public final class CandidateOccurrence {

    private final SourceAddress source;
    private final int rank;

    public CandidateOccurrence(SourceAddress source, int rank) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (rank < 0) {
            throw new IllegalArgumentException("rank must not be negative: " + rank);
        }
        this.source = source;
        this.rank = rank;
    }

    public SourceAddress source() {
        return source;
    }

    /** Zero-based position in the hit list of {@link #source()}. */
    public int rank() {
        return rank;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CandidateOccurrence)) {
            return false;
        }
        CandidateOccurrence that = (CandidateOccurrence) other;
        return rank == that.rank && source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode() * 31 + rank;
    }

    @Override
    public String toString() {
        return source.value() + "#" + rank;
    }
}
