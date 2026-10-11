package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalObserver;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalWarning;

import java.util.logging.Logger;

/**
 * Schreibt ausgefallene Suchpfade und gekürzte Anfragen der Wissenssuche ins technische Log, für Chat-RAG und
 * {@code knowledge_search} gleich. Geloggt werden Pfad, Fehlerart und Meldung des Ports (laut Port-Vertrag ohne
 * Secrets) und Tokenzahlen, nie die Anfrage selbst.
 */
public final class LoggingRetrievalObserver implements RetrievalObserver {

    private static final Logger LOG = Logger.getLogger(LoggingRetrievalObserver.class.getName());

    @Override
    public void pathFailed(RetrievalWarning warning) {
        LOG.warning("Wissenssuche: Suchpfad " + warning.path() + " ausgefallen: " + warning.message());
    }

    @Override
    public void queryShortened(int tokens, int limit) {
        LOG.info("Wissenssuche: Anfrage für die semantische Suche von " + tokens + " auf " + limit
                + " Token gekürzt (Tokengrenze des Embedding-Modells)");
    }
}
