package com.aresstack.enterpriseai.astu.acropolis.mcp;

/**
 * Grenzen der MCP-Wissenswerkzeuge: Gesamtgröße einer Antwort, Länge der Textausschnitte in Suchtreffern,
 * Standardzahl der Treffer und wie viele gescheiterte Ressourcen ein Aktualisierungsbericht aufzählt.
 * Unveränderlich; Änderungen über die {@code with*}-Methoden.
 */
public final class KnowledgeToolSettings {

    /** Standardgrenze je Antwort in Zeichen (Vorbild MainframeMate {@code ReadChunksTool.MAX_TOTAL_CHARS}). */
    public static final int DEFAULT_MAX_RESPONSE_CHARS = 20_000;
    /** Untergrenze, damit Kopfzeilen und Kürzungsmarker immer Platz haben. */
    public static final int MIN_RESPONSE_CHARS = 1_000;
    public static final int DEFAULT_SNIPPET_CHARS = 600;
    public static final int MIN_SNIPPET_CHARS = 80;
    public static final int DEFAULT_MAX_RESULTS = 5;
    public static final int DEFAULT_MAX_FAILURES_LISTED = 20;
    /**
     * Seitenbudget eines Recherche-Laufs des Agenten ({@code agent.tools.maxPages}, 0 = unbegrenzt); gezählt im
     * Agentenprozess. Die Werkzeuge selbst verbrauchen nichts davon, das Vorladen hat eigene Einstellungen.
     */
    public static final int DEFAULT_MAX_PAGES = 4;
    /** Direkte Kinder je {@code knowledge_tree}-Aufruf ohne {@code limit} ({@code agent.tools.treeChildLimit}). */
    public static final int DEFAULT_TREE_CHILD_LIMIT = 20;

    /** Ids one {@code knowledge_read_many} call accepts; not {@link #maxPages()}, which is the agent's budget. */
    public static final int DEFAULT_READ_MANY_MAX_IDS = 8;
    /** Reads one {@code knowledge_read_many} call runs at the same time. */
    public static final int DEFAULT_READ_MANY_PARALLELISM = 4;

    private final int maxResponseChars;
    private final int snippetChars;
    private final int defaultMaxResults;
    private final int maxFailuresListed;
    private final int maxPages;
    private final int treeChildLimit;
    private final int readManyMaxIds;
    private final int readManyParallelism;

    private KnowledgeToolSettings(int maxResponseChars, int snippetChars, int defaultMaxResults,
                                  int maxFailuresListed, int maxPages, int treeChildLimit, int readManyMaxIds,
                                  int readManyParallelism) {
        this.maxResponseChars = maxResponseChars;
        this.snippetChars = snippetChars;
        this.defaultMaxResults = defaultMaxResults;
        this.maxFailuresListed = maxFailuresListed;
        this.maxPages = maxPages;
        this.treeChildLimit = treeChildLimit;
        this.readManyMaxIds = readManyMaxIds;
        this.readManyParallelism = readManyParallelism;
    }

    public static KnowledgeToolSettings defaults() {
        return new KnowledgeToolSettings(DEFAULT_MAX_RESPONSE_CHARS, DEFAULT_SNIPPET_CHARS, DEFAULT_MAX_RESULTS,
                DEFAULT_MAX_FAILURES_LISTED, DEFAULT_MAX_PAGES, DEFAULT_TREE_CHILD_LIMIT, DEFAULT_READ_MANY_MAX_IDS,
                DEFAULT_READ_MANY_PARALLELISM);
    }

    /** Höchstgröße einer Tool-Antwort in Zeichen, mindestens {@value #MIN_RESPONSE_CHARS}. */
    public KnowledgeToolSettings withMaxResponseChars(int chars) {
        if (chars < MIN_RESPONSE_CHARS) {
            throw new IllegalArgumentException("maxResponseChars muss >= " + MIN_RESPONSE_CHARS + " sein: " + chars);
        }
        return new KnowledgeToolSettings(chars, snippetChars, defaultMaxResults, maxFailuresListed,
                maxPages, treeChildLimit, readManyMaxIds, readManyParallelism);
    }

