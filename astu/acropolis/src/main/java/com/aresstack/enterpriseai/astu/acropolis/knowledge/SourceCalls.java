package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

/**
 * Wie ein lesender Aufruf an eine Wissensquelle ausgeführt wird (Live-Suche). Der Use Case ruft die Quelle nur über
 * diese Naht auf; die Composition Root legt dort eine Wiederholungsregel für vorübergehende Fehler darum, damit keine
 * Schleife im Use Case steht. {@link #direct()} ruft genau einmal.
 */
public interface SourceCalls {

    /** Ein einzelner Aufruf an die Quelle. */
    interface Call<T> {
        T run() throws KnowledgeSourceException;
    }

    <T> T call(Call<T> call) throws KnowledgeSourceException;

    /** Genau ein Versuch. */
    static SourceCalls direct() {
        return new SourceCalls() {
            @Override
            public <T> T call(Call<T> call) throws KnowledgeSourceException {
                return call.run();
            }
        };
    }
}
