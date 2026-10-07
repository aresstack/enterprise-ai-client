package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingListener;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.IndexingStage;
import com.aresstack.enterpriseai.application.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.application.knowledge.ResourceIndexingOutcome;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolHandler;
import com.aresstack.enterpriseai.mcp.api.McpToolParameter;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;

import java.util.HashSet;
import java.util.Set;

/**
 * {@code refresh_knowledge_source}: indexiert eine konfigurierte Quelle in ihrem {@code SourceScope} neu, synchron
 * im Handler über {@link IndexKnowledgeUseCase}, und fasst den {@link IndexingReport} zusammen (gefunden,
 * indexiert, leer, entfernt, Duplikate, Fehler je Stufe, einzelne Fehler bis zu einer Höchstzahl).
 *
 * <p>Je Quelle läuft höchstens eine Aktualisierung gleichzeitig; ein zweiter Aufruf wird abgewiesen. Nach
 * {@link KnowledgeMcpTools#shutdown()} bricht ein laufender Lauf zwischen zwei Ressourcen ab (bereits indexierte
 * Ressourcen bleiben), neue Läufe werden abgewiesen.
 */
final class RefreshKnowledgeSourceTool implements McpToolHandler {

    static final String NAME = "refresh_knowledge_source";
    static final String PARAM_SOURCE_ID = "source_id";

    private static final int MESSAGE_CHARS = 300;

    private final IndexKnowledgeUseCase indexing;
    private final KnowledgeSourceCatalog catalog;
    private final KnowledgeToolSettings settings;
    private final ShutdownSignal shutdown;
    private final Set<KnowledgeSourceId> running = new HashSet<KnowledgeSourceId>();

    /** Gemeinsamer Abbruchschalter der Werkzeuge (gesetzt von {@link KnowledgeMcpTools#shutdown()}). */
    interface ShutdownSignal {
        boolean isShutdown();
    }

    RefreshKnowledgeSourceTool(IndexKnowledgeUseCase indexing, KnowledgeSourceCatalog catalog,
                               KnowledgeToolSettings settings, ShutdownSignal shutdown) {
        this.indexing = indexing;
        this.catalog = catalog;
        this.settings = settings;
        this.shutdown = shutdown;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Lädt eine konfigurierte Wissensquelle neu und aktualisiert den Index (Discovery, Laden, Chunken, "
                        + "Embedden). Läuft synchron und kann dauern; die Antwort fasst zusammen, wie viele Dokumente "
                        + "indexiert, entfernt oder leer waren und welche Fehler auftraten.",
                this,
                McpToolParameter.string(PARAM_SOURCE_ID, true,
                        "ID der zu aktualisierenden Quelle (konfiguriert: " + knownSources() + ")"));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String raw = call.getString(PARAM_SOURCE_ID);
        if (ToolText.isBlank(raw)) {
            return McpToolResult.error("Parameter '" + PARAM_SOURCE_ID + "' fehlt oder ist leer.");
        }
        KnowledgeSourceId sourceId;
        try {
            sourceId = KnowledgeSourceId.of(raw.trim());
        } catch (IllegalArgumentException invalid) {
            return McpToolResult.error("Ungültige Quell-ID. Konfigurierte Quellen: " + knownSources() + ".");
        }
        KnowledgeSourceRegistration registration = catalog.find(sourceId);
        if (registration == null) {
            return McpToolResult.error("Unbekannte Quelle '" + sourceId.value() + "'. Konfigurierte Quellen: "
                    + knownSources() + ".");
        }
        if (shutdown.isShutdown()) {
            return McpToolResult.error("Die Wissenswerkzeuge wurden beendet; keine Aktualisierung mehr möglich.");
        }
        if (!acquire(sourceId)) {
            return McpToolResult.error("Die Quelle '" + sourceId.value() + "' wird bereits aktualisiert.");
        }
        IndexingReport report;
        try {
            report = indexing.indexSource(registration.port(), registration.scope(), new IndexingListener() {
                @Override
                public boolean isCancelled() {
                    return shutdown.isShutdown();
                }
            });
        } catch (RuntimeException e) {
            // Meldung bewusst ohne Details: sie könnte Infrastrukturdaten enthalten.
            return McpToolResult.error("Aktualisierung der Quelle '" + sourceId.value() + "' fehlgeschlagen.");
        } finally {
            release(sourceId);
        }
        return McpToolResult.ok(render(report));
    }

    private synchronized boolean acquire(KnowledgeSourceId sourceId) {
        return running.add(sourceId);
    }

    private synchronized void release(KnowledgeSourceId sourceId) {
        running.remove(sourceId);
    }

    private String render(IndexingReport report) {
        int failed = report.count(IndexingStatus.FAILED);
        StringBuilder out = new StringBuilder();
        out.append("Quelle: ").append(report.sourceId().value()).append('\n');
        out.append("Status: ").append(status(report, failed)).append('\n');
        out.append("Gefunden: ").append(report.discovered()).append('\n');
        out.append("Indexiert: ").append(report.count(IndexingStatus.INDEXED)).append(" (Chunks: ")
                .append(report.chunkCount()).append(")\n");
        out.append("Leer: ").append(report.count(IndexingStatus.EMPTY)).append('\n');
        out.append("Entfernt: ").append(report.count(IndexingStatus.REMOVED)).append('\n');
        out.append("Duplikate: ").append(report.count(IndexingStatus.DUPLICATE)).append('\n');
        out.append("Fehler: ").append(failed);
        if (failed > 0) {
            out.append(" (Laden: ").append(countFailed(report, IndexingStage.LOADING))
                    .append(", Embedding: ").append(countFailed(report, IndexingStage.EMBEDDING))
                    .append(", Index: ").append(countFailed(report, IndexingStage.INDEXING)).append(')');
        }
        out.append('\n');
        if (report.discoveryFailed()) {
            out.append("Discovery: ").append(ToolText.oneLine(report.discoveryFailure(), MESSAGE_CHARS)).append('\n');
        }
        int listed = 0;
        for (ResourceIndexingOutcome outcome : report.outcomes()) {
            if (outcome.status() != IndexingStatus.FAILED) {
                continue;
            }
            if (listed >= settings.maxFailuresListed()) {
                out.append("  … ").append(failed - listed).append(" weitere Fehler\n");
                break;
            }
            out.append("  - ").append(outcome.resourceId().value()).append(" [").append(outcome.stage()).append("]: ")
                    .append(ToolText.oneLine(outcome.message(), MESSAGE_CHARS)).append('\n');
            listed++;
        }
        return ToolText.truncate(out.toString().trim(), settings.maxResponseChars());
    }

    private static String status(IndexingReport report, int failed) {
        if (report.discoveryFailed()) {
            return "Discovery fehlgeschlagen, nichts verarbeitet";
        }
        if (report.isCancelled()) {
            return "abgebrochen (Werkzeuge beendet), bereits indexierte Dokumente bleiben";
        }
        return failed > 0 ? "abgeschlossen mit Fehlern" : "vollständig";
    }

    private static int countFailed(IndexingReport report, IndexingStage stage) {
        int n = 0;
        for (ResourceIndexingOutcome outcome : report.outcomes()) {
            if (outcome.status() == IndexingStatus.FAILED && outcome.stage() == stage) {
                n++;
            }
        }
        return n;
    }

    private String knownSources() {
        return catalog.isEmpty() ? "keine" : ToolText.join(catalog.ids());
    }
}
