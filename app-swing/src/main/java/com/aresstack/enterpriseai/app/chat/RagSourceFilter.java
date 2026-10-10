package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.Collections;
import java.util.Set;

/**
 * Welche Quellen eine Chat-Nachricht mit RAG durchsucht; die {@link RagChatBinding} fragt je Nachricht auf dem
 * UI-Thread. Produktiv sind das die Häkchen im Drawer-Reiter „Wissensquellen“.
 */
public interface RagSourceFilter {

    /** Keine Einschränkung: alle Quellen des Index (so lange keine Quelle konfiguriert ist). */
    static RagSourceFilter unrestricted() {
        return new RagSourceFilter() {
            @Override
            public boolean isRestricted() {
                return false;
            }

            @Override
            public Set<KnowledgeSourceId> allowedSources() {
                return Collections.emptySet();
            }
        };
    }

    /** {@code true}: nur {@link #allowedSources()} durchsuchen; {@code false}: alle Quellen. */
    boolean isRestricted();

    /** Die erlaubten Quellen, wenn eingeschränkt; leer heißt dann: keine Quelle gewählt, also keine Suche. */
    Set<KnowledgeSourceId> allowedSources();
}
