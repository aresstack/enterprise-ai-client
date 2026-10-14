package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.AddressableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Löst direkt eingegebene Adressen über die Quellen selbst auf. Das Pattern einer Quelle ist ausschließlich Routing:
 * passt es nicht oder fehlt es, wird diese Quelle nicht gefragt. Mit Source-Hint wird genau eine Quelle aufgerufen;
 * die globale Variante ruft jede passende Quelle separat mit ihrem expliziten Hint auf und rät nie zwischen Quellen.
 */
public final class ResolveKnowledgeAddressUseCase {

    private final Supplier<KnowledgeSourceCatalog> catalog;

    /** @param catalog die aktuell angebundenen Quellen, bei jedem Aufruf frisch gelesen */
    public ResolveKnowledgeAddressUseCase(Supplier<KnowledgeSourceCatalog> catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        this.catalog = catalog;
    }

    /** Ob die vollständige Eingabe dem von genau dieser Quelle gelieferten Pattern entspricht. */
    public boolean matches(String address, SourceAddress sourceHint) {
        KnowledgeSourceRegistration registration = registration(sourceHint);
        if (registration == null) {
            return false;
        }
        Pattern pattern = patternOf(registration.port());
        return pattern != null && pattern.matcher(normalize(address)).matches();
    }

    /**
     * Löst im Kontext genau der genannten Quelle auf. Andere Quellen werden auch bei einem identischen Pattern niemals
     * gefragt.
     */
    public Optional<ResolvedKnowledgeLink> resolve(String address, SourceAddress sourceHint)
            throws KnowledgeSourceException {
        KnowledgeSourceRegistration registration = registration(sourceHint);
        String normalized = normalize(address);
        if (registration == null || normalized.isEmpty()) {
            return Optional.empty();
        }
        Pattern pattern = patternOf(registration.port());
        if (pattern == null || !pattern.matcher(normalized).matches()) {
            return Optional.empty();
        }
        KnowledgeResourceId id = ((AddressableKnowledgeSource) registration.port()).resolveAddress(normalized);
        return id == null ? Optional.<ResolvedKnowledgeLink>empty()
                : Optional.of(new ResolvedKnowledgeLink(registration.sourceId(), id, normalized));
    }

    /**
     * Globale Auflösung ohne Hint: jede Quelle mit passendem Pattern wird einzeln über die Hint-Variante gefragt.
     * Fehler einer Quelle verhindern nicht, dass andere passende Quellen Kandidaten liefern.
     */
    public List<ResolvedKnowledgeLink> resolve(String address) {
        String normalized = normalize(address);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        KnowledgeSourceCatalog current = currentCatalog();
        List<ResolvedKnowledgeLink> resolved = new ArrayList<ResolvedKnowledgeLink>();
        for (SourceAddress sourceId : current.ids()) {
            if (!matches(normalized, sourceId)) {
                continue;
            }
            try {
                Optional<ResolvedKnowledgeLink> candidate = resolve(normalized, sourceId);
                if (candidate.isPresent()) {
                    resolved.add(candidate.get());
                }
            } catch (KnowledgeSourceException ignored) {
                // Kandidatenermittlung: eine nicht erreichbare Quelle darf die anderen Quellen nicht verdecken.
            }
        }
        return Collections.unmodifiableList(resolved);
    }

    /**
     * The configured source whose address is the longest prefix of {@code value} ending at a path boundary: for
     * {@code confluence://cf.intern/confluence/spaces/KEY/pages/1} the source {@code confluence://cf.intern/confluence}
     * rather than {@code confluence://cf.intern}. Compare scheme and locator without case. Empty without a match.
     */
    public Optional<SourceAddress> sourceOf(String value) {
        String normalized = normalize(value);
        SourceAddress best = null;
        for (SourceAddress candidate : currentCatalog().ids()) {
            String prefix = candidate.value();
            if (prefix.length() > normalized.length()
                    || !normalized.regionMatches(true, 0, prefix, 0, prefix.length())
                    || !isBoundary(normalized, prefix.length())) {
                continue;
            }
            if (best == null || prefix.length() > best.value().length()) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isBoundary(String value, int at) {
        if (at >= value.length()) {
            return true;
        }
        char next = value.charAt(at);
        return next == '/' || next == '?' || next == '#' || value.charAt(at - 1) == '/';
    }

    private KnowledgeSourceRegistration registration(SourceAddress sourceId) {
        return sourceId == null ? null : currentCatalog().find(sourceId);
    }

    private KnowledgeSourceCatalog currentCatalog() {
        KnowledgeSourceCatalog current = catalog.get();
        return current == null ? KnowledgeSourceCatalog.empty() : current;
    }

    private static Pattern patternOf(KnowledgeSourcePort source) {
        if (!(source instanceof AddressableKnowledgeSource)) {
            return null;
        }
        return ((AddressableKnowledgeSource) source).addressPattern();
    }

    private static String normalize(String address) {
        return address == null ? "" : address.trim();
    }
}
