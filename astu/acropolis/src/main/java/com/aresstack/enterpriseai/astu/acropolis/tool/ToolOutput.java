package com.aresstack.enterpriseai.astu.acropolis.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The result of one tool execution: the text for the model and the knowledge resources it delivered as context. */
public final class ToolOutput {

    private final String text;
    private final List<ToolContextResource> context;

    public ToolOutput(String text, List<ToolContextResource> context) {
        this.text = text == null ? "" : text;
        List<ToolContextResource> copy = new ArrayList<ToolContextResource>();
        if (context != null) {
            for (ToolContextResource resource : context) {
                if (resource == null) {
                    throw new IllegalArgumentException("context must not contain null");
                }
                copy.add(resource);
            }
        }
        this.context = Collections.unmodifiableList(copy);
    }

    /** Output without context resources. */
    public static ToolOutput text(String text) {
        return new ToolOutput(text, null);
    }

    public String text() {
        return text;
    }

    /** The resources whose content went to the model; empty when the tool delivered none. */
    public List<ToolContextResource> context() {
        return context;
    }
}
