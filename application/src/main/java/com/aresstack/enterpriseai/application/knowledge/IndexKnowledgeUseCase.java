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
import com.aresstack.enterpriseai.knowledge.api.IndexedResourceState;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceScope;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgePassageId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeTokenCounter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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
 *       eigenen Namespace entfernt ({@link IndexingStatus#PRUNED}; leeres {@link KnowledgeIndexPort#replace},
 *       andere Embedding-Welten bleiben unberührt). Eine leere, aber gelungene Discovery räumt die Quelle damit
 *       leer. Scheitert die Abfrage der indexierten Ressourcen, meldet der Bericht
 *       {@link IndexingReport#pruneFailed()} und nichts wird entfernt. Ein Abbruch nach der letzten Ressource
 *       oder während der Bereinigung beendet sie; der Bericht ist dann abgebrochen.</li>
 *   <li>Weiterleitungen: Trägt das geladene Dokument eine andere ID als angefragt, wird unter der Ziel-ID
 *       indexiert und die angefragte ID erst danach aus dem Index entfernt; ein Ziel wird je Lauf nur einmal
 *       erfolgreich indexiert, ein gescheiterter Versuch wird bei der nächsten Weiterleitung wiederholt.</li>
 *   <li>Embedding-Welt: Alle Vektoren landen im Namespace {@code space} aus der Konfiguration; der
 *       {@link EmbeddingPort} muss genau diese Welt liefern (geprüft beim Erzeugen).</li>
 *   <li>Embedding-Eingabe ist {@link KnowledgeChunk#textWithHeading()}, in Batches von
 *       {@code embeddingBatchSize} Texten.</li>
 * </ul>
 *
 * <p>{@link #indexResources} indexiert die genannten Ressourcen immer neu (kein Überspringen, es kennt keine
 * Discovery-Revision) und bereinigt nicht anhand der Discovery (es kennt keinen vollständigen Stand der Quelle).
 * Wie bisher entfernt es die Chunks einer genannten Ressource, die beim Laden {@code NOT_FOUND} meldet oder leer
 * ist.
 *
 * <p>{@link #indexIfChanged} und {@link #indexDocumentIfChanged} sind der suchgesteuerte Weg (MainframeMate): erst
 * die Revision aus dem Suchtreffer mit der indexierten vergleichen, dann bei Bedarf laden und den
 * {@link KnowledgeDocument#contentHash() Inhalts-Hash} vergleichen. Nur bei geändertem Text wird zerlegt, eingebettet
 * und ersetzt; bei gleichem Text und neuer Revision wird nur der Stand an den vorhandenen Vektoren nachgetragen. Jede
 * Indexierung legt den Hash in den Metadaten der Ressource ab ({@link IndexedResourceState#CONTENT_HASH_KEY}).
 *
 * <p>Blockiert für den ganzen Lauf; Aufrufer aus der Oberfläche starten ihn auf einem eigenen Thread und brechen
 * über {@link IndexingListener#isCancelled()} ab. Zustandslos und threadsicher, soweit die Ports es sind.
 */
public final class IndexKnowledgeUseCase {

    public static final int DEFAULT_EMBEDDING_BATCH_SIZE = 16;

    private final KnowledgeIndexPort index;
    private final EmbeddingPort embeddings;
    private final Supplier<EmbeddingModelIdentity> space;
    private final KnowledgeChunker chunker;
    private final int embeddingBatchSize;
    /** Passagen wie askai arch statt fester Chunks; {@code null}: {@link KnowledgeChunker}. */
    private final KnowledgePassageSplitter passages;
    private final KnowledgeTokenCounter tokenCounter = KnowledgeTokenCounter.wordsAndSymbols();

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
        this.space = () -> space;
        this.chunker = chunker;
        this.embeddingBatchSize = embeddingBatchSize;
        this.passages = null;
    }

    /**
     * Mit wechselnder Embedding-Welt (Einstellungen gelten ohne Neustart): Namespace und Port werden je Aufruf
     * gelesen; {@code space} muss dieselbe Welt liefern wie {@code embeddings.modelIdentity()}.
     */
    public IndexKnowledgeUseCase(KnowledgeIndexPort index, EmbeddingPort embeddings,
                                 Supplier<EmbeddingModelIdentity> space, KnowledgeChunker chunker,
                                 int embeddingBatchSize) {
        if (index == null || embeddings == null || space == null || chunker == null) {
            throw new IllegalArgumentException("index, embeddings, space und chunker sind Pflicht");
        }
        if (embeddingBatchSize < 1) {
            throw new IllegalArgumentException("embeddingBatchSize muss >= 1 sein: " + embeddingBatchSize);
        }
        this.index = index;
        this.embeddings = embeddings;
        this.space = space;
        this.chunker = chunker;
        this.embeddingBatchSize = embeddingBatchSize;
        this.passages = null;
    }

    private IndexKnowledgeUseCase(IndexKnowledgeUseCase base, KnowledgePassageSplitter passages) {
        this.index = base.index;
        this.embeddings = base.embeddings;
        this.space = base.space;
        this.chunker = base.chunker;
        this.embeddingBatchSize = base.embeddingBatchSize;
        this.passages = passages;
    }

    /**
     * Derselbe Use Case, der Dokumente wie askai arch in semantische Passagen zerlegt (Struktur zuerst, Satzgrenzen
     * aus dem Satzmodell, Themenwechsel über Satzfenster, eigenes Embedding je Passage) statt in feste Chunks.
     */
    public IndexKnowledgeUseCase withPassages(KnowledgePassageSplitter value) {
        if (value == null) {
            throw new IllegalArgumentException("passages must not be null");
        }
        return new IndexKnowledgeUseCase(this, value);
    }

    /**
     * Fingerabdruck der Zerlegung, mit dem indexiert wird. Unverändert wird eine Ressource nur übersprungen, wenn Text
     * (Hash), Zerlegung (dieser Wert) und Embedding-Modell (Namensraum) gleich sind.
     */
    public String segmentationFingerprint() {
        return passages != null ? passages.fingerprint() : "chunker;" + chunker.fingerprint();
    }

    public EmbeddingModelIdentity space() {
        return space.get();
    }

    /** Kurzform (8 Hex) des Zerlegungs-Fingerabdrucks; Teil jeder Passage-ID. */
    public String segmentationTag() {
        return KnowledgePassageId.versionTag(segmentationFingerprint());
    }

    /** Kurzform (8 Hex) des Embedding-Fingerabdrucks (Modell, Dimension, Attribute); Teil jeder Passage-ID. */
    public String embeddingTag() {
        return KnowledgePassageId.versionTag(space().fingerprint());
    }

    /** Die Passage-ID eines im aktuellen Namensraum gespeicherten Abschnitts. */
    public KnowledgePassageId passageId(KnowledgeChunkId chunk) {
        return KnowledgePassageId.of(chunk.resourceId(), chunk.ordinal(), segmentationTag(), embeddingTag());
    }

    /** {@code true}, wenn der Indexstand mit der aktuellen Zerlegung entstand (sonst muss neu aufbereitet werden). */
    public boolean isPrepared(IndexedResourceState state) {
        return state != null && state.segmentation().equals(segmentationFingerprint());
    }

    /** Die aufbereiteten Passagen einer Ressource im aktuellen Namensraum, nach Position; leer, wenn unbekannt. */
    public List<KnowledgeIndexEntry> entriesOf(KnowledgeResourceId id) {
        try {
            return index.entriesOf(space(), id);
        } catch (KnowledgeIndexException e) {
            return Collections.emptyList();
        }
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

    /**
     * Suchgesteuert: indexiert die genannten Treffer der Quelle nur, wenn sie neu oder geändert sind (Revision, dann
     * Inhalts-Hash). Keine Discovery, keine Bereinigung.
     *
     * @param hits Ressourcen in Rangfolge mit der Revision laut Suche ({@link KnowledgeRevision#unknown()}, wenn
     *             die Suche keine nennt)
     */
    public IndexingReport indexIfChanged(KnowledgeSourcePort source, Map<KnowledgeResourceId, KnowledgeRevision> hits,
                                         IndexingListener listener) {
        if (source == null || hits == null) {
            throw new IllegalArgumentException("source und hits sind Pflicht");
        }
        IndexingListener observer = listener == null ? IndexingListener.none() : listener;
        Map<KnowledgeResourceId, KnowledgeRevision> ordered = new LinkedHashMap<KnowledgeResourceId, KnowledgeRevision>(hits);
        List<ResourceIndexingOutcome> outcomes = new ArrayList<ResourceIndexingOutcome>(ordered.size());
        Set<KnowledgeResourceId> done = new HashSet<KnowledgeResourceId>();
        boolean cancelled = false;
        for (Map.Entry<KnowledgeResourceId, KnowledgeRevision> hit : ordered.entrySet()) {
            if (observer.isCancelled()) {
                cancelled = true;
                break;
            }
            KnowledgeResourceId id = hit.getKey();
            ResourceIndexingOutcome outcome;
            if (isUnchanged(id, hit.getValue())) {
                outcome = ResourceIndexingOutcome.done(id, "", IndexingStatus.UNCHANGED, 0);
            } else {
                KnowledgeDocument document;
                try {
                    document = source.load(id);
                    outcome = indexLoadedIfChanged(id, document, done);
                } catch (KnowledgeSourceException e) {
                    outcome = loadFailed(id, e);
                }
            }
            outcomes.add(outcome);
            observer.onResource(outcome);
        }
        return new IndexingReport(source.sourceId(), ordered.size(), outcomes, cancelled, null, null);
    }

    /**
     * Ein schon geladenes Dokument (z. B. beim Lesen eines Suchtreffers) indexieren, wenn sein Text neu oder geändert
     * ist; sonst nur den Stand nachtragen.
     */
    public ResourceIndexingOutcome indexDocumentIfChanged(KnowledgeDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document ist Pflicht");
        }
        return indexLoadedIfChanged(document.id(), document, new HashSet<KnowledgeResourceId>());
    }

    /**
     * {@code true}, wenn die Ressource im aktuellen Namespace mit genau dieser (bekannten) Revision indexiert ist: dann
     * muss sie weder geladen noch neu indexiert werden.
     */
    public boolean isCurrent(KnowledgeResourceId id, KnowledgeRevision revision) {
        return id != null && isUnchanged(id, revision);
    }

    /** Was der Index im aktuellen Namespace über die Ressource weiß (Revision, Inhalts-Hash). */
    public Optional<IndexedResourceState> stateOf(KnowledgeResourceId id) {
        try {
            return index.stateOf(space(), id);
        } catch (KnowledgeIndexException e) {
            return Optional.empty();
        }
    }

    private ResourceIndexingOutcome indexLoadedIfChanged(KnowledgeResourceId requested, KnowledgeDocument document,
                                                         Set<KnowledgeResourceId> done) {
        KnowledgeResource resource = document.resource();
        Optional<IndexedResourceState> state = stateOf(resource.id());
        if (state.isPresent() && state.get().hasContentHash()
                && state.get().contentHash().equals(document.contentHash())
                && state.get().segmentation().equals(segmentationFingerprint())) {
            if (!state.get().revision().equals(resource.revision())) {
                restamp(resource, document.contentHash());
            }
            done.add(resource.id());
            return ResourceIndexingOutcome.done(resource.id(), resource.title(), IndexingStatus.UNCHANGED, 0);
        }
        return indexLoaded(requested, document, done);
    }

    /** Gleicher Text, neuer Stand: vorhandene Vektoren mit der neuen Revision neu ablegen, ohne zu embedden. */
    private void restamp(KnowledgeResource resource, String contentHash) {
        try {
            KnowledgeResource stamped = withHash(resource, contentHash);
            List<KnowledgeIndexEntry> entries = new ArrayList<KnowledgeIndexEntry>();
            for (KnowledgeIndexEntry entry : index.entriesOf(space(), resource.id())) {
                entries.add(KnowledgeIndexEntry.of(stamped, entry.chunk(), entry.embedding()));
            }
            if (!entries.isEmpty()) {
                index.replace(space(), resource.id(), entries);
            }
        } catch (KnowledgeIndexException | IllegalArgumentException e) {
            // Nur der Stand bleibt alt; der nächste Vergleich lädt die Seite dann erneut und spart wieder per Hash.
        }
    }

    private KnowledgeResource withHash(KnowledgeResource resource, String contentHash) {
        return resource.toBuilder()
                .metadata(resource.metadata().with(IndexedResourceState.CONTENT_HASH_KEY, contentHash)
                        .with(IndexedResourceState.SEGMENTATION_KEY, segmentationFingerprint())).build();
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
            if (listener.isCancelled()) {
                cancelled = true; // Abbruch nach der letzten Ressource: nichts mehr entfernen
            } else {
                Set<KnowledgeResourceId> touched = new HashSet<KnowledgeResourceId>(ids);
                for (ResourceIndexingOutcome outcome : outcomes) {
                    touched.add(outcome.resourceId());
                }
                List<KnowledgeResourceId> stale;
                try {
                    stale = staleResources(source.sourceId(), touched);
                } catch (KnowledgeIndexException e) {
                    stale = Collections.emptyList();
                    pruneFailure = e.getMessage() == null ? "Abfrage der indexierten Ressourcen fehlgeschlagen"
                            : e.getMessage();
                }
                for (KnowledgeResourceId id : stale) {
                    if (listener.isCancelled()) {
                        cancelled = true;
                        break;
                    }
                    ResourceIndexingOutcome outcome = pruneOne(id);
                    outcomes.add(outcome);
                    listener.onResource(outcome);
                }
            }
        }
        return new IndexingReport(source.sourceId(), discovered, outcomes, cancelled, null, pruneFailure);
    }

    /** Unverändert nur bei bekannter Discovery-Revision, die der sauber gespeicherten gleicht. */
    private boolean isUnchanged(KnowledgeResourceId id, KnowledgeRevision discovered) {
        if (discovered == null || !discovered.isKnown()) {
            return false;
        }
        try {
            // Gleiche Revision genügt nur mit gleicher Zerlegung (ein anderes Verfahren baut neu).
            Optional<IndexedResourceState> stored = index.stateOf(space(), id);
            return stored.isPresent() && stored.get().revision().equals(discovered)
                    && stored.get().segmentation().equals(segmentationFingerprint());
        } catch (KnowledgeIndexException e) {
            // Die Abfrage ist nur eine Abkürzung; im Zweifel neu indexieren.
            return false;
        }
    }

    /**
     * Die im eigenen Namespace indexierten Ressourcen der Quelle, die dieser Lauf nicht berührt hat (weder entdeckt
     * noch als Weiterleitungsziel geladen), in Indexreihenfolge.
     *
     * @throws KnowledgeIndexException wenn der Index die Abfrage nicht beantworten kann
     */
    private List<KnowledgeResourceId> staleResources(KnowledgeSourceId sourceId, Set<KnowledgeResourceId> touched) {
        List<KnowledgeResourceId> stale = new ArrayList<KnowledgeResourceId>();
        for (KnowledgeResourceId id : index.resourceIds(space(), sourceId)) {
            if (!touched.contains(id)) {
                stale.add(id);
            }
        }
        return stale;
    }

    /**
     * Entfernt die Chunks der Ressource nur aus dem eigenen Namespace (leeres {@code replace}); eine andere
     * Embedding-Welt, die dieselbe Ressource hält, bereinigt sich in ihrem eigenen Lauf.
     */
    private ResourceIndexingOutcome pruneOne(KnowledgeResourceId id) {
        try {
            index.replace(space(), id, Collections.<KnowledgeIndexEntry>emptyList());
            return ResourceIndexingOutcome.done(id, "", IndexingStatus.PRUNED, 0);
        } catch (KnowledgeIndexException e) {
            return ResourceIndexingOutcome.failed(id, "", IndexingStage.INDEXING, e.getMessage());
        }
    }

    private ResourceIndexingOutcome indexOne(KnowledgeSourcePort source, KnowledgeResourceId requested,
                                             Set<KnowledgeResourceId> done) {
        KnowledgeDocument document;
        try {
            document = source.load(requested);
        } catch (KnowledgeSourceException e) {
            return loadFailed(requested, e);
        }
        return indexLoaded(requested, document, done);
    }

    /** {@code NOT_FOUND} entfernt die Ressource aus dem Index; jeder andere Ladefehler betrifft nur sie. */
    private ResourceIndexingOutcome loadFailed(KnowledgeResourceId requested, KnowledgeSourceException e) {
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

    private ResourceIndexingOutcome indexLoaded(KnowledgeResourceId requested, KnowledgeDocument document,
                                                Set<KnowledgeResourceId> done) {
        KnowledgeResource resource = withHash(document.resource(), document.contentHash());
        KnowledgeResourceId target = resource.id();
        if (done.contains(target)) {
            return dropAlias(requested, target, resource,
                    ResourceIndexingOutcome.done(target, resource.title(), IndexingStatus.DUPLICATE, 0));
        }

        if (passages != null) {
            return indexPassages(requested, target, resource, document, done);
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
                    space().requireSameWorldAs(vector.identity());
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
            index.replace(space(), target, entries);
        } catch (KnowledgeIndexException e) {
            return ResourceIndexingOutcome.failed(target, resource.title(), IndexingStage.INDEXING, e.getMessage());
        }
        done.add(target);
        return dropAlias(requested, target, resource, ResourceIndexingOutcome.done(target, resource.title(),
                entries.isEmpty() ? IndexingStatus.EMPTY : IndexingStatus.INDEXED, entries.size()));
    }

    /**
     * askai arch: Struktur → Sätze → semantische Passagen, jede mit eigenem Embedding; je Passage ein Eintrag
     * (Ordinalzahl = Lesereihenfolge, Abschnittspfad aus den Überschriften).
     */
    private ResourceIndexingOutcome indexPassages(KnowledgeResourceId requested, KnowledgeResourceId target,
                                                  KnowledgeResource resource, KnowledgeDocument document,
                                                  Set<KnowledgeResourceId> done) {
        List<KnowledgeIndexEntry> entries = new ArrayList<KnowledgeIndexEntry>();
        try {
            List<KnowledgePassageSplitter.Passage> found = document.isBlank()
                    ? Collections.<KnowledgePassageSplitter.Passage>emptyList()
                    : passages.split(target.value(), document.text());
            for (int i = 0; i < found.size(); i++) {
                KnowledgePassageSplitter.Passage passage = found.get(i);
                KnowledgeChunk chunk = new KnowledgeChunk(KnowledgeChunkId.of(target, i), resource.sourceId(),
                        headings(passage.headingPath()), passage.text(), tokenCounter.count(passage.text()));
                EmbeddingVector vector = EmbeddingVector.of(embeddings.modelIdentity(), passage.vector());
                space().requireSameWorldAs(vector.identity());
                entries.add(KnowledgeIndexEntry.of(resource, chunk, vector));
            }
        } catch (EmbeddingException e) {
            return ResourceIndexingOutcome.failed(target, resource.title(), IndexingStage.EMBEDDING,
                    e.kind() + ": " + e.getMessage());
        } catch (EmbeddingWorldMismatchException e) {
            return ResourceIndexingOutcome.failed(target, resource.title(), IndexingStage.EMBEDDING, e.getMessage());
        }
        try {
            index.replace(space(), target, entries);
        } catch (KnowledgeIndexException e) {
            return ResourceIndexingOutcome.failed(target, resource.title(), IndexingStage.INDEXING, e.getMessage());
        }
        done.add(target);
        return dropAlias(requested, target, resource, ResourceIndexingOutcome.done(target, resource.title(),
                entries.isEmpty() ? IndexingStatus.EMPTY : IndexingStatus.INDEXED, entries.size()));
    }

    private static List<String> headings(String path) {
        List<String> headings = new ArrayList<String>();
        for (String heading : path.split(" > ")) {
            if (!heading.trim().isEmpty()) {
                headings.add(heading.trim());
            }
        }
        return headings;
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
