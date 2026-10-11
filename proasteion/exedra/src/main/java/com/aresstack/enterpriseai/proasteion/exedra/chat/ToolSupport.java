package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentStore;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Was die {@link RagChatBinding} für Tool-Calling, Dateianhänge und Seiten-Bookmarks braucht: den gerouteten
 * Chat-Port ({@code /chat/completions} mit {@code tools}), die Ablage und die Textextraktion der Anhänge, das
 * Wissens-Werkzeuge ({@code knowledge_search}, {@code knowledge_read}; dieselben wie im Agent-Modus), den Schalter {@code chat.tools.enabled} und einen Executor
 * für die Werkzeug-Schleife (ein Daemon-Thread je Anfrage).
 */
public final class ToolSupport {

    private final ChatCompletionPort chat;
    private final AttachmentStore store;
    private final AttachmentTextExtractor extractor;
    private final List<AiTool> knowledgeTools;
    private final boolean alwaysOn;
    private final Executor loopExecutor;

    /**
     * @param knowledgeTools der Wissens-Werkzeugsatz des Chats (Suche in den angehakten Quellen, Lesen von Treffern
     *                       und Seiten-Bookmarks); {@code null} oder leer: Chat ohne Wissens-Werkzeuge
     */
    public ToolSupport(ChatCompletionPort chat, AttachmentStore store, AttachmentTextExtractor extractor,
                       List<AiTool> knowledgeTools, boolean alwaysOn) {
        if (chat == null || store == null || extractor == null) {
            throw new IllegalArgumentException("chat, store and extractor must not be null");
        }
        this.chat = chat;
        this.store = store;
        this.extractor = extractor;
        List<AiTool> tools = new ArrayList<AiTool>();
        if (knowledgeTools != null) {
            for (AiTool tool : knowledgeTools) {
                if (tool != null) {
                    tools.add(tool);
                }
            }
        }
        this.knowledgeTools = Collections.unmodifiableList(tools);
        this.alwaysOn = alwaysOn;
        this.loopExecutor = command -> {
            Thread thread = new Thread(command, "tool-loop");
            thread.setDaemon(true);
            thread.start();
        };
    }

    public ChatCompletionPort chat() {
        return chat;
    }

    public AttachmentStore store() {
        return store;
    }

    public AttachmentTextExtractor extractor() {
        return extractor;
    }

    /** Der Wissens-Werkzeugsatz in fester Reihenfolge, leer ohne Wissens-Werkzeuge. */
    public List<AiTool> knowledgeTools() {
        return knowledgeTools;
    }

    /** Das Wissens-Werkzeug {@code name} oder {@code null}. */
    public AiTool knowledgeTool(String name) {
        for (AiTool tool : knowledgeTools) {
            if (tool.name().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    /** {@code chat.tools.enabled}: jede Frage über die Werkzeug-Schleife, nicht nur mit Anhängen oder Seiten. */
    public boolean alwaysOn() {
        return alwaysOn;
    }

    public Executor loopExecutor() {
        return loopExecutor;
    }
}
