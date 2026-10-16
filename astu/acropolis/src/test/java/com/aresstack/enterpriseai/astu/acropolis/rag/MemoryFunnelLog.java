package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.searchrun.api.CandidateEvent;
import com.aresstack.enterpriseai.searchrun.api.CandidateEventKind;
import com.aresstack.enterpriseai.searchrun.api.CandidateOccurrence;
import com.aresstack.enterpriseai.searchrun.api.CandidateOrigin;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeCandidate;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeFunnel;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRun;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRunPort;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Run log in memory for funnel tests; {@link #failing} makes every call fail like a broken store. */
final class MemoryFunnelLog implements KnowledgeSearchRunPort {

    final Map<SearchRunId, KnowledgeSearchRun> runs = new LinkedHashMap<SearchRunId, KnowledgeSearchRun>();
    final List<CandidateEvent> events = new ArrayList<CandidateEvent>();
    final Map<SearchRunId, KnowledgeFunnel> funnels = new LinkedHashMap<SearchRunId, KnowledgeFunnel>();
    volatile boolean failing;

    /**
     * Records a run whose candidates are the given resource ids of source {@code source}; the first {@code scored}
     * candidates get an {@code EXCERPT_SCORED} event.
     */
    SearchRunId run(String source, int scored, String... resources) {
        SourceAddress address = SourceAddress.of(source);
        List<KnowledgeCandidate> candidates = new ArrayList<KnowledgeCandidate>();
        for (String resource : resources) {
            candidates.add(new KnowledgeCandidate(candidates.size() + 1, address, KnowledgeResourceId.of(resource),
                    resource, "excerpt of " + resource, null, KnowledgeRevision.unknown(), CandidateOrigin.SOURCE,
                    Collections.singletonList(new CandidateOccurrence(address, candidates.size()))));
        }
        KnowledgeSearchRun run = new KnowledgeSearchRun(SearchRunId.random(), "query", Instant.EPOCH,
                Collections.singletonList(address), Collections.<SourceAddress, String>emptyMap(), candidates);
        record(run);
        for (int i = 1; i <= scored; i++) {
            append(new CandidateEvent(run.id(), i, CandidateEventKind.EXCERPT_SCORED, Instant.EPOCH, "priority " + i,
                    1.0 / i));
            append(new CandidateEvent(run.id(), i, CandidateEventKind.PREFETCH_QUEUED, Instant.EPOCH, null, null));
        }
        return run.id();
    }

    private void check() {
        if (failing) {
            throw new IllegalStateException("store down");
        }
    }

    @Override
    public synchronized void record(KnowledgeSearchRun run) {
        check();
        runs.put(run.id(), run);
    }

    @Override
    public synchronized Optional<KnowledgeSearchRun> find(SearchRunId id) {
        check();
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public synchronized void append(CandidateEvent event) {
        check();
        events.add(event);
    }

    @Override
    public synchronized void appendAll(List<CandidateEvent> list) {
        check();
        events.addAll(list);
    }

    @Override
    public synchronized List<CandidateEvent> events(SearchRunId id) {
        check();
        List<CandidateEvent> result = new ArrayList<CandidateEvent>();
        for (CandidateEvent event : events) {
            if (event.run().equals(id)) {
                result.add(event);
            }
        }
        return result;
    }

    @Override
    public synchronized void recordFunnel(KnowledgeFunnel funnel) {
        check();
        funnels.put(funnel.id(), funnel);
    }

    @Override
    public synchronized Optional<KnowledgeFunnel> funnel(SearchRunId id) {
        check();
        return Optional.ofNullable(funnels.get(id));
    }
}
