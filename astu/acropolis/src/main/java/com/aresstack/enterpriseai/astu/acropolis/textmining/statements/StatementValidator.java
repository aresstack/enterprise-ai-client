package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.textmining.TextSpan;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.Context;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.EventCandidate;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.MentionCandidate;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.OrderCandidate;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.ParticipantCandidate;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.RelationCandidate;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementCandidates.Words;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Keeps only candidates that are grounded in the text (issue #108). Every word range must lie in the named sentence
 * and read as the extractor claims (a text found exactly once in that sentence repairs a wrong range; twice is
 * ambiguous and rejected). Prototype rule: participants, property words, context words and an order's cue lie in the
 * sentence of their statement. An event needs a predicate that is a verb if a part-of-speech model can tell. A
 * proposed base form that shares no initial with the words it claims to come from is dropped, not trusted. Pronouns
 * stay unresolved and make their statements {@link ExtractionStatus#UNRESOLVED_REFERENCE}.
 */
public final class StatementValidator {

    /** Version of the rules; part of the extractor version. */
    public static final String VERSION = "1";

    /** Longest word range a term may have; longer "mentions" are sentence fragments. */
    static final int MAX_TERM_WORDS = 12;

    private static final Set<String> PRONOUNS = new HashSet<String>(Arrays.asList("er", "sie", "es", "ihn", "ihm",
            "ihr", "ihnen", "sich", "dieser", "diese", "dieses", "diesen", "diesem", "jener", "jene", "jenes", "man",
            "der", "die", "das", "dem", "den", "welcher", "welche", "welches", "he", "she", "it", "they", "them",
            "this", "that", "these", "those", "which", "who"));

    private final PredicateCheck predicates;

    public StatementValidator(PredicateCheck predicates) {
        this.predicates = predicates == null ? PredicateCheck.none() : predicates;
    }

    /**
     * @param keyPrefix makes the extractor's local keys unique in the document ({@code p3.})
     */
    public Result validate(ParagraphText paragraph, StatementCandidates candidates, String keyPrefix) {
        Result result = new Result();
        result.reject("malformed", candidates.malformed());
        Map<String, Mention> mentions = new LinkedHashMap<String, Mention>();
        Map<String, SentenceText> mentionSentence = new LinkedHashMap<String, SentenceText>();
        Set<String> keys = new HashSet<String>();
        for (MentionCandidate candidate : candidates.mentions()) {
            if (!keys.add(String.valueOf(candidate.key())) || candidate.key() == null) {
                result.reject("duplicate-key");
                continue;
            }
            SentenceText sentence = paragraph.sentence(candidate.sentence());
            GroundedText surface = ground(paragraph, sentence, candidate.words(), result);
            if (surface == null) {
                continue;
            }
            if (wordCount(sentence, surface.span()) > MAX_TERM_WORDS) {
                result.reject("mention-too-long");
                continue;
            }
            Mention same = sameSpan(mentions.values(), surface.span());
            if (same != null) {
                mentions.put(candidate.key(), same); // one occurrence, two keys: keep one mention
                mentionSentence.put(candidate.key(), sentence);
                continue;
            }
            boolean pronoun = candidate.pronoun() || PRONOUNS.contains(fold(surface.text()));
            String lemma = pronoun ? null : checkedLemma(candidate.lemma(), surface.text(), result);
            String normalized = lemma == null ? TermNormalizer.fromSurface(surface.text())
                    : TermNormalizer.fromLemma(lemma);
            if (normalized.isEmpty()) {
                result.reject("empty-term");
                continue;
            }
            Mention mention = new Mention(keyPrefix + candidate.key(), sentence.span(), surface, lemma, normalized,
                    lemma == null ? TermNormalizer.FROM_SURFACE : TermNormalizer.FROM_LEMMA_CANDIDATE, pronoun,
                    pronoun ? ExtractionStatus.UNRESOLVED_REFERENCE : ExtractionStatus.EXTRACTED);
            mentions.put(candidate.key(), mention);
            mentionSentence.put(candidate.key(), sentence);
            result.mentions.add(mention);
        }

        Map<String, Event> events = new LinkedHashMap<String, Event>();
        Map<String, SentenceText> eventSentence = new LinkedHashMap<String, SentenceText>();
        for (EventCandidate candidate : candidates.events()) {
            if (candidate.key() == null || !keys.add(candidate.key())) {
                result.reject("duplicate-key");
                continue;
            }
            SentenceText sentence = paragraph.sentence(candidate.sentence());
            GroundedText verb = ground(paragraph, sentence, candidate.predicate(), result);
            if (verb == null) {
                continue;
            }
            List<GroundedText> predicate = new ArrayList<GroundedText>();
            predicate.add(verb);
            if (candidate.particle() != null && !candidate.particle().text().trim().isEmpty()) {
                GroundedText particle = ground(paragraph, sentence, candidate.particle(), result);
                if (particle == null) {
                    continue;
                }
                if (!particle.span().equals(verb.span())) {
                    predicate.add(particle);
                }
            }
            List<TextSpan> spans = new ArrayList<TextSpan>();
            for (GroundedText part : predicate) {
                spans.add(part.span());
            }
            Boolean verbal = predicates.isVerbal(sentence, spans);
            if (Boolean.FALSE.equals(verbal)) {
                result.reject("predicate-not-verbal");
                continue;
            }
            StatementContext context = context(paragraph, sentence, candidate.context(), result);
            if (context == null) {
                continue;
            }
            List<Participant> participants = participants(candidate.participants(), mentions, mentionSentence,
                    sentence, spans, result);
            if (participants == null) {
                continue;
            }
            String surface = joined(predicate);
            String lemma = checkedLemma(candidate.lemma(), surface, result);
            String normalized = lemma == null ? TermNormalizer.fromSurface(surface) : TermNormalizer.fromLemma(lemma);
            boolean unresolved = false;
            for (Participant participant : participants) {
                unresolved |= participant.mention().status() == ExtractionStatus.UNRESOLVED_REFERENCE;
            }
            Event event = new Event(keyPrefix + candidate.key(), sentence.span(), predicate, lemma, normalized,
                    lemma == null ? TermNormalizer.FROM_SURFACE : TermNormalizer.FROM_LEMMA_CANDIDATE, verbal,
                    participants, context,
                    unresolved ? ExtractionStatus.UNRESOLVED_REFERENCE : ExtractionStatus.EXTRACTED);
            events.put(candidate.key(), event);
            eventSentence.put(candidate.key(), sentence);
            result.events.add(event);
        }

        for (RelationCandidate candidate : candidates.relations()) {
            if (candidate.key() == null || !keys.add(candidate.key())) {
                result.reject("duplicate-key");
                continue;
            }
            RelationKind kind = RelationKind.parse(candidate.kind());
            if (kind == null || kind == RelationKind.BEFORE) {
                result.reject("unknown-relation-kind");
                continue;
            }
            Mention subject = mentions.get(candidate.subject());
            SentenceText sentence = paragraph.sentence(candidate.sentence());
            if (subject == null) {
                result.reject("unknown-mention");
                continue;
            }
            if (sentence == null || mentionSentence.get(candidate.subject()) != sentence) {
                result.reject("other-sentence");
                continue;
            }
            GroundedText value = ground(paragraph, sentence, candidate.value(), result);
            if (value == null) {
                continue;
            }
            StatementContext context = context(paragraph, sentence, candidate.context(), result);
            if (context == null) {
                continue;
            }
            String lemma = checkedLemma(candidate.lemma(), value.text(), result);
            String normalized = lemma == null ? TermNormalizer.fromSurface(value.text())
                    : TermNormalizer.fromLemma(lemma);
            result.relations.add(Relation.property(keyPrefix + candidate.key(), sentence.span(), kind, subject, value,
                    lemma, normalized, lemma == null ? TermNormalizer.FROM_SURFACE
                            : TermNormalizer.FROM_LEMMA_CANDIDATE, context,
                    subject.status() == ExtractionStatus.UNRESOLVED_REFERENCE
                            ? ExtractionStatus.UNRESOLVED_REFERENCE : ExtractionStatus.EXTRACTED));
        }

        int order = 0;
        Set<String> ordered = new HashSet<String>();
        for (OrderCandidate candidate : candidates.orders()) {
            Event before = events.get(candidate.before());
            Event after = events.get(candidate.after());
            if (before == null || after == null || before == after) {
                result.reject("unknown-event");
                continue;
            }
            SentenceText sentence = paragraph.sentence(candidate.sentence());
            if (sentence == null || eventSentence.get(candidate.before()) != sentence
                    || eventSentence.get(candidate.after()) != sentence) {
                result.reject("other-sentence");
                continue;
            }
            GroundedText cue = ground(paragraph, sentence, candidate.cue(), result);
            if (cue == null || !ordered.add(before.key() + ">" + after.key())) {
                continue;
            }
            result.relations.add(Relation.order(keyPrefix + "o" + (++order), sentence.span(), before, after, cue,
                    ExtractionStatus.EXTRACTED));
        }
        return result;
    }

    private List<Participant> participants(List<ParticipantCandidate> candidates, Map<String, Mention> mentions,
                                           Map<String, SentenceText> mentionSentence, SentenceText sentence,
                                           List<TextSpan> predicate, Result result) {
        List<Participant> participants = new ArrayList<Participant>();
        Set<String> seen = new HashSet<String>();
        for (ParticipantCandidate candidate : candidates) {
            ParticipantRole role = ParticipantRole.parse(candidate.role());
            Mention mention = mentions.get(candidate.mention());
            if (role == null) {
                result.reject("unknown-role");
                return null;
            }
            if (mention == null) {
                result.reject("unknown-mention");
                return null;
            }
            if (mentionSentence.get(candidate.mention()) != sentence) {
                // Prototype rule: participants and predicate in one sentence.
                result.reject("other-sentence");
                return null;
            }
            for (TextSpan part : predicate) {
                if (part.overlaps(mention.surface().span())) {
                    result.reject("participant-is-predicate");
                    return null;
                }
            }
            if (seen.add(role + "|" + mention.key())) {
                participants.add(new Participant(role, mention));
            }
        }
        return participants;
    }

    /** The context grounded in the sentence; {@code null} if a given part is not there (then the claim would widen). */
    private StatementContext context(ParagraphText paragraph, SentenceText sentence, Context context, Result result) {
        GroundedText negation = optional(paragraph, sentence, context.negation(), result);
        GroundedText modality = optional(paragraph, sentence, context.modality(), result);
        GroundedText condition = optional(paragraph, sentence, context.condition(), result);
        if (negation == MISSING || modality == MISSING || condition == MISSING) {
            result.reject("ungrounded-context");
            return null;
        }
        return negation == null && modality == null && condition == null ? StatementContext.none()
                : new StatementContext(negation, modality, condition);
    }

    private static final GroundedText MISSING = new GroundedText(new TextSpan(0, 0), "");

    private GroundedText optional(ParagraphText paragraph, SentenceText sentence, Words words, Result result) {
        if (words == null || words.text().trim().isEmpty() && words.first() < 1) {
            return null;
        }
        GroundedText grounded = ground(paragraph, sentence, words, new Result());
        return grounded == null ? MISSING : grounded;
    }

    /**
     * The words in the sentence: the claimed range if it reads as the claimed text, else the only word-aligned
     * occurrence of the text. Counts why it failed.
     */
    static GroundedText ground(ParagraphText paragraph, SentenceText sentence, Words words, Result result) {
        if (sentence == null) {
            result.reject("unknown-sentence");
            return null;
        }
        if (words == null) {
            result.reject("missing-words");
            return null;
        }
        String document = paragraph.documentText();
        String wanted = squash(words.text());
        TextSpan range = words.first() > 0 ? sentence.wordRange(words.first(), words.last()) : null;
        if (range != null && (wanted.isEmpty() || squash(range.of(document)).equals(wanted))) {
            return new GroundedText(range, range.of(document));
        }
        if (wanted.isEmpty()) {
            result.reject("missing-words");
            return null;
        }
        List<TextSpan> hits = new ArrayList<TextSpan>();
        List<TextSpan> all = sentence.words();
        for (int first = 0; first < all.size(); first++) {
            for (int last = first; last < all.size() && last - first < MAX_TERM_WORDS * 2; last++) {
                TextSpan candidate = new TextSpan(all.get(first).start(), all.get(last).end());
                if (squash(candidate.of(document)).equals(wanted)) {
                    hits.add(candidate);
                }
            }
        }
        if (hits.size() == 1) {
            return new GroundedText(hits.get(0), hits.get(0).of(document));
        }
        result.reject(hits.isEmpty() ? "words-not-in-sentence" : "words-ambiguous");
        return null;
    }

    /**
     * The proposed base form if it plausibly comes from {@code source}: every word of it starts like some word there
     * (umlauts folded, a participle's "ge" skipped). Otherwise {@code null}: a model must not introduce a term the
     * text does not carry. A coarse guard, no morphology; the form stays {@link TermNormalizer#LEMMA_STATUS}.
     */
    static String checkedLemma(String lemma, String source, Result result) {
        if (lemma == null || lemma.trim().isEmpty()) {
            return null;
        }
        String value = lemma.trim().replaceAll("\\s+", " ");
        if (value.length() > source.length() * 3 + 10) {
            result.reject("lemma-dropped");
            return null;
        }
        Set<Character> initials = new HashSet<Character>();
        for (String word : fold(source).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                initials.add(word.charAt(0));
            }
            if (word.length() > 4 && word.startsWith("ge")) {
                initials.add(word.charAt(2)); // participle: "geprüft" comes from "prüfen"
            }
        }
        for (String word : fold(value).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty() && !initials.contains(word.charAt(0))) {
                result.reject("lemma-dropped");
                return null;
            }
        }
        return value;
    }

    private static Mention sameSpan(Iterable<Mention> mentions, TextSpan span) {
        for (Mention mention : mentions) {
            if (mention.surface().span().equals(span)) {
                return mention;
            }
        }
        return null;
    }

    private static int wordCount(SentenceText sentence, TextSpan span) {
        int count = 0;
        for (TextSpan word : sentence.words()) {
            if (span.overlaps(word)) {
                count++;
            }
        }
        return count;
    }

    private static String joined(List<GroundedText> parts) {
        StringBuilder text = new StringBuilder();
        for (GroundedText part : parts) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(part.text());
        }
        return text.toString();
    }

    /** Lower case, umlauts and accents folded (for comparing initials and pronouns). */
    static String fold(String text) {
        String lower = text.trim().toLowerCase(Locale.ROOT).replace("ß", "ss");
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /** Text compared for grounding: whitespace collapsed, case and edge punctuation ignored. */
    private static String squash(String text) {
        return TermNormalizer.fromLemma(text == null ? "" : text);
    }

    /** What survived of one paragraph, and why the rest did not. */
    public static final class Result {
        private final List<Mention> mentions = new ArrayList<Mention>();
        private final List<Event> events = new ArrayList<Event>();
        private final List<Relation> relations = new ArrayList<Relation>();
        private final Map<String, Integer> rejections = new TreeMap<String, Integer>();

        public List<Mention> mentions() {
            return Collections.unmodifiableList(mentions);
        }

        public List<Event> events() {
            return Collections.unmodifiableList(events);
        }

        public List<Relation> relations() {
            return Collections.unmodifiableList(relations);
        }

        /** Rejected or reduced candidates by reason (no document text). */
        public Map<String, Integer> rejections() {
            return Collections.unmodifiableMap(rejections);
        }

        void reject(String reason) {
            reject(reason, 1);
        }

        void reject(String reason, int count) {
            if (count > 0) {
                Integer known = rejections.get(reason);
                rejections.put(reason, (known == null ? 0 : known) + count);
            }
        }
    }
}
