package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.textmining.TextSpan;

/**
 * A grounded statement about a mention or between events (issue #108): a property with a literal from the text
 * ({@link RelationKind#ATTRIBUTE}, {@link RelationKind#STATE}), or the order of two events by a cue word
 * ({@link RelationKind#BEFORE}). Bound to its sentence and context; never a general truth.
 */
public final class Relation {

    private final String key;
    private final TextSpan sentence;
    private final RelationKind kind;
    private final Mention subjectMention;
    private final Event subjectEvent;
    private final Event objectEvent;
    private final GroundedText literal;
    private final String lemmaCandidate;
    private final String normalized;
    private final String normalization;
    private final StatementContext context;
    private final ExtractionStatus status;

    private Relation(String key, TextSpan sentence, RelationKind kind, Mention subjectMention, Event subjectEvent,
                     Event objectEvent, GroundedText literal, String lemmaCandidate, String normalized,
                     String normalization, StatementContext context, ExtractionStatus status) {
        this.key = key;
        this.sentence = sentence;
        this.kind = kind;
        this.subjectMention = subjectMention;
        this.subjectEvent = subjectEvent;
        this.objectEvent = objectEvent;
        this.literal = literal;
        this.lemmaCandidate = lemmaCandidate;
        this.normalized = normalized;
        this.normalization = normalization;
        this.context = context == null ? StatementContext.none() : context;
        this.status = status;
    }

    /** A property of {@code subject} given by {@code literal} words of the same sentence. */
    public static Relation property(String key, TextSpan sentence, RelationKind kind, Mention subject,
                                    GroundedText literal, String lemmaCandidate, String normalized,
                                    String normalization, StatementContext context, ExtractionStatus status) {
        return new Relation(key, sentence, kind, subject, null, null, literal, lemmaCandidate, normalized,
                normalization, context, status);
    }

    /** {@code before} happens before {@code after}, by the cue words {@code cue}. */
    public static Relation order(String key, TextSpan sentence, Event before, Event after, GroundedText cue,
                                 ExtractionStatus status) {
        return new Relation(key, sentence, RelationKind.BEFORE, null, before, after, cue, null,
                TermNormalizer.fromSurface(cue.text()), TermNormalizer.FROM_SURFACE, StatementContext.none(), status);
    }

    public String key() {
        return key;
    }

    public TextSpan sentence() {
        return sentence;
    }

    public RelationKind kind() {
        return kind;
    }

    /** The mention a property is about, or {@code null} for an order. */
    public Mention subjectMention() {
        return subjectMention;
    }

    /** The earlier event of an order, or {@code null}. */
    public Event subjectEvent() {
        return subjectEvent;
    }

    /** The later event of an order, or {@code null}. */
    public Event objectEvent() {
        return objectEvent;
    }

    /** The property words, or the cue words of an order. */
    public GroundedText literal() {
        return literal;
    }

    public String lemmaCandidate() {
        return lemmaCandidate;
    }

    public String normalized() {
        return normalized;
    }

    public String normalization() {
        return normalization;
    }

    public StatementContext context() {
        return context;
    }

    public ExtractionStatus status() {
        return status;
    }

    /** The same relation with another status. */
    public Relation withStatus(ExtractionStatus value) {
        return new Relation(key, sentence, kind, subjectMention, subjectEvent, objectEvent, literal, lemmaCandidate,
                normalized, normalization, context, value);
    }

    @Override
    public String toString() {
        return "Relation[" + key + " " + kind + " " + (subjectMention != null ? subjectMention.key()
                : subjectEvent.key() + "<" + objectEvent.key()) + " " + normalized + ", " + status + "]";
    }
}
