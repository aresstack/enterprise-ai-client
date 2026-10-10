package com.aresstack.enterpriseai.source.sharepoint;

import java.net.URI;
import java.util.Locale;

/**
 * Abbildung SharePoint-URL → WebDAV-UNC-Pfad, aus MainframeMate {@code SharePointPathUtil} und
 * {@code SharePointAuthenticator.extractShareRoot}:
 * <pre>
 *   https://host/path       →  \\host@SSL\DavWWWRoot\path
 *   http://host/path        →  \\host\DavWWWRoot\path
 *   https://host:8443/path  →  \\host@SSL@8443\DavWWWRoot\path
 * </pre>
 */
final class SharePointPaths {

    static final String DAV_ROOT = "DavWWWRoot";

    private SharePointPaths() {
    }

    /** UNC-Pfad zur Web-URL (Pfad URL-dekodiert, {@code /} → {@code \}, ohne abschließenden Trenner). */
    static String toUncPath(URI webUrl) {
        StringBuilder sb = new StringBuilder("\\\\").append(shareHost(webUrl)).append('\\').append(DAV_ROOT);
        String path = webUrl.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            sb.append(trimmed.replace('/', '\\'));
        }
        return sb.toString();
    }

    /** Wurzel der Freigabe für {@code net use}: {@code \\host@SSL\DavWWWRoot}. */
    static String shareRoot(URI webUrl) {
        return "\\\\" + shareHost(webUrl) + "\\" + DAV_ROOT;
    }

    private static String shareHost(URI webUrl) {
        boolean ssl = "https".equals(webUrl.getScheme().toLowerCase(Locale.ROOT));
        StringBuilder sb = new StringBuilder(webUrl.getHost());
        if (ssl) {
            sb.append("@SSL");
        }
        int port = webUrl.getPort();
        if (port > 0 && port != 80 && port != 443) {
            sb.append('@').append(port);
        }
        return sb.toString();
    }
}