    /** Länge des Textausschnitts je Suchtreffer in Zeichen, mindestens {@value #MIN_SNIPPET_CHARS}. */
    public KnowledgeToolSettings withSnippetChars(int chars) {
        if (chars < MIN_SNIPPET_CHARS) {
            throw new IllegalArgumentException("snippetChars muss >= " + MIN_SNIPPET_CHARS + " sein: " + chars);
        }
        return new KnowledgeToolSettings(maxResponseChars, chars, defaultMaxResults, maxFailuresListed,
                maxPages, treeChildLimit, readManyMaxIds, readManyParallelism);
    }

    /** Treffer, wenn der Aufruf {@code max_results} nicht nennt, {@code >= 1}. */
    public KnowledgeToolSettings withDefaultMaxResults(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("defaultMaxResults muss >= 1 sein: " + count);
        }
        return new KnowledgeToolSettings(maxResponseChars, snippetChars, count, maxFailuresListed,
                maxPages, treeChildLimit, readManyMaxIds, readManyParallelism);
    }

    /** Wie viele gescheiterte Ressourcen ein Aktualisierungsbericht einzeln nennt, {@code >= 0}. */
    public KnowledgeToolSettings withMaxFailuresListed(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("maxFailuresListed muss >= 0 sein: " + count);
        }
        return new KnowledgeToolSettings(maxResponseChars, snippetChars, defaultMaxResults, count,
                maxPages, treeChildLimit, readManyMaxIds, readManyParallelism);
    }

    /** Seitenbudget eines Recherche-Laufs (an den Agentenprozess übergeben), {@code 0} = unbegrenzt. */
    public KnowledgeToolSettings withMaxPages(int pages) {
        if (pages < 0) {
            throw new IllegalArgumentException("maxPages muss >= 0 sein (0 = unbegrenzt): " + pages);
        }
        return new KnowledgeToolSettings(maxResponseChars, snippetChars, defaultMaxResults, maxFailuresListed, pages,
                treeChildLimit, readManyMaxIds, readManyParallelism);
    }

    /** Direkte Kinder je {@code knowledge_tree}-Aufruf ohne {@code limit}, {@code 0} = alle. */
    public KnowledgeToolSettings withTreeChildLimit(int children) {
        if (children < 0) {
            throw new IllegalArgumentException("treeChildLimit muss >= 0 sein (0 = alle): " + children);
        }
        return new KnowledgeToolSettings(maxResponseChars, snippetChars, defaultMaxResults, maxFailuresListed, maxPages,
                children, readManyMaxIds, readManyParallelism);
    }

    /** Ids one {@code knowledge_read_many} call accepts, {@code >= 1}. */
    public KnowledgeToolSettings withReadManyMaxIds(int ids) {
        if (ids < 1) {
            throw new IllegalArgumentException("readManyMaxIds muss >= 1 sein: " + ids);
        }
        return new KnowledgeToolSettings(maxResponseChars, snippetChars, defaultMaxResults, maxFailuresListed, maxPages,
                treeChildLimit, ids, readManyParallelism);
    }

    /** Reads one {@code knowledge_read_many} call runs at the same time, {@code >= 1}. */
    public KnowledgeToolSettings withReadManyParallelism(int reads) {
        if (reads < 1) {
            throw new IllegalArgumentException("readManyParallelism muss >= 1 sein: " + reads);
        }
        return new KnowledgeToolSettings(maxResponseChars, snippetChars, defaultMaxResults, maxFailuresListed, maxPages,
                treeChildLimit, readManyMaxIds, reads);
    }

    public int readManyMaxIds() {
        return readManyMaxIds;
    }

    public int readManyParallelism() {
        return readManyParallelism;
    }

    public int treeChildLimit() {
        return treeChildLimit;
    }

    public int maxPages() {
        return maxPages;
    }

    public int maxResponseChars() {
        return maxResponseChars;
    }

    public int snippetChars() {
        return snippetChars;
    }

    public int defaultMaxResults() {
        return defaultMaxResults;
    }

    public int maxFailuresListed() {
        return maxFailuresListed;
    }

    @Override
    public String toString() {
        return "KnowledgeToolSettings{maxResponseChars=" + maxResponseChars + ", snippetChars=" + snippetChars
                + ", defaultMaxResults=" + defaultMaxResults + ", maxFailuresListed=" + maxFailuresListed
                + ", maxPages=" + maxPages + ", treeChildLimit=" + treeChildLimit + ", readManyMaxIds=" + readManyMaxIds
                + ", readManyParallelism=" + readManyParallelism + "}";
    }
}
