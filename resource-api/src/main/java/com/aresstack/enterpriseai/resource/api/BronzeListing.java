package com.aresstack.enterpriseai.resource.api;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceKind;

import java.util.Collections;
import java.util.List;

/**
 * Bronze-level directory listing: child entries of a container resource.
 *
 * <p>A directory listing is bronze-managed state. It does not have to be
 * indexed as text, but it is still managed by the archive.
 */
public final class BronzeListing {

    private final BookmarkUri containerUri;
    private final List<Entry> entries;
    private final long observedAtMillis;

    public BronzeListing(BookmarkUri containerUri, List<Entry> entries, long observedAtMillis) {
        if (containerUri == null) throw new IllegalArgumentException("containerUri must not be null");
        this.containerUri = containerUri;
        this.entries = entries != null
                ? Collections.unmodifiableList(new java.util.ArrayList<Entry>(entries))
                : Collections.<Entry>emptyList();
        this.observedAtMillis = observedAtMillis;
    }

    public BookmarkUri containerUri() { return containerUri; }
    public List<Entry> entries() { return entries; }
    public long observedAtMillis() { return observedAtMillis; }

    @Override
    public String toString() {
        return "BronzeListing{" + containerUri + ", " + entries.size() + " entries}";
    }

    /**
     * A single child entry in a directory listing.
     */
    public static final class Entry {
        private final BookmarkUri uri;
        private final String name;
        private final VirtualResourceKind kind;

        public Entry(BookmarkUri uri, String name, VirtualResourceKind kind) {
            if (uri == null) throw new IllegalArgumentException("uri must not be null");
            if (name == null) throw new IllegalArgumentException("name must not be null");
            if (kind == null) throw new IllegalArgumentException("kind must not be null");
            this.uri = uri;
            this.name = name;
            this.kind = kind;
        }

        public BookmarkUri uri() { return uri; }
        public String name() { return name; }
        public VirtualResourceKind kind() { return kind; }

        @Override
        public String toString() {
            return "Entry{" + name + ", " + kind + "}";
        }
    }
}
