package com.aresstack.enterpriseai.fact.api;

/** Who produced a fact. */
public enum FactOrigin {
    /** Determined by an extractor; replaceable as a set per resource and extractor. */
    AUTOMATIC,
    /** Added or corrected by a person; never touched by an extractor run. */
    MANUAL
}
