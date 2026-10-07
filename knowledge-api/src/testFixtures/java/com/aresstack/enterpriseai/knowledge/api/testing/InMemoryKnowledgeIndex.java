package com.aresstack.enterpriseai.knowledge.api.testing;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchMode;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vollständige, nicht persistente {@link KnowledgeIndexPort}-Implementierung für Tests von Konsumenten (RAG,
 * MCP-Tools) und als Referenz für den Vertrag. Volltext: einfache TF-IDF-Wertung über klein geschriebene
 * Wörter aus Text, Überschriften und Titel; semantisch: exakte Cosine-Ähnlichkeit. Thread-sicher durch
 * Synchronisation auf der Instanz.
 */
public final class InMemoryKnowledgeIndex implements KnowledgeIndexPort {

    /** Namespace-Fingerprint → (Chunk-ID → Eintrag), in Einfügereihenfolge. */
    private final Map<String, Map<KnowledgeChunkId, KnowledgeIndexEntry>> namespaces =
            new LinkedHashMap<String, Map<KnowledgeChunkId, KnowledgeIndexEntry>>();

    @Override
    public synchronized void index(Collection<KnowledgeIndexEntry> entries) {
        for (KnowledgeIndexEntry entry : requireEntries(entries)) {
            namespace(entry.space().fingerprint()).put(entry.chunkId(), entry);
        }
    }

