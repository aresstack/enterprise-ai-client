package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;

/**
 * Bronze-level metadata for a resource: existence, name, type, size, modified time.
 *
 * <p>This is the minimal "I know this resource exists" record in the bronze archive.
 */
public final class BronzeMetadata {

    private final BookmarkUri uri;
    private final String name;
    private final String contentType;
    private final long sizeBytes;
    private final long modifiedAtMillis;
    private final long observedAtMillis;

    public BronzeMetadata(BookmarkUri uri, String name, String contentType,
                          long sizeBytes, long modifiedAtMillis, long observedAtMillis) {
        if (uri == null) throw new IllegalArgumentException("uri must not be null");
        this.uri = uri;
        this.name = name;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.modifiedAtMillis = modifiedAtMillis;
        this.observedAtMillis = observedAtMillis;
    }

    public BookmarkUri uri() { return uri; }
    public String name() { return name; }
    public String contentType() { return contentType; }
    public long sizeBytes() { return sizeBytes; }
    public long modifiedAtMillis() { return modifiedAtMillis; }
    public long observedAtMillis() { return observedAtMillis; }

    @Override
    public String toString() {
        return "BronzeMetadata{" + uri + ", " + name + "}";
    }
}
