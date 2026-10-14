package com.aresstack.enterpriseai.astu.acropolis.textmining;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceRef;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Analyses the canonical text of a document for acronyms (issue #107) and keeps the findings at the stable resource
 * id: unchanged content with the same extractor state is not written again; new content or other rules replace the
 * automatic findings of this extractor in one step. Blocks (sentence model, storage): call it off the Swing event
 * thread. Storing can fail without losing the analysis.
 */
public final class DocumentAcronymsUseCase {

    /** What happened to the findings. */
    public enum Storage {
        /** Written (or replaced). */
        STORED,
        /** Already stored for this content and extractor state. */
        UNCHANGED,
        /** Storing is switched off. */
        DISABLED,
        /** The sink rejected them; see {@link Outcome#problem()}. */
        FAILED
    }

    /** The analysis and what happened to its findings. */
    public static final class Outcome {
        private final AcronymAnalysis analysis;
        private final Storage storage;
        private final String problem;

        Outcome(AcronymAnalysis analysis, Storage storage, String problem) {
            this.analysis = analysis;
            this.storage = storage;
            this.problem = problem == null ? "" : problem;
        }

        public AcronymAnalysis analysis() {
            return analysis;
        }

        public Storage storage() {
            return storage;
        }

        /** Why storing failed, or {@code ""}. Never contains document text. */
        public String problem() {
            return problem;
        }
    }

    private final AcronymExtractor extractor;
    private final Supplier<AcronymSettings> settings;
    private final TextFindingSink sink;
    private final BooleanSupplier storing;

    /**
     * @param settings read for every document
     * @param storing  whether findings are kept (read for every document)
     */
    public DocumentAcronymsUseCase(AcronymExtractor extractor, Supplier<AcronymSettings> settings,
                                   TextFindingSink sink, BooleanSupplier storing) {
        if (extractor == null || settings == null || sink == null || storing == null) {
            throw new IllegalArgumentException("extractor, settings, sink and storing must not be null");
        }
        this.extractor = extractor;
        this.settings = settings;
        this.sink = sink;
        this.storing = storing;
    }

    /** Analyse {@code document} and keep its findings. */
    public Outcome analyze(KnowledgeDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        AcronymSettings rules = settings.get();
        if (rules == null) {
            rules = AcronymSettings.defaults();
        }
        AcronymAnalysis analysis = extractor.analyze(document.text(), rules);
        if (!storing.getAsBoolean()) {
            return new Outcome(analysis, Storage.DISABLED, null);
        }
        KnowledgeResourceRef resource = KnowledgeResourceRef.of(document);
        String version = extractorVersion(rules, analysis.sentenceMethod());
        try {
            if (sink.isCurrent(resource, AcronymExtractor.ID, version)) {
                return new Outcome(analysis, Storage.UNCHANGED, null);
            }
            sink.replace(resource, AcronymExtractor.ID, version, AcronymFindings.types(),
                    AcronymFindings.of(analysis, document.text()));
            return new Outcome(analysis, Storage.STORED, null);
        } catch (RuntimeException failed) {
            return new Outcome(analysis, Storage.FAILED, failed.getClass().getSimpleName()
                    + (failed.getMessage() == null ? "" : ": " + failed.getMessage()));
        }
    }

    /**
     * The extractor state that produced the findings: rule version, thresholds and sentence method. Another state
     * replaces the stored findings even if the content is unchanged.
     */
    static String extractorVersion(AcronymSettings rules, String sentenceMethod) {
        return AcronymExtractor.VERSION + ";letters=" + rules.maxLetters() + ";shouting=" + rules.shoutingPercent()
                + "/" + rules.shoutingMinWords() + ";sentences=" + sentenceMethod;
    }
}
