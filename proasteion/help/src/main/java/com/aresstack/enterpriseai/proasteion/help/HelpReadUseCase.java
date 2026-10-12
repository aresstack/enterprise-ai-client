package com.aresstack.enterpriseai.proasteion.help;

/**
 * Reads a help topic by id: {@code help:chat.attachments}, {@code chat.attachments} or with an anchor
 * {@code help:chat.attachments#unterstuetzte-dateien} for a single section.
 */
public final class HelpReadUseCase {

    private final HelpCatalog catalog;

    public HelpReadUseCase(HelpCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog is required");
        }
        this.catalog = catalog;
    }

    /**
     * @throws UnknownHelpTopicException if no topic has the id, or the topic has no section with the anchor
     */
    public HelpReading read(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new UnknownHelpTopicException("", null);
        }
        String trimmed = id.trim();
        int hash = trimmed.indexOf('#');
        String topicId = hash < 0 ? trimmed : trimmed.substring(0, hash);
        String anchor = hash < 0 ? "" : trimmed.substring(hash + 1).trim();
        HelpTopic topic = catalog.topic(topicId);
        if (topic == null) {
            throw new UnknownHelpTopicException(trimmed, null);
        }
        if (anchor.isEmpty()) {
            return new HelpReading(topic, null);
        }
        HelpSection section = topic.section(anchor);
        if (section == null) {
            section = topic.section(HelpMarkdown.slug(anchor));
        }
        if (section == null) {
            throw new UnknownHelpTopicException(trimmed, topic);
        }
        return new HelpReading(topic, section);
    }

    public HelpCatalog catalog() {
        return catalog;
    }
}
