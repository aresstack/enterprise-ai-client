package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentStore;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Was die {@link RagChatBinding} für Tool-Calling, Dateianhänge und Seiten-Bookmarks braucht: den gerouteten
 * Chat-Port ({@code /chat/completions} mit {@code tools}), die Ablage und die Textextraktion der Anhänge, das
 * Wissens-Werkzeuge ({@code knowledge_search}, {@code knowledge_read}; dieselben wie im Agent-Modus), den Schalter {@code chat.tools.enabled} und einen Executor
 * für die Werkzeug-Schleife (ein Daemon-Thread je Anfrage).
 *
 * <p>Welche Werkzeuge aktiv sind, entscheidet die zentrale Werkzeug-Registry (Reiter „Tools“): der Wissens-Satz wird
 * je Anfrage neu geholt, {@link #isEnabled} prüft die übrigen (Anhang-Werkzeuge) gegen denselben Zustand.
 */
public final class ToolSupport {

    private final ChatCompletionPort chat;
    private final AttachmentStore store;
    private final AttachmentTextExtractor extractor;
    private final Supplier<List<AiTool>> knowledgeTools;
    private final Predicate<String> enabled;
    private final boolean alwaysOn;
    private final Executor loopExecutor;

    /**
     * @param knowledgeTools der Wissens-Werkzeugsatz des Chats (Suche in den angehakten Quellen, Lesen von Treffern
     *                       und Seiten-Bookmarks); {@code null} oder leer: Chat ohne Wissens-Werkzeuge
     */
    public ToolSupport(ChatCompletionPort chat, AttachmentStore store, AttachmentTextExtractor extractor,
                       List<AiTool> knowledgeTools, boolean alwaysOn) {
        this(chat, store, extractor, fixed(knowledgeTools), name -> true, alwaysOn);
    }

    /**
     * @param knowledgeTools liefert je Anfrage die aktiven Wissens-Werkzeuge des Chats
     * @param enabled        sagt, ob ein Werkzeug aktiv ist (für die Anhang-Werkzeuge)
     */
    public ToolSupport(ChatCompletionPort chat, AttachmentStore store, AttachmentTextExtractor extractor,
                       Supplier<List<AiTool>> knowledgeTools, Predicate<String> enabled, boolean alwaysOn) {
        if (chat == null || store == null || extractor == null) {
            throw new IllegalArgumentException("chat, store and extractor must not be null");
        }
        this.chat = chat;
        this.store = store;
        this.extractor = extractor;
        this.knowledgeTools = knowledgeTools == null ? fixed(null) : knowledgeTools;
        this.enabled = enabled == null ? name -> true : enabled;
        this.alwaysOn = alwaysOn;
        this.loopExecutor = command -> {
            Thread thread = new Thread(command, "tool-loop");
            thread.setDaemon(true);
            thread.start();
        };
    }

    private static Supplier<List<AiTool>> fixed(List<AiTool> knowledgeTools) {
        List<AiTool> tools = new ArrayList<AiTool>();
        if (knowledgeTools != null) {
            for (AiTool tool : knowledgeTools()) {
                if (tool != null) {
                    tools.add(tool);
                }
            }
        }
        final List<AiTool> copy = Collections.unmodifiableList(tools);
        return () -> copy;
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

    /** Der aktive Wissens-Werkzeugsatz von jetzt in fester Reihenfolge, leer ohne (aktive) Wissens-Werkzeuge. */
    public List<AiTool> knowledgeTools() {
        List<AiTool> tools = knowledgeTools.get();
        return tools == null ? Collections.<AiTool>emptyList() : tools;
    }

    /** Ist das Werkzeug aktiv (Reiter „Tools“)? */
    public boolean isEnabled(String name) {
        return enabled.test(name);
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
