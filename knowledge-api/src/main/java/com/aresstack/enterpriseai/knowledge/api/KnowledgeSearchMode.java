package com.aresstack.enterpriseai.knowledge.api;

/** Woher ein Treffer stammt; bestimmt die Bedeutung von {@link KnowledgeSearchHit#score()}. */
public enum KnowledgeSearchMode {

    /** Volltextsuche (BM25 o. ä.): Score positiv, nach oben offen, nur innerhalb einer Suche vergleichbar. */
    KEYWORD,

    /** Cosine-Ähnlichkeit zum Anfragevektor: Score in [-1, 1]. */
    SEMANTIC
}
