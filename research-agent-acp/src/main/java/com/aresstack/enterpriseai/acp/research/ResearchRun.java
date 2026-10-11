package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.loop.EnvironmentRunBudget;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchLoop;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchLoopClock;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchLoopListener;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchRunActivity;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchRunBudget;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchRunOutcome;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchRunProgress;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchStopReason;
import com.aresstack.enterpriseai.research.runtime.rerank.Bm25CandidateReranker;
import com.aresstack.enterpriseai.research.runtime.search.SingleProviderSearchStrategy;
import com.aresstack.enterpriseai.research.runtime.search.provider.SearchEngine;
import com.aresstack.enterpriseai.research.runtime.support.HostDomainKeyResolver;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Ein autonomer Recherche-Lauf (askai arch {@code runResearchLoop}): der {@link ResearchLoop} sucht über die
 * Wissenssuche des Hosts statt im Web, öffnet höchstens {@code agent.research.maxPages} Treffer per knowledge_read (Budget aus der
 * Umgebung, 0 = unbegrenzt) und übernimmt passende Seiten über {@code source_accept} als Quellen. Rangfolge
 * lexikalisch (BM25), solange der Client kein RERANK-Modell ausführt.
 */
final class ResearchRun {

    private static final Logger LOG = Logger.getLogger(ResearchRun.class.getName());

    private final ResearchHostControl host;
    private final Map<String, String> environment;
    private final AtomicBoolean cancelled;

    ResearchRun(ResearchHostControl host, Map<String, String> environment, AtomicBoolean cancelled) {
        this.host = host;
        this.environment = environment;
        this.cancelled = cancelled;
    }

    /**
     * Beim Sitzungsende. Der Lauf ist EIN Orchestrator-Thread; Vorladen und Priority-Lesen macht der Host hinter
     * {@code knowledge_search}/{@code knowledge_read} (wie MainframeMate), der Agent hält keinen eigenen Seitenpool.
     */
    void close() {
    }

    ResearchRunOutcome run(String task, final ResearchAgentTurns.Output output) {
        KnowledgePages pages = new KnowledgePages(host);
        ResearchRunBudget budget = EnvironmentRunBudget.from(environment);
        ResearchLoop loop = new ResearchLoop(pages, host, budget, new ResearchLoopClock() {
            @Override
            public long currentTimeMillis() {
                return System.currentTimeMillis();
            }

            @Override
            public void sleepMillis(long millis) {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }, new ResearchLoopListener() {
            @Override
            public void status(String message) {
                LOG.info("[research-run] " + message);
            }

            @Override
            public void progress(ResearchRunProgress progress, ResearchRunActivity activity) {
                if (activity == null) {
                    return;
                }
                if (ResearchRunActivity.SOURCE_ACCEPTED.equals(activity.getToken())) {
                    output.message("Quelle übernommen: " + label(activity));
                } else if (ResearchRunActivity.READING_PAGE.equals(activity.getToken())) {
                    LOG.info("[research-run] lese " + label(activity));
                }
            }

            @Override
            public void phaseReady(ResearchStopReason reason) {
                LOG.info("[research-run] genug Belege: " + reason);
            }

            @Override
            public void attention(String reason, String domainFamily, String url, boolean resolved) {
                LOG.info("[research-run] attention " + reason + " " + url);
            }
        }, cancelled);
        loop.setSearchStrategy(new SingleProviderSearchStrategy(new KnowledgeSearchProvider(pages),
                SearchEngine.PROVIDER_DEFAULT), "Wissensquellen");
        loop.setSourceAcceptancePort(pages.acceptance());
        loop.setReranker(new Bm25CandidateReranker());
        loop.setDomainKeyResolver(new HostDomainKeyResolver());
        LOG.info("[research-run] start pages<=" + limit(budget.getMaxPagesVisited())
                + " sources<=" + limit(budget.getMaxAcceptedSources()));
        ResearchStopReason reason = loop.run(task);
        return loop.outcome(reason);
    }

    /** Die Abschlusszeile für den Chat. */
    static String summary(ResearchRunOutcome outcome) {
        StringBuilder text = new StringBuilder("Recherche beendet: ")
                .append(outcome.getAcceptedSources()).append(" Quellen übernommen, ")
                .append(outcome.getPagesVisited()).append(" Seiten gelesen (")
                .append(reasonText(outcome.getStopReason())).append(").");
        return text.toString();
    }

    private static String reasonText(ResearchStopReason reason) {
        switch (reason) {
            case SUFFICIENT_EVIDENCE:
                return "genug Belege";
            case PAGE_BUDGET_EXHAUSTED:
                return "Seitenbudget erreicht";
            case SOURCE_BUDGET_EXHAUSTED:
                return "Quellenbudget erreicht";
            case TOOL_BUDGET_EXHAUSTED:
                return "Werkzeugbudget erreicht";
            case TIME_BUDGET_EXHAUSTED:
                return "Zeitbudget erreicht";
            case NO_RELEVANT_PATHS:
            case NO_SEMANTIC_MATCHES:
                return "keine weiteren passenden Treffer";
            case USER_CANCELLED:
                return "abgebrochen";
            case MCP_UNAVAILABLE:
                return "Wissenssuche nicht erreichbar";
            default:
                return reason.name();
        }
    }

    private static String label(ResearchRunActivity activity) {
        String title = activity.getPageTitle();
        return title == null || title.trim().isEmpty() ? activity.getUrl() : title.trim();
    }

    private static String limit(int value) {
        return value == ResearchRunBudget.UNLIMITED ? "unbegrenzt" : Integer.toString(value);
    }
}
