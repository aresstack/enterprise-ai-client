package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.Collection;
import java.util.Set;

/**
 * Semantische Anfrage: Cosine-Ähnlichkeit zum Anfragevektor. Der Namespace ist die Identität des Vektors; es
 * werden nur Chunks derselben Embedding-Welt verglichen. Der Vektor muss nicht normiert sein. Optional auf
 * Quellen eingeschränkt (leer = alle).
 *
 * <p>Übernommen aus askai-java8 {@code PassageSemanticQuery}.
 */
public final class KnowledgeSemanticQuery {

    private final EmbeddingVector vector;
    private final int maxResults;
    private final Set<KnowledgeSourceId> sources;

    private KnowledgeSemanticQuery(EmbeddingVector vector, int maxResults, Set<KnowledgeSourceId> sources) {
        if (vector == null) {
            throw new IllegalArgumentException("Anfragevektor fehlt");
        }
        this.vector = vector;
        this.maxResults = QueryLimits.maxResults(maxResults);
        this.sources = sources;
    }

    /** @param maxResults Obergrenze der Treffer; {@code <= 0} heißt Default (10), gedeckelt auf 1000 */
    public static KnowledgeSemanticQuery of(EmbeddingVector vector, int maxResults) {
        return new KnowledgeSemanticQuery(vector, maxResults, QueryLimits.sources(null));
    }

    /** Kopie, die nur in den genannten Quellen sucht; leer hebt die Einschränkung auf. */
    public KnowledgeSemanticQuery restrictedTo(Collection<KnowledgeSourceId> sourceIds) {
        return new KnowledgeSemanticQuery(vector, maxResults, QueryLimits.sources(sourceIds));
    }

    public EmbeddingVector vector() {
        return vector;
    }

    public EmbeddingModelIdentity space() {
        return vector.identity();
    }

    public int maxResults() {
        return maxResults;
    }

    public Set<KnowledgeSourceId> sources() {
        return sources;
    }

    public boolean accepts(KnowledgeSourceId sourceId) {
        return sources.isEmpty() || sources.contains(sourceId);
    }

    @Override
    public String toString() {
        return "KnowledgeSemanticQuery{" + vector + ", maxResults=" + maxResults + ", sources=" + sources + "}";
    }
}
