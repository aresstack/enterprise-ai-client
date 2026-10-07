package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.application.knowledge.RefreshKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Die MCP-Wissenswerkzeuge als Tool-Contributions für einen Endpoint eines {@code McpServerRegistry}:
 * <ul>
 *   <li>{@value #SEARCH_KNOWLEDGE}: hybride Suche über {@link RetrieveKnowledgeUseCase}
 *       (Parameter {@code query}, optional {@code max_results}, {@code source_ids}),</li>
 *   <li>{@value #GET_KNOWLEDGE_DOCUMENT}: vollständiges Dokument über {@link LoadKnowledgeDocumentUseCase}
 *       (Parameter {@code id}, optional {@code source_id}),</li>
 *   <li>{@value #REFRESH_KNOWLEDGE_SOURCE}: Neuindexierung einer konfigurierten Quelle über
 *       {@link RefreshKnowledgeSourceUseCase} (Parameter {@code source_id}).</li>
 * </ul>
 *
 * <p>Alle Abhängigkeiten kommen per Konstruktor; die Werkzeuge kennen die Quellen nur über die IDs im
 * {@link KnowledgeSourceCatalog} der Use Cases (Suche und Dokument über den Katalog des Dokument-Use-Cases,
 * Aktualisierung über den des Aktualisierungs-Use-Cases), nie über Port oder Scope. Die Composition Root (AP23)
 * baut die Use Cases, erzeugt eine Instanz und registriert {@link #contributions()} mit
 * {@code McpServerRegistry.updateTools} am Endpoint des Agenten; beim Abmelden des Endpoints ruft sie
 * {@link #shutdown()}, damit laufende Aktualisierungen zwischen zwei Ressourcen abbrechen.
 *
 * <p>Threadsicher: die Handler sind zustandslos bis auf die Sperre je Quelle in der Aktualisierung und den
 * Abbruchschalter; Aufrufe aus mehreren Server-Threads sind zulässig, soweit die Ports es sind.
 */
public final class KnowledgeMcpTools {

    public static final String SEARCH_KNOWLEDGE = SearchKnowledgeTool.NAME;
    public static final String GET_KNOWLEDGE_DOCUMENT = GetKnowledgeDocumentTool.NAME;
    public static final String REFRESH_KNOWLEDGE_SOURCE = RefreshKnowledgeSourceTool.NAME;

    private final KnowledgeToolSettings settings;
    private final SearchKnowledgeTool search;
    private final GetKnowledgeDocumentTool document;
    private final RefreshKnowledgeSourceTool refresh;
    private volatile boolean shutdown;

    /**
     * @param retrieval hybride Suche
     * @param documents Dokumentzugriff; sein Katalog bestimmt die bekannten Quellen von Suche und Dokumentzugriff
     * @param refresh   Neuindexierung einer konfigurierten Quelle
     * @param settings  Grenzen; {@code null} für {@link KnowledgeToolSettings#defaults()}
     */
    public KnowledgeMcpTools(RetrieveKnowledgeUseCase retrieval, LoadKnowledgeDocumentUseCase documents,
                             RefreshKnowledgeSourceUseCase refresh, KnowledgeToolSettings settings) {
        if (retrieval == null || documents == null || refresh == null) {
            throw new IllegalArgumentException("retrieval, documents und refresh sind Pflicht");
        }
        this.settings = settings == null ? KnowledgeToolSettings.defaults() : settings;
        KnowledgeSourceCatalog catalog = documents.catalog();
        this.search = new SearchKnowledgeTool(retrieval, catalog, this.settings);
        this.document = new GetKnowledgeDocumentTool(documents, this.settings);
        this.refresh = new RefreshKnowledgeSourceTool(refresh, this.settings,
                new RefreshKnowledgeSourceTool.ShutdownSignal() {
                    @Override
                    public boolean isShutdown() {
                        return shutdown;
                    }
                });
    }

    public KnowledgeToolSettings settings() {
        return settings;
    }

    /** Alle drei Werkzeuge in fester Reihenfolge: Suche, Dokument, Aktualisierung. */
    public List<McpToolContribution> contributions() {
        return Collections.unmodifiableList(Arrays.asList(searchKnowledge(), getKnowledgeDocument(),
                refreshKnowledgeSource()));
    }

    public McpToolContribution searchKnowledge() {
        return search.contribution();
    }

    public McpToolContribution getKnowledgeDocument() {
        return document.contribution();
    }

    public McpToolContribution refreshKnowledgeSource() {
        return refresh.contribution();
    }

    /**
     * Beendet die Werkzeuge: Eine laufende Aktualisierung bricht nach der aktuellen Ressource ab, neue
     * Aktualisierungen werden abgewiesen. Suche und Dokumentzugriff sind zustandslos und bleiben aufrufbar, bis die
     * Composition Root den Endpoint abmeldet. Idempotent.
     */
    public void shutdown() {
        shutdown = true;
    }

    public boolean isShutdown() {
        return shutdown;
    }
}
