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
 *   <li>{@value #KNOWLEDGE_READ_MANY}: mehrere bekannte Dokumente in einem Aufruf begrenzt parallel lesen, dieselbe
 *       Lese-Logik wie {@value #KNOWLEDGE_READ} (Parameter {@code ids}, ein Array),</li>
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
    /** The local index alone, for the Research Agent's local stage; not part of {@link #contributions()}. */
    public static final String KNOWLEDGE_INDEX_SEARCH = KnowledgeIndexSearchTool.NAME;
    public static final String KNOWLEDGE_TREE = KnowledgeTreeTool.NAME;
    public static final String KNOWLEDGE_READ = KnowledgeReadTool.NAME;
    public static final String KNOWLEDGE_READ_MANY = KnowledgeReadManyTool.NAME;
    public static final String KNOWLEDGE_ATTACHMENTS = KnowledgeAttachmentsTool.NAME;
    public static final String KNOWLEDGE_RESOLVE_URL = KnowledgeResolveUrlTool.NAME;
    /**
     * Name of the research boundary of the normal chat; the tool itself lives in the composition root (it runs the
     * Research Agent over these tools) and delivers the read resources as context like {@value #KNOWLEDGE_READ}.
     */
    public static final String KNOWLEDGE_RESEARCH =
            com.aresstack.enterpriseai.astu.acropolis.rag.KnowledgeAcquisition.RESEARCH_TOOL;

    private final KnowledgeToolSettings settings;
    private final KnowledgeSearchTool search;
    private final KnowledgeIndexSearchTool indexSearch;
    private final KnowledgeTreeTool tree;
    private final KnowledgeReadTool read;
    private final KnowledgeReadManyTool readMany;
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
        this.indexSearch = new KnowledgeIndexSearchTool(retrieval, live, documents::catalog, enabled, this.settings);
        ResolveKnowledgeAddressUseCase addresses = new ResolveKnowledgeAddressUseCase(documents::catalog);
        KnowledgeResourceReader reader = new KnowledgeResourceReader(documents, live, addresses);
        this.read = new KnowledgeReadTool(reader, this.settings);
        this.readMany = new KnowledgeReadManyTool(reader, this.settings);
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

    /** Alle Werkzeuge in fester Reihenfolge: Suche, Seitenbaum, Lesen, mehrere Lesen, Anhänge, Adresse auflösen. */
    public List<McpToolContribution> contributions() {
        return Collections.unmodifiableList(Arrays.asList(knowledgeSearch(), knowledgeTree(), knowledgeRead(),
                knowledgeReadMany(), knowledgeAttachments(), knowledgeResolveUrl()));
    }

    public McpToolContribution knowledgeReadMany() {
        return readMany.contribution();
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

    /**
     * {@value #KNOWLEDGE_INDEX_SEARCH}: the hybrid search of the local index without live sources. Offered to the
     * Research Agent only, so it is not among {@link #contributions()}.
     */
    public McpToolContribution knowledgeIndexSearch() {
        return indexSearch.contribution();
    }

    public McpToolContribution knowledgeSearch() {
        return search.contribution();
    }

    public McpToolContribution knowledgeRead() {
        return read.contribution();
    }

    /** Merkt das Abmelden des Endpoints und beendet den Lese-Pool von knowledge_read_many. Idempotent. */
    public void shutdown() {
        shutdown = true;
        readMany.shutdown();
    }

    public boolean isShutdown() {
        return shutdown;
    }
}
