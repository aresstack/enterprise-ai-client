package com.aresstack.enterpriseai.research.runtime.loop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The deterministic autonomous research loop. It now DELEGATES the web-acquisition engine (SearchStrategy →
 * rerank → frontier → browse → capture → source acceptance → links → budgets/cancel/CAPTCHA/cleanup) to
 * {@link com.aresstack.enterpriseai.research.runtime.acquire.WebSearchApplicationService}, and keeps ONLY the
 * research-specific concerns: deriving the query terms, the run summary, and the PHASE_READY event. The host
 * stays the only state authority. Issue #32: the loop records NO findings artifact anymore — the legacy
 * {@code finding_add} tool is gone; accepted sources (with full text) plus their derived passages ARE the
 * evidence record.
 */
public final class ResearchLoop {

    private final ToolInvoker browser;
    private final ToolInvoker research;
    private final ResearchRunBudget budget;
    private final ResearchRunProgress progress = new ResearchRunProgress();
    private final ResearchLoopClock clock;
    private final ResearchLoopListener listener;
    private final AtomicBoolean cancelled;
    private final long startedAt;
    private final Set<String> claimedSourceIds = new HashSet<String>();
    /** Enterprise: Quellen kommen nicht aus einem Browser; Challenge-Abfragen gibt es nicht. */
    private final long challengeProbeIntervalMillis = 1000L;
    private final boolean challengeWaitForUser = false;
    /** Public-suffix aware domain families; tests may inject a fake (e.g. host:port for local worlds). */
    private com.aresstack.enterpriseai.research.runtime.support.DomainKeyResolver domainKeys =
            new com.aresstack.enterpriseai.research.runtime.support.HostDomainKeyResolver();
    /**
     * The interchangeable INITIAL-search seam — the ONLY way the loop obtains its start URLs. It defaults to
     * the browser SERP path built by {@link
     * com.aresstack.enterpriseai.research.runtime.search.LegacyBrowserSearchStrategyFactory} (so the loop itself
     * knows nothing about the layout-repair client); the productive runtime injects an API-provider strategy
     * at session start when the snapshot selects one. From {@code result.candidates} onward the acquisition
     * behaves identically no matter which strategy produced the URLs.
     */
    private com.aresstack.enterpriseai.research.runtime.search.SearchStrategy searchStrategy;
    private static final int MIN_READABLE_CHARACTERS = 200;
    /**
     * The MANDATORY local cross-encoder reranking step. Every organic candidate is reranked and only the
     * selected survivors — in relevance order — ever reach the frontier and {@code web_open}; a reranker
     * failure opens NOTHING and ends the run with a typed reranker stop reason (never a raw-order
     * fallback). The productive runtime injects the HTTP-backed reranker at session start; the default is
     * the EXPLICITLY named {@link com.aresstack.enterpriseai.research.runtime.rerank.EngineOrderReranker} test
     * adapter, so the loop never infers "engine order" from the ABSENCE of a reranker — production fails
     * earlier, at session start, when the mandatory snapshot is missing.
     */
    private com.aresstack.enterpriseai.research.runtime.rerank.CandidateReranker reranker =
            new com.aresstack.enterpriseai.research.runtime.rerank.EngineOrderReranker();
    /**
     * The source-acceptance seam. Defaults to the agent's phase-gated {@code source_accept} tool (the exact
     * inline behavior this loop always had); a user-triggered search later injects the internal
     * {@code manual_source_accept} port instead — same acquisition code, different authorization/origin.
     */
    private com.aresstack.enterpriseai.research.runtime.acquire.SourceAcceptancePort sourceAcceptancePort;

    /** Inject the source-acceptance route (T2c wires the manual port; default stays the agent's tool). */
    private com.aresstack.enterpriseai.research.runtime.acquire.SearchScopeControlPort scopeControl;

