package com.aresstack.enterpriseai.app.knowledge;

import com.aresstack.enterpriseai.app.chat.RagSourceFilter;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Die Häkchen des Drawer-Reiters „Wissensquellen“ zur Laufzeit: welche angebundenen Quellen aktiv sind. Die
 * Start-Indexierung überspringt abgewählte Quellen, und RAG durchsucht nur aktive ({@link RagSourceFilter}).
 * Solange keine Quelle angebunden ist, schränkt der Filter nicht ein. Threadsicher.
 */
public final class KnowledgeSourceSelection implements RagSourceFilter {

    private final Map<KnowledgeSourceId, Boolean> sources = new LinkedHashMap<KnowledgeSourceId, Boolean>();

    /** Bindet eine Quelle an (oder setzt ihr Häkchen neu). */
    public synchronized void register(KnowledgeSourceId id, boolean enabled) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        sources.put(id, enabled);
    }

    /** Die Quelle ist nicht mehr konfiguriert: RAG durchsucht sie nicht mehr. */
    public synchronized void unregister(KnowledgeSourceId id) {
        sources.remove(id);
    }

    /** @return {@code false}, wenn die Quelle nicht angebunden ist */
    public synchronized boolean setEnabled(KnowledgeSourceId id, boolean enabled) {
        if (!sources.containsKey(id)) {
            return false;
        }
        sources.put(id, enabled);
        return true;
    }

    public synchronized boolean isRegistered(KnowledgeSourceId id) {
        return sources.containsKey(id);
    }

    public synchronized boolean isEnabled(KnowledgeSourceId id) {
        return Boolean.TRUE.equals(sources.get(id));
    }

    @Override
    public synchronized boolean isRestricted() {
        return !sources.isEmpty();
    }

    @Override
    public synchronized Set<KnowledgeSourceId> allowedSources() {
        Set<KnowledgeSourceId> enabled = new LinkedHashSet<KnowledgeSourceId>();
        for (Map.Entry<KnowledgeSourceId, Boolean> entry : sources.entrySet()) {
            if (entry.getValue()) {
                enabled.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(enabled);
    }

    @Override
    public synchronized String toString() {
        return "KnowledgeSourceSelection" + sources;
    }
}
