package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.textmining.TextSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One action described in one sentence (issue #108): the verbal predicate (one or two word groups for a separable
 * verb), its proposed base form and search value, the participants with their semantic roles, and the context it
 * holds in. Stored once; found by any participant.
 */
public final class Event {

    private final String key;
    private final TextSpan sentence;
    private final List<GroundedText> predicate;
    private final String lemmaCandidate;
    private final String normalized;
    private final String normalization;
    private final Boolean verbal;
    private final List<Participant> participants;
    private final StatementContext context;
    private final ExtractionStatus status;

    /**
     * @param verbal whether a part-of-speech model confirmed a verb, or {@code null} without such a model
     */
    public Event(String key, TextSpan sentence, List<GroundedText> predicate, String lemmaCandidate,
                 String normalized, String normalization, Boolean verbal, List<Participant> participants,
                 StatementContext context, ExtractionStatus status) {
        this.key = key;
        this.sentence = sentence;
        this.predicate = Collections.unmodifiableList(new ArrayList<GroundedText>(predicate));
        this.lemmaCandidate = lemmaCandidate;
        this.normalized = normalized;
        this.normalization = normalization;
        this.verbal = verbal;
        this.participants = Collections.unmodifiableList(new ArrayList<Participant>(participants));
        this.context = context == null ? StatementContext.none() : context;
        this.status = status;
    }

    public String key() {
        return key;
    }

    public TextSpan sentence() {
        return sentence;
    }

    /** The predicate words: the verb, then a separated particle if any. */
    public List<GroundedText> predicate() {
        return predicate;
    }

    /** The predicate as written, parts joined by " … ". */
    public String predicateText() {
        StringBuilder text = new StringBuilder();
        for (GroundedText part : predicate) {
            if (text.length() > 0) {
                text.append(" … ");
            }
            text.append(part.text());
        }
        return text.toString();
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

    /** {@code true}/{@code false} by the part-of-speech model, {@code null} if none was available. */
    public Boolean verbal() {
        return verbal;
    }

    public List<Participant> participants() {
        return participants;
    }

    /** The mentions in {@code role}, in order. */
    public List<Mention> participants(ParticipantRole role) {
        List<Mention> mentions = new ArrayList<Mention>();
        for (Participant participant : participants) {
            if (participant.role() == role) {
                mentions.add(participant.mention());
            }
        }
        return mentions;
    }

    public StatementContext context() {
        return context;
    }

    public ExtractionStatus status() {
        return status;
    }

    /** The same event with another status. */
    public Event withStatus(ExtractionStatus value) {
        return new Event(key, sentence, predicate, lemmaCandidate, normalized, normalization, verbal, participants,
                context, value);
    }

    @Override
    public String toString() {
        return "Event[" + key + " " + normalized + " " + participants + ", " + status + "]";
    }
}
