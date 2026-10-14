package com.aresstack.enterpriseai.fact.api;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;

import java.time.Instant;

/**
 * One structured fact about one resource: own {@link FactId}, the stable {@link KnowledgeResourceId} (the bookmark,
 * never revision or hash), type and payload schema version, JSON payload, optional role and anchor, provenance and
 * review state. Timestamps are set by the store; a fact that was not stored yet has none.
 */
public final class MetadataFact {

    private final FactId id;
    private final KnowledgeResourceId resourceId;
    private final FactTypeId typeId;
    private final int schemaVersion;
    private final FactPayload payload;
    private final FactRole role;
    private final FactProvenance provenance;
    private final FactReview review;
    private final FactAnchor anchor;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MetadataFact(Builder builder) {
        if (builder.schemaVersion < 1) {
            throw new IllegalArgumentException("schema version must be at least 1: " + builder.schemaVersion);
        }
        this.id = builder.id == null ? FactId.random() : builder.id;
        this.resourceId = builder.resourceId;
        this.typeId = builder.typeId;
        this.schemaVersion = builder.schemaVersion;
        this.payload = builder.payload;
        this.role = builder.role;
        this.provenance = builder.provenance;
        this.review = builder.review == null ? FactReview.UNREVIEWED : builder.review;
        this.anchor = builder.anchor;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    /** A new fact with a random id, schema version 1, unreviewed, without role and anchor. */
    public static Builder builder(KnowledgeResourceId resourceId, FactTypeId typeId, FactPayload payload,
                                  FactProvenance provenance) {
        return new Builder(resourceId, typeId, payload, provenance);
    }

    /** A builder with all values of this fact, to store an edited copy under the same id. */
    public Builder toBuilder() {
        return new Builder(resourceId, typeId, payload, provenance).id(id).schemaVersion(schemaVersion).role(role)
                .review(review).anchor(anchor).timestamps(createdAt, updatedAt);
    }

    public FactId id() {
        return id;
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    public FactTypeId typeId() {
        return typeId;
    }

    /** Schema version of the payload (at most the current version of the type when stored). */
    public int schemaVersion() {
        return schemaVersion;
    }

    public FactPayload payload() {
        return payload;
    }

    /** Explicit role, or {@code null}. */
    public FactRole role() {
        return role;
    }

    public FactProvenance provenance() {
        return provenance;
    }

    public FactReview review() {
        return review;
    }

    /** Place inside the resource, or {@code null}. */
    public FactAnchor anchor() {
        return anchor;
    }

    /** When the store first saved the fact, or {@code null} if never stored. */
    public Instant createdAt() {
        return createdAt;
    }

    /** When the store last saved the fact, or {@code null} if never stored. */
    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Compare the analysed state with the current state of the resource. The content hash decides if both sides
     * have one, else the revision if both are known; otherwise the answer is {@link FactFreshness#UNKNOWN}.
     *
     * @param currentRevision    current revision of the resource, or {@code null}
     * @param currentContentHash current content hash of the document, or {@code null}/empty
     */
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

    /** Equal facts carry the same values; timestamps are not compared. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MetadataFact)) {
            return false;
        }
        MetadataFact that = (MetadataFact) other;
        return id.equals(that.id) && resourceId.equals(that.resourceId) && typeId.equals(that.typeId)
                && schemaVersion == that.schemaVersion && payload.equals(that.payload)
                && (role == null ? that.role == null : role.equals(that.role))
                && provenance.equals(that.provenance) && review == that.review
                && (anchor == null ? that.anchor == null : anchor.equals(that.anchor));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "MetadataFact[" + id + ", " + resourceId + ", " + typeId + " v" + schemaVersion + ", "
                + provenance.origin() + "]";
    }

    public static final class Builder {

        private final KnowledgeResourceId resourceId;
        private final FactTypeId typeId;
        private final FactPayload payload;
        private final FactProvenance provenance;
        private FactId id;
        private int schemaVersion = 1;
        private FactRole role;
        private FactReview review;
        private FactAnchor anchor;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder(KnowledgeResourceId resourceId, FactTypeId typeId, FactPayload payload,
                        FactProvenance provenance) {
            if (resourceId == null || typeId == null || payload == null || provenance == null) {
                throw new IllegalArgumentException("resource, type, payload and provenance must not be null");
            }
            this.resourceId = resourceId;
            this.typeId = typeId;
            this.payload = payload;
            this.provenance = provenance;
        }

        public Builder id(FactId value) {
            this.id = value;
            return this;
        }

        public Builder schemaVersion(int value) {
            this.schemaVersion = value;
            return this;
        }

        public Builder role(FactRole value) {
            this.role = value;
            return this;
        }

        public Builder review(FactReview value) {
            this.review = value;
            return this;
        }

        public Builder anchor(FactAnchor value) {
            this.anchor = value;
            return this;
        }

        /** Set by a store when it reads or writes the fact. */
        public Builder timestamps(Instant created, Instant updated) {
            this.createdAt = created;
            this.updatedAt = updated;
            return this;
        }

        public MetadataFact build() {
            return new MetadataFact(this);
        }
    }
}
