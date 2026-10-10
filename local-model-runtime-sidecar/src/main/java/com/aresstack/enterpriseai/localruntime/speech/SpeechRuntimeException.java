package com.aresstack.enterpriseai.localruntime.speech;

/** A voice could not be loaded or run by the {@link LocalSpeechRuntime}; the message names the cause. */
public final class SpeechRuntimeException extends Exception {

    public SpeechRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public SpeechRuntimeException(String message) {
        super(message);
    }
}