    /** SC1: the autonomous loop uses the SAME scope-control gate as the manual search. */
    public void setScopeControl(
            com.aresstack.enterpriseai.research.runtime.acquire.SearchScopeControlPort port) {
        this.scopeControl = port;
    }

    public void setSourceAcceptancePort(
            com.aresstack.enterpriseai.research.runtime.acquire.SourceAcceptancePort port) {
        if (port != null) {
            this.sourceAcceptancePort = port;
        }
    }

    /** Inject the mandatory reranker (productive runtime, and reranking integration tests). */
    public void setReranker(com.aresstack.enterpriseai.research.runtime.rerank.CandidateReranker reranker) {
        if (reranker != null) {
            this.reranker = reranker;
        }
    }

    /** Inject a different domain-key resolver (tests/dev only; production keeps the public-suffix one). */
    public void setDomainKeyResolver(com.aresstack.enterpriseai.research.runtime.support.DomainKeyResolver resolver) {
        if (resolver != null) {
            this.domainKeys = resolver;
        }
    }

    /**
     * Inject an initial-search strategy (productive runtime selects the API-provider strategy; tests inject
     * fakes, incl. a legacy-browser strategy over a scripted repair client). Passing {@code null} keeps the
     * current strategy — there is never a silent fallback.
     */
    public void setSearchStrategy(com.aresstack.enterpriseai.research.runtime.search.SearchStrategy strategy) {
        setSearchStrategy(strategy, null);
    }

    /**
     * @param apiProviderLabel the user-facing REST provider name ("DataForSEO", …) when the strategy is an
     *                         API provider — the search progress then says so (and shows NO browser), or
     *                         {@code null} for the browser SERP path.
     */
    public void setSearchStrategy(com.aresstack.enterpriseai.research.runtime.search.SearchStrategy strategy,
                                  String apiProviderLabel) {
        if (strategy != null) {
            this.searchStrategy = strategy;
            this.apiProviderLabel = apiProviderLabel;
        }
    }

    /** Non-null exactly when the injected strategy is a REST provider (no browser during the search). */
    private String apiProviderLabel;

    /**
     * @param browser  die Seitenwerkzeuge der Quelle ({@code web_open}, {@code web_links}; im Enterprise-Client
     *                 Confluence über den Host statt eines Browsers)
     * @param research die Research-Steuerung des Hosts ({@code source_accept}, {@code source_park})
     */
    public ResearchLoop(ToolInvoker browser, ToolInvoker research, ResearchRunBudget budget,
                        ResearchLoopClock clock, ResearchLoopListener listener, AtomicBoolean cancelled) {
        this.browser = browser;
        this.research = research;
        this.sourceAcceptancePort =
                new com.aresstack.enterpriseai.research.runtime.acquire.AgentSourceAcceptancePort(research);
        this.budget = budget;
        this.clock = clock;
        this.listener = listener;
        this.cancelled = cancelled;
        this.startedAt = clock.currentTimeMillis();
    }

    public ResearchRunProgress getProgress() {
        return progress;
    }

    public ResearchRunBudget getBudget() {
        return budget;
    }

    /**
     * Seed already-visited canonical URLs from earlier runs of the SAME research question (continuation with
     * a fresh budget): none of them is navigated again, and none of them counts as a page of this run.
     */
    public void excludeVisited(java.util.Collection<String> canonicalUrls) {
        if (canonicalUrls != null) {
            for (String canonical : canonicalUrls) {
                progress.noteVisitedAlias(canonical);
            }
        }
    }

