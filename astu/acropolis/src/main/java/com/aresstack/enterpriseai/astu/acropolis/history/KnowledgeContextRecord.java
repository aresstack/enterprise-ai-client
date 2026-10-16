package com.aresstack.enterpriseai.astu.acropolis.history;

import java.util.ArrayList;
import java.util.List;

/**
 * Der zuletzt verwendete Wissenskontext eines gespeicherten Chats: die Suchanfragen und je Auszug nur die stabilen
 * Kennungen (Chunk, Ressource, Quelle), nie der Text; beim Öffnen kommen die Auszüge wieder aus dem Index.
 *
 * <p>The record also keeps the chat project scope it was created under (#143); it is only restored while the chat
 * still has that scope. Records saved before chat projects existed have no scope, which means "never in a project".
 */
public final class KnowledgeContextRecord {

    private List<String> queries = new ArrayList<String>();
    private List<Chunk> chunks = new ArrayList<Chunk>();
    private String scope;

    /** Für die Deserialisierung. */
    public KnowledgeContextRecord() {
    }

    public KnowledgeContextRecord(List<String> queries, List<Chunk> chunks) {
        this.queries = new ArrayList<String>(queries == null ? new ArrayList<String>() : queries);
        this.chunks = new ArrayList<Chunk>(chunks == null ? new ArrayList<Chunk>() : chunks);
    }

    public KnowledgeContextRecord(List<String> queries, List<Chunk> chunks, String scope) {
        this(queries, chunks);
        this.scope = scope;
    }

    /** The chat project scope the context was created under; empty for records from before chat projects. */
    public String getScope() {
        return scope != null ? scope : "";
    }

    public List<String> getQueries() {
        return queries != null ? queries : new ArrayList<String>();
    }

    public List<Chunk> getChunks() {
        return chunks != null ? chunks : new ArrayList<Chunk>();
    }

    /** Ein Auszug des Kontexts, nur über seine Kennungen. */
    public static final class Chunk {

        private String chunkId;
        private String resourceId;
        private String sourceId;

        /** Für die Deserialisierung. */
        public Chunk() {
        }

        public Chunk(String chunkId, String resourceId, String sourceId) {
            this.chunkId = chunkId;
            this.resourceId = resourceId;
            this.sourceId = sourceId;
        }

        public String getChunkId() {
            return chunkId != null ? chunkId : "";
        }

        public String getResourceId() {
            return resourceId != null ? resourceId : "";
        }

        public String getSourceId() {
            return sourceId != null ? sourceId : "";
        }
    }
}
