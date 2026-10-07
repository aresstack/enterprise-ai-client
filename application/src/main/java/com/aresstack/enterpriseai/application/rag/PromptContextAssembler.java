package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Baut aus fusionierten Treffern den Kontextblock für den Prompt. Treffer werden in Fusionsreihenfolge
 * aufgenommen, bis {@link ContextSettings#maxSources()} erreicht ist oder der nächste Treffer das Token-Budget
 * des gesamten Blocks überschreiten würde; dann wird abgebrochen, damit die Reihenfolge der Relevanz erhalten
 * bleibt. Deterministisch und zustandslos.
 *
 * <p>Format (angelehnt an MainframeMate {@code RagContextBuilder}, hier mit nummerierten, zitierbaren Quellen):
 * <pre>
 * &lt;Hinweis&gt;
 *
 * --- KONTEXT ---
 * [1] Titel – Abschnitt &gt; Unterabschnitt
 * Quelle: wiki | Ort: https://... | Stand: 42, 2026-10-01T08:00:00Z
 * Text des Chunks
 *
 * [2] ...
 * --- ENDE KONTEXT ---
 * </pre>
 * Der Block enthält nur Titel, Überschriften, Quelle, Ort, Revision und Chunk-Text; Ressourcen-Metadaten gehen
 * nicht in den Prompt. Der Ort ist laut {@link KnowledgeResource} frei von Zugangsdaten.
 */
public final class PromptContextAssembler {

    static final String BEGIN = "--- KONTEXT ---";
    static final String END = "--- ENDE KONTEXT ---";

    private final ContextSettings settings;

    public PromptContextAssembler(ContextSettings settings) {
        this.settings = settings == null ? ContextSettings.defaults() : settings;
    }

    public ContextSettings settings() {
        return settings;
    }

    /**
     * @param hits Treffer in Fusionsreihenfolge, z. B. {@link RetrievalResult#hits()}
     * @return der Block mit den aufgenommenen Quellen; leer, wenn keine Quelle ins Budget passt
     */
    public PromptContext assemble(List<RetrievedChunk> hits) {
        if (hits == null || hits.isEmpty()) {
            return PromptContext.empty(0);
        }
        String head = settings.instruction().isEmpty() ? BEGIN : settings.instruction() + "\n\n" + BEGIN;
        StringBuilder entries = new StringBuilder();
        List<RagSource> sources = new ArrayList<RagSource>();
        String accepted = null;
        int acceptedTokens = 0;
        for (RetrievedChunk hit : hits) {
            if (sources.size() >= settings.maxSources()) {
                break;
            }
            int number = sources.size() + 1;
            String entry = entry(number, hit);
            String candidate = head + "\n" + entries + entry + "\n" + END;
            int tokens = settings.tokenCounter().count(candidate);
            if (tokens > settings.maxContextTokens()) {
                break;
            }
            entries.append(entry).append('\n');
            sources.add(new RagSource(number, hit));
            accepted = candidate;
            acceptedTokens = tokens;
        }
        if (accepted == null) {
            return PromptContext.empty(hits.size());
        }
        return new PromptContext(accepted, sources, acceptedTokens, hits.size() - sources.size());
    }

    private static String entry(int number, RetrievedChunk hit) {
        KnowledgeResource resource = hit.resource();
        KnowledgeChunk chunk = hit.chunk();
        StringBuilder b = new StringBuilder();
        b.append('[').append(number).append("] ").append(oneLine(title(resource)));
        String headings = oneLine(joinHeadings(chunk.headingPath()));
        if (!headings.isEmpty()) {
            b.append(" – ").append(headings);
        }
        b.append('\n');
        b.append("Quelle: ").append(resource.sourceId().value());
        if (resource.location().isPresent()) {
            URI location = resource.location().get();
            b.append(" | Ort: ").append(location.toASCIIString());
        }
        String revision = revision(resource.revision());
        if (!revision.isEmpty()) {
            b.append(" | Stand: ").append(revision);
        }
        b.append('\n');
        b.append(neutralizeMarkers(chunk.text().trim())).append('\n');
        return b.toString();
    }

    private static String title(KnowledgeResource resource) {
        String title = resource.title();
        return title == null || title.trim().isEmpty() ? resource.id().value() : title.trim();
    }

    private static String joinHeadings(List<String> headings) {
        StringBuilder b = new StringBuilder();
        for (String heading : headings) {
            if (heading == null || heading.trim().isEmpty()) {
                continue;
            }
            if (b.length() > 0) {
                b.append(" > ");
            }
            b.append(heading.trim());
        }
        return b.toString();
    }

    private static String revision(KnowledgeRevision revision) {
        if (revision == null || !revision.isKnown()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        if (revision.version() != null && !revision.version().isEmpty()) {
            b.append(revision.version());
        }
        if (revision.modifiedAt().isPresent()) {
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(revision.modifiedAt().get());
        }
        return oneLine(b.toString());
    }

    private static String oneLine(String text) {
        return text.replace('\r', ' ').replace('\n', ' ').trim();
    }

    /** Ein Chunk darf den Rahmen nicht vorzeitig schließen oder einen neuen öffnen. */
    private static String neutralizeMarkers(String text) {
        return text.replace(END, "-- ENDE KONTEXT --").replace(BEGIN, "-- KONTEXT --");
    }
}
