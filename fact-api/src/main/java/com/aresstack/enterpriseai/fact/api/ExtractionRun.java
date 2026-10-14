package com.aresstack.enterpriseai.fact.api;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;

import java.time.Instant;

/**
 * The last completed run of one extractor on one resource: extractor version plus the revision and content hash it
 * analysed. Recorded together with the run's automatic facts (also when it found nothing), so "is this analysis
 * current" never depends on which facts were kept, reviewed or found.
 */
public final class ExtractionRun {

    private final KnowledgeResourceId resourceId;
    private final FactProvenance provenance;
    private final Instant completedAt;

    private ExtractionRun(KnowledgeResourceId resourceId, FactProvenance provenance, Instant completedAt) {
        if (resourceId == null || provenance == null) {
            throw new IllegalArgumentException("resource id and provenance must not be null");
        }
        if (provenance.origin() != FactOrigin.AUTOMATIC) {
            throw new IllegalArgumentException("an extraction run needs an automatic provenance: " + provenance);
        }
        this.resourceId = resourceId;
        this.provenance = provenance;
        this.completedAt = completedAt;
    }

    /** A run on {@code resource}; {@code provenance} names extractor, version, revision and content hash. */
    public static ExtractionRun of(KnowledgeResourceId resource, FactProvenance provenance) {
        return new ExtractionRun(resource, provenance, null);
    }

    /** Restore a run as stored. */
    public static ExtractionRun of(KnowledgeResourceId resource, FactProvenance provenance, Instant completedAt) {
        return new ExtractionRun(resource, provenance, completedAt);
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    public FactProvenance provenance() {
        return provenance;
    }

    public String extractorId() {
        return provenance.extractorId();
    }

    /** When the store recorded it; {@code null} before it is stored. */
    public Instant completedAt() {
        return completedAt;
    }

    /** Whether this run analysed the given state (same rule as {@link MetadataFact#freshness}). */
    public FactFreshness freshness(KnowledgeRevision currentRevision, String currentContentHash) {
        String hash = currentContentHash == null ? "" : currentContentHash.trim();
        if (!provenance.contentHash().isEmpty() && !hash.isEmpty()) {
            return provenance.contentHash().equals(hash) ? FactFreshness.CURRENT : FactFreshness.STALE;
        }
        if (provenance.sourceRevision().isKnown() && currentRevision != null && currentRevision.isKnown()) {
            return provenance.sourceRevision().equals(currentRevision) ? FactFreshness.CURRENT : FactFreshness.STALE;
        }
        return FactFreshness.UNKNOWN;
    }

    /** Whether a run of {@code extractorVersion} on the given state would repeat this one. */
    public boolean covers(String extractorVersion, KnowledgeRevision currentRevision, String currentContentHash) {
        String version = extractorVersion == null ? "" : extractorVersion.trim();
        return provenance.extractorVersion().equals(version)
                && freshness(currentRevision, currentContentHash) == FactFreshness.CURRENT;
    }

    /** Equal runs describe the same analysis; the completion time is not compared. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExtractionRun)) {
            return false;
        }
        ExtractionRun that = (ExtractionRun) other;
        return resourceId.equals(that.resourceId) && provenance.equals(that.provenance);
    }

    @Override
    public int hashCode() {
        return 31 * resourceId.hashCode() + provenance.hashCode();
    }

    @Override
    public String toString() {
        return "ExtractionRun[" + resourceId + ", " + provenance + ", completed " + completedAt + "]";
    }
}
