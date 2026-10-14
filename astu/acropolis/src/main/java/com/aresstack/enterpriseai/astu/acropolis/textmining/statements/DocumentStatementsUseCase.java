package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceRef;

import java.util.function.Supplier;

/**
 * Analyses one document for mentions, relations and events (issue #108) and keeps them at its stable resource id.
 * Off unless switched on; unavailable without a selected chat model; nothing is sent or written again for content
 * already analysed in the same extractor state. An analysis with failed paragraphs is not stored, so the next
 * opening tries again instead of keeping a partial result as current. Blocks on model calls and storage: call it off
 * the Swing event thread.
 */
public final class DocumentStatementsUseCase {

    /** What happened. */
    public enum Storage {
        /** Analysed and stored. */
        STORED,
        /** Already stored for this content and extractor state; nothing was sent to the model. */
        UNCHANGED,
        /** The analysis is switched off. */
        DISABLED,
        /** No chat model is selected: nothing was sent anywhere. */
        UNAVAILABLE,
        /** Some paragraphs got no answer; nothing stored. */
        INCOMPLETE,
        /** Storage rejected the statements; see {@link Outcome#problem()}. */
        FAILED
    }

    /** The statements (if analysed) and what happened to them. */
    public static final class Outcome {
        private final Storage storage;
        private final DocumentStatements statements;
        private final String problem;

        Outcome(Storage storage, DocumentStatements statements, String problem) {
            this.storage = storage;
            this.statements = statements;
            this.problem = problem == null ? "" : problem;
        }

        public Storage storage() {
            return storage;
        }

        /** The analysis, or {@code null} if none ran. */
        public DocumentStatements statements() {
            return statements;
        }

        /** Why storing failed, or {@code ""}. Never contains document text. */
        public String problem() {
            return problem;
        }
    }

    private final StatementAnalyzer analyzer;
    private final StatementSink sink;
    private final Supplier<StatementSettings> settings;

    /** @param settings read for every document */
    public DocumentStatementsUseCase(StatementAnalyzer analyzer, StatementSink sink,
                                     Supplier<StatementSettings> settings) {
        if (analyzer == null || sink == null || settings == null) {
            throw new IllegalArgumentException("analyzer, sink and settings must not be null");
        }
        this.analyzer = analyzer;
        this.sink = sink;
        this.settings = settings;
    }

    /** Whether opened documents are analysed now (switched on and a chat model selected). */
    public boolean isActive() {
        StatementSettings current = current();
        return current.enabled() && analyzer.isAvailable();
    }

    public Outcome analyze(KnowledgeDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        StatementSettings current = current();
        if (!current.enabled()) {
            return new Outcome(Storage.DISABLED, null, null);
        }
        if (!analyzer.isAvailable()) {
            return new Outcome(Storage.UNAVAILABLE, null, null);
        }
        KnowledgeResourceRef resource = KnowledgeResourceRef.of(document);
        String version = analyzer.version(current);
        try {
            if (sink.isCurrent(resource, StatementAnalyzer.ID, version)) {
                return new Outcome(Storage.UNCHANGED, null, null);
            }
        } catch (RuntimeException failed) {
            return new Outcome(Storage.FAILED, null, problem(failed));
        }
        DocumentStatements statements = analyzer.analyze(document.text(), current);
        if (statements.failedUnits() > 0) {
            return new Outcome(Storage.INCOMPLETE, statements, statements.failedUnits() + " paragraphs failed");
        }
        try {
            sink.replace(resource, StatementAnalyzer.ID, version, document.text(), statements);
            return new Outcome(Storage.STORED, statements, null);
        } catch (RuntimeException failed) {
            return new Outcome(Storage.FAILED, statements, problem(failed));
        }
    }

    private StatementSettings current() {
        StatementSettings current = settings.get();
        return current == null ? StatementSettings.defaults() : current;
    }

    private static String problem(RuntimeException failed) {
        return failed.getClass().getSimpleName() + (failed.getMessage() == null ? "" : ": " + failed.getMessage());
    }
}
