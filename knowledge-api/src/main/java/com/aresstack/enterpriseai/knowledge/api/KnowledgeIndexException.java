package com.aresstack.enterpriseai.knowledge.api;

/**
 * Der Index konnte nicht gelesen oder geschrieben werden (I/O, beschädigte Daten). Da der Index nur eine
 * Projektion ist, lässt er sich per {@link KnowledgeIndexPort#rebuild} aus den kanonischen Daten neu aufbauen.
 */
public class KnowledgeIndexException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public KnowledgeIndexException(String message) {
        super(message);
    }

    public KnowledgeIndexException(String message, Throwable cause) {
        super(message, cause);
    }
}
