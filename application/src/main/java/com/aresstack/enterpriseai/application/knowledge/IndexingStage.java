package com.aresstack.enterpriseai.application.knowledge;

/** Schritt der Indexierung, in dem ein Fehler aufgetreten ist (Vorbild askai-java8 {@code SourceProcessingStage}). */
public enum IndexingStage {
    DISCOVERY,
    LOADING,
    EMBEDDING,
    INDEXING
}
