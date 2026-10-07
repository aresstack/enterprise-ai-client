package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** RAG-Schalter je Nachricht: aus, oder an mit optionaler Einschränkung auf Quellen. Unveränderlich. */
public final class RagOptions {

    private final boolean enabled;
    private final Set<KnowledgeSourceId> sources;

    private RagOptions(boolean enabled, Set<KnowledgeSourceId> sources) {
        this.enabled = enabled;
        this.sources = sources;
    }

    /** Normaler Chat ohne Retrieval. */
    public static RagOptions disabled() {
        return new RagOptions(false, Collections.<KnowledgeSourceId>emptySet());
    }

    /** Retrieval über alle Quellen. */
    public static RagOptions enabled() {
        return new RagOptions(true, Collections.<KnowledgeSourceId>emptySet());
    }

    /** Retrieval nur in den genannten Quellen; leer heißt alle. */
    public RagOptions restrictedTo(Collection<KnowledgeSourceId> sourceIds) {
        Set<KnowledgeSourceId> copy = new LinkedHashSet<KnowledgeSourceId>();
        if (sourceIds != null) {
            for (KnowledgeSourceId id : sourceIds) {
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
    public Set<KnowledgeSourceId> sources() {
        return sources;
    }

    @Override
    public String toString() {
        return enabled ? "RagOptions{enabled, sources=" + sources + "}" : "RagOptions{disabled}";
    }
}
