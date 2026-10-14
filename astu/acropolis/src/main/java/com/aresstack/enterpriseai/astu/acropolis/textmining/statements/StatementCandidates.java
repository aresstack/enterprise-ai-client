package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a {@link StatementExtractor} proposes for one {@link ParagraphText}, before any check: local keys, sentence
 * numbers and word ranges as the extractor gave them. Nothing here is trusted; {@link StatementValidator} decides.
 */
public final class StatementCandidates {

    private final List<MentionCandidate> mentions;
    private final List<EventCandidate> events;
    private final List<RelationCandidate> relations;
    private final List<OrderCandidate> orders;
    private final int malformed;

    public StatementCandidates(List<MentionCandidate> mentions, List<EventCandidate> events,
                               List<RelationCandidate> relations, List<OrderCandidate> orders, int malformed) {
        this.mentions = copy(mentions);
        this.events = copy(events);
        this.relations = copy(relations);
        this.orders = copy(orders);
        this.malformed = Math.max(0, malformed);
    }

    public static StatementCandidates empty() {
        return new StatementCandidates(null, null, null, null, 0);
    }

    public List<MentionCandidate> mentions() {
        return mentions;
    }

    public List<EventCandidate> events() {
        return events;
    }

    public List<RelationCandidate> relations() {
        return relations;
    }

    public List<OrderCandidate> orders() {
        return orders;
    }

    /** Entries the extractor gave in an unreadable shape (dropped while reading). */
    public int malformed() {
        return malformed;
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? Collections.<T>emptyList() : Collections.unmodifiableList(new ArrayList<T>(values));
    }

    /** A word range in one sentence: 1-based, inclusive, with the text the extractor read there. */
    public static final class Words {
        private final int first;
        private final int last;
        private final String text;

        /** @param first 1-based first word, or {@code 0} if only the text is known */
        public Words(int first, int last, String text) {
            this.first = first;
            this.last = last;
            this.text = text == null ? "" : text;
        }

        public int first() {
            return first;
        }

        public int last() {
            return last;
        }

        public String text() {
            return text;
        }

        @Override
        public String toString() {
            return first + "-" + last + ":" + text;
        }
    }

    /** Negation, modality and condition of a statement, each as words of its sentence ({@code null}: none). */
    public static final class Context {
        private final Words negation;
        private final Words modality;
        private final Words condition;

        public Context(Words negation, Words modality, Words condition) {
            this.negation = negation;
            this.modality = modality;
            this.condition = condition;
        }

        public static Context none() {
            return new Context(null, null, null);
        }

        public Words negation() {
            return negation;
        }

        public Words modality() {
            return modality;
        }

        public Words condition() {
            return condition;
        }
    }

    /** A noun group (or pronoun) the extractor found. */
    public static final class MentionCandidate {
        private final String key;
        private final int sentence;
        private final Words words;
        private final String lemma;
        private final boolean pronoun;

        public MentionCandidate(String key, int sentence, Words words, String lemma, boolean pronoun) {
            this.key = key;
            this.sentence = sentence;
            this.words = words;
            this.lemma = lemma;
            this.pronoun = pronoun;
        }

        public String key() {
            return key;
        }

        public int sentence() {
            return sentence;
        }

        public Words words() {
            return words;
        }

        /** Proposed base form, or {@code null}. */
        public String lemma() {
            return lemma;
        }

        public boolean pronoun() {
            return pronoun;
        }
    }

    /** One participant of an event: a role and the key of a mention. */
    public static final class ParticipantCandidate {
        private final String role;
        private final String mention;

        public ParticipantCandidate(String role, String mention) {
            this.role = role;
            this.mention = mention;
        }

        public String role() {
            return role;
        }

        public String mention() {
            return mention;
        }
    }

    /** An action with its predicate (and the separated particle of a separable verb). */
    public static final class EventCandidate {
        private final String key;
        private final int sentence;
        private final Words predicate;
        private final Words particle;
        private final String lemma;
        private final List<ParticipantCandidate> participants;
        private final Context context;

        public EventCandidate(String key, int sentence, Words predicate, Words particle, String lemma,
                              List<ParticipantCandidate> participants, Context context) {
            this.key = key;
            this.sentence = sentence;
            this.predicate = predicate;
            this.particle = particle;
            this.lemma = lemma;
            this.participants = copy(participants);
            this.context = context == null ? Context.none() : context;
        }

        public String key() {
            return key;
        }

        public int sentence() {
            return sentence;
        }

        public Words predicate() {
            return predicate;
        }

        /** Separated verb particle ("aus" in "löst ... aus"), or {@code null}. */
        public Words particle() {
            return particle;
        }

        public String lemma() {
            return lemma;
        }

        public List<ParticipantCandidate> participants() {
            return participants;
        }

        public Context context() {
            return context;
        }
    }

    /** A property of a mention, given by words of the same sentence. */
    public static final class RelationCandidate {
        private final String key;
        private final int sentence;
        private final String kind;
        private final String subject;
        private final Words value;
        private final String lemma;
        private final Context context;

        public RelationCandidate(String key, int sentence, String kind, String subject, Words value, String lemma,
                                 Context context) {
            this.key = key;
            this.sentence = sentence;
            this.kind = kind;
            this.subject = subject;
            this.value = value;
            this.lemma = lemma;
            this.context = context == null ? Context.none() : context;
        }

        public String key() {
            return key;
        }

        public int sentence() {
            return sentence;
        }

        public String kind() {
            return kind;
        }

        public String subject() {
            return subject;
        }

        public Words value() {
            return value;
        }

        public String lemma() {
            return lemma;
        }

        public Context context() {
            return context;
        }
    }

    /** One event before another, by a cue word of the sentence of the later one. */
    public static final class OrderCandidate {
        private final String before;
        private final String after;
        private final int sentence;
        private final Words cue;

        public OrderCandidate(String before, String after, int sentence, Words cue) {
            this.before = before;
            this.after = after;
            this.sentence = sentence;
            this.cue = cue;
        }

        public String before() {
            return before;
        }

        public String after() {
            return after;
        }

        public int sentence() {
            return sentence;
        }

        public Words cue() {
            return cue;
        }
    }
}
