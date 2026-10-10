package com.aresstack.enterpriseai.domain.resource;

/**
 * Identifies the URI scheme of a virtual resource.
 *
 * <p>This is an extensible value object, not a closed enum. Well-known schemes have
 * factory methods for convenience (no static instances: the client forbids global singletons), but any
 * scheme string is accepted via
 * {@link #of(String)}. This allows future adapters to introduce new schemes
 * (e.g. {@code "mvs"}, {@code "tn3270"}) without modifying core code.
 *
 * <p>The scheme is stored in lowercase-normalized form as required by RFC 3986 §3.1.
 *
 * <p>Adapted from MainframeMate's {@code VirtualBackendType} enum and the
 * scheme prefixes in {@code VirtualResourceRef} and {@code BookmarkEntry}.
 */
public final class ResourceScheme {

    // ── Well-known schemes ───────────────────────────────────────────────────────

    /** Standard local filesystem ({@code file:///path}). Canonical for local resources. */
    public static ResourceScheme file() {
        return new ResourceScheme("file");
    }

    /** FTP file transfer. */
    public static ResourceScheme ftp() {
        return new ResourceScheme("ftp");
    }

    /** Natural/NDV mainframe resources. */
    public static ResourceScheme ndv() {
        return new ResourceScheme("ndv");
    }

    /** Mail resources (OST/PST). */
    public static ResourceScheme mail() {
        return new ResourceScheme("mail");
    }

    /** HTTP resources. */
    public static ResourceScheme http() {
        return new ResourceScheme("http");
    }

    /** HTTPS resources. */
    public static ResourceScheme https() {
        return new ResourceScheme("https");
    }

    /** SharePoint resources. */
    public static ResourceScheme sharepoint() {
        return new ResourceScheme("sharepoint");
    }

    /** Confluence wiki pages. */
    public static ResourceScheme confluence() {
        return new ResourceScheme("confluence");
    }

    /** Generic wiki pages. */
    public static ResourceScheme wiki() {
        return new ResourceScheme("wiki");
    }

    // ── Instance fields ──────────────────────────────────────────────────────────

    private final String name;

    private ResourceScheme(String name) {
        this.name = name;
    }

    /**
     * Returns a {@code ResourceScheme} for an arbitrary scheme string.
     *
     * <p>The string is normalized to lowercase; equality is by name.
     *
     * @param scheme the scheme name (e.g. {@code "file"}, {@code "ndv"}, {@code "mvs"})
     * @return a {@code ResourceScheme} instance; never null
     * @throws IllegalArgumentException if scheme is null or empty
     */
    public static ResourceScheme of(String scheme) {
        if (scheme == null || scheme.isEmpty()) {
            throw new IllegalArgumentException("Scheme must not be null or empty");
        }
        String lower = scheme.toLowerCase(java.util.Locale.ROOT);
        return new ResourceScheme(lower);
    }

    /** Returns the lowercase scheme name. */
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceScheme)) return false;
        return name.equals(((ResourceScheme) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
