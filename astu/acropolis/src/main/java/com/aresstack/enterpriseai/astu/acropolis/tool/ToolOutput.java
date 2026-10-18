package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.astu.chat.ChatImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of one tool execution: the text for the model, the knowledge resources it delivered as context and the
 * images it produced (a screenshot); the text describes the images for models that cannot read them.
 */
public final class ToolOutput {

    private final String text;
    private final List<ToolContextResource> context;
    private final List<ChatImage> images;

    public ToolOutput(String text, List<ToolContextResource> context) {
        this(text, context, null);
    }

    public ToolOutput(String text, List<ToolContextResource> context, List<ChatImage> images) {
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
        List<ChatImage> pictures = new ArrayList<ChatImage>();
        if (images != null) {
            for (ChatImage image : images) {
                if (image == null) {
                    throw new IllegalArgumentException("images must not contain null");
                }
                pictures.add(image);
            }
        }
        this.images = Collections.unmodifiableList(pictures);
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

    /** The images of the result; empty for most tools. */
    public List<ChatImage> images() {
        return images;
    }
}
