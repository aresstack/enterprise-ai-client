package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeBookmarkIds;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

/**
 * {@code knowledge_read(id)}: Resource-Resolver (corenth: Bookmark statt Textkopie). Die ID eines Suchtreffers
 * ({@code confluence:47110815}) oder einer Passage
 * ({@code confluence:47110815@<sha256>#p3@9f2c41ab-5e07d1c3}) oder ihr Bookmark ({@code confluence://cf.intern/confluence/page/
 * 47110815}, über {@link KnowledgeBookmarkIds}) wird zuerst aus
 * dem Index beantwortet: Referenz (id, url, Revision, Inhalts-Hash) und die aufbereiteten Passagen mit ihren IDs. Die
 * Quelle wird nur bei unbekanntem oder geändertem Stand gefragt, auf dem Thread des Agenten (an der Vorlade-Warteschlange
 * vorbei), danach wird indexiert und wieder aus dem Index geliefert. Bei einer Passage-ID kommen diese Passage und
 * ihre Nachbarn. Scheitert das Lesen eines nichtkanonischen Bookmarks oder einer Webadresse an der Adresse (nicht
 * gefunden, unbekannt), löst der Adress-Resolver sie einmal auf und das Werkzeug liest die kanonische ID (#109).
 */
final class KnowledgeReadTool implements McpToolHandler {

    static final String NAME = "knowledge_read";
    static final String PARAM_ID = "id";

    private static final String EXAMPLE = KnowledgeResourceReader.EXAMPLE;
    private static final String BOOKMARK_EXAMPLE = "id=\"confluence://cf.intern/confluence/page/47110815\"";

    private final KnowledgeResourceReader reader;
    private final KnowledgeToolSettings settings;

    KnowledgeReadTool(KnowledgeResourceReader reader, KnowledgeToolSettings settings) {
        this.reader = reader;
        this.settings = settings;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Read one document returned by knowledge_search, an attachment listed by knowledge_attachments, or a "
                        + "page bookmark the user added to the chat. Use the exact id from a search hit or attachment "
                        + "(line \"id:\") or the bookmark as given. Example: " + EXAMPLE
                        + ". Example: " + BOOKMARK_EXAMPLE + ".",
                this,
                McpToolParameter.string(PARAM_ID, true, "Document id from knowledge_search or a page bookmark. "
                        + "Example: " + EXAMPLE));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        return reader.read(call.getString(PARAM_ID), settings.maxResponseChars());
    }
}
