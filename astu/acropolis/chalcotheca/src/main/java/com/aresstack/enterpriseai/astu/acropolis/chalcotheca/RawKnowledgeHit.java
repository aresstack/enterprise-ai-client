package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ein Dokument aus dem {@link RawKnowledgeIndexPort}: Ressource, BM25-Score des ganzen Dokuments, Inhalts-Hash,
 * Snippet der Quellensuche und die passendsten Textfenster (bestes zuerst).
 */
public final class RawKnowledgeHit {

    private final KnowledgeResource resource;
    private final double score;
    private final String contentHash;
    private final String snippet;
    private final List<RawTextWindows.Window> windows;

    public RawKnowledgeHit(KnowledgeResource resource, double score, String contentHash, String snippet,
                           List<RawTextWindows.Window> windows) {
        if (resource == null || contentHash == null || windows == null) {
            throw new IllegalArgumentException("resource, contentHash and windows must not be null");
        }
        this.resource = resource;
        this.score = score;
        this.contentHash = contentHash;
        this.snippet = snippet == null ? "" : snippet;
        this.windows = Collections.unmodifiableList(new ArrayList<RawTextWindows.Window>(windows));
    }

    public KnowledgeResource resource() {
        return resource;
    }

    public double score() {
        return score;
    }

    public String contentHash() {
        return contentHash;
    }

    public String snippet() {
        return snippet;
    }

    /** Die passendsten Textfenster, bestes zuerst; nie leer, solange das Dokument Text hat. */
    public List<RawTextWindows.Window> windows() {
        return windows;
    }

    @Override
    public String toString() {
        return "RawKnowledgeHit{" + resource.id() + ", score=" + score + ", windows=" + windows.size() + "}";
    }
}
