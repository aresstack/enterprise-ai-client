package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceRef;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.IndexedResourceState;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Resource-Resolver (corenth Chalcotheca: Ressourcen über ihre Bookmark-Referenz anfordern, der Index ist eine
 * wiederaufbaubare Projektion, die Quelle bleibt führend): eine Resource-ID wird zuerst aus dem Index beantwortet –
 * Referenz ({@link KnowledgeResourceRef}) und die schon aufbereiteten Passagen mit ihren {@link KnowledgePassageId}s.
 * Gelesen wird über {@link LoadKnowledgeDocumentUseCase} und damit über die corenth-Kette
 * {@code KnowledgeBookmarks} → {@code MediatedKnowledgeDocuments} → {@code MediatedResourceService}. Die Quelle wird nur gefragt, wenn der Index die Ressource nicht kennt, ihre Revision unbekannt ist, ein Suchtreffer
 * inzwischen eine andere Revision meldete oder die Zerlegung sich geändert hat. Dann lädt der Priority-Lader, der
 * Index-Thread prüft den Hash, zerlegt bei Bedarf neu, und geliefert werden wieder die Passagen aus dem Index.
 */
public final class ResolveKnowledgeResourceUseCase {

    /** Wie lange das Lesen höchstens auf die Aufbereitung einer neu geholten Seite wartet. */
    public static final long INDEX_WAIT_MILLIS = 60_000L;

    private final IndexKnowledgeUseCase indexing;
    private final KnowledgePrefetchService prefetch;
    private final LoadKnowledgeDocumentUseCase documents;

    public ResolveKnowledgeResourceUseCase(IndexKnowledgeUseCase indexing, KnowledgePrefetchService prefetch,
                                           LoadKnowledgeDocumentUseCase documents) {
        if (indexing == null || prefetch == null || documents == null) {
            throw new IllegalArgumentException("indexing, prefetch und documents sind Pflicht");
        }
        this.indexing = indexing;
        this.prefetch = prefetch;
        this.documents = documents;
    }

    /** Löst eine Resource-ID auf: Index zuerst, Quelle nur bei unbekanntem oder geändertem Stand. */
    public ResolvedResource resolve(final KnowledgeResourceId id)
            throws KnowledgeSourceException, KnowledgeDocumentNotIndexedException {
        if (id == null) {
            throw new IllegalArgumentException("id fehlt");
        }
        ResolvedResource indexed = fromIndex(id, null);
        if (indexed != null) {
            return indexed;
        }
        // Kennt der Index die Ressource schon (nur Stand unklar oder geändert), wird gezielt neu geholt
        // (REFRESH_EXTERNAL an der Bronze-Kopie vorbei); sonst normal gelesen (Bronze-Kopie oder Quelle).
        final boolean stale = indexing.stateOf(id).isPresent();
        KnowledgeDocument document = stale ? prefetch.refresh(id, () -> documents.refresh(id))
                : prefetch.read(id, () -> documents.load(id));
        if (prefetch.indexNow(document, INDEX_WAIT_MILLIS) != null) {
            ResolvedResource refreshed = fromIndex(document.id(), document);
            if (refreshed != null) {
                return refreshed;
            }
        }
        return ResolvedResource.unprepared(document);
    }

    /**
     * Die Passagen aus dem Index, wenn sein Stand gilt; sonst {@code null}. Mit {@code loaded} gilt der Stand, wenn
     * er genau zu diesem gerade geladenen Text passt.
     */
    private ResolvedResource fromIndex(KnowledgeResourceId id, KnowledgeDocument loaded) {
        Optional<IndexedResourceState> state = indexing.stateOf(id);
        if (!state.isPresent() || !indexing.isPrepared(state.get())) {
            return null;
        }
        if (loaded != null) {
            if (!state.get().contentHash().equals(loaded.contentHash())) {
                return null;
            }
        } else {
            KnowledgeRevision indexed = state.get().revision();
            KnowledgeRevision seen = prefetch.seenRevision(id);
            if (!indexed.isKnown() || seen != null && !seen.equals(indexed)) {
                return null; // Stand unklar oder laut Suche geändert: neu holen, der Hash entscheidet
            }
        }
        List<KnowledgeIndexEntry> entries = indexing.entriesOf(id);
        if (entries.isEmpty()) {
            return null;
        }
        KnowledgeResource resource = entries.get(0).resource();
        List<Passage> passages = new ArrayList<Passage>(entries.size());
        for (KnowledgeIndexEntry entry : entries) {
            Optional<KnowledgePassageId> passageId = indexing.passageId(entry);
            if (!passageId.isPresent()) {
                return null; // Eintrag ohne Fassung (Altbestand): neu holen und aufbereiten
            }
            passages.add(new Passage(passageId.get(), entry.chunk().headingLine(), entry.chunk().text()));
        }
        KnowledgeResourceRef ref = new KnowledgeResourceRef(resource.sourceId(), resource.id(),
                resource.location().orElse(null), state.get().revision(), state.get().contentHash());
        return new ResolvedResource(ref, resource.title(), loaded == null, passages, null);
    }

    /** Ergebnis: Referenz, Titel, woher (Index oder frisch aus der Quelle) und die Passagen. */
    public static final class ResolvedResource {

        private final KnowledgeResourceRef ref;
        private final String title;
        private final boolean fromIndex;
        private final List<Passage> passages;
        private final KnowledgeDocument unprepared;

        ResolvedResource(KnowledgeResourceRef ref, String title, boolean fromIndex, List<Passage> passages,
                         KnowledgeDocument unprepared) {
            this.ref = ref;
            this.title = title == null ? "" : title;
            this.fromIndex = fromIndex;
            this.passages = Collections.unmodifiableList(passages);
            this.unprepared = unprepared;
        }

        static ResolvedResource unprepared(KnowledgeDocument document) {
            return new ResolvedResource(KnowledgeResourceRef.of(document), document.resource().title(), false,
                    Collections.<Passage>emptyList(), document);
        }

        public KnowledgeResourceRef ref() {
            return ref;
        }

        public String title() {
            return title;
        }

        /** {@code true}: ohne Quellenzugriff aus dem Index beantwortet. */
        public boolean fromIndex() {
            return fromIndex;
        }

        /** Die aufbereiteten Passagen in Reihenfolge; leer, wenn die Aufbereitung (noch) nicht gelang. */
        public List<Passage> passages() {
            return passages;
        }

        /** Der rohe Text, nur wenn keine Passagen vorliegen (z. B. Embedding-Dienst nicht erreichbar); sonst leer. */
        public Optional<KnowledgeDocument> unpreparedDocument() {
            return Optional.ofNullable(unprepared);
        }
    }

    /** Eine aufbereitete Passage mit ihrer stabilen ID. */
    public static final class Passage {

        private final KnowledgePassageId id;
        private final String heading;
        private final String text;

        Passage(KnowledgePassageId id, String heading, String text) {
            this.id = id;
            this.heading = heading == null ? "" : heading;
            this.text = text == null ? "" : text;
        }

        public KnowledgePassageId id() {
            return id;
        }

        public String heading() {
            return heading;
        }

        public String text() {
            return text;
        }
    }
}
