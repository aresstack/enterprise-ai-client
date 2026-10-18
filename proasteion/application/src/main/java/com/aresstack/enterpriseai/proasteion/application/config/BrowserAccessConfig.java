package com.aresstack.enterpriseai.proasteion.application.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Global access policy of the chat browsers (issue #141) as configured: {@value #MODE_KEY} ({@value #MODE_BLOCK}
 * by default, or {@value #MODE_ALLOW}), {@value #ALLOW_KEY} and {@value #BLOCK_KEY} as comma separated host rules.
 * Kept free of browser port types so the settings UI can read it; the browser layer turns it into its policy.
 */
public final class BrowserAccessConfig {

    public static final String MODE_KEY = "browser.access.mode";
    public static final String ALLOW_KEY = "browser.access.allow";
    public static final String BLOCK_KEY = "browser.access.block";
    public static final String MODE_BLOCK = "block";
    public static final String MODE_ALLOW = "allow";
    public static final String SEPARATOR = ",";

    private final boolean allowByDefault;
    private final List<String> allow;
    private final List<String> block;

    public BrowserAccessConfig(boolean allowByDefault, List<String> allow, List<String> block) {
        this.allowByDefault = allowByDefault;
        this.allow = clean(allow);
        this.block = clean(block);
    }

    public static BrowserAccessConfig defaults() {
        return new BrowserAccessConfig(false, Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    /** Is {@code value} the "allow by default" mode? Anything else means block by default. */
    public static boolean isAllowMode(String value) {
        return value != null && MODE_ALLOW.equals(value.trim().toLowerCase(Locale.ROOT));
    }

    /** Splits a stored comma separated rule list (also accepts line breaks). */
    public static List<String> split(String value) {
        List<String> rules = new ArrayList<String>();
        if (value != null) {
            for (String part : value.split("[,\\r\\n]")) {
                if (!part.trim().isEmpty()) {
                    rules.add(part.trim());
                }
            }
        }
        return rules;
    }

    /** Joins rules into the stored comma separated value. */
    public static String join(List<String> rules) {
        StringBuilder value = new StringBuilder();
        for (String rule : rules) {
            if (rule != null && !rule.trim().isEmpty()) {
                if (value.length() > 0) {
                    value.append(SEPARATOR);
                }
                value.append(rule.trim());
            }
        }
        return value.toString();
    }

    private static List<String> clean(List<String> rules) {
        List<String> cleaned = new ArrayList<String>();
        if (rules != null) {
            for (String rule : rules) {
                String trimmed = rule == null ? "" : rule.trim();
                if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
                    cleaned.add(trimmed);
                }
            }
        }
        return Collections.unmodifiableList(cleaned);
    }

    /** {@code true}: unknown hosts are allowed; {@code false} (default): they need the user's approval. */
    public boolean allowByDefault() {
        return allowByDefault;
    }

    /** Whitelist rules as entered. */
    public List<String> allow() {
        return allow;
    }

    /** Blacklist rules as entered. */
    public List<String> block() {
        return block;
    }

    @Override
    public String toString() {
        return "BrowserAccessConfig[" + (allowByDefault ? MODE_ALLOW : MODE_BLOCK) + ", allow=" + allow
                + ", block=" + block + "]";
    }
}
