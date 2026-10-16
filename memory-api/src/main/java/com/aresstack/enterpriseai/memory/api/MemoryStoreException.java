package com.aresstack.enterpriseai.memory.api;

/** The memory store could not complete an operation; nothing was changed by the failed operation. */
public class MemoryStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MemoryStoreException(String message) {
        super(message);
    }

    public MemoryStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
