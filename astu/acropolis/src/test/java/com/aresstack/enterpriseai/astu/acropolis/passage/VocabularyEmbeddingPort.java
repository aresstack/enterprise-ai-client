package com.aresstack.enterpriseai.astu.acropolis.passage;

import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministisches Test-Embedding aus askai-java8 arch ({@code VocabularyEmbeddingPort}): eine Dimension je
 * Stichwort, Wert = Anzahl der Vorkommen. Texte über dieselben Stichwörter liegen nah beieinander, Texte über
 * verschiedene stehen senkrecht – aussagekräftig genug, um Zerlegung und Clustering ohne Modell zu prüfen.
 */
public final class VocabularyEmbeddingPort implements EmbeddingPort {

    private final String[] vocabulary;
    private final EmbeddingModelIdentity identity;
    private int calls;

    public VocabularyEmbeddingPort(String... vocabulary) {
        this.vocabulary = vocabulary;
        this.identity = EmbeddingModelIdentity.of("test-vocab", vocabulary.length);
    }

    @Override
    public EmbeddingModelIdentity modelIdentity() {
        return identity;
    }

    @Override
    public synchronized EmbeddingBatch embed(List<String> texts) {
        calls++;
        List<EmbeddingVector> vectors = new ArrayList<EmbeddingVector>();
        for (String text : texts) {
            vectors.add(vectorFor(text));
        }
        return vectors.isEmpty() ? EmbeddingBatch.empty(identity) : EmbeddingBatch.of(identity, texts.size(), vectors);
    }

    public EmbeddingVector vectorFor(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        float[] values = new float[vocabulary.length];
        for (int i = 0; i < vocabulary.length; i++) {
            int from = 0;
            while ((from = lower.indexOf(vocabulary[i], from)) >= 0) {
                values[i]++;
                from += vocabulary[i].length();
            }
        }
        return EmbeddingVector.of(identity, values);
    }

    /** Wie oft der Port gerufen wurde. */
    public synchronized int calls() {
        return calls;
    }
}
