package com.aresstack.enterpriseai.proasteion.application.config;

/**
 * Live-Suche des normalen Chats vor dem Retrieval: die angehakten Quellen werden durchsucht, der Vorlader indexiert die
 * Treffer ({@code retrieval.liveSearch.*}). Eine Suche wartet zuerst auf {@link #initialPages()} indexierte Treffer und
 * bewertet die Evidenz; reicht sie nicht, wartet sie auf weitere, höchstens {@link #maxPages()}. Der Vorlader lädt die
 * übrigen im Hintergrund weiter, ohne dass der Turn darauf wartet. Unveränderlich.
 */
public final class LiveSearchConfig {

    public static final int DEFAULT_INITIAL_PAGES = 1;
    public static final int DEFAULT_MAX_PAGES = 4;
    public static final int DEFAULT_HITS_PER_SOURCE = 10;
    public static final int MAX_PAGES_LIMIT = 100;
    public static final int MAX_HITS_PER_SOURCE = 100;

    private final int initialPages;
    private final int maxPages;
    private final int hitsPerSource;

    public LiveSearchConfig(int initialPages, int maxPages, int hitsPerSource) {
        if (initialPages < 0 || maxPages < initialPages || maxPages > MAX_PAGES_LIMIT) {
            throw new IllegalArgumentException("need 0 <= initialPages <= maxPages <= " + MAX_PAGES_LIMIT);
        }
        if (hitsPerSource < 1 || hitsPerSource > MAX_HITS_PER_SOURCE) {
            throw new IllegalArgumentException("hitsPerSource must be within 1.." + MAX_HITS_PER_SOURCE);
        }
        this.initialPages = initialPages;
        this.maxPages = maxPages;
        this.hitsPerSource = hitsPerSource;
    }

    public static LiveSearchConfig defaults() {
        return new LiveSearchConfig(DEFAULT_INITIAL_PAGES, DEFAULT_MAX_PAGES, DEFAULT_HITS_PER_SOURCE);
    }

    /** Auf so viele indexierte Treffer wartet die erste Evidenzbewertung einer Suche ({@code initialPages}). */
    public int initialPages() {
        return initialPages;
    }

    /** Auf höchstens so viele indexierte Treffer wartet eine Suche blockierend ({@code maxPages}). */
    public int maxPages() {
        return maxPages;
    }

    /** So viele Treffer holt die Live-Suche je Quelle ({@code hitsPerSource}). */
    public int hitsPerSource() {
        return hitsPerSource;
    }

    @Override
    public String toString() {
        return "LiveSearchConfig[initialPages=" + initialPages + ", maxPages=" + maxPages + ", hitsPerSource="
                + hitsPerSource + "]";
    }
}
