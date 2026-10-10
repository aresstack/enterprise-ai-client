package com.aresstack.enterpriseai.domain.resource;

/**
 * A lightweight, immutable reference to a virtual resource.
 *
 * <p>A reference combines the bookmark URI that addresses the resource
 * with the structural kind that classifies it. It does not carry content
 * or transport state — it is a stable handle suitable for indexing and caching.
 *
 * <p>Adapted from MainframeMate's {@code VirtualResource} (which coupled ref + kind
 * with UI/transport state). Only the identity concept is kept here; transport state
 * belongs in {@code proasteion}.
 */
public final class VirtualResourceRef {

    private final BookmarkUri uri;
    private final VirtualResourceKind kind;

    public VirtualResourceRef(BookmarkUri uri, VirtualResourceKind kind) {
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null");
        }
        if (kind == null) {
            throw new IllegalArgumentException("Kind must not be null");
        }
        this.uri = uri;
        this.kind = kind;
    }

    /** Returns the bookmark URI that addresses this resource. */
    public BookmarkUri uri() {
        return uri;
    }

    /** Returns the structural kind of this resource. */
    public VirtualResourceKind kind() {
        return kind;
    }

    @Override
    public String toString() {
        return "VirtualResourceRef{" + uri + ", " + kind + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VirtualResourceRef)) return false;
        VirtualResourceRef that = (VirtualResourceRef) o;
        return uri.equals(that.uri) && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return 31 * uri.hashCode() + kind.hashCode();
    }
}
