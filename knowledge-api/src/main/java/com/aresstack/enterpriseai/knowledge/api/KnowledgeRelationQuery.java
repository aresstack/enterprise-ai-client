package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Anfrage an {@link KnowledgeRelationsPort#neighbors}: Namespace, gewünschte Kantenarten, Grenzen für
 * {@link KnowledgeRelationKind#SIMILAR_TO} und strukturelle Kanten, optional ein Quellenfilter. Unveränderlich.
 */
public final class KnowledgeRelationQuery {

    public static final int DEFAULT_MAX_PER_KIND = 3;
    public static final double DEFAULT_MIN_SIMILARITY = 0.80;

    private final EmbeddingModelIdentity space;
    private final Set<KnowledgeRelationKind> kinds;
    private final int maxPerKind;
    private final double minSimilarity;
    private final Set<KnowledgeSourceId> sources;

    private KnowledgeRelationQuery(EmbeddingModelIdentity space, Set<KnowledgeRelationKind> kinds, int maxPerKind,
                                   double minSimilarity, Set<KnowledgeSourceId> sources) {
        this.space = space;
        this.kinds = Collections.unmodifiableSet(kinds);
        this.maxPerKind = maxPerKind;
        this.minSimilarity = minSimilarity;
        this.sources = Collections.unmodifiableSet(sources);
    }

    /** Alle Kantenarten, je Art höchstens {@value #DEFAULT_MAX_PER_KIND}, Ähnlichkeit ab 0,80, alle Quellen. */
    public static KnowledgeRelationQuery of(EmbeddingModelIdentity space) {
        if (space == null) {
            throw new IllegalArgumentException("space fehlt");
        }
        return new KnowledgeRelationQuery(space, EnumSet.allOf(KnowledgeRelationKind.class), DEFAULT_MAX_PER_KIND,
                DEFAULT_MIN_SIMILARITY, new LinkedHashSet<KnowledgeSourceId>());
    }

    /** Nur diese Kantenarten; leer heißt keine. */
    public KnowledgeRelationQuery kinds(Collection<KnowledgeRelationKind> value) {
        Set<KnowledgeRelationKind> copy = EnumSet.noneOf(KnowledgeRelationKind.class);
        if (value != null) {
            copy.addAll(value);
        }
        return new KnowledgeRelationQuery(space, copy, maxPerKind, minSimilarity, sources);
    }

    /** Höchstzahl Kanten je Art, {@code >= 1}. */
    public KnowledgeRelationQuery maxPerKind(int value) {
        if (value < 1 || value > QueryLimits.MAX_RESULTS_LIMIT) {
            throw new IllegalArgumentException("maxPerKind muss in [1, " + QueryLimits.MAX_RESULTS_LIMIT
                    + "] liegen: " + value);
        }
        return new KnowledgeRelationQuery(space, copyKinds(), value, minSimilarity, sources);
    }

    /** Cosine-Schwelle für {@link KnowledgeRelationKind#SIMILAR_TO} in [-1, 1]. */
    public KnowledgeRelationQuery minSimilarity(double value) {
        if (Double.isNaN(value) || value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException("minSimilarity muss in [-1, 1] liegen: " + value);
        }
        return new KnowledgeRelationQuery(space, copyKinds(), maxPerKind, value, sources);
    }

    /** Ziele nur aus diesen Quellen; leer oder {@code null} heißt alle. */
    public KnowledgeRelationQuery restrictedTo(Collection<KnowledgeSourceId> value) {
        Set<KnowledgeSourceId> copy = new LinkedHashSet<KnowledgeSourceId>();
        if (value != null) {
            copy.addAll(value);
        }
        return new KnowledgeRelationQuery(space, copyKinds(), maxPerKind, minSimilarity, copy);
    }

    public EmbeddingModelIdentity space() {
        return space;
    }

    public Set<KnowledgeRelationKind> kinds() {
        return kinds;
    }

    public boolean wants(KnowledgeRelationKind kind) {
        return kinds.contains(kind);
    }

    public int maxPerKind() {
        return maxPerKind;
    }

    public double minSimilarity() {
        return minSimilarity;
    }

    public Set<KnowledgeSourceId> sources() {
        return sources;
    }

    public boolean accepts(KnowledgeSourceId sourceId) {
        return sources.isEmpty() || sources.contains(sourceId);
    }

    private Set<KnowledgeRelationKind> copyKinds() {
        Set<KnowledgeRelationKind> copy = EnumSet.noneOf(KnowledgeRelationKind.class);
        copy.addAll(kinds);
        return copy;
    }

    @Override
    public String toString() {
        return "KnowledgeRelationQuery{" + space + ", kinds=" + kinds + ", maxPerKind=" + maxPerKind
                + ", minSimilarity=" + minSimilarity + (sources.isEmpty() ? "" : ", sources=" + sources) + "}";
    }
}
