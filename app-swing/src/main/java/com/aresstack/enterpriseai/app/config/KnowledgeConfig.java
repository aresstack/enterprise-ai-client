package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.application.rag.ContextSettings;
import com.aresstack.enterpriseai.application.rag.RetrievalSettings;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;

import java.nio.file.Path;

/**
 * Wissensbasis und RAG: Indexverzeichnis, Chunking, Retrieval- und Kontexteinstellungen (Defaults aus AP10) sowie
 * ob die konfigurierten Quellen beim Start im Hintergrund indexiert werden.
 */
public final class KnowledgeConfig {

    private final Path indexDirectory;
    private final boolean indexOnStartup;
    private final KnowledgeChunkingPolicy chunking;
    private final int embeddingBatchSize;
    private final RetrievalSettings retrieval;
    private final ContextSettings context;

    KnowledgeConfig(Path indexDirectory, boolean indexOnStartup, KnowledgeChunkingPolicy chunking,
                    int embeddingBatchSize, RetrievalSettings retrieval, ContextSettings context) {
        this.indexDirectory = indexDirectory;
        this.indexOnStartup = indexOnStartup;
        this.chunking = chunking;
        this.embeddingBatchSize = embeddingBatchSize;
        this.retrieval = retrieval;
        this.context = context;
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
                + ", context=" + context + "]";
    }
}
