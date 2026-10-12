package com.aresstack.enterpriseai.model.api;

/**
 * Signals that the user stopped an installation. Treat it as its own outcome, not as a failure: the model is not
 * installed, partial downloads stay in the staging folder so a later attempt can resume.
 */
public final class LocalVoiceInstallCancelledException extends LocalVoiceInstallException {

    private static final long serialVersionUID = 1L;

    public LocalVoiceInstallCancelledException(String message) {
        super(message);
    }
}
