package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.util.Collection;

/**
 * Schritt vor dem Retrieval eines RAG-Turns: Die Composition Root lässt hier die angehakten Quellen live durchsuchen
 * und neue oder geänderte Treffer indexieren (Suche füllt den Index, wie MainframeMate), damit das anschließende
 * Retrieval sie schon findet. Fehler bleiben im Schritt; das Retrieval läuft in jedem Fall.
 */
public interface RetrievalPreparation {

    /** @param sources die freigegebenen Quellen des Turns; leer = alle */
    void prepare(String query, Collection<SourceAddress> sources);

    static RetrievalPreparation none() {
        return (query, sources) -> {
        };
    }
}
