package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Textfenster eines Rohdokuments, die zu einer Anfrage passen: Der Text wird in überlappende Fenster von etwa
 * {@code windowChars} Zeichen an Wortgrenzen geteilt (Schritt: halbe Fensterlänge, damit keine Fundstelle an einer
 * Grenze zerfällt), jedes Fenster nach BM25 gegen die Anfrageterme bewertet (Statistik über die Fenster dieses
 * Dokuments) und die besten, sich nicht überlappenden zurückgegeben. Trifft kein Fenster (etwa nur der Titel), gilt
 * der Anfang des Dokuments. Keine Passagen, keine Embeddings.
 *
 * <p>Die Terme liefert der Aufrufer ({@code terms}): der Lucene-Adapter mit seinem Analyzer, der InMemory-Index mit
 * einer einfachen Wortzerlegung. Ein Fenster ist über Ressource und Anfangsposition eindeutig ({@link #chunkId});
 * so lässt es sich nach einem Neustart aus dem Rohindex wiederherstellen ({@link #restore}).
 */
public final class RawTextWindows {

    /** BM25-Sättigung der Termhäufigkeit (Lucene-Standard). */
    static final double BM25_K1 = 1.2;
    /** BM25-Längennormierung (Lucene-Standard). */
    static final double BM25_B = 0.75;
    /**
     * Ab dieser Ordnungszahl bezeichnet eine Chunk-ID ein Rohfenster ({@code ORDINAL_BASE + Anfangsposition}); die
     * Abschnitte des Passagenindex zählen von 0 und bleiben weit darunter. Der Ressourcenteil der ID bleibt die
     * Ressource selbst.
     */
    static final int ORDINAL_BASE = 1_000_000_000;

    private RawTextWindows() {
    }

    /** Ein Textfenster: Zeichenbereich {@code [start, end)} im Dokumenttext, Text und BM25-Score. */
    public static final class Window {

        private final int start;
        private final int end;
        private final String text;
        private final double score;

        public Window(int start, int end, String text, double score) {
            if (start < 0 || end < start || text == null) {
                throw new IllegalArgumentException("ungültiges Fenster");
            }
            this.start = start;
            this.end = end;
            this.text = text;
            this.score = score;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        public String text() {
            return text;
        }

        public double score() {
            return score;
        }

        @Override
        public String toString() {
            return "Window[" + start + "," + end + ") score=" + score;
        }
    }

    /**
     * Die besten {@code count} Fenster zur Anfrage, bestes zuerst.
     *
     * @param terms zerlegt einen Text in normalisierte Suchterme (dieselbe Zerlegung für Anfrage und Text)
     */
    public static List<Window> best(String text, String query, int count, int windowChars,
                                    Function<String, List<String>> terms) {
        if (text == null || text.trim().isEmpty() || count < 1) {
            return Collections.emptyList();
        }
        List<int[]> ranges = ranges(text, Math.max(1, windowChars));
        Set<String> wanted = new LinkedHashSet<String>(terms.apply(query == null ? "" : query));
        List<Map<String, Integer>> frequencies = new ArrayList<Map<String, Integer>>(ranges.size());
        int[] lengths = new int[ranges.size()];
        Map<String, Integer> documentFrequency = new HashMap<String, Integer>();
        long totalLength = 0;
        for (int i = 0; i < ranges.size(); i++) {
            List<String> windowTerms = terms.apply(text.substring(ranges.get(i)[0], ranges.get(i)[1]));
            Map<String, Integer> tf = new HashMap<String, Integer>();
            for (String term : windowTerms) {
                if (wanted.contains(term)) {
                    Integer old = tf.get(term);
                    tf.put(term, old == null ? 1 : old + 1);
                }
            }
            for (String term : tf.keySet()) {
                Integer old = documentFrequency.get(term);
                documentFrequency.put(term, old == null ? 1 : old + 1);
            }
            frequencies.add(tf);
            lengths[i] = windowTerms.size();
            totalLength += windowTerms.size();
        }
        double averageLength = Math.max(1.0, (double) totalLength / ranges.size());
        final double[] scores = new double[ranges.size()];
        for (int i = 0; i < ranges.size(); i++) {
            double score = 0.0;
            for (Map.Entry<String, Integer> entry : frequencies.get(i).entrySet()) {
                int df = documentFrequency.get(entry.getKey());
                double idf = Math.log(1.0 + (ranges.size() - df + 0.5) / (df + 0.5));
                double tf = entry.getValue();
                score += idf * tf * (BM25_K1 + 1.0)
                        / (tf + BM25_K1 * (1.0 - BM25_B + BM25_B * lengths[i] / averageLength));
            }
            scores[i] = score;
        }
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < ranges.size(); i++) {
            if (scores[i] > 0.0) {
                order.add(i);
            }
        }
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int byScore = Double.compare(scores[b], scores[a]);
                return byScore != 0 ? byScore : Integer.compare(a, b);
            }
        });
        List<Window> chosen = new ArrayList<Window>();
        for (int index : order) {
            int[] range = ranges.get(index);
            if (overlaps(chosen, range)) {
                continue;
            }
            chosen.add(new Window(range[0], range[1], text.substring(range[0], range[1]).trim(), scores[index]));
            if (chosen.size() == count) {
                break;
            }
        }
        if (chosen.isEmpty()) {
            int[] lead = ranges.get(0);
            chosen.add(new Window(lead[0], lead[1], text.substring(lead[0], lead[1]).trim(), 0.0));
        }
        return chosen;
    }

    /**
     * Das Fenster, das an {@code start} beginnt, so wie {@link #best} es schneidet (ganze Wörter, höchstens
     * {@code windowChars} Zeichen); leer, wenn der Text dort nicht mehr reicht.
     */
    public static Optional<Window> restore(String text, int start, int windowChars) {
        if (text == null || start < 0 || start >= text.length() || windowChars <= 0) {
            return Optional.empty();
        }
        int end = start;
        int i = start;
        while (i < text.length()) {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            int wordEnd = i;
            while (wordEnd < text.length() && !Character.isWhitespace(text.charAt(wordEnd))) {
                wordEnd++;
            }
            if (wordEnd == i || end > start && wordEnd - start > windowChars) {
                break;
            }
            end = wordEnd;
            i = wordEnd;
        }
        String window = text.substring(start, end).trim();
        return window.isEmpty() ? Optional.<Window>empty() : Optional.of(new Window(start, end, window, 0.0));
    }

    /** Die Chunk-ID eines Fensters: die Ressource selbst, Ordnungszahl {@link #ORDINAL_BASE} plus Anfangsposition. */
    public static KnowledgeChunkId chunkId(KnowledgeResourceId resource, Window window) {
        return KnowledgeChunkId.of(resource, ORDINAL_BASE + Math.min(window.start(), Integer.MAX_VALUE - ORDINAL_BASE));
    }

    /** Ob die Chunk-ID ein Rohfenster bezeichnet. */
    public static boolean isRawWindow(KnowledgeChunkId chunkId) {
        return chunkId != null && chunkId.ordinal() >= ORDINAL_BASE;
    }

    /** Anfangsposition des Rohfensters im Dokumenttext; nur für {@link #isRawWindow}. */
    public static int startOf(KnowledgeChunkId chunkId) {
        if (!isRawWindow(chunkId)) {
            throw new IllegalArgumentException("kein Rohfenster: " + chunkId);
        }
        return chunkId.ordinal() - ORDINAL_BASE;
    }

    /** Überlappende Fensterbereiche an Wortgrenzen, Schritt halbe Fensterlänge. */
    private static List<int[]> ranges(String text, int windowChars) {
        List<int[]> words = new ArrayList<int[]>();
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i > start) {
                words.add(new int[] {start, i});
            }
        }
        List<int[]> ranges = new ArrayList<int[]>();
        if (words.isEmpty()) {
            ranges.add(new int[] {0, text.length()});
            return ranges;
        }
        int step = Math.max(1, windowChars / 2);
        int first = 0;
        while (first < words.size()) {
            int start = words.get(first)[0];
            int last = first;
            while (last + 1 < words.size() && words.get(last + 1)[1] - start <= windowChars) {
                last++;
            }
            ranges.add(new int[] {start, words.get(last)[1]});
            if (last == words.size() - 1) {
                break;
            }
            int next = first + 1;
            while (next <= last && words.get(next)[0] - start < step) {
                next++;
            }
            first = Math.max(first + 1, next);
        }
        return ranges;
    }

    private static boolean overlaps(List<Window> chosen, int[] range) {
        for (Window window : chosen) {
            if (range[0] < window.end() && window.start() < range[1]) {
                return true;
            }
        }
        return false;
    }
}
