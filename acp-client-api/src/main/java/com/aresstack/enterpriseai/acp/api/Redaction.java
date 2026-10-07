package com.aresstack.enterpriseai.acp.api;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Log-safe rendering of endpoint URLs. A local MCP endpoint carries its access token in the URL path
 * ({@code /mcp/<id>/<token>}), so anything printable keeps only scheme, host and port and masks the rest.
 */
final class Redaction {

    static final String MASK = "<redacted>";

    private Redaction() {
    }

    /** {@code scheme://host:port/<redacted>}; {@code <none>} for null/empty, {@code <redacted>} if unparsable. */
    static String url(String url) {
        if (url == null || url.isEmpty()) {
            return "<none>";
        }
        try {
            URI uri = new URI(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return MASK;
            }
            String origin = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
            boolean hasMore = (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null;
            return hasMore ? origin + "/" + MASK : origin;
        } catch (URISyntaxException ex) {
            return MASK;
        }
    }

    /** Masks a command-line argument that contains a URL (e.g. {@code --mcp=http://...}); others unchanged. */
    static String argument(String argument) {
        if (argument == null) {
            return "null";
        }
        int scheme = argument.indexOf("://");
        if (scheme < 0) {
            return argument;
        }
        int start = scheme;
        while (start > 0 && Character.isLetterOrDigit(argument.charAt(start - 1))) {
            start--;
        }
        return argument.substring(0, start) + url(argument.substring(start));
    }
}
