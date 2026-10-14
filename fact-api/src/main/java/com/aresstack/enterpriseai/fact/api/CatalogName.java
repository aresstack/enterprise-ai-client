package com.aresstack.enterpriseai.fact.api;

import java.util.regex.Pattern;

/** Validate the upper-case names of fact types, fact roles and argument roles ({@code ACRONYM}, {@code ACTOR}). */
final class CatalogName {

    /** Longest name; matches the column width in every store. */
    static final int MAX_LENGTH = 64;

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");

    private CatalogName() {
    }

    static boolean isValid(String value) {
        return value != null && value.length() <= MAX_LENGTH && FORMAT.matcher(value).matches();
    }

    static String check(String kind, String value) {
        if (value == null || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + kind + " " + (value == null ? "null" : "'" + value + "'")
                    + ": expected [A-Z][A-Z0-9_]* with at most " + MAX_LENGTH + " characters");
        }
        return value;
    }
}
