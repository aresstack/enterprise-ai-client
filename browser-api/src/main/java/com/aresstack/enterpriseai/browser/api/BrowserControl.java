package com.aresstack.enterpriseai.browser.api;

/**
 * One operable element of a page snapshot that is not a plain link: a search or text field, a button, a select box,
 * a menu entry, a tab, a toggle. {@code kind} is one of {@code search}, {@code input}, {@code button}, {@code select},
 * {@code checkbox}, {@code radio}, {@code menu}, {@code tab} and {@code toggle}. {@code target} is the address a form
 * submit goes to when the page declares one, else empty.
 */
public final class BrowserControl {

    private final String id;
    private final String kind;
    private final String label;
    private final String value;
    private final String options;
    private final String target;

    public BrowserControl(String id, String kind, String label, String value, String options, String target) {
        this.id = id == null ? "" : id;
        this.kind = kind == null ? "" : kind;
        this.label = label == null ? "" : label;
        this.value = value == null ? "" : value;
        this.options = options == null ? "" : options;
        this.target = target == null ? "" : target;
    }

    public String id() {
        return id;
    }

    public String kind() {
        return kind;
    }

    public String label() {
        return label;
    }

    /** The current value of a field or the state of a toggle ({@code an}/{@code aus}), else empty. */
    public String value() {
        return value;
    }

    /** The visible options of a select box, separated by {@code " | "}, else empty. */
    public String options() {
        return options;
    }

    public String target() {
        return target;
    }
}
