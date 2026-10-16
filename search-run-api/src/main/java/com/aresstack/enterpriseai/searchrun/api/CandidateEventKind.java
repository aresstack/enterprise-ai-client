package com.aresstack.enterpriseai.searchrun.api;

/**
 * What happened to a candidate after discovery. Each kind is a separate fact; none replaces the discovery record and
 * none means "cited". New kinds are added at the end.
 */
public enum CandidateEventKind {
    /** Someone asked to deepen this candidate later ("Treffer 18 vertiefen"), without a new search. */
    DEEPENING_REQUESTED,
    /** The resource was already available locally with a matching revision; nothing was downloaded. */
    ALREADY_CURRENT,
    /** The full text was loaded. */
    LOADED,
    /** Loading the full text failed. */
    LOAD_FAILED,
    /**
     * Title and excerpt went through the candidate reranker; {@link CandidateEvent#score()} is the score, the detail
     * names the load priority. Means "promising for deepening", never "evidence" or "used".
     */
    EXCERPT_SCORED,
    /** The candidate was queued for loading and indexing; the detail names its position in the load order. */
    PREFETCH_QUEUED
}
