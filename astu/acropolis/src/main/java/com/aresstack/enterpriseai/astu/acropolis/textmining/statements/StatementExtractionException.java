package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

/** No proposal for a paragraph: the model failed or answered in no readable form. Never carries document text. */
public final class StatementExtractionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StatementExtractionException(String reason) {
        super(reason);
    }

    public StatementExtractionException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
