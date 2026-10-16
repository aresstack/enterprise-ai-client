package com.aresstack.enterpriseai.searchrun.api;

/**
 * The work stage a research run is in. Progress is shown by phase, never as a percentage derived from the funnel
 * counts: relevant / examined is selectivity, not progress.
 */
public enum FunnelPhase {
    /** Live sources are being searched for candidates. */
    DISCOVERY,
    /** Candidates were scored by their excerpts; the prioritised ones are being loaded into the index. */
    HARVESTING,
    /** The index is being searched (full text, semantic). */
    RETRIEVAL,
    /** Retrieved content is being judged for relevance and sufficiency. */
    EVIDENCE,
    /** The answer is being prepared from the chosen sources. */
    ANSWER,
    /** The run has ended (completely or not, see {@link KnowledgeFunnel#complete()}). */
    DONE
}
