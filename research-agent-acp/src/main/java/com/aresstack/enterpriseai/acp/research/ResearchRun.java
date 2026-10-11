package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.acquire.ServiceSearchScopeControlPort;
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

import java.util.LinkedHashMap;
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
                report(activity.getToken(), activity.getSearchQuery(), activity.getPageTitle(), activity.getUrl(),
                        progress);
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
        KnowledgeSearchProvider provider = new KnowledgeSearchProvider(pages);
        final ResearchLoop running = loop;
        provider.setPlan(searchPlan(), new java.util.function.Consumer<String>() {
            @Override
            public void accept(String query) {
                report("SEARCHING", query, "", "", running.getProgress());
            }
        });
        loop.setSearchStrategy(new SingleProviderSearchStrategy(provider, SearchEngine.PROVIDER_DEFAULT),
                "Wissensquellen");
        loop.setSourceAcceptancePort(pages.acceptance());
        loop.setReranker(new Bm25CandidateReranker());
        loop.setDomainKeyResolver(new HostDomainKeyResolver());
        // Der Weidezaun begrenzt den Lauf (askai arch SC1): der Host friert ihn ein und misst die Treffer dagegen.
        loop.setScopeControl(new ServiceSearchScopeControlPort(host));
        LOG.info("[research-run] start pages<=" + limit(budget.getMaxPagesVisited())
                + " sources<=" + limit(budget.getMaxAcceptedSources()));
        report("STARTED", firstLine(task), "", "", loop.getProgress());
        ResearchStopReason reason = loop.run(task);
        return loop.outcome(reason);
    }

    /**
     * Meldet dem Host das Ende des Laufs: er geht in die Freigabe der Quellen (REQUEST_EVIDENCE_REVIEW) und
     * liefert die Zusammenfassung für den Nutzer; ohne Host bleibt die eigene Abschlusszeile.
     */
    String finish(ResearchRunOutcome outcome) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("reason", outcome.getStopReason().name());
        args.put("pages", Integer.valueOf(outcome.getPagesVisited()));
        args.put("accepted", Integer.valueOf(outcome.getAcceptedSources()));
        try {
            String summary = host.call("research_run_finished", args);
            return summary == null || summary.trim().isEmpty() ? summary(outcome) : summary;
        } catch (Exception unavailable) {
            LOG.warning("[research-run] Ende nicht gemeldet: " + unavailable.getMessage());
            return summary(outcome);
        }
    }

    /** Die Suchen aus Konzept und Weidezaun; leer, wenn der Host keine liefert (dann sucht der Auftrag selbst). */
    private java.util.List<String> searchPlan() {
        java.util.List<String> queries = new java.util.ArrayList<String>();
        try {
            for (String line : host.call("research_search_plan", null).split("\r?\n")) {
                if (!line.trim().isEmpty()) {
                    queries.add(line.trim());
                }
            }
        } catch (Exception unavailable) {
            LOG.info("[research-run] kein Suchplan: " + unavailable.getMessage());
        }
        LOG.info("[research-run] Suchen: " + queries);
        return queries;
    }

    /** Fortschritt an den Host (Anzeige im Client); ein Fehler hier hält den Lauf nie an. */
    private void report(String activity, String query, String title, String url, ResearchRunProgress progress) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("activity", activity == null ? "" : activity);
        args.put("query", query == null ? "" : query);
        args.put("title", title == null ? "" : title);
        args.put("url", url == null ? "" : url);
        args.put("pages", Integer.valueOf(progress.getPagesVisited()));
        args.put("accepted", Integer.valueOf(progress.getAcceptedSources()));
        try {
            host.call("research_progress", args);
        } catch (Exception unavailable) {
            LOG.fine("[research-run] Fortschritt nicht gemeldet: " + unavailable.getMessage());
        }
    }

    private static String firstLine(String text) {
        String line = text == null ? "" : text.trim();
        int end = line.indexOf('\n');
        line = end < 0 ? line : line.substring(0, end);
        return line.length() > 160 ? line.substring(0, 160) + "…" : line;
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
