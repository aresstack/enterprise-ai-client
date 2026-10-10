package com.aresstack.enterpriseai.model.api;

/** Eine Stimme konnte nicht installiert werden; die Meldung nennt den Grund für die Oberfläche. */
public final class LocalVoiceInstallException extends Exception {

    private static final long serialVersionUID = 1L;

    public LocalVoiceInstallException(String message) {
        super(message);
    }

    public LocalVoiceInstallException(String message, Throwable cause) {
        super(message, cause);
    }
}
