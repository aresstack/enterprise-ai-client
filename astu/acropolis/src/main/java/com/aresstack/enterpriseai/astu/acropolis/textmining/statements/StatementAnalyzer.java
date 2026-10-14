package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.passage.SentenceSegmenter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The sentence analysis of issue #108 without storage: split the canonical text into paragraphs and sentences,
 * let the {@link StatementExtractor} propose per paragraph, keep what {@link StatementValidator} accepts, and put each
 * remaining event and relation to the optional yes/no check. Claims the check denies are dropped (with any order that
 * needs them); undecided ones stay as {@link ExtractionStatus#UNCERTAIN}; unresolved references are not checked.
 * Blocks on model calls: off the Swing event thread and outside storage transactions.
 */
public final class StatementAnalyzer {

    /** Extractor id in the metadata catalog. */
    public static final String ID = "sentence-statements";
    /** Version of the pipeline itself. */
    public static final String VERSION = "1";

    private final SentenceSegmenter segmenter;
    private final StatementExtractor extractor;
    private final PredicateCheck predicates;
    private final StatementValidator validator;
    private final StatementVerifier verifier;

    public StatementAnalyzer(SentenceSegmenter segmenter, StatementExtractor extractor, PredicateCheck predicates,
                             StatementVerifier verifier) {
        if (segmenter == null || extractor == null) {
            throw new IllegalArgumentException("segmenter and extractor must not be null");
        }
        this.segmenter = segmenter;
        this.extractor = extractor;
        this.predicates = predicates == null ? PredicateCheck.none() : predicates;
        this.validator = new StatementValidator(this.predicates);
        this.verifier = verifier == null ? StatementVerifier.none() : verifier;
    }

    public boolean isAvailable() {
        return extractor.isAvailable();
    }

    /** Everything that changes the result: pipeline, extractor, check, sentence and word-class models, limits. */
    public String version(StatementSettings settings) {
        return VERSION + ";extractor=" + extractor.version() + ";check="
                + (settings.verify() ? verifier.version() : "none") + ";sentences=" + segmenter.id() + ";pos="
                + predicates.id() + ";rules=" + StatementValidator.VERSION + ";terms=" + TermNormalizer.VERSION
                + ";paragraphs=" + settings.maxParagraphs() + "/" + settings.maxParagraphChars();
    }

    /** Analyse a whole document text. */
    public DocumentStatements analyze(String documentText, StatementSettings settings) {
        List<ParagraphText> units = ParagraphText.split(documentText, segmenter, settings.maxParagraphChars());
        int skipped = Math.max(0, units.size() - settings.maxParagraphs());
        return analyze(units.subList(0, units.size() - skipped), skipped, settings.verify());
    }

    /** Analyse given units (the evaluation passes one reference sentence per unit). */
    public DocumentStatements analyze(List<ParagraphText> units, int skipped, boolean verify) {
        Collector all = new Collector();
        int analysed = 0;
        int failed = 0;
        for (ParagraphText unit : units) {
            StatementCandidates candidates;
            try {
                candidates = extractor.extract(unit);
            } catch (StatementExtractionException e) {
                failed++;
                continue;
            }
            analysed++;
            StatementValidator.Result result = validator.validate(unit, candidates, "p" + unit.index() + ".");
            all.add(result);
            if (verify && verifier.isEnabled()) {
                check(unit, result, all);
            } else {
                all.events.addAll(result.events());
                all.relations.addAll(result.relations());
            }
        }
        return new DocumentStatements(all.mentions, all.events, all.relations, analysed, failed, skipped,
                all.rejections, all.verdicts);
    }

    private void check(ParagraphText unit, StatementValidator.Result result, Collector all) {
        Set<String> dropped = new HashSet<String>();
        Map<String, Event> kept = new TreeMap<String, Event>();
        String text = unit.documentText();
        for (Event event : result.events()) {
            if (event.status() != ExtractionStatus.EXTRACTED) {
                all.events.add(event);
                kept.put(event.key(), event);
                continue;
            }
            Event checked = apply(event, verdict(event.sentence().of(text), claim(event), all));
            if (checked == null) {
                dropped.add(event.key());
            } else {
                all.events.add(checked);
                kept.put(checked.key(), checked);
            }
        }
        for (Relation relation : result.relations()) {
            if (relation.kind() == RelationKind.BEFORE) {
                if (dropped.contains(relation.subjectEvent().key()) || dropped.contains(relation.objectEvent().key())) {
                    all.reject("order-without-event");
                    continue;
                }
                relation = Relation.order(relation.key(), relation.sentence(), kept.get(relation.subjectEvent().key()),
                        kept.get(relation.objectEvent().key()), relation.literal(), relation.status());
            }
            if (relation.status() != ExtractionStatus.EXTRACTED) {
                all.relations.add(relation);
                continue;
            }
            StatementVerifier.Verdict verdict = verdict(relation.sentence().of(text), claim(relation), all);
            if (verdict != StatementVerifier.Verdict.NO) {
                all.relations.add(relation.withStatus(status(verdict, relation.status())));
            }
        }
    }

    private Event apply(Event event, StatementVerifier.Verdict verdict) {
        return verdict == StatementVerifier.Verdict.NO ? null : event.withStatus(status(verdict, event.status()));
    }

    private StatementVerifier.Verdict verdict(String sentence, String claim, Collector all) {
        StatementVerifier.Verdict verdict = verifier.verify(sentence, claim);
        Integer count = all.verdicts.get(verdict);
        all.verdicts.put(verdict, count == null ? 1 : count + 1);
        if (verdict == StatementVerifier.Verdict.NO) {
            all.reject("check-denied");
        }
        return verdict;
    }

    private static ExtractionStatus status(StatementVerifier.Verdict verdict, ExtractionStatus current) {
        switch (verdict) {
            case YES:
                return ExtractionStatus.MODEL_VALIDATED;
            case UNSURE:
                return ExtractionStatus.UNCERTAIN;
            default:
                return current;
        }
    }

    /** The claim of an event in plain words, as the check sees it. */
    static String claim(Event event) {
        StringBuilder claim = new StringBuilder("Handlung: ").append(event.lemmaCandidate() != null
                ? event.lemmaCandidate() : event.predicateText()).append('.');
        for (Participant participant : event.participants()) {
            claim.append(' ').append(label(participant.role())).append(": ")
                    .append(participant.mention().surface().text()).append('.');
        }
        return claim.append(context(event.context())).toString();
    }

    /** The claim of a relation in plain words, as the check sees it. */
    static String claim(Relation relation) {
        if (relation.kind() == RelationKind.BEFORE) {
            return "Die Handlung „" + relation.subjectEvent().predicateText() + "“ geschieht vor der Handlung „"
                    + relation.objectEvent().predicateText() + "“.";
        }
        return relation.subjectMention().surface().text() + " hat "
                + (relation.kind() == RelationKind.STATE ? "den Zustand" : "die Eigenschaft") + " „"
                + relation.literal().text() + "“." + context(relation.context());
    }

    private static String context(StatementContext context) {
        StringBuilder text = new StringBuilder();
        if (context.negation() != null) {
            text.append(" Verneint durch: ").append(context.negation().text()).append('.');
        }
        if (context.modality() != null) {
            text.append(" Modalität: ").append(context.modality().text()).append('.');
        }
        if (context.condition() != null) {
            text.append(" Nur unter der Bedingung: ").append(context.condition().text()).append('.');
        }
        return text.toString();
    }

    private static String label(ParticipantRole role) {
        switch (role) {
            case ACTOR:
                return "Wer handelt";
            case AFFECTED_OBJECT:
                return "Worauf sich die Handlung richtet";
            case RECIPIENT:
                return "Wer etwas erhält";
            case INSTRUMENT:
                return "Womit";
            default:
                return "Wo";
        }
    }

    /** Collects the results of all units. */
    private static final class Collector {
        final List<Mention> mentions = new ArrayList<Mention>();
        final List<Event> events = new ArrayList<Event>();
        final List<Relation> relations = new ArrayList<Relation>();
        final Map<String, Integer> rejections = new TreeMap<String, Integer>();
        final Map<StatementVerifier.Verdict, Integer> verdicts =
                new EnumMap<StatementVerifier.Verdict, Integer>(StatementVerifier.Verdict.class);

        void add(StatementValidator.Result result) {
            mentions.addAll(result.mentions());
            for (Map.Entry<String, Integer> entry : result.rejections().entrySet()) {
                reject(entry.getKey(), entry.getValue());
            }
        }

        void reject(String reason) {
            reject(reason, 1);
        }

        void reject(String reason, int count) {
            Integer known = rejections.get(reason);
            rejections.put(reason, (known == null ? 0 : known) + count);
        }
    }
}
