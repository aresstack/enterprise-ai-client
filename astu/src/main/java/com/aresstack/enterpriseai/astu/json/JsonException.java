package com.aresstack.enterpriseai.astu.json;

/** JSON text could not be read; the message names the problem and the character offset, never the text itself. */
public final class JsonException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final int offset;

    public JsonException(String problem, int offset) {
        super(problem + " at offset " + offset);
        this.offset = offset;
    }

    /** Character offset where reading failed. */
    public int offset() {
        return offset;
    }
}
