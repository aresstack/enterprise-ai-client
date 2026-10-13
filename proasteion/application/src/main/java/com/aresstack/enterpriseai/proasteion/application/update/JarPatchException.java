package com.aresstack.enterpriseai.proasteion.application.update;

/** A patch cannot be read, does not fit the running jar, or the patched jar fails its check. */
public final class JarPatchException extends Exception {

    private static final long serialVersionUID = 1L;

    public JarPatchException(String message) {
        super(message);
    }

    public JarPatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
