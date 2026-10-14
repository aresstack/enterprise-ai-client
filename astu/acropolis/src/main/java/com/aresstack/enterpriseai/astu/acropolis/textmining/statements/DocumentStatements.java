package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The statements of one document version (issue #108) and how the analysis went: units analysed, failed and left
 * out, rejected candidates by reason, verdicts of the yes/no check. Diagnostics never contain document text.
 */
public final class DocumentStatements {

    private final List<Mention> mentions;
    private final List<Event> events;
    private final List<Relation> relations;
    private final int analysedUnits;
    private final int failedUnits;
    private final int skippedUnits;
    private final Map<String, Integer> rejections;
    private final Map<StatementVerifier.Verdict, Integer> verdicts;

    DocumentStatements(List<Mention> mentions, List<Event> events, List<Relation> relations, int analysedUnits,
                       int failedUnits, int skippedUnits, Map<String, Integer> rejections,
                       Map<StatementVerifier.Verdict, Integer> verdicts) {
        this.mentions = Collections.unmodifiableList(new ArrayList<Mention>(mentions));
        this.events = Collections.unmodifiableList(new ArrayList<Event>(events));
        this.relations = Collections.unmodifiableList(new ArrayList<Relation>(relations));
        this.analysedUnits = analysedUnits;
        this.failedUnits = failedUnits;
        this.skippedUnits = skippedUnits;
        this.rejections = Collections.unmodifiableMap(new TreeMap<String, Integer>(rejections));
        this.verdicts = Collections.unmodifiableMap(new TreeMap<StatementVerifier.Verdict, Integer>(verdicts));
    }

    public List<Mention> mentions() {
        return mentions;
    }

    public List<Event> events() {
        return events;
    }

    public List<Relation> relations() {
        return relations;
    }

    /** Units (paragraphs or runs of sentences) the extractor answered for. */
    public int analysedUnits() {
        return analysedUnits;
    }

    /** Units without an answer (model failed or unreadable). */
    public int failedUnits() {
        return failedUnits;
    }

    /** Units left out beyond the paragraph limit. */
    public int skippedUnits() {
        return skippedUnits;
    }

    /** Rejected or reduced candidates by reason, including claims the check denied ({@code check-denied}). */
    public Map<String, Integer> rejections() {
        return rejections;
    }

    public Map<StatementVerifier.Verdict, Integer> verdicts() {
        return verdicts;
    }

    @Override
    public String toString() {
        return "DocumentStatements[" + mentions.size() + " mentions, " + events.size() + " events, "
                + relations.size() + " relations; units " + analysedUnits + "/" + failedUnits + " failed/"
                + skippedUnits + " skipped; rejected " + rejections + "; checks " + verdicts + "]";
    }
}
