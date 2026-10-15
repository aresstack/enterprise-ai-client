package com.aresstack.enterpriseai.research.runtime.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The grounded result of one delegated research task: which search expressions actually reached the knowledge
 * tools, which resources were read, and the answer written only from those resources. Without evidence there is no
 * answer text from the model, only the honest statement what was searched and not found.
 */
public final class KnowledgeResearchResult {

    /** How the research ended. */
    public enum Outcome {
        /** Resources were read and the answer is grounded in them. */
        ANSWERED,
        /** Searched, but nothing found or nothing readable. */
        NO_EVIDENCE,
        /** The knowledge tools failed; nothing could be searched. */
        FAILED
    }

    private final String task;
    private final List<String> queries;
    private final List<String> notes;
    private final List<KnowledgeHit> sources;
    private final List<KnowledgeHit> unread;
    private final String answer;
    private final Outcome outcome;

    public KnowledgeResearchResult(String task, List<String> queries, java.util.Collection<String> notes,
                                   List<KnowledgeHit> sources,
                                   List<KnowledgeHit> unread, String answer, Outcome outcome) {
        this.task = task == null ? "" : task;
        this.queries = Collections.unmodifiableList(new ArrayList<String>(queries));
        this.notes = Collections.unmodifiableList(new ArrayList<String>(notes));
        this.sources = Collections.unmodifiableList(new ArrayList<KnowledgeHit>(sources));
        this.unread = Collections.unmodifiableList(new ArrayList<KnowledgeHit>(unread));
        this.answer = answer == null ? "" : answer;
        this.outcome = outcome;
    }

    public String task() {
        return task;
    }

    /** The search expressions sent to {@code knowledge_search}, in order. */
    public List<String> queries() {
        return queries;
    }

    /** Diagnostics of the search (a source or the index could not be searched); never evidence. */
    public List<String> notes() {
        return notes;
    }

    /** The resources that were read; the answer cites them as {@code [n]} in this order. */
    public List<KnowledgeHit> sources() {
        return sources;
    }

    /** Hits that were found but not read (budget or unreadable); listed, never used as evidence. */
    public List<KnowledgeHit> unread() {
        return unread;
    }

    public String answer() {
        return answer;
    }

    public Outcome outcome() {
        return outcome;
    }

    /** The result as text for the calling chat: findings with numbered sources, the queries used, further hits. */
    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("Research task: ").append(task.replaceAll("\\s+", " ").trim()).append('\n');
        out.append("Searched with: ");
        for (int i = 0; i < queries.size(); i++) {
            out.append(i == 0 ? "" : ", ").append('"').append(queries.get(i)).append('"');
        }
        out.append('\n');
        for (String note : notes) {
            out.append("Note: ").append(note).append('\n');
        }
        if (outcome == Outcome.ANSWERED) {
            out.append("\nFindings (only from the sources below; cite them as [n]):\n").append(answer.trim())
                    .append("\n\nSources:\n");
            for (int i = 0; i < sources.size(); i++) {
                appendSource(out, "[" + (i + 1) + "] ", sources.get(i));
            }
        } else {
            out.append('\n').append(answer.trim()).append('\n');
        }
        if (!unread.isEmpty()) {
            out.append("\nFurther hits (not read, no evidence; read one with knowledge_read):\n");
            for (KnowledgeHit hit : unread) {
                appendSource(out, "- ", hit);
            }
        }
        return out.toString().trim();
    }

    private static void appendSource(StringBuilder out, String prefix, KnowledgeHit hit) {
        out.append(prefix).append(hit.label()).append('\n').append("    id: ").append(hit.id).append('\n');
        if (!hit.url.isEmpty()) {
            out.append("    url: ").append(hit.url).append('\n');
        }
        if (!hit.source.isEmpty()) {
            out.append("    source: ").append(hit.source).append('\n');
        }
    }
}
