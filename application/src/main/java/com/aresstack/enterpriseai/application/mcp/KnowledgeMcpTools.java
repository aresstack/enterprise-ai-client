package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.application.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.application.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Die MCP-Wissenswerkzeuge für einen Endpoint eines {@code McpServerRegistry} – bewusst nur zwei, klein und mit
 * Beispielen (askai arch, {@code ResearchConceptTools}):
 * <ul>
 *   <li>{@value #KNOWLEDGE_SEARCH}: Quellen live durchsuchen, neue oder geänderte Treffer im Rahmen des Budgets
 *       indexieren, dann hybride Suche im Index (Parameter {@code query}, optional {@code limit}),</li>
 *   <li>{@value #KNOWLEDGE_READ}: ein Dokument aus einem Treffer lesen; es wird bei Bedarf indexiert
 *       (Parameter {@code id}).</li>
 * </ul>
 * Kein Werkzeug zum Neuindexieren ganzer Quellen: Vollindexierung ist eine Funktion der Oberfläche, kein Werkzeug
 * für das Modell. Die verfügbaren Quellen sind die des Katalogs von {@code documents}.
 */
public final class KnowledgeMcpTools {

    public static final String KNOWLEDGE_SEARCH = KnowledgeSearchTool.NAME;
    public static final String KNOWLEDGE_READ = KnowledgeReadTool.NAME;

    private final KnowledgeToolSettings settings;
    private final KnowledgeSearchTool search;
    private final KnowledgeReadTool read;
    private volatile boolean shutdown;

    /**
     * @param retrieval hybride Suche im Index
     * @param documents Dokumentzugriff; sein Katalog bestimmt die Quellen beider Werkzeuge
     * @param live      Live-Suche, die Treffer indexiert
     * @param settings  Grenzen und Budget; {@code null} für {@link KnowledgeToolSettings#defaults()}
     */
    public KnowledgeMcpTools(RetrieveKnowledgeUseCase retrieval, LoadKnowledgeDocumentUseCase documents,
                             SearchDrivenIndexingUseCase live, KnowledgeToolSettings settings) {
        if (retrieval == null || documents == null || live == null) {
            throw new IllegalArgumentException("retrieval, documents und live sind Pflicht");
        }
        this.settings = settings == null ? KnowledgeToolSettings.defaults() : settings;
        this.search = new KnowledgeSearchTool(retrieval, live, documents.catalog(), this.settings);
        this.read = new KnowledgeReadTool(documents, live, this.settings);
    }

    public KnowledgeToolSettings settings() {
        return settings;
    }

    /** Beide Werkzeuge in fester Reihenfolge: Suche, Lesen. */
    public List<McpToolContribution> contributions() {
        return Collections.unmodifiableList(Arrays.asList(knowledgeSearch(), knowledgeRead()));
    }

    public McpToolContribution knowledgeSearch() {
        return search.contribution();
    }

    public McpToolContribution knowledgeRead() {
        return read.contribution();
    }

    /** Merkt das Abmelden des Endpoints; die Werkzeuge sind zustandslos. Idempotent. */
    public void shutdown() {
        shutdown = true;
    }

    public boolean isShutdown() {
        return shutdown;
    }
}
