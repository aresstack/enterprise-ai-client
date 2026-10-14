package com.aresstack.enterpriseai.fact.api;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Global metadata catalog (#106): any number of structured facts per stable resource id, across all knowledge
 * sources, queryable by resource and by type without the knowledge index.
 *
 * <p>Contract for every implementation:
 * <ul>
 *   <li>Every method is one short transaction; changes are visible on return. Callers never hold a transaction
 *       open across model, network or extraction work.</li>
 *   <li>Methods block on storage and may be called from any thread except the Swing event thread; concurrent
 *       readers and writers are allowed.</li>
 *   <li>A fact needs a registered resource ({@link #registerResource}) and a registered type ({@link #registerType});
 *       otherwise it is rejected with {@link FactStoreException.Reason#UNKNOWN_RESOURCE} or
 *       {@link FactStoreException.Reason#UNKNOWN_TYPE}.</li>
 *   <li>Nothing cascades implicitly: facts disappear only through {@link #removeFact}, {@link #replaceAutomaticFacts},
 *       {@link #removeResource} or {@link #removeSource}. Removing a source from the knowledge index does not touch
 *       the catalog.</li>
 *   <li>Failures are reported as {@link FactStoreException}; a failed operation changes nothing.</li>
 * </ul>
 *
 * <p>Payload fields are not indexed: queries go by resource, type and fact id only. Searching inside payloads (e.g.
 * all resources with acronym X) needs a separate, normalized search projection.
 */
public interface FactStorePort {

    /** Register or update a type (see {@link FactType} for the version rules). */
    void registerType(FactType type);

    Optional<FactType> type(FactTypeId id);

    /** All registered types, ordered by id. */
    List<FactType> types();

    /**
     * Register the stable identity of a resource so facts can refer to it; idempotent. Revision, content hash and URL
     * are not part of it.
     *
     * @param source the configured source of the resource (for removing a whole source), or {@code null} to keep
     *               the one already registered
     */
    void registerResource(KnowledgeResourceId resource, SourceAddress source);

    boolean isResourceRegistered(KnowledgeResourceId resource);

    /**
     * Insert the fact or update the fact with the same id; returns it as stored (timestamps set, payload as read back).
     * The schema version must not exceed the type's current version; a stored fact keeps its resource.
     */
    MetadataFact save(MetadataFact fact);

    Optional<MetadataFact> fact(FactId id);

    /** @return whether a fact was removed */
    boolean removeFact(FactId id);

    /** Fact sheet of one resource: all its facts, ordered by type and creation. */
    List<MetadataFact> factsOf(KnowledgeResourceId resource);

    /** Facts of one type on one resource, ordered by creation. */
    List<MetadataFact> factsOf(KnowledgeResourceId resource, FactTypeId type);

    /** All facts of one type across all resources and sources, ordered by resource and creation. */
    List<MetadataFact> factsOfType(FactTypeId type);

    /** Distinct resources carrying at least one fact of the type, ordered by id. */
    Set<KnowledgeResourceId> resourcesWithType(FactTypeId type);

    /**
     * Replace the unreviewed automatic facts of one extractor on one resource by {@code facts} in one transaction
     * (empty = remove them). Manual facts, reviewed facts and facts of other extractors stay. If any new fact is
     * rejected, nothing changes.
     *
     * @return number of facts removed
     * @throws IllegalArgumentException if a fact belongs to another resource, is not automatic or names another
     *                                  extractor
     */
    int replaceAutomaticFacts(KnowledgeResourceId resource, String extractorId, List<MetadataFact> facts);

    /**
     * Remove a resource that no longer exists, with all its facts (manual ones included). Explicit only; nothing calls
     * it on the caller's behalf.
     *
     * @return number of facts removed
     */
    int removeResource(KnowledgeResourceId resource);

    /**
     * Remove every resource registered with {@code source}, with all their facts. Explicit only.
     *
     * @return number of facts removed
     */
    int removeSource(SourceAddress source);
}
