package com.aresstack.enterpriseai.source.ndv;

/**
 * Exception for NDV (Natural Development Server) operations.
 */
class NdvException extends Exception {

    public NdvException(String message) {
        super(message);
    }

    public NdvException(String message, Throwable cause) {
        super(message, cause);
    }
}

