package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.application.knowledge.KnowledgeDocumentNotIndexedException;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolHandler;
import com.aresstack.enterpriseai.mcp.api.McpToolParameter;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

/**
 * {@code get_knowledge_document}: lädt den vollständigen, aktuellen Text einer Ressource über
 * {@link LoadKnowledgeDocumentUseCase} aus ihrer Quelle. Mit indexgeführtem Use Case sind nur indexierte Dokumente
 * lesbar; eine andere ID ergibt ein Fehlerresultat mit Hinweis auf Suche und Aktualisierung. Kopf mit Titel, IDs,
 * Quelle, Typ, Ort, Stand, Parent und Bereich; der Text wird bei Überschreitung der Antwortgröße gekürzt und
 * gekennzeichnet.
 */
final class GetKnowledgeDocumentTool implements McpToolHandler {

    static final String NAME = "get_knowledge_document";
    static final String PARAM_ID = "id";
    static final String PARAM_SOURCE_ID = "source_id";

    private static final int ID_ECHO_CHARS = 200;
    private static final int FIELD_CHARS = 200;

    private final LoadKnowledgeDocumentUseCase documents;
    private final KnowledgeToolSettings settings;

    GetKnowledgeDocumentTool(LoadKnowledgeDocumentUseCase documents, KnowledgeToolSettings settings) {
        this.documents = documents;
        this.settings = settings;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Liest ein indexiertes Dokument der Wissensbasis vollständig aus seiner Quelle: Titel, Quelle, Ort, "
                        + "Stand und den ganzen Text (bei Überlänge gekürzt und gekennzeichnet). Die Dokument-ID stammt "
                        + "aus einem Treffer von search_knowledge (Feld Id); nicht indexierte Dokumente sind nicht "
                        + "lesbar.",
                this,
                McpToolParameter.string(PARAM_ID, true, "Dokument-ID in der Form <schema>:<id>, z. B. aus search_knowledge"),
                McpToolParameter.string(PARAM_SOURCE_ID, false,
                        "Quelle, aus der geladen wird (Standard: die Quelle, die die ID kennt; konfiguriert: "
                                + knownSources() + ")"));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String rawId = call.getString(PARAM_ID);
        if (ToolText.isBlank(rawId)) {
            return McpToolResult.error("Parameter '" + PARAM_ID + "' fehlt oder ist leer.");
        }
        KnowledgeResourceId resourceId;
        try {
            resourceId = KnowledgeResourceId.of(rawId.trim());
        } catch (IllegalArgumentException invalid) {
            return McpToolResult.error("Ungültige Dokument-ID '" + ToolText.oneLine(rawId, ID_ECHO_CHARS)
                    + "': erwartet <schema>:<id>.");
        }
        KnowledgeSourceId sourceId = null;
        String rawSource = call.getString(PARAM_SOURCE_ID);
        if (!ToolText.isBlank(rawSource)) {
            try {
                sourceId = KnowledgeSourceId.of(rawSource.trim());
            } catch (IllegalArgumentException invalid) {
                return McpToolResult.error("Ungültige Quell-ID. Konfigurierte Quellen: " + knownSources() + ".");
            }
            if (!catalog().contains(sourceId)) {
                return McpToolResult.error("Unbekannte Quelle '" + sourceId.value() + "'. Konfigurierte Quellen: "
                        + knownSources() + ".");
            }
        }

        KnowledgeDocument document;
        try {
            document = sourceId == null ? documents.load(resourceId) : documents.load(resourceId, sourceId);
        } catch (KnowledgeDocumentNotIndexedException e) {
            return McpToolResult.error("Dokument '" + resourceId.value() + "' ist nicht in der Wissensbasis indexiert"
                    + (sourceId == null ? "" : " (Quelle '" + sourceId.value() + "')")
                    + "; lesbar sind nur indexierte Dokumente, etwa aus Treffern von search_knowledge. Liegt es im "
                    + "Bereich einer konfigurierten Quelle, holt refresh_knowledge_source es nach.");
        } catch (KnowledgeSourceException e) {
            return McpToolResult.error(describe(e.kind(), resourceId, sourceId));
        } catch (RuntimeException e) {
            // Meldung bewusst ohne Details: sie könnte Infrastrukturdaten enthalten.
            return McpToolResult.error("Laden des Dokuments '" + resourceId.value() + "' fehlgeschlagen.");
        }
        return McpToolResult.ok(render(resourceId, document));
    }

    private String describe(KnowledgeSourceException.Kind kind, KnowledgeResourceId id, KnowledgeSourceId source) {
        String document = "'" + id.value() + "'";
        switch (kind) {
            case NOT_FOUND:
                return source == null
                        ? "Dokument " + document + " wurde nicht gefunden."
                        : "Dokument " + document + " wurde in Quelle '" + source.value() + "' nicht gefunden.";
            case UNSUPPORTED:
                return source == null
                        ? "Keine konfigurierte Quelle kennt das Dokument " + document + " (konfiguriert: "
                                + knownSources() + ")."
                        : "Quelle '" + source.value() + "' kennt das Dokument " + document + " nicht.";
            case ACCESS_DENIED:
                return "Zugriff auf das Dokument " + document + " wurde verweigert.";
            case UNAVAILABLE:
                return "Die Quelle des Dokuments " + document + " ist derzeit nicht erreichbar.";
            case INVALID_RESPONSE:
                return "Die Quelle lieferte für das Dokument " + document + " eine ungültige Antwort.";
            default:
                return "Dokument " + document + " konnte nicht geladen werden.";
        }
    }

    private String render(KnowledgeResourceId requested, KnowledgeDocument document) {
        KnowledgeResource resource = document.resource();
        StringBuilder out = new StringBuilder();
        out.append("Titel: ").append(ToolText.oneLine(resource.title(), FIELD_CHARS)).append('\n');
        out.append("Id: ").append(resource.id().value()).append('\n');
        if (!resource.id().equals(requested)) {
            out.append("Angefragt: ").append(requested.value()).append(" (weitergeleitet)\n");
        }
        out.append("Quelle: ").append(resource.sourceId().value()).append('\n');
        out.append("Typ: ").append(ToolText.oneLine(resource.contentType(), FIELD_CHARS)).append('\n');
        if (resource.location().isPresent()) {
            out.append("Ort: ").append(resource.location().get()).append('\n');
        }
        out.append("Stand: ").append(ToolText.revision(resource.revision())).append('\n');
        if (resource.parentId().isPresent()) {
            out.append("Übergeordnet: ").append(resource.parentId().get().value()).append('\n');
        }
        if (!resource.scope().isEmpty()) {
            out.append("Bereich: ").append(ToolText.oneLine(resource.scope(), FIELD_CHARS)).append('\n');
        }
        out.append("Zeichen: ").append(document.text().length()).append('\n');
        if (document.isBlank()) {
            out.append("Text: (leer)");
        } else {
            out.append("Text:\n").append(document.text());
        }
        return ToolText.truncate(out.toString(), settings.maxResponseChars());
    }

    private KnowledgeSourceCatalog catalog() {
        return documents.catalog();
    }

    private String knownSources() {
        return catalog().isEmpty() ? "keine" : ToolText.join(catalog().ids());
    }
}
