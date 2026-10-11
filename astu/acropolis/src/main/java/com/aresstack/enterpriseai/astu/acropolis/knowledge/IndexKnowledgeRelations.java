package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelation;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationKind;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationsPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeSearchHit;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeSemanticQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link KnowledgeRelationsPort}, abgeleitet aus dem, was der {@link KnowledgeIndexPort} ohnehin speichert – kein
 * Kantenspeicher, kein neues Embedding, kein Crawlen. Kanten entstehen bei der Abfrage (lazy):
 * <ul>
 *   <li>{@code SIMILAR_TO}: Cosine-Suche mit dem gespeicherten Vektor des Ausgangschunks; ohne ihn selbst und ohne
 *       seine direkten Nachbarabsätze (die teilen sich durch den Satz-Overlap des Chunkers Text und wären sonst
 *       immer die "ähnlichsten"), ab der Schwelle, höchstens {@code maxPerKind}.</li>
 *   <li>{@code PARENT}: erster Chunk der Elternressource ({@code KnowledgeResource.parentId()}).</li>
 *   <li>{@code NEXT}/{@code PREVIOUS}: Chunk mit {@code ordinal ± 1} derselben Ressource.</li>
 *   <li>{@code SAME_SECTION}: weitere Chunks derselben Ressource mit gleichem, nicht leerem {@code headingPath},
 *       die nächstgelegenen zuerst.</li>
 *   <li>{@code LINKS_TO}: liefert noch nichts – Verweise ({@code discoverLinks}) werden beim Indexieren bisher nicht
 *       gespeichert. Sobald der Index sie trägt, kommen sie hier dazu.</li>
 * </ul>
 * Zustandslos; threadsicher, soweit der Index-Port es ist.
 */
public final class IndexKnowledgeRelations implements KnowledgeRelationsPort {

    /** Zusätzliche Kandidaten der Cosine-Suche für den Ausgangschunk und seine beiden Nachbarabsätze. */
    private static final int SELF_AND_ADJACENT = 3;

    private final KnowledgeIndexPort index;

    public IndexKnowledgeRelations(KnowledgeIndexPort index) {
        if (index == null) {
            throw new IllegalArgumentException("index must not be null");
        }
        this.index = index;
    }

    @Override
    public List<KnowledgeRelation> neighbors(KnowledgeChunkId chunkId, KnowledgeRelationQuery query) {
        if (chunkId == null || query == null) {
            throw new IllegalArgumentException("chunk und query sind Pflicht");
        }
        Optional<KnowledgeIndexEntry> found = index.entry(query.space(), chunkId);
        if (!found.isPresent() || query.kinds().isEmpty()) {
            return Collections.emptyList();
        }
        KnowledgeIndexEntry seed = found.get();
        Map<KnowledgeChunkId, KnowledgeRelation> relations = new LinkedHashMap<KnowledgeChunkId, KnowledgeRelation>();
        if (query.wants(KnowledgeRelationKind.SIMILAR_TO)) {
            similar(seed, query, relations);
        }
        if (query.wants(KnowledgeRelationKind.PARENT)) {
            parent(seed, query, relations);
        }
        boolean structural = query.wants(KnowledgeRelationKind.NEXT) || query.wants(KnowledgeRelationKind.PREVIOUS)
                || query.wants(KnowledgeRelationKind.SAME_SECTION);
        if (structural) {
            List<KnowledgeIndexEntry> siblings = index.entriesOf(query.space(), seed.resource().id());
            int ordinal = seed.chunk().ordinal();
            if (query.wants(KnowledgeRelationKind.NEXT)) {
                addOrdinal(seed, siblings, ordinal + 1, KnowledgeRelationKind.NEXT, query, relations);
            }
            if (query.wants(KnowledgeRelationKind.PREVIOUS)) {
                addOrdinal(seed, siblings, ordinal - 1, KnowledgeRelationKind.PREVIOUS, query, relations);
            }
            if (query.wants(KnowledgeRelationKind.SAME_SECTION)) {
                sameSection(seed, siblings, query, relations);
            }
        }
        return new ArrayList<KnowledgeRelation>(relations.values());
    }

