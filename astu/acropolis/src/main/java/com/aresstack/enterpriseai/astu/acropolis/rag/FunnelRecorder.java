package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.searchrun.api.CandidateEvent;
import com.aresstack.enterpriseai.searchrun.api.CandidateEventKind;
import com.aresstack.enterpriseai.searchrun.api.FunnelPhase;
import com.aresstack.enterpriseai.searchrun.api.FunnelResource;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeCandidate;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeFunnel;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRun;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRunPort;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Counts the funnel of one research run (#155): the only place that decides what counts as discovered, examined,
 * relevant and used. The chat turn and {@code knowledge_research} both use it, so both report the same numbers.
 *
 * <p>The live searches are read back from the run log (#153): their candidates are discovered, and a candidate whose
 * excerpt was actually reranked ({@link CandidateEventKind#EXCERPT_SCORED}) is examined. A provider's total hit count
 * is never seen here. Thread-safe; the log never fails a run.
 */
public final class FunnelRecorder {

    private final KnowledgeSearchRunPort runs;
    private final SearchRunId id = SearchRunId.random();
    private final Instant startedAt;
    private final Set<SearchRunId> liveRuns = new LinkedHashSet<SearchRunId>();
    private final Set<FunnelResource> examined = new LinkedHashSet<FunnelResource>();
    private final Set<FunnelResource> relevant = new LinkedHashSet<FunnelResource>();
    private final Set<FunnelResource> used = new LinkedHashSet<FunnelResource>();
    private int discovered;
    private FunnelPhase phase = FunnelPhase.DISCOVERY;
    private KnowledgeFunnel finished;

    /** @param runs the run log; {@code null} counts without live search runs and stores nothing */
    public FunnelRecorder(KnowledgeSearchRunPort runs) {
        this(runs, Clock.systemUTC());
    }

    FunnelRecorder(KnowledgeSearchRunPort runs, Clock clock) {
        this.runs = runs;
        this.startedAt = clock.instant();
    }

    public SearchRunId id() {
        return id;
    }

    /** The resource key of a funnel. */
    public static FunnelResource resource(SourceAddress source, KnowledgeResourceId resource) {
        return new FunnelResource(source.value(), resource.value());
    }

    /**
     * A live search of this run was recorded under {@code runId}: its candidates are discovered, the reranked
     * excerpts examined. The same run counts once; an unknown id or a failing log changes nothing.
     */
    public synchronized void liveSearch(String runId) {
        if (runs == null || runId == null || runId.trim().isEmpty() || finished != null) {
            return;
        }
        SearchRunId run;
        try {
            run = SearchRunId.of(runId);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (liveRuns.contains(run)) {
            return;
        }
        try {
            Optional<KnowledgeSearchRun> recorded = runs.find(run);
            if (!recorded.isPresent()) {
                return;
            }
            List<CandidateEvent> events = runs.events(run);
            liveRuns.add(run);
            discovered += recorded.get().candidates().size();
            for (CandidateEvent event : events) {
                if (event.kind() == CandidateEventKind.EXCERPT_SCORED) {
                    Optional<KnowledgeCandidate> candidate = recorded.get().candidate(event.candidateNumber());
                    if (candidate.isPresent()) {
                        examined.add(resource(candidate.get().sourceId(), candidate.get().resourceId()));
                    }
                }
            }
        } catch (RuntimeException e) {
            // the log never fails a run; this live search is then missing from the funnel
        }
    }

    /** These resources' content entered an evaluation in this run. */
    public synchronized void examined(Collection<FunnelResource> resources) {
        add(examined, resources);
    }

    /** These resources survived the relevance stage (implies examined). */
    public synchronized void relevant(Collection<FunnelResource> resources) {
        add(relevant, resources);
    }

    /** These resources are in the answer's provenance (implies relevant). */
    public synchronized void used(Collection<FunnelResource> resources) {
        add(used, resources);
    }

    public synchronized void phase(FunnelPhase next) {
        if (next != null && finished == null) {
            phase = next;
        }
    }

    /** The funnel as counted so far. */
    public synchronized KnowledgeFunnel snapshot() {
        return finished != null ? finished
                : new KnowledgeFunnel(id, startedAt, liveRuns, discovered, examined, relevant, used, phase, false);
    }

    /**
     * Ends the run and stores the funnel; later calls change nothing and return the same funnel.
     *
     * @param complete {@code false} when the run was cancelled or failed: the counts reached so far stay
     */
    public synchronized KnowledgeFunnel finish(boolean complete) {
        if (finished != null) {
            return finished;
        }
        finished = new KnowledgeFunnel(id, startedAt, liveRuns, discovered, examined, relevant, used,
                FunnelPhase.DONE, complete);
        if (runs != null) {
            try {
                runs.recordFunnel(finished);
            } catch (RuntimeException e) {
                // the log never fails a run
            }
        }
        return finished;
    }

    private void add(Set<FunnelResource> target, Collection<FunnelResource> resources) {
        if (resources == null || finished != null) {
            return;
        }
        for (FunnelResource resource : new ArrayList<FunnelResource>(resources)) {
            if (resource != null) {
                target.add(resource);
            }
        }
    }
}
