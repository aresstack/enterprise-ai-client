package com.aresstack.enterpriseai.proasteion.help;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All help topics of one language, keyed by id. Loaded once and never changed; a format error in any chapter fails
 * the load, so a broken chapter never ships silently (the documentation test runs the same load).
 */
public final class HelpCatalog {

    private final Map<String, HelpTopic> topics;

    private HelpCatalog(Map<String, HelpTopic> topics) {
        this.topics = Collections.unmodifiableMap(topics);
    }

    /**
     * Parses every chapter of {@code documents}.
     *
     * @throws HelpFormatException if a chapter breaks the format, its id does not match its path or ids repeat
     * @throws IOException         if a chapter cannot be read
     */
    public static HelpCatalog load(HelpDocuments documents) throws IOException {
        Map<String, HelpTopic> topics = new LinkedHashMap<String, HelpTopic>();
        for (String name : documents.names()) {
            HelpTopic topic = HelpMarkdown.parse(name, documents.read(name));
            String expected = expectedId(name);
            if (!topic.id().equals(expected)) {
                throw new HelpFormatException(name, "id '" + topic.id() + "' must be '" + expected + "'");
            }
            if (topics.put(topic.id(), topic) != null) {
                throw new HelpFormatException(name, "duplicate id '" + topic.id() + "'");
            }
        }
        return new HelpCatalog(topics);
    }

    /** {@code chat/read-aloud.md → chat.read-aloud}. */
    static String expectedId(String name) {
        String withoutExtension = name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
        return withoutExtension.replace('/', '.');
    }

    /** Topics in load order. */
    public List<HelpTopic> topics() {
        return Collections.unmodifiableList(new ArrayList<HelpTopic>(topics.values()));
    }

    /** Topic by id with or without {@code help:} prefix, or {@code null}. */
    public HelpTopic topic(String id) {
        if (id == null) {
            return null;
        }
        String plain = id.trim();
        if (plain.startsWith(HelpTopic.ID_PREFIX)) {
            plain = plain.substring(HelpTopic.ID_PREFIX.length());
        }
        return topics.get(plain);
    }

    public int size() {
        return topics.size();
    }
}
