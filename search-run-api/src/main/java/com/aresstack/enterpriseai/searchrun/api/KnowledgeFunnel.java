package com.aresstack.enterpriseai.searchrun.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The funnel of one research run (a chat turn or one {@code knowledge_research} task, #155): how many candidates the
 * live searches discovered, which resources were actually examined, which proved relevant and which the answer used.
 *
 * <ul>
 *     <li><b>examined</b>: the content entered an evaluation in this run (a reranked excerpt, indexed content scored
 *     by retrieval, freshly loaded and scored full text). A provider's total hit count never counts.</li>
 *     <li><b>relevant</b>: survived the full-text / retrieval relevance stage; an excerpt score alone is not enough.</li>
 *     <li><b>used</b>: named in the answer's provenance.</li>
 * </ul>
 *
 * <p>Each set is deduplicated by {@link FunnelResource}. The type guarantees {@code used ⊆ relevant ⊆ examined}: a
 * resource the answer uses was relevant, and a relevant one was examined. {@link #complete()} is {@code false} when the
 * run was cancelled or failed; the counts are then the ones honestly reached until then.
 */
public final class KnowledgeFunnel {

    private final SearchRunId id;
    private final Instant startedAt;
    private final List<SearchRunId> runs;
    private final int discovered;
    private final List<FunnelResource> examined;
    private final List<FunnelResource> relevant;
    private final List<FunnelResource> used;
    private final FunnelPhase phase;
    private final boolean complete;

    /**
     * @param id         id of this research run (not one of the live search {@code runs})
     * @param runs       the live search runs of this research run, from the run log
     * @param discovered candidates those runs received
     */
    public KnowledgeFunnel(SearchRunId id, Instant startedAt, Collection<SearchRunId> runs, int discovered,
                           Collection<FunnelResource> examined, Collection<FunnelResource> relevant,
                           Collection<FunnelResource> used, FunnelPhase phase, boolean complete) {
        if (id == null || startedAt == null || phase == null) {
            throw new IllegalArgumentException("id, start and phase must not be null");
        }
        if (discovered < 0) {
            throw new IllegalArgumentException("discovered must not be negative");
        }
        this.id = id;
        this.startedAt = startedAt;
        this.runs = Collections.unmodifiableList(new ArrayList<SearchRunId>(new LinkedHashSet<SearchRunId>(
                runs == null ? Collections.<SearchRunId>emptyList() : runs)));
        Set<FunnelResource> usedSet = ordered(used);
        Set<FunnelResource> relevantSet = ordered(relevant);
        relevantSet.addAll(usedSet);
        Set<FunnelResource> examinedSet = ordered(examined);
        examinedSet.addAll(relevantSet);
        this.discovered = discovered;
        this.examined = Collections.unmodifiableList(new ArrayList<FunnelResource>(examinedSet));
        this.relevant = Collections.unmodifiableList(new ArrayList<FunnelResource>(relevantSet));
        this.used = Collections.unmodifiableList(new ArrayList<FunnelResource>(usedSet));
        this.phase = phase;
        this.complete = complete;
    }

    private static Set<FunnelResource> ordered(Collection<FunnelResource> resources) {
        Set<FunnelResource> set = new LinkedHashSet<FunnelResource>();
        if (resources != null) {
            for (FunnelResource resource : resources) {
                if (resource != null) {
                    set.add(resource);
                }
            }
        }
        return set;
    }

    public SearchRunId id() {
        return id;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** The live search runs of this research run, in the order they ran. */
    public List<SearchRunId> runs() {
        return runs;
    }

    /** Candidates the live searches received (sum over {@link #runs()}); never a provider's total hit count. */
    public int discovered() {
        return discovered;
    }

    /** Resources whose content entered an evaluation in this run, in the order they were first examined. */
    public List<FunnelResource> examined() {
        return examined;
    }

    /** Resources that survived the relevance stage; a subset of {@link #examined()}. */
    public List<FunnelResource> relevant() {
        return relevant;
    }

    /** Resources in the answer's provenance; a subset of {@link #relevant()}. */
    public List<FunnelResource> used() {
        return used;
    }

    public FunnelPhase phase() {
        return phase;
    }

    /** {@code false} when the run was cancelled or failed before it ended normally. */
    public boolean complete() {
        return complete;
    }

    @Override
    public String toString() {
        return discovered + " discovered / " + examined.size() + " examined / " + relevant.size() + " relevant / "
                + used.size() + " used (" + phase + (complete ? "" : ", incomplete") + ")";
    }
}
