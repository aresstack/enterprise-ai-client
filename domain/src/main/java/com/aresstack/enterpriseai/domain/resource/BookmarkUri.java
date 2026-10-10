package com.aresstack.enterpriseai.domain.resource;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A bookmark-style URI that addresses a virtual resource.
 *
 * <p>For standard schemes ({@code file}, {@code http}, {@code https}, {@code ftp}),
 * the URI is parsed and validated using {@link java.net.URI}. For opaque/non-standard
 * schemes ({@code ndv}, {@code mail}, {@code sharepoint}, etc.), the scheme-specific
 * part is kept as an opaque locator string.
 *
 * <p>Examples:
 * <pre>
 *   file:///C:/Users/example/Documents/note.txt
 *   file://server/share/folder/document.txt
 *   https://example.internal/wiki/page
 *   ndv://mainframe/system/program.cgp
 *   mail://inbox/2024-03-01/subject
 * </pre>
 *
 * <p>{@code local://} is not a canonical scheme. Local filesystem resources must use
 * {@code file:} URIs. If a {@code local://} URI is encountered during parsing, it is
 * normalized to {@code file:///} automatically.
 *
 * <p>This class is immutable and safe for use as a map key.
 *
 * <p>Adapted from MainframeMate's {@code VirtualResourceRef} prefix concept and
 * {@code BookmarkEntry} protocol-prefixed paths.
 */
public final class BookmarkUri {

    private final ResourceScheme scheme;
    private final String schemeSpecificPart;
    private final URI standardUri; // non-null only for standard URI schemes

    private BookmarkUri(ResourceScheme scheme, String schemeSpecificPart, URI standardUri) {
        this.scheme = scheme;
        this.schemeSpecificPart = schemeSpecificPart;
        this.standardUri = standardUri;
    }

    /**
     * Parses a bookmark URI string.
     *
     * <p>Standard URIs (file, http, https, ftp) are fully parsed via {@link java.net.URI}.
     * Non-standard schemes are split at the first {@code ":"} and the remainder is kept
     * as an opaque scheme-specific part.
     *
     * <p>The legacy {@code local://} prefix is normalized to {@code file:///}.
     *
     * @param uri the full URI string
     * @return a parsed {@code BookmarkUri}
     * @throws IllegalArgumentException if the URI is null, empty or malformed
     */
    public static BookmarkUri parse(String uri) {
        if (uri == null || uri.isEmpty()) {
            throw new IllegalArgumentException("Bookmark URI must not be null or empty");
        }

        // Normalize legacy local:// to file:///
        if (uri.regionMatches(true, 0, "local://", 0, 8)) {
            uri = "file:///" + uri.substring(8);
        }

        int colonIdx = uri.indexOf(':');
        if (colonIdx <= 0) {
            throw new IllegalArgumentException(
                    "Bookmark URI must contain a scheme followed by ':' — got: " + uri);
        }

        String rawScheme = uri.substring(0, colonIdx);
        ResourceScheme scheme = ResourceScheme.of(rawScheme);

        // For standard schemes, delegate to java.net.URI
        if (isStandardScheme(scheme)) {
            try {
                URI parsed = new URI(uri);
                String ssp = parsed.getSchemeSpecificPart();
                return new BookmarkUri(scheme, ssp, parsed);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Malformed URI: " + uri + " — " + e.getMessage());
            }
        }

        // For opaque/non-standard schemes, keep the scheme-specific part as-is
        String ssp = uri.substring(colonIdx + 1);
        // Strip leading "//" for consistency with scheme://path convention
        if (ssp.startsWith("//")) {
            ssp = ssp.substring(2);
        }
        return new BookmarkUri(scheme, ssp, null);
    }

    /**
     * Constructs a bookmark URI from a scheme and a scheme-specific part.
     *
     * @param scheme the resource scheme
     * @param schemeSpecificPart the path/locator after the scheme
     * @return a new {@code BookmarkUri}
     */
    public static BookmarkUri of(ResourceScheme scheme, String schemeSpecificPart) {
        if (scheme == null) {
            throw new IllegalArgumentException("Scheme must not be null");
        }
        if (schemeSpecificPart == null) {
            throw new IllegalArgumentException("Scheme-specific part must not be null");
        }

        if (isStandardScheme(scheme)) {
            String full = scheme.name() + ":" + schemeSpecificPart;
            try {
                URI parsed = new URI(full);
                return new BookmarkUri(scheme, parsed.getSchemeSpecificPart(), parsed);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Malformed URI: " + full + " — " + e.getMessage());
            }
        }

        return new BookmarkUri(scheme, schemeSpecificPart, null);
    }

    /** Returns the resolved scheme. */
    public ResourceScheme scheme() {
        return scheme;
    }

    /**
     * Returns the scheme-specific part of the URI.
     *
     * <p>For standard URIs this is the decoded scheme-specific part from {@link URI#getSchemeSpecificPart()}.
     * For opaque schemes, this is the locator string after stripping the leading {@code "://"}.
     */
    public String schemeSpecificPart() {
        return schemeSpecificPart;
    }

    /**
     * Returns the underlying {@link java.net.URI} if this bookmark uses a standard scheme,
     * or {@code null} for opaque/non-standard schemes.
     */
    public URI toURI() {
        return standardUri;
    }

    /** Returns the full URI string representation. */
    @Override
    public String toString() {
        if (standardUri != null) {
            return standardUri.toString();
        }
        return scheme.name() + "://" + schemeSpecificPart;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookmarkUri)) return false;
        BookmarkUri that = (BookmarkUri) o;
        return scheme.equals(that.scheme) && schemeSpecificPart.equals(that.schemeSpecificPart);
    }

    @Override
    public int hashCode() {
        return 31 * scheme.hashCode() + schemeSpecificPart.hashCode();
    }

    /** Ob das Schema hierarchisch über {@link URI} geparst wird ({@code file}, {@code http(s)}, {@code ftp}). */
    public static boolean isStandard(ResourceScheme scheme) {
        return isStandardScheme(scheme);
    }

    private static boolean isStandardScheme(ResourceScheme scheme) {
        return ResourceScheme.file().equals(scheme)
                || ResourceScheme.http().equals(scheme)
                || ResourceScheme.https().equals(scheme)
                || ResourceScheme.ftp().equals(scheme);
    }
}
