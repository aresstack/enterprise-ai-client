package com.aresstack.enterpriseai.proasteion.help;

/** One search hit: the best matching section of a topic, its relative score (best hit = 1.0) and a snippet. */
public final class HelpHit {

    private final HelpTopic topic;
    private final HelpSection section;
    private final double score;
    private final String snippet;

    HelpHit(HelpTopic topic, HelpSection section, double score, String snippet) {
        this.topic = topic;
        this.section = section;
        this.score = score;
        this.snippet = snippet;
    }

    public HelpTopic topic() {
        return topic;
    }

    public HelpSection section() {
        return section;
    }

    /** Score relative to the best hit of the same search, between 0 and 1. */
    public double score() {
        return score;
    }

    /** Plain-text excerpt of the section around the first matching word. */
    public String snippet() {
        return snippet;
    }

    /** {@code help:<id>} for the introduction, otherwise {@code help:<id>#<anchor>}. */
    public String sectionId() {
        return section.isIntroduction() ? topic.qualifiedId() : topic.qualifiedId() + "#" + section.anchor();
    }
}
