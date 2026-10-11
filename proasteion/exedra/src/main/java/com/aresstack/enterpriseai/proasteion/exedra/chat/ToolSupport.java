package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentStore;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;

import java.util.concurrent.Executor;

/**
 * Was die {@link RagChatBinding} für Tool-Calling, Dateianhänge und Seiten-Bookmarks braucht: den gerouteten
 * Chat-Port ({@code /chat/completions} mit {@code tools}), die Ablage und die Textextraktion der Anhänge, das
 * Werkzeug {@code knowledge_read} für Seiten des Chats, den Schalter {@code chat.tools.enabled} und einen Executor
 * für die Werkzeug-Schleife (ein Daemon-Thread je Anfrage).
 */
public final class ToolSupport {

    private final ChatCompletionPort chat;
    private final AttachmentStore store;
    private final AttachmentTextExtractor extractor;
    private final AiTool knowledgeRead;
    private final boolean alwaysOn;
    private final Executor loopExecutor;

    /** @param knowledgeRead liest Seiten-Bookmarks des Chats; {@code null}: Seiten bleiben ohne Werkzeug */
    public ToolSupport(ChatCompletionPort chat, AttachmentStore store, AttachmentTextExtractor extractor,
                       AiTool knowledgeRead, boolean alwaysOn) {
        if (chat == null || store == null || extractor == null) {
            throw new IllegalArgumentException("chat, store and extractor must not be null");
        }
        this.chat = chat;
        this.store = store;
        this.extractor = extractor;
        this.knowledgeRead = knowledgeRead;
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

    /** {@code knowledge_read} oder {@code null}. */
    public AiTool knowledgeRead() {
        return knowledgeRead;
    }

    /** {@code chat.tools.enabled}: jede Frage über die Werkzeug-Schleife, nicht nur mit Anhängen oder Seiten. */
    public boolean alwaysOn() {
        return alwaysOn;
    }

    public Executor loopExecutor() {
        return loopExecutor;
    }
}
