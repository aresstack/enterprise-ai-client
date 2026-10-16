package com.aresstack.enterpriseai.searchrun.api;

/** Which representation of a resource a candidate stands for: the source's own search result or the local index. */
public enum CandidateOrigin {
    /** Live hit of the source's own search (title and excerpt as the source returned them). */
    SOURCE,
    /** Hit of the local knowledge index (already loaded content). */
    INDEX
}