    @Override
    public synchronized void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId,
                                     Collection<KnowledgeIndexEntry> entries) {
        if (space == null || resourceId == null) {
            throw new IllegalArgumentException("space und resourceId sind Pflicht");
        }
        List<KnowledgeIndexEntry> checked = requireEntries(entries);
        for (KnowledgeIndexEntry entry : checked) {
            if (!entry.space().equals(space)) {
                throw new IllegalArgumentException(entry + " gehört nicht zum Namespace " + space);
            }
            if (!entry.resource().id().equals(resourceId)) {
                throw new IllegalArgumentException(entry + " gehört nicht zur Ressource " + resourceId);
            }
        }
        Map<KnowledgeChunkId, KnowledgeIndexEntry> namespace = namespace(space.fingerprint());
        removeWhere(namespace, resourceId, null);
        for (KnowledgeIndexEntry entry : checked) {
            namespace.put(entry.chunkId(), entry);
        }
    }

    @Override
    public synchronized List<KnowledgeSearchHit> keywordSearch(KnowledgeKeywordQuery query) {
        List<String> terms = terms(query.text());
        Map<KnowledgeChunkId, KnowledgeIndexEntry> namespace = namespaces.get(query.space().fingerprint());
        List<KnowledgeSearchHit> hits = new ArrayList<KnowledgeSearchHit>();
        if (namespace == null || terms.isEmpty()) {
            return hits;
        }
        Map<KnowledgeIndexEntry, List<String>> documents = new LinkedHashMap<KnowledgeIndexEntry, List<String>>();
        Map<String, Integer> documentFrequency = new HashMap<String, Integer>();
        for (KnowledgeIndexEntry entry : namespace.values()) {
            List<String> words = terms(entry.chunk().textWithHeading() + " " + entry.resource().title());
            documents.put(entry, words);
            for (String term : new java.util.HashSet<String>(words)) {
                Integer count = documentFrequency.get(term);
                documentFrequency.put(term, count == null ? 1 : count + 1);
            }
        }
        for (Map.Entry<KnowledgeIndexEntry, List<String>> document : documents.entrySet()) {
            if (!query.accepts(document.getKey().resource().sourceId())) {
                continue;
            }
            double score = 0;
            for (String term : terms) {
                int frequency = Collections.frequency(document.getValue(), term);
                if (frequency > 0) {
                    score += frequency * Math.log(1 + (double) documents.size() / documentFrequency.get(term));
                }
            }
            if (score > 0) {
                hits.add(hit(document.getKey(), score, KnowledgeSearchMode.KEYWORD));
            }
        }
        return top(hits, query.maxResults());
    }

    @Override
    public synchronized List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query) {
        Map<KnowledgeChunkId, KnowledgeIndexEntry> namespace = namespaces.get(query.space().fingerprint());
        List<KnowledgeSearchHit> hits = new ArrayList<KnowledgeSearchHit>();
        if (namespace == null) {
            return hits;
        }
        for (KnowledgeIndexEntry entry : namespace.values()) {
            if (query.accepts(entry.resource().sourceId())) {
                // cosineSimilarity prüft selbst noch einmal, dass beide Vektoren aus derselben Welt stammen.
                hits.add(hit(entry, query.vector().cosineSimilarity(entry.embedding()), KnowledgeSearchMode.SEMANTIC));
            }
        }
        return top(hits, query.maxResults());
    }

    @Override
    public synchronized void remove(KnowledgeResourceId resourceId) {
        for (Map<KnowledgeChunkId, KnowledgeIndexEntry> namespace : namespaces.values()) {
            removeWhere(namespace, resourceId, null);
        }
    }

    @Override
    public synchronized void removeSource(KnowledgeSourceId sourceId) {
        for (Map<KnowledgeChunkId, KnowledgeIndexEntry> namespace : namespaces.values()) {
            removeWhere(namespace, null, sourceId);
        }
    }

    @Override
    public synchronized void rebuild(Collection<KnowledgeIndexEntry> entries) {
        List<KnowledgeIndexEntry> checked = requireEntries(entries);
        namespaces.clear();
        index(checked);
    }

    /** Anzahl aller Einträge über alle Namespaces (Diagnose in Tests). */
    public synchronized int size() {
        int size = 0;
        for (Map<KnowledgeChunkId, KnowledgeIndexEntry> namespace : namespaces.values()) {
            size += namespace.size();
        }
        return size;
    }

    private Map<KnowledgeChunkId, KnowledgeIndexEntry> namespace(String fingerprint) {
        Map<KnowledgeChunkId, KnowledgeIndexEntry> namespace = namespaces.get(fingerprint);
        if (namespace == null) {
            namespace = new LinkedHashMap<KnowledgeChunkId, KnowledgeIndexEntry>();
            namespaces.put(fingerprint, namespace);
        }
        return namespace;
    }

    private static void removeWhere(Map<KnowledgeChunkId, KnowledgeIndexEntry> namespace,
                                    KnowledgeResourceId resourceId, KnowledgeSourceId sourceId) {
        for (Iterator<KnowledgeIndexEntry> it = namespace.values().iterator(); it.hasNext(); ) {
            KnowledgeIndexEntry entry = it.next();
            if (entry.resource().id().equals(resourceId) || entry.resource().sourceId().equals(sourceId)) {
                it.remove();
            }
        }
    }

    private static List<KnowledgeIndexEntry> requireEntries(Collection<KnowledgeIndexEntry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("entries darf nicht null sein");
        }
        List<KnowledgeIndexEntry> copy = new ArrayList<KnowledgeIndexEntry>(entries);
        if (copy.contains(null)) {
            throw new IllegalArgumentException("entries enthält null");
        }
        return copy;
    }

    private static KnowledgeSearchHit hit(KnowledgeIndexEntry entry, double score, KnowledgeSearchMode mode) {
        return new KnowledgeSearchHit(entry.resource(), entry.chunk(), score, mode);
    }

    private static List<KnowledgeSearchHit> top(List<KnowledgeSearchHit> hits, int maxResults) {
        Collections.sort(hits, KnowledgeSearchHit.byRelevance());
        return hits.size() > maxResults ? new ArrayList<KnowledgeSearchHit>(hits.subList(0, maxResults)) : hits;
    }

    private static List<String> terms(String text) {
        List<String> terms = new ArrayList<String>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) {
                terms.add(token);
            }
        }
        return terms;
    }
}
