package com.aresstack.enterpriseai.astu.acropolis.chat.linguistics;

/** One token of a message with the part-of-speech tag of the local model and its probability. */
public final class LinguisticToken {

    /** A tag below this probability is more likely wrong than right. */
    public static final double UNCERTAIN_BELOW = 0.5;

    private final String text;
    private final String tag;
    private final double probability;

    /**
     * @param probability the tagger's probability, or a negative value when unknown
     */
    public LinguisticToken(String text, String tag, double probability) {
        this.text = text == null ? "" : text;
        this.tag = tag == null ? "" : tag;
        this.probability = probability;
    }

    public String text() {
        return text;
    }

    /** The model's tag (e.g. STTS {@code NN}, {@code VVIMP}). */
    public String tag() {
        return tag;
    }

    /** The tagger's probability, negative when unknown. */
    public double probability() {
        return probability;
    }

    /** Whether the tagger was unsure about this token. */
    public boolean isUncertain() {
        return probability >= 0 && probability < UNCERTAIN_BELOW;
    }

    @Override
    public String toString() {
        return text + "/" + tag + (probability >= 0 ? String.format(java.util.Locale.ROOT, "(%.2f)", probability) : "");
    }
}
