package com.aresstack.enterpriseai.browser.api.engine;

/**
 * One control of a rendered page as the engine read it. {@code ref} is the engine's handle of the element within the
 * current document; the other fields mean the same as in {@code BrowserControl}.
 */
public final class RawControl {

    private final String ref;
    private final String kind;
    private final String label;
    private final String value;
    private final String options;
    private final String target;

    public RawControl(String ref, String kind, String label, String value, String options, String target) {
        this.ref = ref == null ? "" : ref;
        this.kind = kind == null ? "" : kind;
        this.label = label == null ? "" : label;
        this.value = value == null ? "" : value;
        this.options = options == null ? "" : options;
        this.target = target == null ? "" : target;
    }

    public String ref() {
        return ref;
    }

    public String kind() {
        return kind;
    }

    public String label() {
        return label;
    }

    public String value() {
        return value;
    }

    public String options() {
        return options;
    }

    public String target() {
        return target;
    }
}
