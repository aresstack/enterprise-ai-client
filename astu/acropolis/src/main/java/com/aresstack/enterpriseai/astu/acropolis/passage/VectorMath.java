package com.aresstack.enterpriseai.astu.acropolis.passage;

import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;

import java.util.List;

/**
 * Cosinus und Mittelwert mit Fingerabdruck-Schutz, aus askai-java8 arch {@code VectorMath}: Vektoren verschiedener
 * Embedding-Modelle oder Dimensionen werden NIE verglichen oder gemittelt (harter Fehler, kein Rauschen). Der
 * Cosinus zweier {@link EmbeddingVector} ist {@link EmbeddingVector#cosineSimilarity}, der dieselbe Prüfung macht.
 * Abweichung von arch: Mittelwerte werden in {@code double} summiert (wie die Passagenbildung des Clients seit .94),
 * damit sich keine Passagengrenze verschiebt.
 */
public final class VectorMath {

    private VectorMath() {
    }

    /** Cosinus zweier Vektoren derselben Embedding-Welt; Norm 0 ergibt 0. */
    public static double cosine(EmbeddingVector a, EmbeddingVector b) {
        return a.cosineSimilarity(b);
    }

    /** Cosinus zweier Mittelwerte aus {@link #meanValues}; Norm 0 ergibt 0. */
    public static double cosine(double[] left, double[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Dimensionen verschieden: " + left.length + " vs " + right.length);
        }
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

    /** Das arithmetische Mittel als {@code double}-Werte; verlangt eine Embedding-Welt für alle. */
    public static double[] meanValues(List<EmbeddingVector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("cannot average zero vectors");
        }
        EmbeddingVector first = vectors.get(0);
        double[] sum = new double[first.dimension()];
        for (EmbeddingVector vector : vectors) {
            if (!first.isComparableWith(vector)) {
                throw new IllegalArgumentException("mixed embedding models in mean(): " + first.identity()
                        + " vs " + vector.identity());
            }
            for (int d = 0; d < sum.length; d++) {
                sum[d] += vector.valueAt(d);
            }
        }
        for (int d = 0; d < sum.length; d++) {
            sum[d] /= vectors.size();
        }
        return sum;
    }

    /** Das arithmetische Mittel als Vektor derselben Embedding-Welt. */
    public static EmbeddingVector mean(List<EmbeddingVector> vectors) {
        double[] values = meanValues(vectors);
        float[] mean = new float[values.length];
        for (int d = 0; d < values.length; d++) {
            mean[d] = (float) values[d];
        }
        return EmbeddingVector.of(vectors.get(0).identity(), mean);
    }
}
