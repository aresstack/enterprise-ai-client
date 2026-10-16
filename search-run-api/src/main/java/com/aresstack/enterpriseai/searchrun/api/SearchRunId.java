package com.aresstack.enterpriseai.searchrun.api;

import java.util.UUID;

/** Stable id of one knowledge search run; survives restarts so a candidate can be addressed later. */
public final class SearchRunId {

    private static final int MAX_LENGTH = 64;

    private final String value;

    private SearchRunId(String value) {
        this.value = value;
    }

    public static SearchRunId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("search run id must not be empty");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("search run id too long: " + trimmed.length());
        }
        return new SearchRunId(trimmed);
    }

    /** A new, random id. */
    public static SearchRunId random() {
        return new SearchRunId(UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SearchRunId && ((SearchRunId) other).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
