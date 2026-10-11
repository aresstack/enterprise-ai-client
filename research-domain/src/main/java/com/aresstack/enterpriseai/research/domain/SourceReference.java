package com.aresstack.enterpriseai.research.domain;

/**
 * What a research project keeps of a source: a contentless reference like corenth's {@code VirtualResourceRef}.
 * The page text lives in the knowledge index (resolved by resource id or bookmark); a {@link SourceCapture} with
 * its structural blocks exists only while a page is being processed and is never stored in the project.
 */
public final class SourceReference {

    private final String captureId;
    private final String sourceId;
    private final String canonicalUrl;
    private final long capturedAtMillis;
    private final String checksum;
    private final String title;

    public SourceReference(String captureId, String sourceId, String canonicalUrl, long capturedAtMillis,
                           String checksum, String title) {
        this.captureId = captureId == null ? "" : captureId;
        this.sourceId = sourceId == null ? "" : sourceId;
        this.canonicalUrl = canonicalUrl == null ? "" : canonicalUrl;
        this.capturedAtMillis = capturedAtMillis;
        this.checksum = checksum == null ? "" : checksum;
        this.title = title == null ? "" : title;
    }

    /** The reference of a processed capture, without its content. */
    public static SourceReference of(SourceCapture capture) {
        return new SourceReference(capture.getCaptureId(), capture.getSourceId(), capture.getCanonicalUrl(),
                capture.getCapturedAtMillis(), capture.getChecksum(), capture.getTitle());
    }

    public String getCaptureId() {
        return captureId;
    }

    /** The resource id in the knowledge index (e.g. {@code confluence:...}). */
    public String getSourceId() {
        return sourceId;
    }

    /** Bookmark / navigation address of the resource. */
    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public long getCapturedAtMillis() {
        return capturedAtMillis;
    }

    /** Revision / content checksum of the version that was evaluated. */
    public String getChecksum() {
        return checksum;
    }

    public String getTitle() {
        return title;
    }
}
