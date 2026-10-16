package com.aresstack.enterpriseai.proasteion.application.memory;

/** Model-facing outcome of a memory use case: a short text, marked as error when nothing (or not all) happened. */
public final class MemoryResult {

    private final String text;
    private final boolean error;

    private MemoryResult(String text, boolean error) {
        this.text = text;
        this.error = error;
    }

    static MemoryResult ok(String text) {
        return new MemoryResult(text, false);
    }

    static MemoryResult error(String text) {
        return new MemoryResult(text, true);
    }

    public String text() {
        return text;
    }

    public boolean isError() {
        return error;
    }
}
