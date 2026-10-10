package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.resource.api.AcquisitionPort;
import com.aresstack.enterpriseai.resource.api.BronzeContent;
import com.aresstack.enterpriseai.resource.api.BronzeListing;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;
import com.aresstack.enterpriseai.application.resource.policy.AccessDecisionType;
import com.aresstack.enterpriseai.application.resource.policy.AccessReasonCode;
import com.aresstack.enterpriseai.application.resource.policy.ResourceAccessDecision;
import com.aresstack.enterpriseai.application.resource.policy.ResourceAccessPolicy;
import com.aresstack.enterpriseai.application.resource.policy.ResourceAccessRequest;
import com.aresstack.enterpriseai.application.resource.policy.ResourceOperation;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mediated bronze archive access service.
 *
 * <p>This is the archive counter: all external access to bronze resources flows
 * through this service. It consults Tamias for every operation, uses the
 * internal {@link AcquisitionPort} for acquisition when allowed, and manages
 * the cached bronze state.
 *
 * <p><strong>Callers must not talk to connectors directly.</strong> They request
 * resources from this service by {@link BookmarkUri}. The service decides, via
 * Tamias, whether the caller may see, list, fetch, refresh, index, or delete
 * that resource.
 *
 * <p>Acquisition is a separate controlled decision: when a cache miss occurs,
 * the service issues a second {@link ResourceOperation#FETCH_EXTERNAL} request
 * to Tamias before invoking the internal {@link AcquisitionPort}.
 *
 * <p>Lifecycle use cases depend on the {@link MediatedResourceAccess} contract that this
 * service implements, not on this class, so that the composition point decides how the
 * counter is assembled.
 *
 * <p><strong>Known limitation (tracked by #5 and #10):</strong> the three in-memory stores
 * below are payload caches without invalidation and without TTL. Content or listings read
 * once are served from the cache until {@link #deleteEntry(ResourceAccessRequest)} removes
 * them, so a changed source is not re-acquired within the lifetime of a service instance.
 * Since #33 the {@link ResourceArchive} is a facade over the authoritative resource records
 * (versions, indexed-version fact, removal at source); those records hold facts about
 * payloads, not the payloads, and they do not record cache presence. Deciding when a cached
 * payload is invalid is Tamias (#5); consolidating the stores against the records is #10
 * Slice 5. They are not replaced ad hoc here.
 */
public final class MediatedResourceService implements MediatedResourceAccess {

    private final ResourceAccessPolicy accessPolicy;
    private final AcquisitionPort acquisitionPort;
    private final ResourceArchive archive;

    // In-memory bronze state stores without TTL or invalidation; see the known-limitation note
    // in the class Javadoc (consolidated against the #33 record/version contract and #5 invalidation).
    private final Map<BookmarkUri, BronzeListing> listingCache = new ConcurrentHashMap<BookmarkUri, BronzeListing>();
    private final Map<BookmarkUri, BronzeContent> contentCache = new ConcurrentHashMap<BookmarkUri, BronzeContent>();
    private final Map<BookmarkUri, BronzeMetadata> metadataCache = new ConcurrentHashMap<BookmarkUri, BronzeMetadata>();

    public MediatedResourceService(ResourceAccessPolicy accessPolicy,
                                   AcquisitionPort acquisitionPort,
                                   ResourceArchive archive) {
        if (accessPolicy == null) throw new IllegalArgumentException("accessPolicy must not be null");
        if (acquisitionPort == null) throw new IllegalArgumentException("acquisitionPort must not be null");
        if (archive == null) throw new IllegalArgumentException("archive must not be null");
        this.accessPolicy = accessPolicy;
        this.acquisitionPort = acquisitionPort;
        this.archive = archive;
    }

    /**
     * Lists children of a container resource, mediated by Tamias.
     *
     * <p>The request must carry {@link ResourceOperation#LIST_CHILDREN}. If the
     * cached listing is missing and external acquisition is needed, a second
     * policy evaluation with {@link ResourceOperation#FETCH_EXTERNAL} is performed.
     *
     * @param request the access request (operation must be LIST_CHILDREN)
     * @return the result of the mediated listing operation
     */
    @Override
    public MediatedResult<BronzeListing> listChildren(ResourceAccessRequest request) {
        if (request == null) {
            return MediatedResult.error("request must not be null");
        }

        // Fix 1: Validate operation matches this method
        if (request.operation() != ResourceOperation.LIST_CHILDREN) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.INVALID_OPERATION,
                    "Operation mismatch: listChildren requires LIST_CHILDREN, got " + request.operation()));
        }

        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (!decision.isAllowed()) {
            return MediatedResult.denied(decision);
        }

        BookmarkUri uri = request.target();
        BronzeListing cached = listingCache.get(uri);

        // ALLOW_CACHED_ONLY: return cached state only, never fetch externally
        if (decision.type() == AccessDecisionType.ALLOW_CACHED_ONLY) {
            if (cached != null) {
                return MediatedResult.success(cached, decision);
            }
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.CACHE_ONLY_ALLOWED,
                    "No cached listing available and external fetch not permitted"));
        }

        // ALLOW: return cached if present
        if (cached != null) {
            return MediatedResult.success(cached, decision);
        }

        // Cache miss: check if external acquisition is allowed (Fix 2)
        // Only strict ALLOW permits external fetch; ALLOW_CACHED_ONLY, REQUIRE_AUTH,
        // REQUIRE_SOURCE_CHECK, and DENY must all block acquisition.
        ResourceAccessRequest fetchRequest = new ResourceAccessRequest(
                request.actor(), uri, ResourceOperation.FETCH_EXTERNAL, request.purpose());
        ResourceAccessDecision fetchDecision = accessPolicy.evaluate(fetchRequest);
        if (fetchDecision.type() != AccessDecisionType.ALLOW) {
            return MediatedResult.denied(fetchDecision);
        }

        // Acquire internally
        try {
            BronzeListing acquired = acquisitionPort.listChildren(uri);
            listingCache.put(uri, acquired);
            return MediatedResult.success(acquired, decision);
        } catch (IOException e) {
            return MediatedResult.error("Acquisition failed: " + e.getMessage());
        }
    }

    /**
     * Reads content of a resource, mediated by Tamias.
     *
     * <p>The request must carry {@link ResourceOperation#READ_CONTENT}. If the
     * cached content is missing and external acquisition is needed, a second
     * policy evaluation with {@link ResourceOperation#FETCH_EXTERNAL} is performed.
     *
     * @param request the access request (operation must be READ_CONTENT)
     * @return the result of the mediated content read
     */
    @Override
    public MediatedResult<BronzeContent> readContent(ResourceAccessRequest request) {
        if (request == null) {
            return MediatedResult.error("request must not be null");
        }

        // Fix 1: Validate operation matches this method
        if (request.operation() != ResourceOperation.READ_CONTENT) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.INVALID_OPERATION,
                    "Operation mismatch: readContent requires READ_CONTENT, got " + request.operation()));
        }

        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (!decision.isAllowed()) {
            return MediatedResult.denied(decision);
        }

        BookmarkUri uri = request.target();
        BronzeContent cached = contentCache.get(uri);

        // ALLOW_CACHED_ONLY: return cached state only, never fetch externally
        if (decision.type() == AccessDecisionType.ALLOW_CACHED_ONLY) {
            if (cached != null) {
                return MediatedResult.success(cached, decision);
            }
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.CACHE_ONLY_ALLOWED,
                    "No cached content available and external fetch not permitted"));
        }

        // ALLOW: return cached if present
        if (cached != null) {
            return MediatedResult.success(cached, decision);
        }

        // Cache miss: check if external acquisition is allowed (Fix 2)
        // Only strict ALLOW permits external fetch; ALLOW_CACHED_ONLY, REQUIRE_AUTH,
        // REQUIRE_SOURCE_CHECK, and DENY must all block acquisition.
        ResourceAccessRequest fetchRequest = new ResourceAccessRequest(
                request.actor(), uri, ResourceOperation.FETCH_EXTERNAL, request.purpose());
        ResourceAccessDecision fetchDecision = accessPolicy.evaluate(fetchRequest);
        if (fetchDecision.type() != AccessDecisionType.ALLOW) {
            return MediatedResult.denied(fetchDecision);
        }

        // Acquire internally
        try {
            BronzeContent acquired = acquisitionPort.fetchContent(uri);
            contentCache.put(uri, acquired);
            return MediatedResult.success(acquired, decision);
        } catch (IOException e) {
            return MediatedResult.error("Acquisition failed: " + e.getMessage());
        }
    }

    /**
     * Deletes a bronze archive entry (tombstones it).
     *
     * <p>The request must carry {@link ResourceOperation#DELETE_ARCHIVE_ENTRY}.
     * This removes the cached payload state and records a removal at the source for every
     * archive record with the URI (type-agnostic). The records' version histories are kept,
     * and an indexed-version fact is not withdrawn: derived indexes are untouched here, and
     * withdrawing them is decided by Tamias (#5) and executed by Acropolis (#10).
     * User-specific denial does NOT call this method — only explicit
     * blacklist/tombstone policy does.
     *
     * @param request the access request (operation must be DELETE_ARCHIVE_ENTRY)
     * @return the result
     */
    public MediatedResult<Void> deleteEntry(ResourceAccessRequest request) {
        if (request == null) {
            return MediatedResult.error("request must not be null");
        }

        // Fix 1: Validate operation matches this method
        if (request.operation() != ResourceOperation.DELETE_ARCHIVE_ENTRY) {
            return MediatedResult.denied(ResourceAccessDecision.deny(
                    AccessReasonCode.INVALID_OPERATION,
                    "Operation mismatch: deleteEntry requires DELETE_ARCHIVE_ENTRY, got " + request.operation()));
        }

        ResourceAccessDecision decision = accessPolicy.evaluate(request);
        if (decision.type() != AccessDecisionType.ALLOW) {
            return MediatedResult.denied(decision);
        }

        BookmarkUri uri = request.target();
        listingCache.remove(uri);
        contentCache.remove(uri);
        metadataCache.remove(uri);

        // Fix 5: Tombstone the archive records by URI (type-agnostic)
        archive.removeByUri(uri);

        return MediatedResult.success(null, decision);
    }

    /**
     * Returns whether the archive has cached state for the given URI.
     * This is useful for testing that denial does NOT delete global state.
     */
    public boolean hasCachedContent(BookmarkUri uri) {
        return contentCache.containsKey(uri);
    }

    /**
     * Returns whether the archive has a cached listing for the given URI.
     */
    public boolean hasCachedListing(BookmarkUri uri) {
        return listingCache.containsKey(uri);
    }

    /**
     * Stores content in the cache directly (for pre-populating in tests or migration).
     */
    public void storeBronzeContent(BronzeContent content) {
        contentCache.put(content.uri(), content);
    }

    /**
     * Stores a listing in the cache directly.
     */
    public void storeBronzeListing(BronzeListing listing) {
        listingCache.put(listing.containerUri(), listing);
    }
}