    private void similar(KnowledgeIndexEntry seed, KnowledgeRelationQuery query,
                         Map<KnowledgeChunkId, KnowledgeRelation> relations) {
        // Der Index-Port kappt die Kandidatenzahl selbst auf seine Obergrenze.
        List<KnowledgeSearchHit> hits = index.semanticSearch(KnowledgeSemanticQuery.of(seed.embedding(),
                query.maxPerKind() + SELF_AND_ADJACENT).restrictedTo(query.sources()));
        int added = 0;
        for (KnowledgeSearchHit hit : hits) {
            if (added >= query.maxPerKind() || hit.score() < query.minSimilarity()) {
                break;
            }
            if (isSelfOrAdjacent(seed.chunk(), hit.chunk())) {
                continue;
            }
            if (put(relations, new KnowledgeRelation(KnowledgeRelationKind.SIMILAR_TO, seed.chunkId(),
                    hit.resource(), hit.chunk(), hit.score()), seed)) {
                added++;
            }
        }
    }

    private void parent(KnowledgeIndexEntry seed, KnowledgeRelationQuery query,
                        Map<KnowledgeChunkId, KnowledgeRelation> relations) {
        KnowledgeResource resource = seed.resource();
        if (!resource.parentId().isPresent()) {
            return;
        }
        List<KnowledgeIndexEntry> parent = index.entriesOf(query.space(), resource.parentId().get());
        if (!parent.isEmpty() && query.accepts(parent.get(0).resource().sourceId())) {
            KnowledgeIndexEntry first = parent.get(0);
            put(relations, structural(KnowledgeRelationKind.PARENT, seed, first), seed);
        }
    }

    private static void addOrdinal(KnowledgeIndexEntry seed, List<KnowledgeIndexEntry> siblings, int ordinal,
                                   KnowledgeRelationKind kind, KnowledgeRelationQuery query,
                                   Map<KnowledgeChunkId, KnowledgeRelation> relations) {
        for (KnowledgeIndexEntry sibling : siblings) {
            if (sibling.chunk().ordinal() == ordinal && query.accepts(sibling.resource().sourceId())) {
                put(relations, structural(kind, seed, sibling), seed);
                return;
            }
        }
    }

    private static void sameSection(KnowledgeIndexEntry seed, List<KnowledgeIndexEntry> siblings,
                                    KnowledgeRelationQuery query,
                                    Map<KnowledgeChunkId, KnowledgeRelation> relations) {
        final KnowledgeChunk origin = seed.chunk();
        if (origin.headingPath().isEmpty()) {
            return;
        }
        List<KnowledgeIndexEntry> section = new ArrayList<KnowledgeIndexEntry>();
        for (KnowledgeIndexEntry sibling : siblings) {
            if (sibling.chunk().headingPath().equals(origin.headingPath())
                    && !isSelfOrAdjacent(origin, sibling.chunk()) && query.accepts(sibling.resource().sourceId())) {
                section.add(sibling);
            }
        }
        Collections.sort(section, (a, b) -> {
            int byDistance = Integer.compare(Math.abs(a.chunk().ordinal() - origin.ordinal()),
                    Math.abs(b.chunk().ordinal() - origin.ordinal()));
            return byDistance != 0 ? byDistance : Integer.compare(a.chunk().ordinal(), b.chunk().ordinal());
        });
        int added = 0;
        for (KnowledgeIndexEntry sibling : section) {
            if (added >= query.maxPerKind()) {
                return;
            }
            if (put(relations, structural(KnowledgeRelationKind.SAME_SECTION, seed, sibling), seed)) {
                added++;
            }
        }
    }

    private static boolean isSelfOrAdjacent(KnowledgeChunk origin, KnowledgeChunk other) {
        return origin.resourceId().equals(other.resourceId()) && Math.abs(origin.ordinal() - other.ordinal()) <= 1;
    }

    private static KnowledgeRelation structural(KnowledgeRelationKind kind, KnowledgeIndexEntry seed,
                                                KnowledgeIndexEntry target) {
        return new KnowledgeRelation(kind, seed.chunkId(), target.resource(), target.chunk(), 1.0);
    }

    /** Erste Kante je Ziel gewinnt; der Ausgangschunk ist nie Ziel. */
    private static boolean put(Map<KnowledgeChunkId, KnowledgeRelation> relations, KnowledgeRelation relation,
                               KnowledgeIndexEntry seed) {
        if (relation.to().equals(seed.chunkId()) || relations.containsKey(relation.to())) {
            return false;
        }
        relations.put(relation.to(), relation);
        return true;
    }
}
