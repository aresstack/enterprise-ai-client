package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexException;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Suche in einem festen Korpus von Ressourcen, etwa den übernommenen Quellen eines Research-Projekts: dieselben
 * Passagen und Vektoren wie im Wissensindex, aber nur die der genannten Ressourcen. Hybrid wie die Chat-Suche:
 * BM25 über die Passagentexte des Korpus und Cosine zum Anfragevektor, zusammengeführt per Reciprocal Rank Fusion
 * (k = 60). Gerechnet wird im Speicher über {@link KnowledgeIndexPort#entriesOf}, weil ein Projekt nur wenige
 * Dutzend Seiten hat; fällt das Embedding aus, bleibt die BM25-Rangfolge.
 */
public final class SearchResourceCorpusUseCase {

    /** Ein Treffer: der Indexeintrag (Passage samt Ressource) und sein fusionierter Wert. */
    public static final class Hit {
        private final KnowledgeIndexEntry entry;
        private final double score;

        Hit(KnowledgeIndexEntry entry, double score) {
            this.entry = entry;
            this.score = score;
        }

        public KnowledgeIndexEntry entry() {
            return entry;
        }

        public double score() {
            return score;
        }
    }

    private static final double RRF_K = 60.0;
    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;

    private final KnowledgeIndexPort index;
    private final EmbeddingPort embeddings;
    private final Supplier<EmbeddingModelIdentity> space;

    public SearchResourceCorpusUseCase(KnowledgeIndexPort index, EmbeddingPort embeddings,
                                       Supplier<EmbeddingModelIdentity> space) {
        if (index == null || space == null) {
            throw new IllegalArgumentException("index and space must not be null");
        }
        this.index = index;
        this.embeddings = embeddings;
        this.space = space;
    }

    /**
     * @param resources die Ressourcen des Korpus (unbekannte oder nicht indexierte fallen weg)
     * @param query     die Frage; leer ergibt keine Treffer
     * @param limit     höchstens so viele Passagen
     */
    public List<Hit> search(Collection<KnowledgeResourceId> resources, String query, int limit) {
        if (resources == null || resources.isEmpty() || query == null || query.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        EmbeddingModelIdentity world = space.get();
        List<KnowledgeIndexEntry> corpus = new ArrayList<KnowledgeIndexEntry>();
        for (KnowledgeResourceId resource : new LinkedHashSet<KnowledgeResourceId>(resources)) {
            try {
                corpus.addAll(index.entriesOf(world, resource));
            } catch (KnowledgeIndexException unreadable) {
                // eine unlesbare Ressource fehlt im Korpus; die anderen bleiben durchsuchbar
            }
        }
        if (corpus.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> lexical = rankByBm25(corpus, terms(query));
        List<Integer> semantic = rankByCosine(corpus, query, world);
        Map<Integer, Double> fused = new HashMap<Integer, Double>();
        addRanks(fused, lexical);
        addRanks(fused, semantic);
        List<Map.Entry<Integer, Double>> ordered = new ArrayList<Map.Entry<Integer, Double>>(fused.entrySet());
        Collections.sort(ordered, new Comparator<Map.Entry<Integer, Double>>() {
            @Override
            public int compare(Map.Entry<Integer, Double> a, Map.Entry<Integer, Double> b) {
                int byScore = Double.compare(b.getValue(), a.getValue());
                return byScore != 0 ? byScore : Integer.compare(a.getKey(), b.getKey());
            }
        });
        List<Hit> hits = new ArrayList<Hit>();
        for (Map.Entry<Integer, Double> ranked : ordered) {
            if (hits.size() >= limit) {
                break;
            }
            hits.add(new Hit(corpus.get(ranked.getKey()), ranked.getValue()));
        }
        return hits;
    }

    private static void addRanks(Map<Integer, Double> fused, List<Integer> ranking) {
        for (int rank = 0; rank < ranking.size(); rank++) {
            Integer key = ranking.get(rank);
            Double before = fused.get(key);
            fused.put(key, (before == null ? 0.0 : before) + 1.0 / (RRF_K + rank + 1));
        }
    }

    /** Nur Passagen mit mindestens einem Anfragewort, nach BM25 absteigend. */
    private static List<Integer> rankByBm25(List<KnowledgeIndexEntry> corpus, Set<String> queryTerms) {
        List<List<String>> documents = new ArrayList<List<String>>();
        Map<String, Integer> documentFrequency = new HashMap<String, Integer>();
        double totalLength = 0;
        for (KnowledgeIndexEntry entry : corpus) {
            List<String> tokens = tokens(entry.chunk().textWithHeading());
            documents.add(tokens);
            totalLength += tokens.size();
            for (String term : new HashSet<String>(tokens)) {
                Integer count = documentFrequency.get(term);
                documentFrequency.put(term, count == null ? 1 : count + 1);
            }
        }
        double averageLength = totalLength / Math.max(1, documents.size());
        final double[] scores = new double[documents.size()];
        List<Integer> matching = new ArrayList<Integer>();
        for (int i = 0; i < documents.size(); i++) {
            List<String> tokens = documents.get(i);
            Map<String, Integer> frequencies = new HashMap<String, Integer>();
            for (String token : tokens) {
                Integer count = frequencies.get(token);
                frequencies.put(token, count == null ? 1 : count + 1);
            }
            double score = 0;
            for (String term : queryTerms) {
                Integer tf = frequencies.get(term);
                if (tf == null) {
                    continue;
                }
                int df = documentFrequency.get(term);
                double idf = Math.log(1 + (documents.size() - df + 0.5) / (df + 0.5));
                score += idf * tf * (BM25_K1 + 1)
                        / (tf + BM25_K1 * (1 - BM25_B + BM25_B * tokens.size() / Math.max(1.0, averageLength)));
            }
            scores[i] = score;
            if (score > 0) {
                matching.add(i);
            }
        }
        Collections.sort(matching, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int byScore = Double.compare(scores[b], scores[a]);
                return byScore != 0 ? byScore : Integer.compare(a, b);
            }
        });
        return matching;
    }

    /** Alle Passagen derselben Embedding-Welt nach Cosine absteigend; ohne Embedding leer. */
    private List<Integer> rankByCosine(List<KnowledgeIndexEntry> corpus, String query, EmbeddingModelIdentity world) {
        if (embeddings == null) {
            return Collections.emptyList();
        }
        final EmbeddingVector vector;
        try {
            vector = embeddings.embedQueries(Collections.singletonList(query)).get(0);
        } catch (RuntimeException failed) { // auch EmbeddingException
            return Collections.emptyList();
        }
        if (!world.equals(vector.identity())) {
            return Collections.emptyList(); // nie in einer fremden Embedding-Welt vergleichen
        }
        final double[] scores = new double[corpus.size()];
        List<Integer> ranked = new ArrayList<Integer>();
        for (int i = 0; i < corpus.size(); i++) {
            EmbeddingVector passage = corpus.get(i).embedding();
            if (passage == null || !vector.isComparableWith(passage)) {
                continue;
            }
            scores[i] = vector.cosineSimilarity(passage);
            ranked.add(i);
        }
        Collections.sort(ranked, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int byScore = Double.compare(scores[b], scores[a]);
                return byScore != 0 ? byScore : Integer.compare(a, b);
            }
        });
        return ranked;
    }

    private static Set<String> terms(String text) {
        return new HashSet<String>(tokens(text));
    }

    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<String>();
        for (String word : (text == null ? "" : text).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+")) {
            if (word.length() >= 2) {
                tokens.add(word);
            }
        }
        return tokens;
    }
}
