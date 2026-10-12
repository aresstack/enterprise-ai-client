package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * Begrenzt, wie viele Aufrufe des Embedding-Endpunkts gleichzeitig laufen ({@code embedding.maxConcurrency}): die
 * Aufbereitung im Hintergrund (Passagen, Satzfenster, Passagen-Embeddings) rechnet mit mehreren Workern, der Dienst
 * bekommt aber nie mehr parallele Anfragen als eingestellt. Die Grenze gilt je Modell-Port und wird bei jedem Aufruf
 * gelesen (Einstellungen ohne Neustart; eine kleinere Grenze wirkt, sobald laufende Aufrufe enden).
 *
 * <p>Nur die Aufbereitung geht über diesen Port. Das Anfrage-Embedding des Chats nimmt den Port direkt und wartet
 * so nie hinter der Hintergrundarbeit.
 */
public final class ConcurrencyLimitedEmbeddingPort implements EmbeddingPort {

    private final EmbeddingPort delegate;
    private final IntSupplier limit;
    private int running;

    public ConcurrencyLimitedEmbeddingPort(EmbeddingPort delegate, IntSupplier limit) {
        if (delegate == null || limit == null) {
            throw new IllegalArgumentException("delegate and limit must not be null");
        }
        this.delegate = delegate;
        this.limit = limit;
    }

    @Override
    public EmbeddingModelIdentity modelIdentity() {
        return delegate.modelIdentity();
    }

    @Override
    public EmbeddingBatch embed(List<String> texts) {
        acquire();
        try {
            return delegate.embed(texts);
        } finally {
            release();
        }
    }

    @Override
    public EmbeddingBatch embedQueries(List<String> queries) {
        acquire();
        try {
            return delegate.embedQueries(queries);
        } finally {
            release();
        }
    }

    /** Wie viele Aufrufe gerade laufen (Tests). */
    synchronized int running() {
        return running;
    }

    private synchronized void acquire() {
        while (running >= Math.max(1, limit.getAsInt())) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "Embedding abgebrochen");
            }
        }
        running++;
    }

    private synchronized void release() {
        running--;
        notifyAll();
    }
}
