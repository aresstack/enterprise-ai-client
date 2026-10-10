package com.aresstack.enterpriseai.speech.api;

/** Die Spracheingabe ist nicht verfügbar oder das Modell kann die Aufnahme nicht erkennen; ohne Secrets in der Meldung. */
public class SpeechRecognitionException extends Exception {

    private static final long serialVersionUID = 1L;

    public SpeechRecognitionException(String message) {
        super(message);
    }

    public SpeechRecognitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
