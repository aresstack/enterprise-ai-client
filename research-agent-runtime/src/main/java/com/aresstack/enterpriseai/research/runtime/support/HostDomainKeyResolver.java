package com.aresstack.enterpriseai.research.runtime.support;

import java.util.Locale;

/**
 * Der {@link DomainKeyResolver} des Enterprise-Clients: Quellen sind Confluence-Seiten und andere
 * Firmenquellen, keine offenen Webdomains, darum ohne Public-Suffix-Liste (askai arch bündelte dafür einen
 * Schnappschuss von publicsuffix.org). Die registrierbare Domäne ist der Host ohne Subdomänen bis auf zwei
 * Labels; das genügt, um Challenge-/Blacklist-Familien zusammenzuhalten.
 */
public final class HostDomainKeyResolver implements DomainKeyResolver {

    @Override
    public DomainIdentity resolve(String urlOrHost) {
        String host = hostOf(urlOrHost);
        if (host.isEmpty()) {
            return new DomainIdentity("", "", HostKind.SINGLE_LABEL_HOST);
        }
        if (isIpLiteral(host)) {
            return new DomainIdentity(host, host, HostKind.IP_LITERAL);
        }
        if ("localhost".equals(host)) {
            return new DomainIdentity(host, host, HostKind.LOCAL_HOST);
        }
        int last = host.lastIndexOf('.');
        if (last < 0) {
            return new DomainIdentity(host, host, HostKind.SINGLE_LABEL_HOST);
        }
        int previous = host.lastIndexOf('.', last - 1);
        return new DomainIdentity(host, previous < 0 ? host : host.substring(previous + 1),
                HostKind.REGISTERED_NAME);
    }

    /** Extract the bare host (no scheme, no port, no path) from a URL or return the input as host. */
    public static String hostOf(String urlOrHost) {
        String value = urlOrHost == null ? "" : urlOrHost.trim().toLowerCase(Locale.ROOT);
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
            int slash = value.indexOf('/');
            if (slash >= 0) {
                value = value.substring(0, slash);
            }
        }
        int at = value.indexOf('@');
        if (at >= 0) {
            value = value.substring(at + 1);
        }
        if (value.startsWith("[")) { // IPv6 literal with optional port: [::1]:8080
            int end = value.indexOf(']');
            return end > 0 ? value.substring(0, end + 1) : value;
        }
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(0, colon) : value;
    }

    private static boolean isIpLiteral(String host) {
        if (host.startsWith("[") || host.matches("[0-9a-f:]*:[0-9a-f:]*")) {
            return true; // IPv6
        }
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }
}
