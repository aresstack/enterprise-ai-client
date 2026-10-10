package com.aresstack.enterpriseai.speech.runtime;

/** Eine Stimme ließ sich nicht laden oder rechnen; die Meldung nennt den Grund. */
public final class SpeechRuntimeException extends Exception {

    private static final long serialVersionUID = 1L;

    public SpeechRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public SpeechRuntimeException(String message) {
        super(message);
    }
}
