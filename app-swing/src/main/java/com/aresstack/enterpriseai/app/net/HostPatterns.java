package com.aresstack.enterpriseai.app.net;

import java.util.List;
import java.util.Locale;

/** Hostregeln der Proxy-Ausnahmen: Loopback und Muster mit {@code *} am Anfang oder Ende ({@code *.intern}). */
final class HostPatterns {

    private HostPatterns() {
    }

    static boolean isLoopback(String host) {
        if (host == null) {
            return true;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        return h.equals("localhost") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1") || h.startsWith("127.");
    }

    static boolean matchesAny(String host, List<String> patterns) {
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (matches(h, pattern.toLowerCase(Locale.ROOT).trim())) {
                return true;
            }
        }
        return false;
    }

    static boolean matches(String host, String pattern) {
        if (pattern.isEmpty()) {
            return false;
        }
        boolean leading = pattern.startsWith("*");
        boolean trailing = pattern.endsWith("*");
        if (leading && trailing) {
            String core = pattern.substring(1, pattern.length() - 1);
            return core.isEmpty() || host.contains(core);
        }
        if (leading) {
            return host.endsWith(pattern.substring(1));
        }
        if (trailing) {
            return host.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return host.equals(pattern);
    }
}
