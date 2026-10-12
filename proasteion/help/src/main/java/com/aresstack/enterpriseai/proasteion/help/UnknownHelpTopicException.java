package com.aresstack.enterpriseai.proasteion.help;

/** No help topic (or no section of an existing topic) has the requested id. */
public final class UnknownHelpTopicException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient HelpTopic topic;

    UnknownHelpTopicException(String id, HelpTopic topic) {
        super("unknown help id: " + id);
        this.topic = topic;
    }

    /** The topic when only the anchor was unknown, otherwise {@code null}. */
    public HelpTopic topic() {
        return topic;
    }
}
