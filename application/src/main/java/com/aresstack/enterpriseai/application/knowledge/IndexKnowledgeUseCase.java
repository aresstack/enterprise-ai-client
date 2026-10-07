package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingWorldMismatchException;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Use Case "Wissen indexieren": bringt Ressourcen einer {@link KnowledgeSourcePort Quelle} in den
 * {@link KnowledgeIndexPort Index} – Discovery, Laden, Chunken ({@link KnowledgeChunker}), Embedden
 * ({@link EmbeddingPort}) und Ersetzen der Chunks je Ressource.
 *
 * <p>Regeln (Ablauf und Fehlerzuordnung nach askai-java8 {@code SourceProcessingWorker}):
 * <ul>
 *   <li>Seriell, eine Ressource nach der anderen. Indexieren ist der letzte Schritt: Erst wenn alle Chunks einer
 *       Ressource vektorisiert sind, ersetzt {@link KnowledgeIndexPort#replace} ihre Chunks atomar. Scheitert ein
 *       Schritt vorher, bleibt der bisherige Indexstand der Ressource unverändert.</li>
 *   <li>Ein Fehler betrifft nur seine Ressource ({@link IndexingStatus#FAILED} mit {@link IndexingStage}); der Lauf
 *       geht weiter. Nur eine gescheiterte Discovery beendet den Lauf (im Bericht, nicht als Ausnahme).</li>
 *   <li>Meldet die Quelle eine Ressource beim Laden als {@code NOT_FOUND}, werden ihre Chunks entfernt.</li>
 *   <li>Unverändert heißt übersprungen: Liefert die Discovery für eine Ressource dieselbe <em>bekannte</em>
 *       Revision, die {@link KnowledgeIndexPort#revisionOf} im Namespace speichert, wird sie weder geladen noch
 *       vektorisiert ({@link IndexingStatus#UNCHANGED}). Eine unbekannte Revision gilt als verändert; ebenso eine
 *       Ressource, deren Chunks uneinheitlich indexiert sind.</li>
 *   <li>Verschwunden heißt entfernt: Am Ende eines vollständigen {@link #indexSource}-Laufs (nicht abgebrochen,
 *       Discovery gelungen) werden die Ressourcen der Quelle, die {@link KnowledgeIndexPort#resourceIds} noch
 *       kennt, die Discovery aber nicht mehr geliefert hat und die in diesem Lauf nicht berührt wurden, aus dem
 *       Index entfernt ({@link IndexingStatus#PRUNED}). Eine leere, aber gelungene Discovery räumt die Quelle
 *       damit leer. Scheitert die Abfrage der indexierten Ressourcen, meldet der Bericht
 *       {@link IndexingReport#pruneFailed()} und nichts wird entfernt.</li>
 *   <li>Weiterleitungen: Trägt das geladene Dokument eine andere ID als angefragt, wird unter der Ziel-ID
 *       indexiert und die angefragte ID erst danach aus dem Index entfernt; ein Ziel wird je Lauf nur einmal
 *       erfolgreich indexiert, ein gescheiterter Versuch wird bei der nächsten Weiterleitung wiederholt.</li>
 *   <li>Embedding-Welt: Alle Vektoren landen im Namespace {@code space} aus der Konfiguration; der
 *       {@link EmbeddingPort} muss genau diese Welt liefern (geprüft beim Erzeugen).</li>
 *   <li>Embedding-Eingabe ist {@link KnowledgeChunk#textWithHeading()}, in Batches von
 *       {@code embeddingBatchSize} Texten.</li>
 * </ul>
 *
 * <p>{@link #indexResources} indexiert die genannten Ressourcen immer neu und entfernt nichts; es kennt keine
 * Discovery-Revision und keinen vollständigen Stand der Quelle.
 *
 * <p>Blockiert für den ganzen Lauf; Aufrufer aus der Oberfläche starten ihn auf einem eigenen Thread und brechen
 * über {@link IndexingListener#isCancelled()} ab. Zustandslos und threadsicher, soweit die Ports es sind.
 */
public final class IndexKnowledgeUseCase {

    public static final int DEFAULT_EMBEDDING_BATCH_SIZE = 16;

    private final KnowledgeIndexPort index;
    private final EmbeddingPort embeddings;
    private final EmbeddingModelIdentity space;
    private final KnowledgeChunker chunker;
    private final int embeddingBatchSize;

    public IndexKnowledgeUseCase(KnowledgeIndexPort index, EmbeddingPort embeddings, EmbeddingModelIdentity space,
                                 KnowledgeChunker chunker) {
        this(index, embeddings, space, chunker, DEFAULT_EMBEDDING_BATCH_SIZE);
    }

    /**
     * @param space              Namespace im Index, aus der Konfiguration; muss {@code embeddings.modelIdentity()}
     *                           entsprechen
     * @param embeddingBatchSize Texte je {@link EmbeddingPort#embed}-Aufruf, {@code >= 1}
     * @throws IllegalArgumentException bei fehlenden Ports oder wenn der Embedding-Port eine andere Welt liefert
     */
    public IndexKnowledgeUseCase(KnowledgeIndexPort index, EmbeddingPort embeddings, EmbeddingModelIdentity space,
                                 KnowledgeChunker chunker, int embeddingBatchSize) {
        if (index == null || embeddings == null || space == null || chunker == null) {
            throw new IllegalArgumentException("index, embeddings, space und chunker sind Pflicht");
        }
        if (!space.equals(embeddings.modelIdentity())) {
            throw new IllegalArgumentException("EmbeddingPort liefert " + embeddings.modelIdentity()
                    + ", konfiguriert ist " + space);
        }
        if (embeddingBatchSize < 1) {
            throw new IllegalArgumentException("embeddingBatchSize muss >= 1 sein: " + embeddingBatchSize);
        }
        this.index = index;
        this.embeddings = embeddings;
        this.space = space;
        this.chunker = chunker;
        this.embeddingBatchSize = embeddingBatchSize;
    }

    public EmbeddingModelIdentity space() {
        return space;
    }

    /** Ermittelt die Ressourcen im {@code scope} der Quelle und indexiert jede davon. */
    public IndexingReport indexSource(KnowledgeSourcePort source, SourceScope scope, IndexingListener listener) {
        if (source == null || scope == null) {
            throw new IllegalArgumentException("source und scope sind Pflicht");
        }
        IndexingListener observer = listener == null ? IndexingListener.none() : listener;
        List<KnowledgeResource> resources;
        try {
            resources = source.discover(scope);
        } catch (KnowledgeSourceException e) {
            return new IndexingReport(source.sourceId(), 0, Collections.<ResourceIndexingOutcome>emptyList(), false,
                    describe(e));
        }
        observer.onDiscovered(Collections.unmodifiableList(new ArrayList<KnowledgeResource>(resources)));
        List<KnowledgeResourceId> ids = new ArrayList<KnowledgeResourceId>(resources.size());
        Map<KnowledgeResourceId, KnowledgeResource> discovered = new HashMap<KnowledgeResourceId, KnowledgeResource>();
        for (KnowledgeResource resource : resources) {
            ids.add(resource.id());
            discovered.put(resource.id(), resource);
        }
        return run(source, ids, discovered, resources.size(), observer, true);
    }

    /** Indexiert einzelne, bereits bekannte Ressourcen der Quelle neu (z. B. nach einer Änderung). */
    public IndexingReport indexResources(KnowledgeSourcePort source, Collection<KnowledgeResourceId> resourceIds,
                                         IndexingListener listener) {
        if (source == null || resourceIds == null) {
            throw new IllegalArgumentException("source und resourceIds sind Pflicht");
        }
        List<KnowledgeResourceId> ids = new ArrayList<KnowledgeResourceId>(resourceIds);
        return run(source, ids, Collections.<KnowledgeResourceId, KnowledgeResource>emptyMap(), ids.size(),
                listener == null ? IndexingListener.none() : listener, false);
    }

    /** Entfernt alle Chunks einer Quelle aus dem Index, z. B. wenn sie aus der Konfiguration fällt. */
    public void removeSource(KnowledgeSourceId sourceId) {
        index.removeSource(sourceId);
    }

    /**
     * @param discoveredResources die Discovery-Sicht je ID (Revision für das Überspringen); leer bei
     *                            {@link #indexResources}
     * @param prune               am Ende verschwundene Ressourcen entfernen (nur nach vollständiger Discovery)
     */
    private IndexingReport run(KnowledgeSourcePort source, List<KnowledgeResourceId> ids,
                               Map<KnowledgeResourceId, KnowledgeResource> discoveredResources, int discovered,
                               IndexingListener listener, boolean prune) {
        List<ResourceIndexingOutcome> outcomes = new ArrayList<ResourceIndexingOutcome>(ids.size());
        Set<KnowledgeResourceId> done = new HashSet<KnowledgeResourceId>();
        boolean cancelled = false;
        for (KnowledgeResourceId id : ids) {
            if (listener.isCancelled()) {
                cancelled = true;
                break;
            }
            KnowledgeResource known = discoveredResources.get(id);
            ResourceIndexingOutcome outcome;
            if (known != null && isUnchanged(id, known.revision())) {
                done.add(id);
                outcome = ResourceIndexingOutcome.done(id, known.title(), IndexingStatus.UNCHANGED, 0);
            } else {
                outcome = indexOne(source, id, done);
            }
            outcomes.add(outcome);
            listener.onResource(outcome);
        }
        String pruneFailure = null;
        if (prune && !cancelled) {
            Set<KnowledgeResourceId> touched = new HashSet<KnowledgeResourceId>(ids);
            for (ResourceIndexingOutcome outcome : outcomes) {
                touched.add(outcome.resourceId());
            }
            pruneFailure = prune(source.sourceId(), touched, outcomes, listener);
        }
        return new IndexingReport(source.sourceId(), discovered, outcomes, cancelled, null, pruneFailure);
    }

    /** Unverändert nur bei bekannter Discovery-Revision, die der sauber gespeicherten gleicht. */
    private boolean isUnchanged(KnowledgeResourceId id, KnowledgeRevision discovered) {
        if (discovered == null || !discovered.isKnown()) {
            return false;
        }
        try {
            Optional<KnowledgeRevision> stored = index.revisionOf(space, id);
            return stored.isPresent() && stored.get().equals(discovered);
        } catch (KnowledgeIndexException e) {
            // Die Abfrage ist nur eine Abkürzung; im Zweifel neu indexieren.
            return false;
        }
    }

    /**
     * Entfernt die indexierten Ressourcen der Quelle, die dieser Lauf nicht berührt hat (weder entdeckt noch als
     * Weiterleitungsziel geladen).
     *
     * @return Meldung, wenn die Abfrage der indexierten Ressourcen scheitert; sonst {@code null}
     */
    private String prune(KnowledgeSourceId sourceId, Set<KnowledgeResourceId> touched,
                         List<ResourceIndexingOutcome> outcomes, IndexingListener listener) {
        Set<KnowledgeResourceId> indexed;
        try {
            indexed = new LinkedHashSet<KnowledgeResourceId>(index.resourceIds(space, sourceId));
        } catch (KnowledgeIndexException e) {
            return e.getMessage() == null ? "Abfrage der indexierten Ressourcen fehlgeschlagen" : e.getMessage();
        }
        for (KnowledgeResourceId id : indexed) {
            if (touched.contains(id)) {
                continue;
            }
            ResourceIndexingOutcome outcome;
            try {
                index.remove(id);
                outcome = ResourceIndexingOutcome.done(id, "", IndexingStatus.PRUNED, 0);
            } catch (KnowledgeIndexException e) {
                outcome = ResourceIndexingOutcome.failed(id, "", IndexingStage.INDEXING, e.getMessage());
            }
            outcomes.add(outcome);
            listener.onResource(outcome);
        }
        return null;
    }

    private ResourceIndexingOutcome indexOne(KnowledgeSourcePort source, KnowledgeResourceId requested,
                                             Set<KnowledgeResourceId> done) {
        KnowledgeDocument document;
        try {
            document = source.load(requested);
        } catch (KnowledgeSourceException e) {
            if (e.kind() == KnowledgeSourceException.Kind.NOT_FOUND) {
                try {
                    index.remove(requested);
                } catch (KnowledgeIndexException ie) {
                    return ResourceIndexingOutcome.failed(requested, "", IndexingStage.INDEXING, ie.getMessage());
                }
                return ResourceIndexingOutcome.done(requested, "", IndexingStatus.REMOVED, 0);
            }
            return ResourceIndexingOutcome.failed(requested, "", IndexingStage.LOADING, describe(e));
        }
        KnowledgeResource resource = document.resource();
        KnowledgeResourceId target = resource.id();
        if (done.contains(target)) {
            return dropAlias(requested, target, resource,
                    ResourceIndexingOutcome.done(target, resource.title(), IndexingStatus.DUPLICATE, 0));
        }

        List<KnowledgeChunk> chunks = chunker.chunk(document);

        List<KnowledgeIndexEntry> entries = new ArrayList<KnowledgeIndexEntry>(chunks.size());
        try {
            for (int from = 0; from < chunks.size(); from += embeddingBatchSize) {
                List<KnowledgeChunk> slice = chunks.subList(from, Math.min(chunks.size(), from + embeddingBatchSize));
                List<String> texts = new ArrayList<String>(slice.size());
                for (KnowledgeChunk chunk : slice) {
                    texts.add(chunk.textWithHeading());
                }
                EmbeddingBatch batch = embeddings.embed(texts);
                for (int i = 0; i < slice.size(); i++) {
                    EmbeddingVector vector = batch.get(i);
                    space.requireSameWorldAs(vector.identity());
                    entries.add(KnowledgeIndexEntry.of(resource, slice.get(i), vector));
                }
            }
        } catch (EmbeddingException e) {
            return ResourceIndexingOutcome.failed(target, resource.title(), IndexingStage.EMBEDDING,
                    e.kind() + ": " + e.getMessage());
        } catch (EmbeddingWorldMismatchException e) {
            // Der Port hat seine Welt gewechselt; niemals in einen fremden Namespace schreiben.
            return ResourceIndexingOutcome.failed(target, resource.title(), IndexingStage.EMBEDDING, e.getMessage());
        }

        try {
            index.replace(space, target, entries);
        } catch (KnowledgeIndexException e) {
            return ResourceIndexingOutcome.failed(target, resource.title(), IndexingStage.INDEXING, e.getMessage());
        }
        done.add(target);
        return dropAlias(requested, target, resource, ResourceIndexingOutcome.done(target, resource.title(),
                entries.isEmpty() ? IndexingStatus.EMPTY : IndexingStatus.INDEXED, entries.size()));
    }

    /**
     * Nach einer Weiterleitung die angefragte ID aus dem Index nehmen – erst, wenn das Ziel erfolgreich indexiert
     * ist, damit ein gescheiterter Lauf keine durchsuchbaren Daten verliert.
     */
    private ResourceIndexingOutcome dropAlias(KnowledgeResourceId requested, KnowledgeResourceId target,
                                              KnowledgeResource resource, ResourceIndexingOutcome success) {
        if (target.equals(requested)) {
            return success;
        }
        try {
            index.remove(requested);
        } catch (KnowledgeIndexException e) {
            return ResourceIndexingOutcome.failed(target, resource.title(), IndexingStage.INDEXING, e.getMessage());
        }
        return success;
    }

    private static String describe(KnowledgeSourceException e) {
        return e.kind() + ": " + e.getMessage();
    }
}
