package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.textmining.TextSpan;

/**
 * One concrete occurrence of a noun group in one document version (issue #108): the exact words with their span, the
 * proposed base form (unverified) and the search value with its method. Not a concept: two mentions with the same
 * value stay two mentions. A multi-word term is one mention.
 */
public final class Mention {

    private final String key;
    private final TextSpan sentence;
    private final GroundedText surface;
    private final String lemmaCandidate;
    private final String normalized;
    private final String normalization;
    private final boolean pronoun;
    private final ExtractionStatus status;

    public Mention(String key, TextSpan sentence, GroundedText surface, String lemmaCandidate, String normalized,
                   String normalization, boolean pronoun, ExtractionStatus status) {
        this.key = key;
        this.sentence = sentence;
        this.surface = surface;
        this.lemmaCandidate = lemmaCandidate;
        this.normalized = normalized;
        this.normalization = normalization;
        this.pronoun = pronoun;
        this.status = status;
    }

    /** Key within the analysed document. */
    public String key() {
        return key;
    }

    /** Span of the sentence the mention is in. */
    public TextSpan sentence() {
        return sentence;
    }

    public GroundedText surface() {
        return surface;
    }

    /** Proposed base form ({@link TermNormalizer#LEMMA_STATUS}), or {@code null}. */
    public String lemmaCandidate() {
        return lemmaCandidate;
    }

    /** Search value; never an identity. */
    public String normalized() {
        return normalized;
    }

    /** How {@link #normalized()} was made ({@link TermNormalizer#FROM_LEMMA_CANDIDATE} or ...SURFACE). */
    public String normalization() {
        return normalization;
    }

    /** A pronoun or other reference whose referent is not resolved. */
    public boolean pronoun() {
        return pronoun;
    }

    public ExtractionStatus status() {
        return status;
    }

    @Override
    public String toString() {
        return "Mention[" + key + " " + surface + " -> " + normalized + ", " + status + "]";
    }
}
