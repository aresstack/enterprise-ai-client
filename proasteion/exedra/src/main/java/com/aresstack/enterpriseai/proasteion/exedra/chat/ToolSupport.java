package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentStore;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.astu.acropolis.tool.ChatCompletionsToolLoop;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolActivityListener;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.proasteion.application.browser.WebExplorationProgress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.IntSupplier;
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
    private final java.util.function.BooleanSupplier alwaysOn;
    private final Executor loopExecutor;
    private final IntSupplier maxRounds;
    private final ToolActivityListener diagnostics;
    private final Function<String, List<AiTool>> chatTools;
    private Function<String, List<AiTool>> browserTools = chatId -> Collections.<AiTool>emptyList();
    private java.util.function.BooleanSupplier browserReady = () -> false;
    private java.util.function.Consumer<String> browserTurnEnd = chatId -> { };
    private BrowserExploration browserExploration = BrowserExploration.NONE;

    /** Where a turn's web exploration is recorded (#141); the display only reads it. */
    public interface BrowserExploration {

        BrowserExploration NONE = new BrowserExploration() {
            @Override
            public WebExplorationProgress begin(String chatId, java.util.function.Consumer<WebExplorationProgress> l) {
                return WebExplorationProgress.none();
            }

            @Override
            public WebExplorationProgress end(String chatId) {
                return WebExplorationProgress.none();
            }
        };

        /** Starts a turn's recording; {@code listener} gets every change on the tool's thread. */
        WebExplorationProgress begin(String chatId, java.util.function.Consumer<WebExplorationProgress> listener);

        /** Ends the turn's recording and returns the final state. */
        WebExplorationProgress end(String chatId);
    }

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
        this.alwaysOn = () -> alwaysOn;
        this.loopExecutor = command -> {
            Thread thread = new Thread(command, "tool-loop");
            thread.setDaemon(true);
            thread.start();
        };
        this.maxRounds = () -> ChatCompletionsToolLoop.DEFAULT_MAX_ROUNDS;
        this.diagnostics = null;
        this.chatTools = chatId -> Collections.<AiTool>emptyList();
    }

    private ToolSupport(ToolSupport base, IntSupplier maxRounds, ToolActivityListener diagnostics) {
        this(base, maxRounds, diagnostics, base.chatTools, base.alwaysOn);
    }

    private ToolSupport(ToolSupport base, IntSupplier maxRounds, ToolActivityListener diagnostics,
                        Function<String, List<AiTool>> chatTools, java.util.function.BooleanSupplier alwaysOn) {
        this.chat = base.chat;
        this.store = base.store;
        this.extractor = base.extractor;
        this.knowledgeTools = base.knowledgeTools;
        this.enabled = base.enabled;
        this.alwaysOn = alwaysOn;
        this.loopExecutor = base.loopExecutor;
        this.maxRounds = maxRounds;
        this.diagnostics = diagnostics;
        this.chatTools = chatTools;
        this.browserTools = base.browserTools;
        this.browserReady = base.browserReady;
        this.browserTurnEnd = base.browserTurnEnd;
        this.browserExploration = base.browserExploration;
    }

    /**
     * Copy that offers, on every tool turn, the tools bound to the turn's chat (e.g. memories, #144). The chat id comes
     * from the turn, never from the model.
     */
    public ToolSupport withChatTools(Function<String, List<AiTool>> chatTools) {
        if (chatTools == null) {
            throw new IllegalArgumentException("chatTools must not be null");
        }
        return new ToolSupport(this, maxRounds, diagnostics, chatTools, alwaysOn);
    }

    /**
     * Copy that knows the chat's browser tools (#141). They are offered only in a turn of a chat whose browser switch
     * is on, and such a turn takes the tool path while {@code ready} holds (Firefox found, tools on in "Tools").
     */
    public ToolSupport withBrowserTools(Function<String, List<AiTool>> browserTools,
                                        java.util.function.BooleanSupplier ready) {
        if (browserTools == null || ready == null) {
            throw new IllegalArgumentException("browserTools and ready must not be null");
        }
        ToolSupport copy = new ToolSupport(this, maxRounds, diagnostics, chatTools, alwaysOn);
        copy.browserTools = browserTools;
        copy.browserReady = ready;
        return copy;
    }

    /**
     * Copy that ends the chat's browser after every turn that offered the browser tools: like askai, a finished
     * browsing turn does not leave Firefox open. The profile (cookies, logins) stays.
     */
    public ToolSupport withBrowserTurnEnd(java.util.function.Consumer<String> browserTurnEnd) {
        if (browserTurnEnd == null) {
            throw new IllegalArgumentException("browserTurnEnd must not be null");
        }
        ToolSupport copy = new ToolSupport(this, maxRounds, diagnostics, chatTools, alwaysOn);
        copy.browserTurnEnd = browserTurnEnd;
        return copy;
    }

    /** Copy that records each browser turn's exploration in {@code exploration} (#141). */
    public ToolSupport withBrowserExploration(BrowserExploration exploration) {
        if (exploration == null) {
            throw new IllegalArgumentException("exploration must not be null");
        }
        ToolSupport copy = new ToolSupport(this, maxRounds, diagnostics, chatTools, alwaysOn);
        copy.browserExploration = exploration;
        return copy;
    }

    /** The recording of the browser turns. */
    public BrowserExploration browserExploration() {
        return browserExploration;
    }

    /** Called off the UI thread when a turn with browser tools of chat {@code chatId} ended in any way. */
    public void browserTurnEnded(String chatId) {
        browserTurnEnd.accept(chatId);
    }

    /** Whether the browser can be used now (global switches); per chat the composer switch decides. */
    public boolean browserReady() {
        return browserReady.getAsBoolean();
    }

    /** The browser tools bound to the chat {@code chatId}, empty without a browser. */
    public List<AiTool> browserTools(String chatId) {
        List<AiTool> tools = browserTools.apply(chatId);
        return tools == null ? Collections.<AiTool>emptyList() : tools;
    }

    /** The active tools bound to the chat {@code chatId}, empty without any. */
    public List<AiTool> chatTools(String chatId) {
        List<AiTool> tools = chatTools.apply(chatId);
        return tools == null ? Collections.<AiTool>emptyList() : tools;
    }

    /**
     * Kopie, die die Höchstzahl der Werkzeugrunden ({@code chat.tools.maxRounds}) je Anfrage aus {@code maxRounds}
     * liest, damit ein gespeicherter Wert ohne Neustart gilt.
     */
    public ToolSupport withMaxRounds(IntSupplier maxRounds) {
        if (maxRounds == null) {
            throw new IllegalArgumentException("maxRounds must not be null");
        }
        return new ToolSupport(this, maxRounds, diagnostics);
    }

    /** Copy that also reports every tool call of the chat to {@code diagnostics} (diagnostic log). */
    public ToolSupport withDiagnostics(ToolActivityListener diagnostics) {
        return new ToolSupport(this, maxRounds, diagnostics);
    }

    /** Where tool calls are reported for diagnosis, or {@code null}. */
    public ToolActivityListener diagnostics() {
        return diagnostics;
    }

    private static Supplier<List<AiTool>> fixed(List<AiTool> knowledgeTools) {
        List<AiTool> tools = new ArrayList<AiTool>();
        if (knowledgeTools != null) {
            for (AiTool tool : knowledgeTools) {
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
        for (AiTool tool : knowledgeTools()) {
            if (tool.name().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    /** {@code chat.tools.enabled}: jede Frage über die Werkzeug-Schleife, nicht nur mit Anhängen oder Seiten. */
    public boolean alwaysOn() {
        return alwaysOn.getAsBoolean();
    }

    public Executor loopExecutor() {
        return loopExecutor;
    }

    /** Werkzeugrunden je Anfrage von jetzt, mindestens 1; danach antwortet das Modell ohne Werkzeuge. */
    public int maxRounds() {
        return Math.max(1, maxRounds.getAsInt());
    }
}