    /**
     * Run for a task: derive the research terms, delegate the deterministic acquisition to the
     * {@link com.aresstack.enterpriseai.research.runtime.acquire.WebSearchApplicationService}, then emit the run
     * summary and — for SUFFICIENT_EVIDENCE — PHASE_READY. Issue #32: acceptance records no findings
     * artifact anymore; the listener is a no-op like the manual search's.
     */
    public ResearchStopReason run(String task) {
        if (searchStrategy == null) {
            listener.status("run stopped: no search strategy");
            return ResearchStopReason.MCP_UNAVAILABLE;
        }
        com.aresstack.enterpriseai.research.runtime.acquire.WebSearchApplicationService acquisition =
                new com.aresstack.enterpriseai.research.runtime.acquire.WebSearchApplicationService(
                        browser, budget, progress, clock, listener, cancelled, searchStrategy, apiProviderLabel,
                        reranker, domainKeys, sourceAcceptancePort, startedAt, challengeProbeIntervalMillis,
                        new com.aresstack.enterpriseai.research.runtime.acquire.WebSearchApplicationService
                                .AcceptedSourceListener() {
                            public ResearchStopReason onAccepted(
                                    com.aresstack.enterpriseai.research.runtime.acquire.WebSearchApplicationService
                                            .AcceptedSource source,
                                    com.aresstack.enterpriseai.research.runtime.acquire.WebSearchApplicationService
                                            .ToolBudget budgetGate) {
                                // Acceptance already persisted the source (full text) — nothing derived here.
                                return null;
                            }
                        },
                        challengeWaitForUser,
                        0, // keine Lesbarkeitsprüfung: eine Quellenseite ist nach web_open lesbar
                        MIN_READABLE_CHARACTERS);
        if (scopeControl != null) {
            // SC1: the SAME WebSearchApplicationService gate + host scope service the manual
            // search uses — one filter truth for both acquisition paths.
            acquisition.setScopeControl(scopeControl);
        }
        ResearchStopReason reason = acquisition.execute(task);
        listener.status("run stopped: " + reason
                + " (pages=" + progress.getPagesVisited()
                + " sources=" + progress.getAcceptedSources()
                + " hosts=" + progress.getDistinctHosts().size() + ")");
        if (reason == ResearchStopReason.SUFFICIENT_EVIDENCE) {
            listener.phaseReady(reason); // an EVENT — the host decides about WAITING_APPROVAL
        }
        return reason;
    }

    /** The structured, user-facing result of this run (built from the final progress vs. the budget). */
    public ResearchRunOutcome outcome(ResearchStopReason reason) {
        return ResearchRunOutcome.from(reason, progress, budget);
    }

    // ------------------------------------------------------------------ pure text helpers (see WebAcquisitionText)
    // Thin delegators kept so existing callers and tests remain stable after the helper extraction (T2a Step 2).

    static Set<String> queryTerms(String task) {
        return com.aresstack.enterpriseai.research.runtime.acquire.WebAcquisitionText.queryTerms(task);
    }

    static String field(String result, String key) {
        return com.aresstack.enterpriseai.research.runtime.acquire.WebAcquisitionText.field(result, key);
    }

    static List<String> extractUrls(String text) {
        return com.aresstack.enterpriseai.research.runtime.acquire.WebAcquisitionText.extractUrls(text);
    }

    static String canonicalish(String url) {
        return com.aresstack.enterpriseai.research.runtime.acquire.WebAcquisitionText.canonicalish(url);
    }

    static String finalUrlOf(String page) {
        return com.aresstack.enterpriseai.research.runtime.acquire.WebAcquisitionText.finalUrlOf(page);
    }

    static String titleOf(String page) {
        return com.aresstack.enterpriseai.research.runtime.acquire.WebAcquisitionText.titleOf(page);
    }

    static String stripStatusLines(String results) {
        return com.aresstack.enterpriseai.research.runtime.acquire.WebAcquisitionText.stripStatusLines(results);
    }

    static List<String> providerHostsOf(String results) {
        return com.aresstack.enterpriseai.research.runtime.acquire.WebAcquisitionText.providerHostsOf(results);
    }

    static String hostOf(String url) {
        return com.aresstack.enterpriseai.research.runtime.acquire.WebAcquisitionText.hostOf(url);
    }

}
