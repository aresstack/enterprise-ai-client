package com.aresstack.enterpriseai.searchrun.api;

import java.util.List;
import java.util.Optional;

/**
 * Persistent log of knowledge search runs (#153). Runs are written once and never changed; later facts about a
 * candidate are appended as {@link CandidateEvent}s. All methods throw {@link SearchRunStoreException} when the store
 * fails.
 */
public interface KnowledgeSearchRunPort {

    /** Stores a complete run; a run id that already exists is refused. */
    void record(KnowledgeSearchRun run);

    /** The run with this id, as recorded. */
    Optional<KnowledgeSearchRun> find(SearchRunId id);

    /** Appends an event; refused when the run or its candidate does not exist. */
    void append(CandidateEvent event);

    /** Appends several events in one write, in list order; refused as a whole when one of them is refused. */
    void appendAll(List<CandidateEvent> events);

    /** All events of a run in the order they were appended. */
    List<CandidateEvent> events(SearchRunId id);

    /**
     * Stores the funnel of a research run (#155). Writing a funnel with an id that already exists replaces it, so a
     * run can be saved while it progresses and once more when it ends.
     */
    void recordFunnel(KnowledgeFunnel funnel);

    /** The funnel with this id, as last recorded. */
    Optional<KnowledgeFunnel> funnel(SearchRunId id);
}
