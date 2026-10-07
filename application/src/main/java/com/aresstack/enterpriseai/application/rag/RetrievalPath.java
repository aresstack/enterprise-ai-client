package com.aresstack.enterpriseai.application.rag;

/** Die beiden Suchpfade des hybriden Retrievals. */
public enum RetrievalPath {

    /** Volltextsuche über {@code KnowledgeIndexPort.keywordSearch} (BM25 im Lucene-Adapter). */
    KEYWORD,

    /** Embedding der Anfrage und Cosine-Suche über {@code KnowledgeIndexPort.semanticSearch}. */
    SEMANTIC
}
