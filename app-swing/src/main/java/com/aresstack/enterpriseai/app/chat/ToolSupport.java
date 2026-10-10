package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.application.attachment.AttachmentStore;
import com.aresstack.enterpriseai.application.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.chat.api.ResponsesPort;

import java.util.concurrent.Executor;

/**
 * Was die {@link RagChatBinding} für Tool-Calling und Dateianhänge braucht: den {@code /responses}-Port, die
 * Ablage und die Textextraktion der Anhänge, den Schalter {@code chat.tools.enabled} und einen Executor für die
 * Werkzeug-Schleife (ein Daemon-Thread je Anfrage).
 */
public final class ToolSupport {

    private final ResponsesPort responses;
    private final AttachmentStore store;
    private final AttachmentTextExtractor extractor;
    private final boolean alwaysOn;
    private final Executor loopExecutor;

    public ToolSupport(ResponsesPort responses, AttachmentStore store, AttachmentTextExtractor extractor,
                       boolean alwaysOn) {
        if (responses == null || store == null || extractor == null) {
            throw new IllegalArgumentException("responses, store and extractor must not be null");
        }
        this.responses = responses;
        this.store = store;
        this.extractor = extractor;
        this.alwaysOn = alwaysOn;
        this.loopExecutor = command -> {
            Thread thread = new Thread(command, "tool-loop");
            thread.setDaemon(true);
            thread.start();
        };
    }

    public ResponsesPort responses() {
        return responses;
    }

    public AttachmentStore store() {
        return store;
    }

    public AttachmentTextExtractor extractor() {
        return extractor;
    }

    /** {@code chat.tools.enabled}: jede Frage über {@code /responses}, nicht nur mit Anhängen. */
    public boolean alwaysOn() {
        return alwaysOn;
    }

    public Executor loopExecutor() {
        return loopExecutor;
    }
}
