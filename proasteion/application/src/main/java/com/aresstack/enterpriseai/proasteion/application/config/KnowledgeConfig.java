package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.rag.ContextSettings;
import com.aresstack.enterpriseai.astu.acropolis.rag.EvidencePolicy;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalSettings;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;

import java.nio.file.Path;

/**
 * Wissensbasis und RAG: Indexverzeichnis, Chunking, Retrieval- und Kontexteinstellungen (Defaults aus AP10); dazu
 * das Vorladen der Suchtreffer
 * ({@code knowledge.prefetch.*}, MainframeMate {@code confluencePrefetchMaxItems/Concurrency/CacheMaxMb}).
 */
public final class KnowledgeConfig {

    private final Path indexDirectory;
    private final KnowledgeChunkingPolicy chunking;
    private final int embeddingBatchSize;
    private final RetrievalSettings retrieval;
    private final ContextSettings context;
    private final KnowledgePrefetchService.Settings prefetch;
    private final String passageLanguage;
    private final EvidencePolicy evidence;

    KnowledgeConfig(Path indexDirectory, KnowledgeChunkingPolicy chunking,
                    int embeddingBatchSize, RetrievalSettings retrieval, ContextSettings context,
                    KnowledgePrefetchService.Settings prefetch, String passageLanguage) {
        this(indexDirectory, chunking, embeddingBatchSize, retrieval, context, prefetch, passageLanguage,
                EvidencePolicy.defaults());
    }

    KnowledgeConfig(Path indexDirectory, KnowledgeChunkingPolicy chunking,
                    int embeddingBatchSize, RetrievalSettings retrieval, ContextSettings context,
                    KnowledgePrefetchService.Settings prefetch, String passageLanguage, EvidencePolicy evidence) {
        this.indexDirectory = indexDirectory;
        this.chunking = chunking;
        this.embeddingBatchSize = embeddingBatchSize;
        this.retrieval = retrieval;
        this.context = context;
        this.prefetch = prefetch == null ? KnowledgePrefetchService.Settings.defaults() : prefetch;
        this.passageLanguage = passageLanguage == null || passageLanguage.trim().isEmpty() ? "de"
                : passageLanguage.trim();
        this.evidence = evidence == null ? EvidencePolicy.defaults() : evidence;
    }

    /**
     * Wann die Wissenssuche im normalen Chat genug hat: Mindestzahl unabhängiger Quellen ({@code context.minSources}),
     * Nachsuchen ({@code retrieval.maxFollowUps}) und Reranking ({@code retrieval.rerank}).
     */
    public EvidencePolicy evidence() {
        return evidence;
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
        return "KnowledgeConfig[indexDirectory=" + indexDirectory
                + ", chunking=" + chunking + ", embeddingBatchSize=" + embeddingBatchSize + ", retrieval=" + retrieval
                + ", context=" + context + ", prefetch=" + prefetch + ", evidence=" + evidence + "]";
    }
}
