package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.application.chat.ChatTurn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ein gestarteter Chat-Turn mit seinem RAG-Kontext. Streaming, Abbruch und Abschluss laufen wie gewohnt über
 * {@link #turn()} und den übergebenen {@code ChatTurnListener}; die Quellen stehen schon beim Start fest und
 * können vor der ersten Antwortzeile angezeigt werden.
 */
public final class RagChatTurn {

    private final ChatTurn turn;
    private final boolean retrievalRequested;
    private final PromptContext context;
    private final List<RetrievalWarning> warnings;
    private final boolean retrievalFailed;

    RagChatTurn(ChatTurn turn, boolean retrievalRequested, PromptContext context, List<RetrievalWarning> warnings,
                boolean retrievalFailed) {
        this.turn = turn;
        this.retrievalRequested = retrievalRequested;
        this.context = context;
        this.warnings = Collections.unmodifiableList(new ArrayList<RetrievalWarning>(warnings));
        this.retrievalFailed = retrievalFailed;
    }

    public ChatTurn turn() {
        return turn;
    }

    /** RAG war für diese Nachricht eingeschaltet. */
    public boolean retrievalRequested() {
        return retrievalRequested;
    }

    /** Der mitgegebene Kontextblock; leer bei RAG aus, ohne Treffer oder bei Ausfall des Retrievals. */
    public PromptContext context() {
        return context;
    }

    /** Die Quellen, auf die sich die Antwort stützen kann, Nummer 1 zuerst. */
    public List<RagSource> sources() {
        return context.sources();
    }

    /** Ausgefallene Suchpfade; bei {@link #retrievalFailed()} alle. */
    public List<RetrievalWarning> warnings() {
        return warnings;
    }

    /** Das Retrieval ist ganz ausgefallen; die Nachricht ging ohne Kontext an das Modell. */
    public boolean retrievalFailed() {
        return retrievalFailed;
    }

    @Override
    public String toString() {
        return "RagChatTurn{" + turn + ", rag=" + retrievalRequested + ", " + context
                + (retrievalFailed ? ", retrievalFailed" : "") + "}";
    }
}
