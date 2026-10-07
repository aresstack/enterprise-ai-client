package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.Collection;
import java.util.Set;

/**
 * Volltextanfrage innerhalb eines Namespaces ({@link EmbeddingModelIdentity}), damit nur die Chunks der aktiven
 * Embedding-Welt durchsucht werden. Der Text wird vom Index analysiert; Anfragesyntax (Operatoren, Phrasen)
 * wird nicht interpretiert. Optional auf Quellen eingeschränkt (leer = alle).
 *
 * <p>Übernommen aus askai-java8 {@code PassageTextQuery}.
 */
public final class KnowledgeKeywordQuery {

    private final EmbeddingModelIdentity space;
    private final String text;
    private final int maxResults;
    private final Set<KnowledgeSourceId> sources;

    private KnowledgeKeywordQuery(EmbeddingModelIdentity space, String text, int maxResults,
                                  Set<KnowledgeSourceId> sources) {
        if (space == null) {
            throw new IllegalArgumentException("Namespace (EmbeddingModelIdentity) fehlt");
        }
        this.space = space;
        this.text = text == null ? "" : text;
        this.maxResults = QueryLimits.maxResults(maxResults);
        this.sources = sources;
    }

    /** @param maxResults Obergrenze der Treffer; {@code <= 0} heißt Default (10), gedeckelt auf 1000 */
    public static KnowledgeKeywordQuery of(EmbeddingModelIdentity space, String text, int maxResults) {
        return new KnowledgeKeywordQuery(space, text, maxResults, QueryLimits.sources(null));
    }

    /** Kopie, die nur in den genannten Quellen sucht; leer hebt die Einschränkung auf. */
    public KnowledgeKeywordQuery restrictedTo(Collection<KnowledgeSourceId> sourceIds) {
        return new KnowledgeKeywordQuery(space, text, maxResults, QueryLimits.sources(sourceIds));
    }

    public EmbeddingModelIdentity space() {
        return space;
    }

    public String text() {
        return text;
    }

    public int maxResults() {
        return maxResults;
    }

    /** Erlaubte Quellen; leer heißt alle. */
    public Set<KnowledgeSourceId> sources() {
        return sources;
    }

    public boolean accepts(KnowledgeSourceId sourceId) {
        return sources.isEmpty() || sources.contains(sourceId);
    }

    @Override
    public String toString() {
        return "KnowledgeKeywordQuery{chars=" + text.length() + ", maxResults=" + maxResults + ", sources=" + sources
                + "}";
    }
}
