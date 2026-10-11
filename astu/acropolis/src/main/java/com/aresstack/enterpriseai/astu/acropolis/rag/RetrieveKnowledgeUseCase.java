package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.passage.PassageSegmentation;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeTokenCounter;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexException;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelation;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationKind;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationsPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeSearchHit;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeSemanticQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ReciprocalRankFusion fusion;
    private final KnowledgeRelationsPort relations;
    private final Supplier<RetrievalSettings> relationSettings;
    private final RetrievalObserver observer;

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
        this.fusion = new ReciprocalRankFusion(effective);
        this.relations = null;
        this.relationSettings = null;
        this.observer = RetrievalObserver.none();
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
        this.fusion = new ReciprocalRankFusion(effective);
        this.relations = null;
        this.relationSettings = null;
        this.observer = RetrievalObserver.none();
    }

    private RetrieveKnowledgeUseCase(RetrieveKnowledgeUseCase base, KnowledgeRelationsPort relations,
                                     Supplier<RetrievalSettings> relationSettings, RetrievalObserver observer) {
        this.index = base.index;
        this.embeddings = base.embeddings;
        this.space = base.space;
        this.settings = base.settings;
        this.fusion = base.fusion;
        this.relations = relations;
        this.relationSettings = relationSettings;
        this.observer = observer;
    }

    /** Kopie, die Kürzungen und ausgefallene Suchpfade an {@code observer} meldet. */
    public RetrieveKnowledgeUseCase withObserver(RetrievalObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("observer must not be null");
        }
        return new RetrieveKnowledgeUseCase(this, relations, relationSettings, observer);
    }

    /**
     * Kopie mit Nachbarschaftserweiterung über {@code relations}. Die {@code relation*}-Werte werden je Suche aus
     * {@code liveSettings} gelesen (Einstellungen gelten ohne Neustart); {@code null} nimmt die eigenen Settings.
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

    /** Sucht in allen Quellen. */
    public RetrievalResult retrieve(String query) {
        return retrieve(query, Collections.<SourceAddress>emptySet());
    }

    /**
     * @param query   Suchtext, z. B. die Nutzerfrage; leer ergibt ein leeres Ergebnis ohne Portaufruf
     * @param sources nur in diesen Quellen suchen; leer oder {@code null} heißt alle
     * @return höchstens {@link RetrievalSettings#maxResults()} fusionierte Treffer
     * @throws KnowledgeRetrievalException wenn alle aktiven Suchpfade ausgefallen sind
     */
    public RetrievalResult retrieve(String query, Collection<SourceAddress> sources) {
        if (query == null || query.trim().isEmpty()) {
            return RetrievalResult.empty();
        }
        Collection<SourceAddress> filter = sources == null ? Collections.<SourceAddress>emptySet() : sources;
        List<RetrievalWarning> warnings = new ArrayList<RetrievalWarning>();
        RuntimeException lastFailure = null;
        int failedPaths = 0;
        int activePaths = 0;

        List<KnowledgeSearchHit> keywordHits = Collections.emptyList();
        if (settings.keywordEnabled()) {
            activePaths++;
            try {
                keywordHits = index.keywordSearch(KnowledgeKeywordQuery.of(space(), query, settings.keywordCandidates())
                        .restrictedTo(filter));
            } catch (KnowledgeIndexException e) {
                warnings.add(new RetrievalWarning(RetrievalPath.KEYWORD, "Volltextsuche fehlgeschlagen: "
                        + e.getMessage()));
                lastFailure = e;
                failedPaths++;
            }
        }

        List<KnowledgeSearchHit> semanticHits = Collections.emptyList();
        if (settings.semanticEnabled()) {
            activePaths++;
            try {
                semanticHits = semanticSearch(query, filter);
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
        List<RetrievedChunk> fused = expand(fusion.fuse(keywordHits, semanticHits), filter);
        if (fused.size() > settings.maxResults()) {
            fused = fused.subList(0, settings.maxResults());
        }
        return new RetrievalResult(fused, warnings);
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

    private List<KnowledgeSearchHit> semanticSearch(String query, Collection<SourceAddress> filter) {
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
