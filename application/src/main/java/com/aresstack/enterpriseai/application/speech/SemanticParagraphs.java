package com.aresstack.enterpriseai.application.speech;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Absätze zum Vorlesen nach Thema, übernommen aus askai-java8 arch {@code PassageSegmentation}
 * ({@code BoundaryMode.SEMANTIC}): Leerzeilen sind harte Grenzen; innerhalb eines Absatzes wird zusätzlich dort
 * umgebrochen, wo das linke und das rechte Satzfenster (bis {@link #WINDOW_SIZE} Sätze) wenig ähnlich sind
 * (Cosinus unter {@link #BOUNDARY_THRESHOLD}). Mindest- und Höchstzahl an Sätzen verhindern Mini-Absätze und
 * Textwände. Werte wie die Standards in arch (3, 0.35, 2, 8).
 */
final class SemanticParagraphs {

    static final int WINDOW_SIZE = 3;
    static final double BOUNDARY_THRESHOLD = 0.35;
    static final int MIN_SENTENCES = 2;
    static final int MAX_SENTENCES = 8;

    private SemanticParagraphs() {
    }

    /**
     * @return Stücke in Lesereihenfolge, je Stück höchstens {@link SpeechText#MAX_UNIT_CHARS} Zeichen
     * @throws RuntimeException wenn das Embedding-Modell nicht rechnet (der Aufrufer fällt auf Absätze zurück)
     */
    static List<String> chunks(String plainText, EmbeddingPort embeddings) {
        List<String> chunks = new ArrayList<String>();
        for (List<String> sentences : SpeechText.paragraphSentences(plainText)) {
            for (List<String> passage : split(sentences, embeddings)) {
                SpeechText.addUnits(chunks, passage);
            }
        }
        return chunks;
    }

    private static List<List<String>> split(List<String> sentences, EmbeddingPort embeddings) {
        List<List<String>> passages = new ArrayList<List<String>>();
        if (sentences.size() <= MIN_SENTENCES) {
            passages.add(sentences);
            return passages;
        }
        List<EmbeddingVector> vectors = embeddings.embed(sentences).vectors();
        List<String> current = new ArrayList<String>();
        for (int i = 0; i < sentences.size(); i++) {
            current.add(sentences.get(i));
            boolean last = i == sentences.size() - 1;
            boolean maxReached = current.size() >= MAX_SENTENCES;
            boolean semanticBoundary = false;
            if (!last && current.size() >= MIN_SENTENCES && sentences.size() - i - 1 >= MIN_SENTENCES) {
                semanticBoundary = boundaryStrength(vectors, i) < BOUNDARY_THRESHOLD;
            }
            if (last || maxReached || semanticBoundary) {
                passages.add(current);
                current = new ArrayList<String>();
            }
        }
        if (!current.isEmpty()) {
            passages.add(current);
        }
        return passages;
    }

    /** Ähnlichkeit der Fenster LINKS der Grenze (…i) und RECHTS davon (i+1…): niedrig = Themenwechsel. */
    private static double boundaryStrength(List<EmbeddingVector> vectors, int boundaryAfter) {
        int leftFrom = Math.max(0, boundaryAfter - WINDOW_SIZE + 1);
        int rightTo = Math.min(vectors.size(), boundaryAfter + 1 + WINDOW_SIZE);
        return cosine(mean(vectors.subList(leftFrom, boundaryAfter + 1)),
                mean(vectors.subList(boundaryAfter + 1, rightTo)));
    }

    private static double[] mean(List<EmbeddingVector> vectors) {
        double[] sum = new double[vectors.get(0).dimension()];
        for (EmbeddingVector vector : vectors) {
            for (int d = 0; d < sum.length; d++) {
                sum[d] += vector.valueAt(d);
            }
        }
        for (int d = 0; d < sum.length; d++) {
            sum[d] /= vectors.size();
        }
        return sum;
    }

    private static double cosine(double[] left, double[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int d = 0; d < left.length; d++) {
            dot += left[d] * right[d];
            leftNorm += left[d] * left[d];
            rightNorm += right[d] * right[d];
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
