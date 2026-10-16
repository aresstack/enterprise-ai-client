package com.aresstack.enterpriseai.searchrun.api;

/** The search run store could not complete an operation; nothing was changed by the failed operation. */
public class SearchRunStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SearchRunStoreException(String message) {
        super(message);
    }

    public SearchRunStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
