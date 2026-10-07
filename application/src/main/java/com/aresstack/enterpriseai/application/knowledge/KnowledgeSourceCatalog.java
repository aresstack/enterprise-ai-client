package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die konfigurierten Wissensquellen, unveränderlich und nach {@link KnowledgeSourceId} auffindbar, in
 * Konfigurationsreihenfolge. Die Composition Root befüllt ihn einmal; Use Cases ({@link LoadKnowledgeDocumentUseCase})
 * und die MCP-Wissenswerkzeuge lesen daraus. Doppelte Source-IDs werden abgelehnt.
 */
public final class KnowledgeSourceCatalog {

    private final Map<KnowledgeSourceId, KnowledgeSourceRegistration> registrations;

    public KnowledgeSourceCatalog(Collection<KnowledgeSourceRegistration> registrations) {
        Map<KnowledgeSourceId, KnowledgeSourceRegistration> byId =
                new LinkedHashMap<KnowledgeSourceId, KnowledgeSourceRegistration>();
        if (registrations != null) {
            for (KnowledgeSourceRegistration registration : registrations) {
                if (registration == null) {
                    throw new IllegalArgumentException("Registrierung darf nicht null sein");
                }
                if (byId.put(registration.sourceId(), registration) != null) {
                    throw new IllegalArgumentException("Quelle doppelt registriert: " + registration.sourceId());
                }
            }
        }
        this.registrations = Collections.unmodifiableMap(byId);
    }

    /** Katalog ohne Quellen. */
    public static KnowledgeSourceCatalog empty() {
        return new KnowledgeSourceCatalog(Collections.<KnowledgeSourceRegistration>emptyList());
    }

    public static KnowledgeSourceCatalog of(KnowledgeSourceRegistration... registrations) {
        List<KnowledgeSourceRegistration> list = new ArrayList<KnowledgeSourceRegistration>();
        if (registrations != null) {
            Collections.addAll(list, registrations);
        }
        return new KnowledgeSourceCatalog(list);
    }

    /** Die Source-IDs in Konfigurationsreihenfolge. */
    public Set<KnowledgeSourceId> ids() {
        return registrations.keySet();
    }

    public Collection<KnowledgeSourceRegistration> registrations() {
        return registrations.values();
    }

    /** Die Ports aller Quellen in Konfigurationsreihenfolge. */
    public List<KnowledgeSourcePort> ports() {
        List<KnowledgeSourcePort> ports = new ArrayList<KnowledgeSourcePort>(registrations.size());
        for (KnowledgeSourceRegistration registration : registrations.values()) {
            ports.add(registration.port());
        }
        return Collections.unmodifiableList(ports);
    }

    /** @return die Registrierung oder {@code null}, wenn die Quelle nicht konfiguriert ist */
    public KnowledgeSourceRegistration find(KnowledgeSourceId sourceId) {
        return sourceId == null ? null : registrations.get(sourceId);
    }

    public boolean contains(KnowledgeSourceId sourceId) {
        return sourceId != null && registrations.containsKey(sourceId);
    }

    public boolean isEmpty() {
        return registrations.isEmpty();
    }

    public int size() {
        return registrations.size();
    }

    @Override
    public String toString() {
        return "KnowledgeSourceCatalog" + registrations.keySet();
    }
}
