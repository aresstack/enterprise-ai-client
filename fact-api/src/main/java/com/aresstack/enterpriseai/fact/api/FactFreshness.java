package com.aresstack.enterpriseai.fact.api;

/** Whether a fact still describes the current state of its resource (see {@link MetadataFact#freshness}). */
public enum FactFreshness {
    /** Determined against the current content hash or revision. */
    CURRENT,
    /** Determined against an older state; the anchor may point to the wrong place, re-check before use. */
    STALE,
    /** Neither hash nor revision is known on both sides. */
    UNKNOWN
}
