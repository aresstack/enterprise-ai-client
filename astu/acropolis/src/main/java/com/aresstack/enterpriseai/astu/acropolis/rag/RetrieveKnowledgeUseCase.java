package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.passage.PassageSegmentation;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeTokenCounter;
import com.aresstack.enterpriseai.astu.knowledge.ProjectKnowledge;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexException;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelation;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationKind;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationsPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeSearchHit;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeSemanticQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeSearchMode;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.IndexedResourceState;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeDocument;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeHit;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawTextWindows;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Use Case "Wissen abrufen": hybride Suche über den {@link KnowledgeIndexPort} – Volltext (BM25) und semantisch
 * (Anfrage über den {@link EmbeddingPort} vektorisieren, Cosine) – und Fusion beider Ranglisten per
 * {@link ReciprocalRankFusion}.
 *
 * <p>Beide Pfade suchen im Namespace {@code space}, der {@link EmbeddingModelIdentity} aus der Konfiguration. Sie
 * wird unverändert an den Port gereicht; der Embedding-Port muss genau diese Welt liefern (geprüft beim
 * Erzeugen), damit nie Vektoren verschiedener Welten verglichen werden.
 *
 * <p>Ausfall eines Pfades: Wirft der Embedding-Port oder der Index in einem Pfad, liefert der andere Pfad das
 * Ergebnis allein und der Ausfall steht als {@link RetrievalWarning} im Ergebnis (Vorbild MainframeMate
 * {@code HybridRetriever}, der dort nur loggt). Fallen alle aktiven Pfade aus, wirft der Use Case
 * {@link KnowledgeRetrievalException}. Programmierfehler (z. B. {@code IllegalArgumentException}) werden nicht
 * abgefangen.
 *
 * <p>Nachbarschaft: Mit {@link #withRelations} verdrahtet, werden die besten fusionierten Treffer vor dem Kürzen um
 * ihre 1-Hop-Nachbarn aus dem {@link KnowledgeRelationsPort} ergänzt (siehe {@link RetrievalSettings}). Die
 * Erweiterung ist eine Zugabe: Scheitert sie, bleibt das Ergebnis der Suche unverändert. Ein Reranker über die
 * erweiterte Menge hätte seinen Platz direkt nach der Erweiterung; die Kategorie RERANK ist noch nicht ausführbar
 * angebunden.
 *
 * <p>Länge der Anfrage: Für die semantische Suche wird eine Anfrage über {@link #MAX_QUERY_TOKENS} Token an einer
 * Wortgrenze gekürzt, statt den Embedding-Dienst mit einem Text über dessen Tokengrenze scheitern zu lassen (E5:
 * 512). Die Grenze ist dieselbe wie die der indexierten Passagen ({@code PassageSegmentation}); die Volltextsuche
 * bekommt die ganze Anfrage. Kürzungen und ausgefallene Suchpfade meldet der {@link RetrievalObserver}
 * ({@link #withObserver}), damit sie im technischen Log stehen.
 *
 * <p>Rohindex ({@link #withRawDocuments}): Geladene Seiten, die noch nicht (oder in anderer Fassung) semantisch
 * aufbereitet sind, findet der Volltextpfad zusätzlich im {@link RawKnowledgeIndexPort}, je Seite mit ihren
 * passendsten Textfenstern ({@link RetrievalSettings#rawWindows()}). Die Fenster stehen abwechselnd mit den
 * Volltexttreffern der Passagen in der Volltextliste; ist eine Seite in dieser Fassung schon aufbereitet, zählen nur
 * ihre Passagen. So ist eine neu gefundene Seite durchsuchbar, sobald sie geladen ist, ohne auf Passagenbildung und
 * Embeddings zu warten.
 *
 * <p>Blockiert für die Dauer von Embedding-Aufruf und Indexsuche; Aufrufer aus der Oberfläche rufen ihn nicht auf
 * dem Event-Thread auf. Zustandslos und threadsicher, soweit die Ports es sind.
 */
public final class RetrieveKnowledgeUseCase {

    /** Längste Anfrage für das Embedding; dieselbe Grenze wie für indexierte Passagen (eine Quelle, kein Duplikat). */
    public static final int MAX_QUERY_TOKENS = PassageSegmentation.MAX_PASSAGE_TOKENS;

    private static final KnowledgeTokenCounter QUERY_TOKENS = KnowledgeTokenCounter.wordsAndSymbols();

    private final KnowledgeIndexPort index;
    private final EmbeddingPort embeddings;
    private final Supplier<EmbeddingModelIdentity> space;
    private final RetrievalSettings settings;
    private final KnowledgeRelationsPort relations;
    private final Supplier<RetrievalSettings> relationSettings;
    private final RetrievalObserver observer;
    /** Rohindex der geladenen Seiten oder {@code null}. */
    private final RawKnowledgeIndexPort raw;

    /**
     * @param index      der Wissensindex
     * @param embeddings Port für die Anfragevektoren; darf nur fehlen, wenn die semantische Suche aus ist
     * @param space      Namespace beider Suchen, z. B. {@code embeddings.modelIdentity()} aus der Konfiguration
     * @param settings   Suchpfade, Kandidatenzahlen und Fusion; {@code null} für {@link RetrievalSettings#defaults()}
     * @throws IllegalArgumentException wenn der Embedding-Port fehlt oder eine andere Welt als {@code space} liefert
     */
    public RetrieveKnowledgeUseCase(KnowledgeIndexPort index, EmbeddingPort embeddings, EmbeddingModelIdentity space,
                                    RetrievalSettings settings) {
        if (index == null) {
            throw new IllegalArgumentException("index must not be null");
        }
        if (space == null) {
            throw new IllegalArgumentException("space (EmbeddingModelIdentity) must not be null");
        }
        RetrievalSettings effective = settings == null ? RetrievalSettings.defaults() : settings;
        if (effective.semanticEnabled()) {
            if (embeddings == null) {
                throw new IllegalArgumentException("semantische Suche aktiv, aber kein EmbeddingPort");
            }
            if (!space.equals(embeddings.modelIdentity())) {
                throw new IllegalArgumentException("EmbeddingPort liefert " + embeddings.modelIdentity()
                        + ", konfiguriert ist " + space);
            }
        }
        this.index = index;
        this.embeddings = embeddings;
        this.space = () -> space;
        this.settings = effective;
        this.relations = null;
        this.relationSettings = null;
        this.observer = RetrievalObserver.none();
        this.raw = null;
    }

    /**
     * Mit wechselnder Embedding-Welt (Einstellungen gelten ohne Neustart): Namespace und Port werden je Suche
     * gelesen; {@code space} muss dieselbe Welt liefern wie {@code embeddings.modelIdentity()}.
     */
    public RetrieveKnowledgeUseCase(KnowledgeIndexPort index, EmbeddingPort embeddings,
                                    Supplier<EmbeddingModelIdentity> space, RetrievalSettings settings) {
        if (index == null || space == null) {
            throw new IllegalArgumentException("index and space must not be null");
        }
        RetrievalSettings effective = settings == null ? RetrievalSettings.defaults() : settings;
        if (effective.semanticEnabled() && embeddings == null) {
            throw new IllegalArgumentException("semantische Suche aktiv, aber kein EmbeddingPort");
        }
        this.index = index;
        this.embeddings = embeddings;
        this.space = space;
        this.settings = effective;
        this.relations = null;
        this.relationSettings = null;
        this.observer = RetrievalObserver.none();
        this.raw = null;
    }

    private RetrieveKnowledgeUseCase(RetrieveKnowledgeUseCase base, KnowledgeRelationsPort relations,
                                     Supplier<RetrievalSettings> relationSettings, RetrievalObserver observer) {
        this(base, relations, relationSettings, observer, base.raw);
    }

    private RetrieveKnowledgeUseCase(RetrieveKnowledgeUseCase base, KnowledgeRelationsPort relations,
                                     Supplier<RetrievalSettings> relationSettings, RetrievalObserver observer,
                                     RawKnowledgeIndexPort raw) {
        this.index = base.index;
        this.embeddings = base.embeddings;
        this.space = base.space;
        this.settings = base.settings;
        this.relations = relations;
        this.relationSettings = relationSettings;
        this.observer = observer;
        this.raw = raw;
    }

    /**
     * Kopie, deren Volltextpfad zusätzlich im Rohindex sucht: geladene Seiten, deren aktuelle Fassung noch nicht
     * semantisch aufbereitet ist, kommen mit ihren passendsten Textfenstern in die Treffer.
     */
    public RetrieveKnowledgeUseCase withRawDocuments(RawKnowledgeIndexPort raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw must not be null");
        }
        return new RetrieveKnowledgeUseCase(this, relations, relationSettings, observer, raw);
    }

    /** Kopie, die Kürzungen und ausgefallene Suchpfade an {@code observer} meldet. */
    public RetrieveKnowledgeUseCase withObserver(RetrievalObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("observer must not be null");
        }
        return new RetrieveKnowledgeUseCase(this, relations, relationSettings, observer);
    }

    /**
     * Kopie mit Nachbarschaftserweiterung über {@code relations}. Alle Werte (Suchpfade, Kandidatenzahlen, Schwelle,
     * Fusion, {@code relation*}, {@link RetrievalSettings#maxResults()}) werden je Suche aus {@code liveSettings}
     * gelesen (Einstellungen gelten ohne Neustart); {@code null} nimmt die eigenen Settings. Eine eingeschaltete
     * semantische Suche ohne Embedding-Port bleibt aus.
     */
    public RetrieveKnowledgeUseCase withRelations(KnowledgeRelationsPort relations,
                                                  Supplier<RetrievalSettings> liveSettings) {
        if (relations == null) {
            throw new IllegalArgumentException("relations must not be null");
        }
        final RetrievalSettings own = settings;
        return new RetrieveKnowledgeUseCase(this, relations, liveSettings == null ? () -> own : liveSettings,
                observer);
    }

    public EmbeddingModelIdentity space() {
        return space.get();
    }

    public RetrievalSettings settings() {
        return settings;
    }

    /** Höchstzahl der Treffer je Suche, von jetzt: aus den Live-Einstellungen, sonst aus den eigenen. */
    public int maxResults() {
        return current().maxResults();
    }

    /** Die Einstellungen von jetzt: aus den Live-Einstellungen, sonst die eigenen. */
    private RetrievalSettings current() {
        RetrievalSettings live = relationSettings == null ? null : relationSettings.get();
        return live == null ? settings : live;
    }

    /**
     * Lädt Auszüge eines früheren Wissenskontexts über ihre Chunk-IDs aus dem Index der aktuellen Embedding-Welt, in
     * der gegebenen Reihenfolge, ohne zu suchen. Was dort fehlt (gelöscht, anderes Embedding-Modell), fällt weg;
     * ein Indexfehler ergibt ein leeres Ergebnis.
     */
    public List<RetrievedChunk> restore(List<KnowledgeChunkId> chunkIds) {
        List<RetrievedChunk> restored = new ArrayList<RetrievedChunk>();
        if (chunkIds == null) {
            return restored;
        }
        try {
            for (int i = 0; i < chunkIds.size(); i++) {
                if (raw != null && RawTextWindows.isRawWindow(chunkIds.get(i))) {
                    RetrievedChunk window = restoreWindow(chunkIds.get(i), 1.0 / (i + 1));
                    if (window != null) {
                        restored.add(window);
                    }
                    continue;
                }
                Optional<KnowledgeIndexEntry> entry = index.entry(space(), chunkIds.get(i));
                if (entry.isPresent()) {
                    // Score fällt mit der Position: die Reihenfolge des früheren Kontextblocks bleibt.
                    restored.add(RetrievedChunk.restored(entry.get().resource(), entry.get().chunk(),
                            1.0 / (i + 1)));
                }
            }
        } catch (KnowledgeIndexException e) {
            return new ArrayList<RetrievedChunk>();
        }
        return restored;
    }

    /** Ein Textfenster aus dem Rohindex, solange die Seite dort liegt; sonst {@code null}. */
    private RetrievedChunk restoreWindow(KnowledgeChunkId chunkId, double score) {
        Optional<RawKnowledgeDocument> document = raw.get(chunkId.resourceId());
        if (!document.isPresent()) {
            return null;
        }
        Optional<RawTextWindows.Window> window = RawTextWindows.restore(document.get().text(),
                RawTextWindows.startOf(chunkId), current().rawWindowChars());
        if (!window.isPresent()) {
            return null;
        }
        return RetrievedChunk.restored(document.get().resource(), windowChunk(document.get().resource(),
                chunkId, window.get().text()), score);
    }

    /** Sucht in allen Quellen. */
    public RetrievalResult retrieve(String query) {
        return retrieve(query, Collections.<SourceAddress>emptySet());
    }

    /**
     * @param query   Suchtext, z. B. die Nutzerfrage; leer ergibt ein leeres Ergebnis ohne Portaufruf
     * @param sources nur in diesen Quellen suchen; leer oder {@code null} heißt alle
     * @return höchstens {@link #maxResults()} fusionierte Treffer
     * @throws KnowledgeRetrievalException wenn alle aktiven Suchpfade ausgefallen sind
     */
    public RetrievalResult retrieve(String query, Collection<SourceAddress> sources) {
        return retrieve(query, sources, RetrievalProgress.none());
    }

    /**
     * Wie {@link #retrieve(String, Collection)}; meldet vor jedem Suchpfad an {@code progress}, welcher gerade läuft.
     */
    public RetrievalResult retrieve(String query, Collection<SourceAddress> sources, RetrievalProgress progress) {
        return retrieve(query, sources, null, progress);
    }

    /**
     * Like {@link #retrieve(String, Collection, RetrievalProgress)}, plus the files of the chat project
     * {@code visibleProject} (#143). Project files of any other project never appear, whatever {@code sources} says:
     * "all sources" means all sources outside chat projects plus the one visible project.
     *
     * @param visibleProject the project source the turn may see ({@link ProjectKnowledge#source}), {@code null}: none
     */
    public RetrievalResult retrieve(String query, Collection<SourceAddress> sources, SourceAddress visibleProject,
                                    RetrievalProgress progress) {
        RetrievalProgress steps = progress == null ? RetrievalProgress.none() : progress;
        if (query == null || query.trim().isEmpty()) {
            return RetrievalResult.empty();
        }
        Collection<SourceAddress> filter = sources == null ? Collections.<SourceAddress>emptySet() : sources;
        if (visibleProject != null && !filter.isEmpty() && !filter.contains(visibleProject)) {
            List<SourceAddress> widened = new ArrayList<SourceAddress>(filter);
            widened.add(visibleProject);
            filter = widened;
        }
        List<RetrievalWarning> warnings = new ArrayList<RetrievalWarning>();
        RuntimeException lastFailure = null;
        int failedPaths = 0;
        int activePaths = 0;

        // Suchpfade, Kandidatenzahlen, Schwelle und Fusion von jetzt (Einstellungen gelten ohne Neustart).
        RetrievalSettings current = current();
        List<KnowledgeSearchHit> keywordHits = Collections.emptyList();
        if (current.keywordEnabled()) {
            activePaths++;
            steps.pathStarted(RetrievalPath.KEYWORD);
            boolean passagesFailed = false;
            try {
                keywordHits = index.keywordSearch(KnowledgeKeywordQuery.of(space(), query, current.keywordCandidates())
                        .restrictedTo(filter));
            } catch (KnowledgeIndexException e) {
                warnings.add(new RetrievalWarning(RetrievalPath.KEYWORD, "Volltextsuche fehlgeschlagen: "
                        + e.getMessage()));
                lastFailure = e;
                passagesFailed = true;
            }
            boolean rawFailed = raw == null;
            if (raw != null) {
                try {
                    keywordHits = interleave(rawWindows(query, filter, current), keywordHits);
                } catch (KnowledgeIndexException e) {
                    warnings.add(new RetrievalWarning(RetrievalPath.KEYWORD, "Suche in den geladenen Seiten "
                            + "fehlgeschlagen: " + e.getMessage()));
                    lastFailure = e;
                    rawFailed = true;
                }
            }
            if (passagesFailed && rawFailed) {
                failedPaths++;
            }
        }

        List<KnowledgeSearchHit> semanticHits = Collections.emptyList();
        if (current.semanticEnabled() && embeddings != null) {
            activePaths++;
            steps.pathStarted(RetrievalPath.SEMANTIC);
            try {
                semanticHits = semanticSearch(query, filter, current);
            } catch (EmbeddingException e) {
                warnings.add(new RetrievalWarning(RetrievalPath.SEMANTIC, "Embedding der Anfrage fehlgeschlagen ("
                        + e.kind() + "): " + e.getMessage()));
                lastFailure = e;
                failedPaths++;
            } catch (KnowledgeIndexException e) {
                warnings.add(new RetrievalWarning(RetrievalPath.SEMANTIC, "Semantische Suche fehlgeschlagen: "
                        + e.getMessage()));
                lastFailure = e;
                failedPaths++;
            }
        }

        for (RetrievalWarning warning : warnings) {
            observer.pathFailed(warning);
        }
        if (failedPaths == activePaths) {
            throw new KnowledgeRetrievalException(warnings, lastFailure);
        }
        List<RetrievedChunk> fused = visibleTo(visibleProject,
                expand(new ReciprocalRankFusion(current).fuse(keywordHits, semanticHits), filter));
        int maxResults = maxResults();
        if (fused.size() > maxResults) {
            fused = fused.subList(0, maxResults);
        }
        return new RetrievalResult(fused, warnings);
    }

    /** Drops every hit from a chat project other than {@code visibleProject} (#143); the order stays. */
    private static List<RetrievedChunk> visibleTo(SourceAddress visibleProject, List<RetrievedChunk> hits) {
        List<RetrievedChunk> visible = new ArrayList<RetrievedChunk>(hits.size());
        for (RetrievedChunk hit : hits) {
            if (ProjectKnowledge.isVisible(hit.chunk().sourceId(), visibleProject)) {
                visible.add(hit);
            }
        }
        return visible.size() == hits.size() ? hits : visible;
    }

    /**
     * 1-Hop-Erweiterung: Nachbarn der besten Treffer, die noch nicht im Ergebnis sind, mit
     * {@code seedScore * relationWeight} (ähnliche Absätze zusätzlich mal Cosine); danach neu sortiert.
     */
    private List<RetrievedChunk> expand(List<RetrievedChunk> fused, Collection<SourceAddress> filter) {
        RetrievalSettings live = relationSettings == null ? null : relationSettings.get();
        if (relations == null || live == null || !live.relationsEnabled() || fused.isEmpty()
                || live.relationWeight() == 0.0) {
            return fused;
        }
        KnowledgeRelationQuery query = KnowledgeRelationQuery.of(space())
                .maxPerKind(live.relationNeighbors())
                .minSimilarity(live.relationMinSimilarity())
                .restrictedTo(filter);
        Map<KnowledgeChunkId, RetrievedChunk> result = new LinkedHashMap<KnowledgeChunkId, RetrievedChunk>();
        for (RetrievedChunk hit : fused) {
            result.put(hit.chunk().id(), hit);
        }
        int seeds = Math.min(live.relationSeeds(), fused.size());
        for (int i = 0; i < seeds; i++) {
            RetrievedChunk seed = fused.get(i);
            List<KnowledgeRelation> neighbors;
            try {
                neighbors = relations.neighbors(seed.chunk().id(), query);
            } catch (KnowledgeIndexException e) {
                return fused; // Zugabe gescheitert: das Suchergebnis bleibt, wie es war
            }
            for (KnowledgeRelation neighbor : neighbors) {
                if (result.containsKey(neighbor.to())) {
                    continue;
                }
                double factor = neighbor.kind() == KnowledgeRelationKind.SIMILAR_TO
                        ? Math.max(0.0, neighbor.score()) : 1.0;
                result.put(neighbor.to(), RetrievedChunk.related(neighbor,
                        seed.fusedScore() * live.relationWeight() * factor));
            }
        }
        if (result.size() == fused.size()) {
            return fused;
        }
        List<RetrievedChunk> expanded = new ArrayList<RetrievedChunk>(result.values());
        Collections.sort(expanded, RetrievedChunk.byFusedScore());
        return expanded;
    }

    /**
     * Textfenster der geladenen Seiten zur Anfrage (BM25 im Rohindex), als Volltexttreffer. Seiten, deren aktuelle
     * Fassung schon aufbereitet ist, fehlen hier: für sie sprechen ihre Passagen.
     */
    private List<KnowledgeSearchHit> rawWindows(String query, Collection<SourceAddress> filter,
                                                RetrievalSettings current) {
        List<RawKnowledgeHit> documents = raw.search(RawKnowledgeQuery.of(query, current.keywordCandidates(),
                current.rawWindows(), current.rawWindowChars()).restrictedTo(filter));
        List<KnowledgeSearchHit> hits = new ArrayList<KnowledgeSearchHit>();
        for (RawKnowledgeHit document : documents) {
            if (isPrepared(document)) {
                continue;
            }
            for (RawTextWindows.Window window : document.windows()) {
                KnowledgeChunkId id = RawTextWindows.chunkId(document.resource().id(), window);
                hits.add(new KnowledgeSearchHit(document.resource(), windowChunk(document.resource(), id,
                        window.text()), document.score(), KnowledgeSearchMode.KEYWORD));
            }
        }
        return hits;
    }

    /** Ob die Seite in genau dieser Fassung (Inhalts-Hash) schon als Passagen im Index liegt. */
    private boolean isPrepared(RawKnowledgeHit document) {
        try {
            Optional<IndexedResourceState> state = index.stateOf(space(), document.resource().id());
            return state.isPresent() && document.contentHash().equals(state.get().contentHash());
        } catch (KnowledgeIndexException e) {
            return false;
        }
    }

    private static KnowledgeChunk windowChunk(com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource resource,
                                              KnowledgeChunkId id, String text) {
        return new KnowledgeChunk(id, resource.sourceId(), Collections.<String>emptyList(), text,
                QUERY_TOKENS.count(text));
    }

    /** Abwechselnd aus beiden Listen, je in ihrer Reihenfolge (Ränge beider Volltextpfade gleich gewichtet). */
    private static List<KnowledgeSearchHit> interleave(List<KnowledgeSearchHit> first,
                                                       List<KnowledgeSearchHit> second) {
        List<KnowledgeSearchHit> merged = new ArrayList<KnowledgeSearchHit>(first.size() + second.size());
        for (int i = 0; i < Math.max(first.size(), second.size()); i++) {
            if (i < first.size()) {
                merged.add(first.get(i));
            }
            if (i < second.size()) {
                merged.add(second.get(i));
            }
        }
        return merged;
    }

    /** Die Anfrage, höchstens {@link #MAX_QUERY_TOKENS} Token lang, an einer Wortgrenze gekürzt. */
    private String shortened(String query) {
        int tokens = QUERY_TOKENS.count(query);
        if (tokens <= MAX_QUERY_TOKENS) {
            return query;
        }
        StringBuilder kept = new StringBuilder();
        int keptTokens = 0;
        for (String word : query.trim().split("\\s+")) {
            int wordTokens = QUERY_TOKENS.count(word);
            if (keptTokens + wordTokens > MAX_QUERY_TOKENS) {
                break;
            }
            if (kept.length() > 0) {
                kept.append(' ');
            }
            kept.append(word);
            keptTokens += wordTokens;
        }
        if (kept.length() == 0) {
            // ein einziges überlanges "Wort": höchstens ein Token je Zeichen
            String head = query.trim().substring(0, Math.min(query.trim().length(), MAX_QUERY_TOKENS));
            kept.append(head);
            keptTokens = QUERY_TOKENS.count(head);
        }
        observer.queryShortened(tokens, keptTokens);
        return kept.toString();
    }

    private List<KnowledgeSearchHit> semanticSearch(String query, Collection<SourceAddress> filter,
                                                    RetrievalSettings settings) {
        EmbeddingVector vector = embeddings.embedQueries(Collections.singletonList(shortened(query))).get(0);
        if (!space().equals(vector.identity())) {
            // Der Port hat seine Welt gewechselt; niemals in einem fremden Namespace suchen.
            throw new EmbeddingException(EmbeddingFailureKind.INVALID_RESPONSE,
                    "Anfragevektor aus einer anderen Embedding-Welt");
        }
        List<KnowledgeSearchHit> hits = index.semanticSearch(KnowledgeSemanticQuery.of(vector,
                settings.semanticCandidates()).restrictedTo(filter));
        if (settings.minSemanticScore() <= -1.0) {
            return hits;
        }
        List<KnowledgeSearchHit> accepted = new ArrayList<KnowledgeSearchHit>(hits.size());
        for (KnowledgeSearchHit hit : hits) {
            if (hit.score() >= settings.minSemanticScore()) {
                accepted.add(hit);
            }
        }
        return accepted;
    }
}
