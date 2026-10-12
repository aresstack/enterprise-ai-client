package com.aresstack.enterpriseai.astu.acropolis.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Der Wissenskontext, mit dem eine Konversation zuletzt geantwortet hat (oder antworten wollte): die Suchanfragen und
 * die Treffer, die in den Kontextblock kamen, in ihrer Reihenfolge. Die nächste Nachricht derselben Konversation prüft
 * zuerst, ob er sie beantwortet, bevor sie neu sucht. Bleibt auch, wenn die Antwort danach scheitert. Unveränderlich.
 */
public final class ConversationKnowledgeContext {

    private final List<String> queries;
    private final List<RetrievedChunk> hits;

    public ConversationKnowledgeContext(List<String> queries, List<RetrievedChunk> hits) {
        this.queries = Collections.unmodifiableList(new ArrayList<String>(
                queries == null ? Collections.<String>emptyList() : queries));
        this.hits = Collections.unmodifiableList(new ArrayList<RetrievedChunk>(
                hits == null ? Collections.<RetrievedChunk>emptyList() : hits));
    }

    public static ConversationKnowledgeContext empty() {
        return new ConversationKnowledgeContext(Collections.<String>emptyList(),
                Collections.<RetrievedChunk>emptyList());
    }

    /** Die Suchanfragen, aus denen die Treffer stammen, älteste zuerst. */
    public List<String> queries() {
        return queries;
    }

    /** Die Treffer des Kontextblocks in seiner Reihenfolge; ohne Dubletten. */
    public List<RetrievedChunk> hits() {
        return hits;
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }

    @Override
    public String toString() {
        return "ConversationKnowledgeContext{queries=" + queries.size() + ", hits=" + hits.size() + "}";
    }
}
