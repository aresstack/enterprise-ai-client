package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chat.planning.NextAction;
import com.aresstack.enterpriseai.searchrun.api.FunnelPhase;
import com.aresstack.enterpriseai.searchrun.api.FunnelResource;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeFunnel;

import java.util.ArrayList;
import java.util.List;

/**
 * Feeds the {@link FunnelRecorder} of a chat turn from its retrieval progress and passes every step on, plus a
 * {@link #funnelChanged} whenever the funnel moved. The phases follow the steps that actually run.
 */
final class FunnelProgress implements RetrievalProgress {

    private final RetrievalProgress next;
    private final FunnelRecorder funnel;

    FunnelProgress(RetrievalProgress next, FunnelRecorder funnel) {
        this.next = next == null ? RetrievalProgress.none() : next;
        this.funnel = funnel;
    }

    FunnelRecorder recorder() {
        return funnel;
    }

    private void moved(FunnelPhase phase) {
        if (phase != null) {
            funnel.phase(phase);
        }
        next.funnelChanged(funnel.snapshot());
    }

    static List<FunnelResource> resources(List<RetrievedChunk> hits) {
        List<FunnelResource> resources = new ArrayList<FunnelResource>();
        if (hits != null) {
            for (RetrievedChunk hit : hits) {
                resources.add(FunnelRecorder.resource(hit.chunk().sourceId(), hit.chunk().resourceId()));
            }
        }
        return resources;
    }

    @Override
    public void searchStarted(String query, int number, int max) {
        next.searchStarted(query, number, max);
    }

    @Override
    public void liveSearchStarted(String query) {
        next.liveSearchStarted(query);
        moved(FunnelPhase.DISCOVERY);
    }

    @Override
    public void liveSearchRecorded(String runId) {
        next.liveSearchRecorded(runId);
        funnel.liveSearch(runId);
        moved(FunnelPhase.HARVESTING);
    }

    @Override
    public void liveHitsFound(int hits) {
        next.liveHitsFound(hits);
    }

    @Override
    public void pagesAvailable(int available, int awaited) {
        next.pagesAvailable(available, awaited);
    }

    @Override
    public void pathStarted(RetrievalPath path) {
        next.pathStarted(path);
        moved(FunnelPhase.RETRIEVAL);
    }

    @Override
    public void hitsFound(String query, int hits) {
        next.hitsFound(query, hits);
    }

    @Override
    public void searchFailed(String query) {
        next.searchFailed(query);
    }

    @Override
    public void contentExamined(List<RetrievedChunk> hits) {
        next.contentExamined(hits);
        funnel.examined(resources(hits));
        moved(null);
    }

    @Override
    public void rankingStarted(int candidates) {
        next.rankingStarted(candidates);
        moved(FunnelPhase.EVIDENCE);
    }

    @Override
    public void candidatesRanked(int candidates, int relevant) {
        next.candidatesRanked(candidates, relevant);
    }

    @Override
    public void contentRelevant(List<RetrievedChunk> hits) {
        next.contentRelevant(hits);
        funnel.relevant(resources(hits));
        moved(null);
    }

    @Override
    public void evidenceChecked(int sources, int independent, boolean sufficient) {
        next.evidenceChecked(sources, independent, sufficient);
    }

    @Override
    public void morePagesStarted() {
        next.morePagesStarted();
        moved(FunnelPhase.HARVESTING);
    }

    @Override
    public void contextCheckStarted() {
        next.contextCheckStarted();
        moved(FunnelPhase.EVIDENCE);
    }

    @Override
    public void contextReady(int sources) {
        next.contextReady(sources);
    }

    @Override
    public void sourcesUsed(List<RagSource> sources) {
        next.sourcesUsed(sources);
        List<RetrievedChunk> hits = new ArrayList<RetrievedChunk>();
        if (sources != null) {
            for (RagSource source : sources) {
                hits.add(source.hit());
            }
        }
        funnel.used(resources(hits));
        moved(FunnelPhase.ANSWER);
    }

    @Override
    public void reusingSources(int sources) {
        next.reusingSources(sources);
    }

    @Override
    public void reuseInsufficient(String query, int sources) {
        next.reuseInsufficient(query, sources);
    }

    @Override
    public void planningStarted() {
        next.planningStarted();
    }

    @Override
    public void planningFinished(NextAction action) {
        next.planningFinished(action);
    }

    @Override
    public void triageStarted() {
        next.triageStarted();
    }

    @Override
    public void searchSkipped() {
        next.searchSkipped();
    }

    @Override
    public void funnelChanged(KnowledgeFunnel changed) {
        next.funnelChanged(changed);
    }

    @Override
    public boolean isCancelled() {
        return next.isCancelled();
    }
}
