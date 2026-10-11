package com.aresstack.enterpriseai.research.runtime.service;

import com.aresstack.enterpriseai.research.runtime.support.CancellationSignal;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchRunWire;
import com.aresstack.enterpriseai.research.runtime.search.InitialSearchRequest;
import com.aresstack.enterpriseai.research.runtime.search.InitialSearchResult;
import com.aresstack.enterpriseai.research.runtime.search.SearchBudgetGate;
import com.aresstack.enterpriseai.research.runtime.search.SearchStrategy;

import java.util.function.BooleanSupplier;

/**
 * Executes a USER-triggered web search on the SAME productive {@link SearchStrategy} the autonomous research
 * loop uses — never a second provider implementation. It emits typed {@code #RSX1# manual_search_*} events
 * carrying the {@code requestId} and honours the session's cancel flag. It is deliberately free of any host
 * state / phase / TeamAgent / model dependency: a manual search is phase-independent by construction (there is
 * no {@code phase == RESEARCH} gate here — that gate belongs only to the agent's own MCP web-search tool).
 *
 * <p>Extracted from {@code ResearchAgentMain} so it is unit-testable with a fake strategy and a capturing
 * emitter, without an ACP context.</p>
 */
public final class ManualSearchHandler {

    /** Sends one encoded {@code #RSX1#} wire line back to the host (the same channel the loop uses). */
    public interface Emitter {
        void emit(String wireLine);
    }

    /** Result depth for a manual search; provider/country stay in the productive strategy config. */
    private static final int RESULT_COUNT = 10;

    private final SearchStrategy strategy; // null → this session has no productive search backend
    private final BooleanSupplier cancelled;

    private java.util.function.Consumer<String> diagnostics = new java.util.function.Consumer<String>() {
        @Override
        public void accept(String line) {
        }
    };

    /** Diagnosezeilen (askai schrieb sie auf STDERR); der Agentenprozess hängt sein Log an. */
    public void setDiagnostics(java.util.function.Consumer<String> diagnostics) {
        if (diagnostics != null) {
            this.diagnostics = diagnostics;
        }
    }

    public ManualSearchHandler(SearchStrategy strategy, BooleanSupplier cancelled) {
        this.strategy = strategy;
        this.cancelled = cancelled;
    }

    /**
     * Run the search and stream {@code started → completed | failed}. A missing strategy or a technical
     * failure is an HONEST, visible failure event — never a silent no-op and never a fallback to a different
     * strategy. Cancellation surfaces as a {@code CANCELLED} failure so late results of an aborted run are
     * distinguishable by the host via the {@code requestId}.
     *
     * @param languageCode the IMMUTABLE language snapshot ("en"/"de") of this search — it overrides the
     *                     provider's configured default language; {@code null}/empty keeps the provider
     *                     default (legacy hosts without a language field). The country is deliberately NOT
     *                     derived from the language.
     */
    public void handle(String requestId, String query, String languageCode, Emitter emitter) {
        diagnostics.accept("[manual-search] execute started requestId=" + requestId
                + " strategy=" + (strategy == null ? "unavailable" : strategy.getClass().getSimpleName()));
        emitter.emit(ResearchRunWire.manualSearchStarted(requestId, query));
        if (query == null || query.trim().isEmpty()) {
            diagnostics.accept("[manual-search] failed stage=validate reason=EMPTY_QUERY requestId=" + requestId);
            emitter.emit(ResearchRunWire.manualSearchFailed(requestId, "EMPTY_QUERY"));
            return;
        }
        if (strategy == null) {
            // The legacy-browser default strategy is built INSIDE the research loop per run, so it is not
            // available to a control-turn handler; only an API_PROVIDER config sets a session-scoped strategy.
            diagnostics.accept("[manual-search] failed stage=strategy reason=SEARCH_UNAVAILABLE requestId="
                    + requestId + " (no session-scoped SearchStrategy outside the research loop)");
            emitter.emit(ResearchRunWire.manualSearchFailed(requestId, "SEARCH_UNAVAILABLE"));
            return;
        }
        try {
            InitialSearchResult result = strategy.search(
                    new InitialSearchRequest(query.trim(), RESULT_COUNT,
                            languageCode == null || languageCode.isEmpty() ? null : languageCode,
                            null),
                    new CancellationSignal() {
                        public boolean isCancelled() {
                            return cancelled.getAsBoolean();
                        }
                    },
                    new SearchBudgetGate() {
                        public boolean beforeToolCall() {
                            return !cancelled.getAsBoolean();
                        }
                    });
            if (cancelled.getAsBoolean()) {
                diagnostics.accept("[manual-search] failed stage=execute reason=CANCELLED requestId=" + requestId);
                emitter.emit(ResearchRunWire.manualSearchFailed(requestId, "CANCELLED"));
                return;
            }
            int count = result == null || result.candidates == null ? 0 : result.candidates.size();
            String status = result == null || result.status == null ? "" : result.status.name();
            diagnostics.accept("[manual-search] execute completed requestId=" + requestId
                    + " hits=" + count + " status=" + status);
            emitter.emit(ResearchRunWire.manualSearchCompleted(requestId, count, status));
        } catch (Exception failure) {
            diagnostics.accept("[manual-search] failed stage=execute requestId=" + requestId
                    + " message=" + failure.getClass().getSimpleName());
            emitter.emit(ResearchRunWire.manualSearchFailed(requestId,
                    cancelled.getAsBoolean() ? "CANCELLED" : "SEARCH_FAILED"));
        }
    }
}
