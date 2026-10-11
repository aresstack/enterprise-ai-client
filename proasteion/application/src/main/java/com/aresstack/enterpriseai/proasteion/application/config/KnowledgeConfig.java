package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.rag.ContextSettings;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalSettings;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;

import java.nio.file.Path;

/**
 * Wissensbasis und RAG: Indexverzeichnis, Chunking, Retrieval- und Kontexteinstellungen (Defaults aus AP10) sowie
 * ob die konfigurierten Quellen beim Start im Hintergrund indexiert werden; dazu das Vorladen der Suchtreffer
 * ({@code knowledge.prefetch.*}, MainframeMate {@code confluencePrefetchMaxItems/Concurrency/CacheMaxMb}).
 */
public final class KnowledgeConfig {

    private final Path indexDirectory;
    private final boolean indexOnStartup;
    private final KnowledgeChunkingPolicy chunking;
    private final int embeddingBatchSize;
    private final RetrievalSettings retrieval;
    private final ContextSettings context;
    private final KnowledgePrefetchService.Settings prefetch;
    private final String passageLanguage;

    KnowledgeConfig(Path indexDirectory, boolean indexOnStartup, KnowledgeChunkingPolicy chunking,
                    int embeddingBatchSize, RetrievalSettings retrieval, ContextSettings context,
                    KnowledgePrefetchService.Settings prefetch, String passageLanguage) {
        this.indexDirectory = indexDirectory;
        this.indexOnStartup = indexOnStartup;
        this.chunking = chunking;
        this.embeddingBatchSize = embeddingBatchSize;
        this.retrieval = retrieval;
        this.context = context;
        this.prefetch = prefetch == null ? KnowledgePrefetchService.Settings.defaults() : prefetch;
        this.passageLanguage = passageLanguage == null || passageLanguage.trim().isEmpty() ? "de"
                : passageLanguage.trim();
    }

    /** Sprache des Satzmodells für die Passagenbildung beim Indexieren ({@code knowledge.passages.language}). */
    public String passageLanguage() {
        return passageLanguage;
    }

    /** Vorladen der Suchtreffer: Fenster ab der Cursorposition, parallele Abrufe, Cache-Größe. */
    public KnowledgePrefetchService.Settings prefetch() {
        return prefetch;
    }

    /** Verzeichnis des Lucene-/Vektorindex; eine Instanz je Verzeichnis und Prozess. */
    public Path indexDirectory() {
        return indexDirectory;
    }

    /** {@code true}: alle Quellen werden nach dem Start in einem Hintergrund-Thread indexiert. */
    public boolean indexOnStartup() {
        return indexOnStartup;
    }

    public KnowledgeChunkingPolicy chunking() {
        return chunking;
    }

    /** Texte je Aufruf des Embedding-Ports beim Indexieren. */
    public int embeddingBatchSize() {
        return embeddingBatchSize;
    }

    public RetrievalSettings retrieval() {
        return retrieval;
    }

    public ContextSettings context() {
        return context;
    }

    @Override
    public String toString() {
        return "KnowledgeConfig[indexDirectory=" + indexDirectory + ", indexOnStartup=" + indexOnStartup
                + ", chunking=" + chunking + ", embeddingBatchSize=" + embeddingBatchSize + ", retrieval=" + retrieval
                + ", context=" + context + ", prefetch=" + prefetch + "]";
    }
}
