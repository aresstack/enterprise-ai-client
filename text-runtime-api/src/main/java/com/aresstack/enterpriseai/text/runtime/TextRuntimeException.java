package com.aresstack.enterpriseai.text.runtime;

/** Ein Text-Modell ließ sich nicht laden oder rechnen; die Meldung nennt den Grund. */
public final class TextRuntimeException extends Exception {

    private static final long serialVersionUID = 1L;

    public TextRuntimeException(String message) {
        super(message);
    }

    public TextRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
