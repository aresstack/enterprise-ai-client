package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatBookmark;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Die Wissensbeschaffung eines Chat-Turns als Ganzes: automatisches RAG (Volltext und semantisch) und die
 * Wissenswerkzeuge, die das Modell im Turn aufgerufen hat. Die Oberfläche bewertet daran den Turn, nicht nur den
 * RAG-Teilpfad: ein ausgefallener Suchpfad heißt nicht, dass die Antwort ohne Wissen entstand, wenn
 * {@code knowledge_read} eine Seite geliefert hat.
 *
 * <p>Als gelieferter Wissenskontext zählt ein erfolgreiches {@value ChatBookmark#READ_TOOL}. Ein Ergebnis von
 * {@code knowledge_search} zählt nicht, weil es auch ohne Treffer erfolgreich ist; der Inhalt eines Treffers kommt
 * über {@value ChatBookmark#READ_TOOL}.
 */
public final class KnowledgeAcquisition {

    /** Gesamtzustand der Wissensbeschaffung eines Turns. */
    public enum Status {

        /** Weder RAG noch ein Wissenswerkzeug ist gescheitert oder ohne Ergebnis geblieben, das zählt. */
        NOT_REQUESTED,

        /** RAG lief vollständig und fand Abschnitte. */
        RAG_SUCCEEDED,

        /** Ein Suchpfad fiel aus, der andere fand Abschnitte; sie gingen als Kontext an das Modell. */
        RAG_PARTIALLY_FAILED,

        /** RAG lief vollständig und fand nichts; ob ein Werkzeug Kontext lieferte, sagt {@link #toolContext()}. */
        RAG_NO_HITS,

        /** RAG fiel ganz oder teilweise aus und fand nichts, aber ein Wissenswerkzeug lieferte Kontext. */
        RAG_FAILED_TOOL_CONTEXT,

        /** Der Turn hatte überhaupt keinen Wissenskontext, obwohl Wissen gesucht oder gelesen werden sollte. */
        NO_CONTEXT
    }

    private final Status status;
    private final Set<RetrievalPath> failedPaths;
    private final boolean toolContext;

    private KnowledgeAcquisition(Status status, Set<RetrievalPath> failedPaths, boolean toolContext) {
        this.status = status;
        this.failedPaths = failedPaths;
        this.toolContext = toolContext;
    }

    /**
     * @param turn          der RAG-Turn ({@code retrievalRequested} sagt, ob RAG lief)
     * @param toolReads     erfolgreiche Aufrufe eines Werkzeugs, das Wissenskontext liefert
     * @param toolReadFails gescheiterte Aufrufe eines solchen Werkzeugs
     */
    public static KnowledgeAcquisition of(RagChatTurn turn, int toolReads, int toolReadFails) {
        if (turn == null) {
            throw new IllegalArgumentException("turn must not be null");
        }
        boolean toolContext = toolReads > 0;
        if (!turn.retrievalRequested()) {
            Status status = toolReadFails > 0 && !toolContext ? Status.NO_CONTEXT : Status.NOT_REQUESTED;
            return new KnowledgeAcquisition(status, Collections.<RetrievalPath>emptySet(), toolContext);
        }
        Set<RetrievalPath> failed = EnumSet.noneOf(RetrievalPath.class);
        for (RetrievalWarning warning : turn.warnings()) {
            failed.add(warning.path());
        }
        if (turn.retrievalFailed()) {
            failed.addAll(EnumSet.allOf(RetrievalPath.class));
        }
        boolean hits = !turn.retrievalFailed() && !turn.sources().isEmpty();
        Status status;
        if (hits) {
            status = failed.isEmpty() ? Status.RAG_SUCCEEDED : Status.RAG_PARTIALLY_FAILED;
        } else if (failed.isEmpty()) {
            status = Status.RAG_NO_HITS;
        } else {
            status = toolContext ? Status.RAG_FAILED_TOOL_CONTEXT : Status.NO_CONTEXT;
        }
        return new KnowledgeAcquisition(status, Collections.unmodifiableSet(failed), toolContext);
    }

    /** Liefert dieses Werkzeug bei Erfolg Wissenskontext für den Turn? */
    public static boolean deliversContext(String toolName) {
        return ChatBookmark.READ_TOOL.equals(toolName);
    }

    public Status status() {
        return status;
    }

    /** Ausgefallene RAG-Suchpfade; bei ganz ausgefallenem Retrieval beide. */
    public Set<RetrievalPath> failedPaths() {
        return failedPaths;
    }

    /** Ein Wissenswerkzeug hat im Turn erfolgreich Kontext geliefert. */
    public boolean toolContext() {
        return toolContext;
    }

    /** Hatte der Turn irgendeinen Wissenskontext (RAG-Abschnitte oder gelesene Seiten)? */
    public boolean hasContext() {
        return status == Status.RAG_SUCCEEDED || status == Status.RAG_PARTIALLY_FAILED || toolContext;
    }

    @Override
    public String toString() {
        return "KnowledgeAcquisition{" + status + ", failed=" + failedPaths + (toolContext ? ", toolContext" : "")
                + "}";
    }
}
