package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** RAG-Schalter je Nachricht: aus, oder an mit optionaler Einschränkung auf Quellen. Unveränderlich. */
public final class RagOptions {

    private final boolean enabled;
    private final Set<SourceAddress> sources;

    private RagOptions(boolean enabled, Set<SourceAddress> sources) {
        this.enabled = enabled;
        this.sources = sources;
    }

    /** Normaler Chat ohne Retrieval. */
    public static RagOptions disabled() {
        return new RagOptions(false, Collections.<SourceAddress>emptySet());
    }

    /** Retrieval über alle Quellen. */
    public static RagOptions enabled() {
        return new RagOptions(true, Collections.<SourceAddress>emptySet());
    }

    /** Retrieval nur in den genannten Quellen; leer heißt alle. */
    public RagOptions restrictedTo(Collection<SourceAddress> sourceIds) {
        Set<SourceAddress> copy = new LinkedHashSet<SourceAddress>();
        if (sourceIds != null) {
            for (SourceAddress id : sourceIds) {
                if (id == null) {
                    throw new IllegalArgumentException("Quellenfilter enthält null");
                }
                copy.add(id);
            }
        }
        return new RagOptions(enabled, Collections.unmodifiableSet(copy));
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Erlaubte Quellen; leer heißt alle. */
    public Set<SourceAddress> sources() {
        return sources;
    }

    @Override
    public String toString() {
        return enabled ? "RagOptions{enabled, sources=" + sources + "}" : "RagOptions{disabled}";
    }
}
