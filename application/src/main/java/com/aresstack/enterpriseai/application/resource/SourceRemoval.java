package com.aresstack.enterpriseai.application.resource;

/**
 * The observation that a resource was removed at its source (tombstone).
 *
 * <p>A removal observation is independent of the indexed-version fact: a resource can be
 * removed at the source while a version of it is still recorded as indexed. Withdrawing it
 * from derived indexes is decided by Tamias (#5) and executed by Acropolis (#10).
 */
public final class SourceRemoval {

    private final long observedAtMillis;

    public SourceRemoval(long observedAtMillis) {
        this.observedAtMillis = observedAtMillis;
    }

    /** Returns the epoch millis when the removal was observed. */
    public long observedAtMillis() {
        return observedAtMillis;
    }

    @Override
    public String toString() {
        return "SourceRemoval{observed=" + observedAtMillis + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceRemoval)) return false;
        return observedAtMillis == ((SourceRemoval) o).observedAtMillis;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(observedAtMillis).hashCode();
    }
}
