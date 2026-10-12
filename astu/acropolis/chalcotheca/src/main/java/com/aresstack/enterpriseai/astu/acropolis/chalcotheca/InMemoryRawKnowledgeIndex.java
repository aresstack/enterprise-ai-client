package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * {@link RawKnowledgeIndexPort} im Speicher, ohne Lucene: Wörter klein geschrieben, an allem außer Buchstaben und
 * Ziffern getrennt, BM25 über die gespeicherten Dokumente. Für Tests und als Rückfall, wenn kein Lucene-Rohindex
 * angebunden ist (z. B. ein Index ohne Verzeichnis). Threadsicher.
 */
public final class InMemoryRawKnowledgeIndex implements RawKnowledgeIndexPort {

    /** Die Wortzerlegung dieses Index (auch für die Textfenster). */
    public static final Function<String, List<String>> WORDS = new Function<String, List<String>>() {
        @Override
        public List<String> apply(String text) {
            List<String> words = new ArrayList<String>();
            for (String word : (text == null ? "" : text).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
            return words;
        }
    };

    private final Map<KnowledgeResourceId, RawKnowledgeDocument> documents =
            new LinkedHashMap<KnowledgeResourceId, RawKnowledgeDocument>();

    @Override
    public synchronized void put(RawKnowledgeDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        documents.put(document.id(), document);
    }

    @Override
    public List<RawKnowledgeHit> search(RawKnowledgeQuery query) {
        List<RawKnowledgeDocument> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<RawKnowledgeDocument>(documents.values());
        }
        Set<String> wanted = new LinkedHashSet<String>(WORDS.apply(query.text()));
        if (wanted.isEmpty()) {
            return Collections.emptyList();
        }
        List<RawKnowledgeDocument> candidates = new ArrayList<RawKnowledgeDocument>();
        List<Map<String, Integer>> frequencies = new ArrayList<Map<String, Integer>>();
        List<Integer> lengths = new ArrayList<Integer>();
        Map<String, Integer> documentFrequency = new HashMap<String, Integer>();
        long total = 0;
        for (RawKnowledgeDocument document : snapshot) {
            if (!query.accepts(document.resource().sourceId())) {
                continue;
            }
            List<String> words = WORDS.apply(document.resource().title() + "\n" + document.text());
            Map<String, Integer> tf = new HashMap<String, Integer>();
            for (String word : words) {
                if (wanted.contains(word)) {
                    Integer old = tf.get(word);
                    tf.put(word, old == null ? 1 : old + 1);
                }
            }
            for (String word : tf.keySet()) {
                Integer old = documentFrequency.get(word);
                documentFrequency.put(word, old == null ? 1 : old + 1);
            }
            candidates.add(document);
            frequencies.add(tf);
            lengths.add(words.size());
            total += words.size();
        }
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        double average = Math.max(1.0, (double) total / candidates.size());
        List<RawKnowledgeHit> hits = new ArrayList<RawKnowledgeHit>();
        for (int i = 0; i < candidates.size(); i++) {
            double score = 0.0;
            for (Map.Entry<String, Integer> entry : frequencies.get(i).entrySet()) {
                int df = documentFrequency.get(entry.getKey());
                double idf = Math.log(1.0 + (candidates.size() - df + 0.5) / (df + 0.5));
                double tf = entry.getValue();
                score += idf * tf * (RawTextWindows.BM25_K1 + 1.0) / (tf + RawTextWindows.BM25_K1
                        * (1.0 - RawTextWindows.BM25_B + RawTextWindows.BM25_B * lengths.get(i) / average));
            }
            if (score > 0.0) {
                RawKnowledgeDocument document = candidates.get(i);
                hits.add(new RawKnowledgeHit(document.resource(), score, document.contentHash(), document.snippet(),
                        RawTextWindows.best(document.text(), query.text(), query.windowsPerDocument(),
                                query.windowChars(), WORDS)));
            }
        }
        Collections.sort(hits, new Comparator<RawKnowledgeHit>() {
            @Override
            public int compare(RawKnowledgeHit a, RawKnowledgeHit b) {
                int byScore = Double.compare(b.score(), a.score());
                return byScore != 0 ? byScore : a.resource().id().value().compareTo(b.resource().id().value());
            }
        });
        return hits.size() > query.maxDocuments() ? new ArrayList<RawKnowledgeHit>(
                hits.subList(0, query.maxDocuments())) : hits;
    }

    @Override
    public synchronized Optional<RawKnowledgeDocument> get(KnowledgeResourceId id) {
        return Optional.ofNullable(documents.get(id));
    }

    @Override
    public synchronized Optional<String> contentHashOf(KnowledgeResourceId id) {
        RawKnowledgeDocument document = documents.get(id);
        return document == null ? Optional.<String>empty() : Optional.of(document.contentHash());
    }

    @Override
    public synchronized Optional<KnowledgeRevision> revisionOf(KnowledgeResourceId id) {
        RawKnowledgeDocument document = documents.get(id);
        return document == null ? Optional.<KnowledgeRevision>empty() : Optional.of(document.resource().revision());
    }

    @Override
    public synchronized void remove(KnowledgeResourceId id) {
        documents.remove(id);
    }

    @Override
    public synchronized void removeSource(SourceAddress sourceId) {
        for (Iterator<RawKnowledgeDocument> it = documents.values().iterator(); it.hasNext(); ) {
            if (it.next().resource().sourceId().equals(sourceId)) {
                it.remove();
            }
        }
    }

    /** Wie viele Dokumente gespeichert sind (Tests). */
    public synchronized int size() {
        return documents.size();
    }
}
