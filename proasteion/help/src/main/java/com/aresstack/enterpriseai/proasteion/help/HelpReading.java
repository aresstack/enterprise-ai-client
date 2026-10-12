package com.aresstack.enterpriseai.proasteion.help;

/** What {@code app_help_read} returns: a whole topic, or one section of it when the id named an anchor. */
public final class HelpReading {

    private final HelpTopic topic;
    private final HelpSection section;

    HelpReading(HelpTopic topic, HelpSection section) {
        this.topic = topic;
        this.section = section;
    }

    public HelpTopic topic() {
        return topic;
    }

    /** The requested section, or {@code null} for the whole topic. */
    public HelpSection section() {
        return section;
    }

    public boolean isSection() {
        return section != null;
    }
}
