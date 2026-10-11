package com.aresstack.enterpriseai.astu.acropolis.navigation;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.IndexedResourceState;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResourceIndexingOutcome;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId;
import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * Use Case „Dokumentnavigation aufbauen und aktualisieren“ (FA-06). {@link #outline} liefert sofort, was ohne Analyse
 * geht: die zwischengespeicherte Navigation dieser Fassung oder die Originalüberschriften. {@link #analyze} wartet
 * ohne Vorrang, bis die Index-Pipeline genau diese Fassung aufbereitet hat (dieselbe Warteschlange wie jede Seite,
 * kein Blockieren eines Threads), liest deren Passagen aus dem Index und baut daraus die semantische Navigation; der
 * Aufrufer zieht den Banner damit nach. Eine Fassung (Capture-ID) wird nur einmal analysiert, auch über den Neustart
 * hinaus ({@link DocumentNavigationStore}).
 *
 * <p>Kontrollierter Rückfall auf die Originalüberschriften: kein OpenNLP-Satzmodell installiert, Aufbereitung
 * gescheitert (z. B. Embedding-Dienst nicht erreichbar) oder keine Passagen dieser Fassung im Index.</p>
 */
public final class BuildDocumentNavigationUseCase {

    static final int CACHE_SIZE = 64;
    static final String NO_SENTENCE_MODEL = "Kein Satzmodell installiert: nur Originalüberschriften.";
    static final String INDEXING_FAILED = "Aufbereitung gescheitert (Embedding-Dienst?): nur Originalüberschriften.";
    static final String NOT_INDEXED = "Keine Passagen dieser Fassung im Index: nur Originalüberschriften.";

    private final KnowledgePrefetchService prefetch;
    private final BooleanSupplier sentenceModelAvailable;
    private final SectionLabeler labeler;
    private final Executor workExecutor;
    private final DocumentNavigationBuilder builder;
    private final DocumentNavigationStore store;
    private final Map<String, DocumentNavigation> analyzed = new LinkedHashMap<String, DocumentNavigation>(16, 0.75f,
            true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, DocumentNavigation> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private int analyses;

    /**
     * @param sentenceModelAvailable ob das OpenNLP-Satzmodell der Wissensbasis-Sprache installiert ist (je Aufruf
     *                               gefragt, gilt ohne Neustart)
     * @param labeler                Benenner der Themenabschnitte; liefert er {@code null}: Analyse-Bezeichnungen
     * @param workExecutor           wo analysiert, gelesen und gespeichert wird (nie der Index-Thread)
     * @param store                  analysierte Fassungen über den Neustart hinaus
     */
    public BuildDocumentNavigationUseCase(KnowledgePrefetchService prefetch, BooleanSupplier sentenceModelAvailable,
                                          SectionLabeler labeler, Executor workExecutor,
                                          DocumentNavigationStore store) {
        this(prefetch, sentenceModelAvailable, labeler, workExecutor, store, new DocumentNavigationBuilder());
    }

    BuildDocumentNavigationUseCase(KnowledgePrefetchService prefetch, BooleanSupplier sentenceModelAvailable,
                                   SectionLabeler labeler, Executor workExecutor, DocumentNavigationStore store,
                                   DocumentNavigationBuilder builder) {
        if (prefetch == null || sentenceModelAvailable == null || labeler == null || workExecutor == null
                || store == null || builder == null) {
            throw new IllegalArgumentException("all arguments are required");
        }
        this.prefetch = prefetch;
        this.sentenceModelAvailable = sentenceModelAvailable;
        this.labeler = labeler;
        this.workExecutor = workExecutor;
        this.builder = builder;
        this.store = store;
    }

    /** Sofort: die schon analysierte Navigation dieser Fassung, sonst die Originalüberschriften. */
    public DocumentNavigation outline(KnowledgeDocument document) {
        String capture = captureOf(document);
        DocumentNavigation cached = cached(key(document, capture));
        return cached != null ? cached : headings(document, capture, "");
    }

    /**
     * Die semantische Navigation dieser Fassung, sobald ihre Passagen im Index liegen; bei unveränderter Fassung aus
     * dem Speicher (auch nach dem Neustart), ohne neue Analyse. Endet nie mit einem Fehler: im Zweifel die
     * Originalüberschriften mit Grund.
     */
    public CompletableFuture<DocumentNavigation> analyze(final KnowledgeDocument document) {
        final String capture = captureOf(document);
        final String key = key(document, capture);
        DocumentNavigation cached = cached(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> stored(document, capture), workExecutor)
                .thenCompose(stored -> {
                    if (stored != null) {
                        remember(key, stored);
                        return CompletableFuture.completedFuture(stored);
                    }
                    return fresh(document, capture, key);
                });
    }

    private DocumentNavigation stored(KnowledgeDocument document, String capture) {
        try {
            return store.load(document.id().value(), capture);
        } catch (RuntimeException unreadable) {
            return null; // dann eben neu analysieren
        }
    }

    private CompletableFuture<DocumentNavigation> fresh(final KnowledgeDocument document, final String capture,
                                                        final String key) {
        if (!sentenceModelAvailable.getAsBoolean()) {
            return CompletableFuture.completedFuture(headings(document, capture, NO_SENTENCE_MODEL));
        }
        if (document.isBlank()) {
            return CompletableFuture.completedFuture(headings(document, capture, ""));
        }
        CompletableFuture<ResourceIndexingOutcome> indexed;
        try {
            indexed = prefetch.whenIndexed(document);
        } catch (RuntimeException failed) {
            return CompletableFuture.completedFuture(headings(document, capture, INDEXING_FAILED));
        }
        return indexed.handleAsync((outcome, failure) -> {
            if (failure != null || (outcome != null && outcome.status() == IndexingStatus.FAILED)) {
                return headings(document, capture, INDEXING_FAILED);
            }
            DocumentNavigation built = fromIndex(document, capture);
            if (!NOT_INDEXED.equals(built.fallbackReason())) {
                remember(key, built);
                persist(built); // echte Analyse dieser Fassung: nach dem Neustart nicht wiederholen
            }
            return built;
        }, workExecutor);
    }

    /** Wie oft tatsächlich analysiert wurde (für Tests: unveränderte Fassung = keine neue Analyse). */
    public synchronized int analysisCount() {
        return analyses;
    }

    private DocumentNavigation fromIndex(KnowledgeDocument document, String capture) {
        IndexKnowledgeUseCase indexing = prefetch.indexing();
        List<NavigationPassage> passages = new ArrayList<NavigationPassage>();
        for (KnowledgeIndexEntry entry : indexing.entriesOf(document.id())) {
            Optional<String> hash = entry.resource().metadata().get(IndexedResourceState.CONTENT_HASH_KEY);
            if (!hash.isPresent() || !hash.get().equalsIgnoreCase(document.contentHash())) {
                continue; // eine andere Fassung: ihre Positionen passen nicht zum angezeigten Text
            }
            Optional<KnowledgePassageId> id = indexing.passageId(entry);
            passages.add(new NavigationPassage(id.isPresent() ? id.get().value()
                    : entry.chunkId().value(), entry.chunk().text(), entry.embedding()));
        }
        if (passages.isEmpty()) {
            return headings(document, capture, NOT_INDEXED);
        }
        synchronized (this) {
            analyses++;
        }
        return builder.build(document.id().value(), capture, document.resource().title(), document.text(),
                passages, labeler);
    }

    private DocumentNavigation headings(KnowledgeDocument document, String capture, String reason) {
        return builder.headingsOnly(document.id().value(), capture, document.resource().title(), document.text(),
                reason);
    }

    private synchronized DocumentNavigation cached(String key) {
        return analyzed.get(key);
    }

    private void persist(DocumentNavigation navigation) {
        try {
            store.save(navigation);
        } catch (RuntimeException ignored) {
            // nicht gespeichert: nach dem Neustart wird diese Fassung neu analysiert
        }
    }

    private synchronized void remember(String key, DocumentNavigation navigation) {
        analyzed.put(key, navigation);
    }

    private static String key(KnowledgeDocument document, String capture) {
        return document.id().value() + "@" + capture;
    }

    private static String captureOf(KnowledgeDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        return KnowledgePassageId.captureOf(document.contentHash());
    }
}
