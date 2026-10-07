package com.aresstack.enterpriseai.integration.live;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schwärzt eine Exception-Kette für die Ausgabe der Live-Verifikation: Meldungen, die ein Secret enthalten (roh,
 * getrimmt oder URL-kodiert), werden vollständig ersetzt; konfigurierte Adressen und ihre Hostnamen werden durch
 * {@code <host>} ersetzt. Adapter- und JDK-Transportfehler nennen den Endpunkt sonst wörtlich
 * ("embedding request to https://… failed", "connection to host failed", {@code UnknownHostException}).
 * Klassennamen, Stacktraces, Ursachen und unterdrückte Exceptions bleiben erhalten.
 */
final class FailureRedaction {

    /** Eine geschwärzte Exception; die Meldung beginnt mit dem Namen der ursprünglichen Klasse. */
    static final class RedactedFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String originalClassName;

        RedactedFailure(String originalClassName, String message, Throwable cause, StackTraceElement[] stackTrace) {
            super(message == null ? originalClassName : originalClassName + ": " + message, cause);
            this.originalClassName = originalClassName;
            setStackTrace(stackTrace);
        }

        String originalClassName() {
            return originalClassName;
        }
    }

    private FailureRedaction() {
    }

    /**
     * @param thrown die ursprüngliche Exception
     * @param secrets je Secret ein Array: Name der Quelle, dann die zu suchenden Varianten (siehe {@link #secretVariants})
     * @param addresses zu ersetzende Adressen und Hostnamen (siehe {@link #addressVariants})
     * @return die geschwärzte Kette
     */
    static RedactedFailure redact(Throwable thrown, List<String[]> secrets, List<String> addresses) {
        return redactChain(thrown, secrets, addresses, new IdentityHashMap<Throwable, Boolean>());
    }

    private static RedactedFailure redactChain(Throwable current, List<String[]> secrets, List<String> addresses,
                                               Map<Throwable, Boolean> visited) {
        if (current == null || visited.containsKey(current)) {
            return null;
        }
        visited.put(current, Boolean.TRUE);
        RedactedFailure cause = redactChain(current.getCause(), secrets, addresses, visited);
        RedactedFailure result = new RedactedFailure(current.getClass().getName(),
                redactMessage(current.getMessage(), current.getClass().getSimpleName(), secrets, addresses), cause,
                current.getStackTrace());
        for (Throwable suppressed : current.getSuppressed()) {
            RedactedFailure redactedSuppressed = redactChain(suppressed, secrets, addresses, visited);
            if (redactedSuppressed != null) {
                result.addSuppressed(redactedSuppressed);
            }
        }
        return result;
    }

    static String redactMessage(String message, String className, List<String[]> secrets, List<String> addresses) {
        if (message == null) {
            return null;
        }
        for (String[] variants : secrets) {
            for (int i = 1; i < variants.length; i++) {
                if (!variants[i].isEmpty() && message.contains(variants[i])) {
                    return "<Meldung der " + className + " geschwärzt: enthält das Secret aus " + variants[0] + ">";
                }
            }
        }
        String redacted = message;
        for (String address : addresses) {
            redacted = redacted.replace(address, "<host>");
        }
        return redacted;
    }

    /** @return Name der Quelle, dann Rohwert, getrimmter und URL-kodierte Werte; {@code null} für leere Werte */
    static String[] secretVariants(String sourceName, String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            return null;
        }
        return new String[] {sourceName, secret, secret.trim(), urlEncoded(secret), urlEncoded(secret.trim())};
    }

    private static String urlEncoded(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            return value;
        }
    }

    /** @return die Adressen und ihre Hostnamen, längste zuerst (damit die URL vor ihrem Host ersetzt wird) */
    static List<String> addressVariants(Collection<String> addresses) {
        Set<String> result = new LinkedHashSet<String>();
        for (String value : addresses) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            result.add(value.trim());
            try {
                String host = URI.create(value.trim()).getHost();
                if (host != null && !host.isEmpty()) {
                    result.add(host);
                }
            } catch (IllegalArgumentException ignored) {
                // keine URL, der Rohwert reicht
            }
        }
        List<String> ordered = new ArrayList<String>(result);
        Collections.sort(ordered, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });
        return ordered;
    }
}
