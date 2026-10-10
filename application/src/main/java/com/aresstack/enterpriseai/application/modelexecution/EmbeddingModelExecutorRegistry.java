package com.aresstack.enterpriseai.application.modelexecution;

import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Embedding-Ausführung je Modellkatalog: die EMBEDDING-Auswahl nennt den Katalog, die Registry liefert dessen
 * {@link EmbeddingPort} (mit dem gewählten Modell gebaut). Jeder Port trägt seine eigene Identität, ein Wechsel des
 * Katalogs ist damit für den Index ein Wechsel der Embedding-Welt.
 */
public final class EmbeddingModelExecutorRegistry {

    private final Map<String, EmbeddingPort> ports;

    /** @param ports Embedding-Port je Katalog */
    public EmbeddingModelExecutorRegistry(Map<String, EmbeddingPort> ports) {
        if (ports == null || ports.isEmpty()) {
            throw new IllegalArgumentException("ports must not be empty");
        }
        this.ports = Collections.unmodifiableMap(new LinkedHashMap<String, EmbeddingPort>(ports));
    }

    public boolean supports(String catalogId) {
        return ports.containsKey(catalogId);
    }

    /** Der Port des Katalogs; {@link IllegalStateException}, wenn der Katalog keine Embeddings ausführen kann. */
    public EmbeddingPort require(String catalogId) {
        EmbeddingPort port = ports.get(catalogId);
        if (port == null) {
            throw new IllegalStateException("no embeddings for catalog " + catalogId);
        }
        return port;
    }

    @Override
    public String toString() {
        return "EmbeddingModelExecutorRegistry" + ports.keySet();
    }
}
