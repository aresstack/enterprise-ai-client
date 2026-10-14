package com.aresstack.enterpriseai.fact.api;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;

/**
 * Where a fact comes from and against which state of the resource it was determined: origin, extractor (or editor)
 * id and version, plus the source revision and content hash of the document if known. Revision and hash describe the
 * analysed state only; they are never part of the resource identity.
 */
public final class FactProvenance {

    /** Longest extractor id or version; matches the column width in every store. */
    public static final int MAX_EXTRACTOR_LENGTH = 128;
    /** Longest source revision version; matches the column width in every store. */
    public static final int MAX_REVISION_LENGTH = 512;
    /** Longest content hash; matches the column width in every store. */
    public static final int MAX_CONTENT_HASH_LENGTH = 256;

    private final FactOrigin origin;
    private final String extractorId;
    private final String extractorVersion;
    private final KnowledgeRevision sourceRevision;
    private final String contentHash;

    private FactProvenance(FactOrigin origin, String extractorId, String extractorVersion,
                           KnowledgeRevision sourceRevision, String contentHash) {
        if (origin == null) {
            throw new IllegalArgumentException("origin must not be null");
        }
        String id = extractorId == null ? "" : extractorId.trim();
        if (id.isEmpty() || id.length() > MAX_EXTRACTOR_LENGTH) {
            throw new IllegalArgumentException("extractor id must have 1 to " + MAX_EXTRACTOR_LENGTH + " characters");
        }
        String version = extractorVersion == null ? "" : extractorVersion.trim();
        if (version.length() > MAX_EXTRACTOR_LENGTH) {
            throw new IllegalArgumentException("extractor version longer than " + MAX_EXTRACTOR_LENGTH);
        }
        String hash = contentHash == null ? "" : contentHash.trim();
        if (hash.length() > MAX_CONTENT_HASH_LENGTH) {
            throw new IllegalArgumentException("content hash longer than " + MAX_CONTENT_HASH_LENGTH);
        }
        if (sourceRevision != null && sourceRevision.version().length() > MAX_REVISION_LENGTH) {
            throw new IllegalArgumentException("source revision longer than " + MAX_REVISION_LENGTH);
        }
        this.origin = origin;
        this.extractorId = id;
        this.extractorVersion = version;
        this.sourceRevision = sourceRevision == null ? KnowledgeRevision.unknown() : sourceRevision;
        this.contentHash = hash;
    }

    /** A fact determined by extractor {@code extractorId} in version {@code extractorVersion}. */
    public static FactProvenance automatic(String extractorId, String extractorVersion) {
        return new FactProvenance(FactOrigin.AUTOMATIC, extractorId, extractorVersion, null, null);
    }

    /** A fact added by a person; {@code editorId} names the editor or the input path (e.g. {@code manual}). */
    public static FactProvenance manual(String editorId) {
        return new FactProvenance(FactOrigin.MANUAL, editorId, null, null, null);
    }

    /** Restore a provenance as stored. */
    public static FactProvenance of(FactOrigin origin, String extractorId, String extractorVersion,
                                    KnowledgeRevision sourceRevision, String contentHash) {
        return new FactProvenance(origin, extractorId, extractorVersion, sourceRevision, contentHash);
    }

    /** The same provenance, determined against {@code revision} of the resource. */
    public FactProvenance withSourceRevision(KnowledgeRevision revision) {
        return new FactProvenance(origin, extractorId, extractorVersion, revision, contentHash);
    }

    /** The same provenance, determined against the document with {@code hash} (see KnowledgeDocument.contentHash()). */
    public FactProvenance withContentHash(String hash) {
        return new FactProvenance(origin, extractorId, extractorVersion, sourceRevision, hash);
    }

    public FactOrigin origin() {
        return origin;
    }

    public String extractorId() {
        return extractorId;
    }

    /** Version of the extractor, or {@code ""}. */
    public String extractorVersion() {
        return extractorVersion;
    }

    /** Revision of the analysed resource; {@link KnowledgeRevision#unknown()} if not known. */
    public KnowledgeRevision sourceRevision() {
        return sourceRevision;
    }

    /** Content hash of the analysed document, or {@code ""}. */
    public String contentHash() {
        return contentHash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FactProvenance)) {
            return false;
        }
        FactProvenance that = (FactProvenance) other;
        return origin == that.origin && extractorId.equals(that.extractorId)
                && extractorVersion.equals(that.extractorVersion) && sourceRevision.equals(that.sourceRevision)
                && contentHash.equals(that.contentHash);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * origin.hashCode() + extractorId.hashCode()) + contentHash.hashCode();
    }

    @Override
    public String toString() {
        return "FactProvenance[" + origin + ", " + extractorId + " " + extractorVersion + ", " + sourceRevision
                + ", hash=" + contentHash + "]";
    }
}
