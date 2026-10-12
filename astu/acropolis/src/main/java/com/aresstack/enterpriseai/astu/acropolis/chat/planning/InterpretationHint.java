package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

/** One change the answer planning step made to the wording: {@code original} was probably meant as {@code interpretedAs}. */
public final class InterpretationHint {

    private final String original;
    private final String interpretedAs;
    private final String reason;

    public InterpretationHint(String original, String interpretedAs, String reason) {
        this.original = original == null ? "" : original;
        this.interpretedAs = interpretedAs == null ? "" : interpretedAs;
        this.reason = reason == null ? "" : reason;
    }

    public String original() {
        return original;
    }

    public String interpretedAs() {
        return interpretedAs;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return "\"" + original + "\" -> \"" + interpretedAs + "\"" + (reason.isEmpty() ? "" : " (" + reason + ")");
    }
}
