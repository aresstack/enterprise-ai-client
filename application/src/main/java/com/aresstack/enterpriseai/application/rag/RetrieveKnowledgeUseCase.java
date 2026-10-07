package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
 * <p>Blockiert für die Dauer von Embedding-Aufruf und Indexsuche; Aufrufer aus der Oberfläche rufen ihn nicht auf
 * dem Event-Thread auf. Zustandslos und threadsicher, soweit die Ports es sind.
 */
public final class RetrieveKnowledgeUseCase {

    private final KnowledgeIndexPort index;
    private final EmbeddingPort embeddings;
    private final EmbeddingModelIdentity space;
    private final RetrievalSettings settings;
    private final ReciprocalRankFusion fusion;

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
        this.space = space;
        this.settings = effective;
        this.fusion = new ReciprocalRankFusion(effective);
    }

    public EmbeddingModelIdentity space() {
        return space;
    }

    public RetrievalSettings settings() {
        return settings;
    }

    /** Sucht in allen Quellen. */
    public RetrievalResult retrieve(String query) {
        return retrieve(query, Collections.<KnowledgeSourceId>emptySet());
    }

    /**
     * @param query   Suchtext, z. B. die Nutzerfrage; leer ergibt ein leeres Ergebnis ohne Portaufruf
     * @param sources nur in diesen Quellen suchen; leer oder {@code null} heißt alle
     * @return höchstens {@link RetrievalSettings#maxResults()} fusionierte Treffer
     * @throws KnowledgeRetrievalException wenn alle aktiven Suchpfade ausgefallen sind
     */
    public RetrievalResult retrieve(String query, Collection<KnowledgeSourceId> sources) {
        if (query == null || query.trim().isEmpty()) {
            return RetrievalResult.empty();
        }
        Collection<KnowledgeSourceId> filter = sources == null ? Collections.<KnowledgeSourceId>emptySet() : sources;
        List<RetrievalWarning> warnings = new ArrayList<RetrievalWarning>();
        RuntimeException lastFailure = null;
        int failedPaths = 0;
        int activePaths = 0;

        List<KnowledgeSearchHit> keywordHits = Collections.emptyList();
        if (settings.keywordEnabled()) {
            activePaths++;
            try {
                keywordHits = index.keywordSearch(KnowledgeKeywordQuery.of(space, query, settings.keywordCandidates())
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

        if (failedPaths == activePaths) {
            throw new KnowledgeRetrievalException(warnings, lastFailure);
        }
        List<RetrievedChunk> fused = fusion.fuse(keywordHits, semanticHits);
        if (fused.size() > settings.maxResults()) {
            fused = fused.subList(0, settings.maxResults());
        }
        return new RetrievalResult(fused, warnings);
    }

    private List<KnowledgeSearchHit> semanticSearch(String query, Collection<KnowledgeSourceId> filter) {
        EmbeddingVector vector = embeddings.embed(Collections.singletonList(query)).get(0);
        if (!space.equals(vector.identity())) {
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
