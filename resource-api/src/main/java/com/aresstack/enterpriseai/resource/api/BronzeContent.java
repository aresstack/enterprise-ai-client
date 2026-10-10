package com.aresstack.enterpriseai.resource.api;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;

import java.util.Arrays;

/**
 * Bronze-level content snapshot: the actual acquired content for a resource.
 *
 * <p>This class is immutable; byte arrays are defensively copied on construction
 * and on access.
 */
public final class BronzeContent {

    private final BookmarkUri uri;
    private final byte[] content;
    private final ResourceDigest digest;
    private final long fetchedAtMillis;

    public BronzeContent(BookmarkUri uri, byte[] content, ResourceDigest digest, long fetchedAtMillis) {
        if (uri == null) throw new IllegalArgumentException("uri must not be null");
        if (content == null) throw new IllegalArgumentException("content must not be null");
        if (digest == null) throw new IllegalArgumentException("digest must not be null");
        this.uri = uri;
        this.content = Arrays.copyOf(content, content.length);
        this.digest = digest;
        this.fetchedAtMillis = fetchedAtMillis;
    }

    public BookmarkUri uri() { return uri; }
    public byte[] content() { return Arrays.copyOf(content, content.length); }
    public ResourceDigest digest() { return digest; }
    public long fetchedAtMillis() { return fetchedAtMillis; }

    @Override
    public String toString() {
        return "BronzeContent{" + uri + ", " + content.length + " bytes}";
    }
}

