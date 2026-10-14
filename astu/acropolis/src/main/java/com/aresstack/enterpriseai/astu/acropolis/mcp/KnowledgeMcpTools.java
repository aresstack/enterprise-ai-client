package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.BrowseKnowledgeHierarchyUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ListKnowledgeAttachmentsUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.PageNavigationUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeAddressUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Die MCP-Wissenswerkzeuge für einen Endpoint eines {@code McpServerRegistry} – bewusst wenige, klein und mit
 * Beispielen (askai arch, {@code ResearchConceptTools}):
 * <ul>
 *   <li>{@value #KNOWLEDGE_SEARCH}: Quellen live durchsuchen (die Treffer lädt der Vorlader im Hintergrund
 *       und indexiert sie), dazu hybride Suche im Index (Parameter {@code query}, optional {@code limit}),</li>
 *   <li>{@value #KNOWLEDGE_TREE}: die Seitenhierarchie um einen Bereich oder eine Seite (Eltern bis zum Bereich,
 *       direkte Kinder mit Cursor), ohne Ziel die navigierbaren Bereiche; nie Seitentext (Issue #116),</li>
 *   <li>{@value #KNOWLEDGE_READ}: ein Dokument aus einem Treffer lesen (Cache, sonst sofort, auch nicht indexierte
 *       Treffer); es wird bei Bedarf indexiert
 *       (Parameter {@code id}); auch Dateianhänge (PDF, Office, Bilder), notfalls nach Einmalcode,</li>
 *   <li>{@value #KNOWLEDGE_ATTACHMENTS}: die Dateianhänge einer Seite auflisten, ohne sie zu laden (Parameter
 *       {@code id}),</li>
 *   <li>{@value #KNOWLEDGE_RESOLVE_URL}: eine genannte Adresse als Ressource der Quellen auflösen, alle Kandidaten
 *       ohne Auswahl (Parameter {@code url}, optional {@code source}).</li>
 * </ul>
 * Kein Werkzeug zum Neuindexieren ganzer Quellen: Vollindexierung ist eine Funktion der Oberfläche, kein Werkzeug
 * für das Modell. Die verfügbaren Quellen sind die des Katalogs von {@code documents}.
 */
public final class KnowledgeMcpTools {

    public static final String KNOWLEDGE_SEARCH = KnowledgeSearchTool.NAME;
    public static final String KNOWLEDGE_TREE = KnowledgeTreeTool.NAME;
    public static final String KNOWLEDGE_READ = KnowledgeReadTool.NAME;
    public static final String KNOWLEDGE_ATTACHMENTS = KnowledgeAttachmentsTool.NAME;
    public static final String KNOWLEDGE_RESOLVE_URL = KnowledgeResolveUrlTool.NAME;

    private final KnowledgeToolSettings settings;
    private final KnowledgeSearchTool search;
    private final KnowledgeTreeTool tree;
    private final KnowledgeReadTool read;
    private final KnowledgeAttachmentsTool attachments;
    private final KnowledgeResolveUrlTool resolveUrl;
    private volatile boolean shutdown;

    /**
     * @param retrieval hybride Suche im Index
     * @param documents Dokumentzugriff; sein Katalog bestimmt die Quellen beider Werkzeuge
     * @param live      Live-Suche mit Vorlader (Cache, Priority-Lesen, Indexierung)
     * @param settings  Grenzen der Antworten; {@code null} für {@link KnowledgeToolSettings#defaults()}
     */
    public KnowledgeMcpTools(RetrieveKnowledgeUseCase retrieval, LoadKnowledgeDocumentUseCase documents,
                             SearchDrivenIndexingUseCase live, KnowledgeToolSettings settings) {
        this(retrieval, documents, live, id -> true, settings);
    }

    /** Wie oben; {@code enabled} sagt je Aufruf, welche Quellen angehakt sind (nur die durchsucht knowledge_search). */
    public KnowledgeMcpTools(RetrieveKnowledgeUseCase retrieval, LoadKnowledgeDocumentUseCase documents,
                             SearchDrivenIndexingUseCase live, Predicate<SourceAddress> enabled,
                             KnowledgeToolSettings settings) {
        this(retrieval, documents, live, enabled, settings, null);
    }

    /**
     * Wie oben; {@code treeChildLimit} liefert je Aufruf die direkten Kinder eines {@code knowledge_tree} ohne
     * {@code limit} (Einstellung ohne Neustart), {@code null} für {@link KnowledgeToolSettings#treeChildLimit()}.
     */
    public KnowledgeMcpTools(RetrieveKnowledgeUseCase retrieval, LoadKnowledgeDocumentUseCase documents,
                             SearchDrivenIndexingUseCase live, Predicate<SourceAddress> enabled,
                             KnowledgeToolSettings settings, IntSupplier treeChildLimit) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled ist Pflicht");
        }
        if (retrieval == null || documents == null || live == null) {
            throw new IllegalArgumentException("retrieval, documents und live sind Pflicht");
        }
        this.settings = settings == null ? KnowledgeToolSettings.defaults() : settings;
        this.search = new KnowledgeSearchTool(retrieval, live, documents::catalog, enabled, this.settings);
        ResolveKnowledgeAddressUseCase addresses = new ResolveKnowledgeAddressUseCase(documents::catalog);
        this.read = new KnowledgeReadTool(documents, live, addresses, this.settings);
        this.attachments = new KnowledgeAttachmentsTool(new ListKnowledgeAttachmentsUseCase(documents::catalog),
                this.settings);
        this.resolveUrl = new KnowledgeResolveUrlTool(addresses);
        final KnowledgeToolSettings fixed = this.settings;
        this.tree = new KnowledgeTreeTool(new BrowseKnowledgeHierarchyUseCase(documents::catalog,
                new PageNavigationUseCase()), this.settings,
                treeChildLimit == null ? fixed::treeChildLimit : treeChildLimit);
    }

    public KnowledgeToolSettings settings() {
        return settings;
    }

    /** Alle Werkzeuge in fester Reihenfolge: Suche, Seitenbaum, Lesen, Anhänge, Adresse auflösen. */
    public List<McpToolContribution> contributions() {
        return Collections.unmodifiableList(Arrays.asList(knowledgeSearch(), knowledgeTree(), knowledgeRead(),
                knowledgeAttachments(), knowledgeResolveUrl()));
    }

    public McpToolContribution knowledgeTree() {
        return tree.contribution();
    }

    public McpToolContribution knowledgeResolveUrl() {
        return resolveUrl.contribution();
    }

    public McpToolContribution knowledgeAttachments() {
        return attachments.contribution();
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
