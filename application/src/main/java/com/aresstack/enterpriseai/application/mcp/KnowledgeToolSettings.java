package com.aresstack.enterpriseai.application.mcp;

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

    private final int maxResponseChars;
    private final int snippetChars;
    private final int defaultMaxResults;
    private final int maxFailuresListed;

    private KnowledgeToolSettings(int maxResponseChars, int snippetChars, int defaultMaxResults,
                                  int maxFailuresListed) {
        this.maxResponseChars = maxResponseChars;
        this.snippetChars = snippetChars;
        this.defaultMaxResults = defaultMaxResults;
        this.maxFailuresListed = maxFailuresListed;
    }

    public static KnowledgeToolSettings defaults() {
        return new KnowledgeToolSettings(DEFAULT_MAX_RESPONSE_CHARS, DEFAULT_SNIPPET_CHARS, DEFAULT_MAX_RESULTS,
                DEFAULT_MAX_FAILURES_LISTED);
    }

    /** Höchstgröße einer Tool-Antwort in Zeichen, mindestens {@value #MIN_RESPONSE_CHARS}. */
    public KnowledgeToolSettings withMaxResponseChars(int chars) {
        if (chars < MIN_RESPONSE_CHARS) {
            throw new IllegalArgumentException("maxResponseChars muss >= " + MIN_RESPONSE_CHARS + " sein: " + chars);
        }
        return new KnowledgeToolSettings(chars, snippetChars, defaultMaxResults, maxFailuresListed);
    }

    /** Länge des Textausschnitts je Suchtreffer in Zeichen, mindestens {@value #MIN_SNIPPET_CHARS}. */
    public KnowledgeToolSettings withSnippetChars(int chars) {
        if (chars < MIN_SNIPPET_CHARS) {
            throw new IllegalArgumentException("snippetChars muss >= " + MIN_SNIPPET_CHARS + " sein: " + chars);
        }
        return new KnowledgeToolSettings(maxResponseChars, chars, defaultMaxResults, maxFailuresListed);
    }

    /** Treffer, wenn der Aufruf {@code max_results} nicht nennt, {@code >= 1}. */
    public KnowledgeToolSettings withDefaultMaxResults(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("defaultMaxResults muss >= 1 sein: " + count);
        }
        return new KnowledgeToolSettings(maxResponseChars, snippetChars, count, maxFailuresListed);
    }

    /** Wie viele gescheiterte Ressourcen ein Aktualisierungsbericht einzeln nennt, {@code >= 0}. */
    public KnowledgeToolSettings withMaxFailuresListed(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("maxFailuresListed muss >= 0 sein: " + count);
        }
        return new KnowledgeToolSettings(maxResponseChars, snippetChars, defaultMaxResults, count);
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
                + ", defaultMaxResults=" + defaultMaxResults + ", maxFailuresListed=" + maxFailuresListed + "}";
    }
}
