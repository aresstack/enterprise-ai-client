package com.aresstack.enterpriseai.fact.api;

/**
 * Whether a person has checked the fact. A store never sets anything but {@link #UNREVIEWED} on its own; there is no
 * implied verification.
 */
public enum FactReview {
    UNREVIEWED,
    CONFIRMED,
    REJECTED
}
