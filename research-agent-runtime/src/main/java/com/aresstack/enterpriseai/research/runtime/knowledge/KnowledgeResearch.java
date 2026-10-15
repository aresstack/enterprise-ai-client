package com.aresstack.enterpriseai.research.runtime.knowledge;

import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Research Agent's mediation between a research task in natural language and the technical knowledge
 * operations. The caller (the normal chat, or the agent process) speaks natural language; this class speaks the
 * search language of the knowledge tools. It runs its {@link DiscoveryStrategy stages} in order (by default
 * {@link LexicalDiscoveryStrategy}: one concise query, literal identifiers kept, planned by the model, searched with
 * {@code knowledge_search}): after each lookup it reads the best hits with {@code knowledge_read} and lets the
 * {@link EvidenceJudge} decide whether the read evidence answers the task; only when it does not, the next lookup or
 * the next stage runs. A stage whose tool is not offered is skipped. The answer is written exclusively from the read
 * excerpts, each statement tied to its source.
 *
 * <p>Fundamental rule: every task is searched. Model knowledge helps to understand the task, choose terms and write
 * up the evidence, but is never evidence itself; without read resources there is no model answer.
 *
 * <p>Talks only through the two existing seams of the research runtime, {@link ToolInvoker} (the knowledge tools of
 * the host, source-independent) and {@link MainModelChat}; it knows no source, no CQL and no project. Later research
 * steps (acronym or fact lookup, semantic retrieval, relations) belong here, behind the same {@link #research} call.
 */
public final class KnowledgeResearch {

    public static final String SEARCH_TOOL = "knowledge_search";
    public static final String READ_TOOL = "knowledge_read";

    /** Hits per search. */
    static final int HITS_PER_QUERY = 6;
    /** Resources read per search. */
    static final int MAX_READS = 3;
    /** The initial query and at most one refinement. */
    static final int MAX_SEARCHES = 2;
    private static final int PLAN_TOKENS = 200;
    private static final int ANSWER_TOKENS = 2000;

    private static final int JUDGE_TOKENS = 10;

    private final ToolInvoker tools;
    private final MainModelChat model;
    private final List<DiscoveryStrategy> stages;
    private final EvidenceJudge judge;

    /** With the default stages ({@link #defaultStages()}) and the model as evidence judge. */
    public KnowledgeResearch(ToolInvoker tools, MainModelChat model) {
        this(tools, model, defaultStages(), null);
    }

    /**
     * @param strategy how queries are formulated (the only stage)
     * @param judge    whether read evidence suffices; {@code null} for {@link #modelJudge}
     */
    public KnowledgeResearch(ToolInvoker tools, MainModelChat model, DiscoveryStrategy strategy, EvidenceJudge judge) {
        this(tools, model, strategy == null ? null : Collections.singletonList(strategy), judge);
    }

    /**
     * @param stages the discovery stages in the order they run, cheap ones first
     * @param judge  whether read evidence suffices; {@code null} for {@link #modelJudge}
     */
    public KnowledgeResearch(ToolInvoker tools, MainModelChat model, List<DiscoveryStrategy> stages,
                             EvidenceJudge judge) {
        if (tools == null || model == null || stages == null || stages.isEmpty() || stages.contains(null)) {
            throw new IllegalArgumentException("tools, model and stages must not be null or empty");
        }
        this.tools = tools;
        this.model = model;
        this.stages = Collections.unmodifiableList(new ArrayList<DiscoveryStrategy>(stages));
        this.judge = judge == null ? modelJudge() : judge;
    }

    /**
     * The stages of the Research Agent today: first the cheap lookup of acronym-like terms in the structured findings
     * ({@link TermLookupStrategy}), then the lexical search ({@link LexicalDiscoveryStrategy}).
     */
    public static List<DiscoveryStrategy> defaultStages() {
        return Collections.unmodifiableList(java.util.Arrays.<DiscoveryStrategy>asList(new TermLookupStrategy(),
                new LexicalDiscoveryStrategy()));
    }

    /**
     * Researches the task in the knowledge sources.
     *
     * @throws ToolInvoker.EndpointUnavailable when the knowledge tools cannot be reached at all
     */
    public KnowledgeResearchResult research(String task) throws ToolInvoker.EndpointUnavailable {
        String request = task == null ? "" : task.trim();
        if (request.isEmpty()) {
            throw new IllegalArgumentException("task must not be blank");
        }
        List<String> searched = new ArrayList<String>();
        Map<String, KnowledgeHit> found = new LinkedHashMap<String, KnowledgeHit>();
        List<KnowledgeHit> read = new ArrayList<KnowledgeHit>();
        List<String> excerpts = new ArrayList<String>();
        Set<String> tried = new HashSet<String>();
        String failure = null;
        java.util.LinkedHashSet<String> notes = new java.util.LinkedHashSet<String>();
        ToolInvoker.EndpointUnavailable unavailable = null;
        boolean reached = false;
        boolean answered = false; // at least one lookup returned an answer (possibly without hits)
        List<String> failures = new ArrayList<String>();
        stages:
        for (int stage = 0; stage < stages.size(); stage++) {
            DiscoveryStrategy strategy = stages.get(stage);
            List<String> lookups = lookups(strategy, request);
            for (int index = 0; index < lookups.size(); index++) {
                String query = lookups.get(index);
                List<KnowledgeHit> hits;
                try {
                    String output = tools.call(strategy.tool(), strategy.arguments(query, HITS_PER_QUERY));
                    reached = true;
                    answered = true;
                    searched.add(strategy.label(query));
                    notes.addAll(KnowledgeToolOutput.notes(output));
                    hits = KnowledgeToolOutput.parseHits(output);
                } catch (ToolInvoker.ToolFailure failed) {
                    reached = true;
                    searched.add(strategy.label(query));
                    failure = failed.getMessage();
                    failures.add(strategy.label(query) + " failed: " + failed.getMessage());
                    continue;
                } catch (ToolInvoker.EndpointUnavailable notOffered) {
                    unavailable = notOffered; // this stage cannot run here; the next one may
                    continue stages;
                }
                for (KnowledgeHit hit : hits) {
                    if (!found.containsKey(hit.id)) {
                        found.put(hit.id, hit);
                    }
                }
                int readNow = 0;
                for (KnowledgeHit hit : hits) {
                    if (readNow >= MAX_READS) {
                        break;
                    }
                    if (!tried.add(hit.id)) {
                        continue;
                    }
                    String output = readOutput(hit.id);
                    String page = output == null ? "" : KnowledgeToolOutput.pageText(output);
                    if (page.isEmpty()) {
                        continue;
                    }
                    read.add(described(hit, output));
                    readNow++;
                    excerpts.add(ResearchQueries.excerpt(page, request + " " + query));
                }
                boolean lookupLeft = index + 1 < lookups.size() || stage + 1 < stages.size();
                if (!read.isEmpty() && (!lookupLeft || judge.sufficient(request, read, excerpts))) {
                    break stages; // a further lookup only when the evidence so far does not answer the task
                }
            }
        }
        if (!reached && unavailable != null) {
            throw unavailable;
        }
        if (answered) {
            // a failed lookup next to answered ones leaves the research incomplete, never "nothing found"
            notes.addAll(failures);
        }
        Set<String> readIds = new HashSet<String>();
        for (KnowledgeHit hit : read) {
            readIds.add(hit.id);
        }
        List<KnowledgeHit> unread = new ArrayList<KnowledgeHit>();
        for (KnowledgeHit hit : found.values()) {
            if (!readIds.contains(hit.id)) {
                unread.add(hit);
            }
        }
        if (read.isEmpty()) {
            if (found.isEmpty() && failure != null && !answered) {
                return new KnowledgeResearchResult(request, searched, notes, read, unread,
                        "The knowledge search failed: " + failure, KnowledgeResearchResult.Outcome.FAILED);
            }
            String text = found.isEmpty() && !notes.isEmpty()
                    ? "No hits, but not every knowledge source could be searched (see notes). This is no evidence "
                    + "that nothing exists; say that the search was incomplete."
                    : found.isEmpty()
                    ? "No hits in the enabled knowledge sources. Do not answer from general knowledge; say that "
                    + "nothing was found and ask for other terms if useful."
                    : "Hits were found but none could be read; they are listed below as unverified hits.";
            return new KnowledgeResearchResult(request, searched, notes, read, unread, text,
                    KnowledgeResearchResult.Outcome.NO_EVIDENCE);
        }
        return new KnowledgeResearchResult(request, searched, notes, read, unread, answer(request, read, excerpts),
                KnowledgeResearchResult.Outcome.ANSWERED);
    }

    /**
     * The search expressions for the task: the model's concise initial query and one materially different
     * alternative; sentences the model may return are dropped; without a usable plan the literal terms of the task.
     */
    List<String> plan(String task) {
        for (DiscoveryStrategy stage : stages) {
            if (stage.planned()) {
                return plan(task, stage);
            }
        }
        return plan(task, stages.get(0));
    }

    /** The lookups of one stage: planned by the model, or derived from the task; at most {@link #MAX_SEARCHES}. */
    private List<String> lookups(DiscoveryStrategy strategy, String task) {
        if (strategy.planned()) {
            return plan(task, strategy);
        }
        List<String> queries = new ArrayList<String>();
        for (String query : strategy.directQueries(task)) {
            if (query != null && !query.trim().isEmpty() && !containsIgnoreCase(queries, query.trim())
                    && queries.size() < MAX_SEARCHES) {
                queries.add(query.trim());
            }
        }
        return queries;
    }

    private List<String> plan(String task, DiscoveryStrategy strategy) {
        String system = "Du bist der Recherche-Agent eines Unternehmens-Clients. Du formulierst Suchanfragen für die "
                + "Wissensquellen des Unternehmens (z. B. Confluence, Dateien), beantwortest aber nichts selbst. "
                + "Erkenne im Auftrag die starken wörtlichen Begriffe: Bezeichner, Abkürzungen, System-, Projekt- und "
                + "Produktnamen. " + strategy.planningRules() + " Antworte NUR mit JSON: {\"queries\": [\"...\", \"...\"]}. "
                + "Die erste Anfrage ist die eine prägnante Erstsuche mit den stärksten Begriffen; die zweite eine "
                + "wesentlich andere Anfrage, falls die erste nichts Brauchbares findet.";
        String answer = complete(system, "AUFTRAG: " + task, 0.0d, PLAN_TOKENS);
        List<String> parsed = ResearchQueries.parsePlan(answer, MAX_SEARCHES + 2);
        List<String> queries = new ArrayList<String>();
        if (parsed != null) {
            for (String query : parsed) {
                if (strategy.accepts(query) && !containsIgnoreCase(queries, query)) {
                    queries.add(query);
                }
            }
        }
        if (queries.isEmpty()) {
            queries.add(strategy.fallbackQuery(task));
        }
        return queries.size() > MAX_SEARCHES ? new ArrayList<String>(queries.subList(0, MAX_SEARCHES)) : queries;
    }

    /**
     * The default judge: the model answers whether the numbered excerpts answer the task (JA/NEIN). Without a usable
     * verdict the read evidence counts as sufficient, so a failing model never causes extra searches.
     */
    EvidenceJudge modelJudge() {
        return new EvidenceJudge() {
            @Override
            public boolean sufficient(String task, List<KnowledgeHit> read, List<String> excerpts) {
                String system = "Du prüfst Rechercheergebnisse. Antworte NUR mit JA oder NEIN: Beantworten die "
                        + "Auszüge den Auftrag inhaltlich? NEIN, wenn sie das Thema nur streifen oder Wesentliches "
                        + "fehlt.";
                String verdict = complete(system, "AUFTRAG: " + task + "\n\nAUSZÜGE:\n" + numbered(read, excerpts),
                        0.0d, JUDGE_TOKENS).trim().toUpperCase(java.util.Locale.ROOT);
                return !verdict.startsWith("NEIN") && !verdict.startsWith("NO");
            }
        };
    }

    private static String numbered(List<KnowledgeHit> read, List<String> excerpts) {
        StringBuilder sources = new StringBuilder();
        for (int i = 0; i < read.size(); i++) {
            sources.append('[').append(i + 1).append("] ").append(read.get(i).label()).append(":\n")
                    .append(excerpts.get(i)).append("\n\n");
        }
        return sources.toString();
    }

    /**
     * The hit with title and address from the read header when the lookup named only the resource (a stage such as
     * {@link TermLookupStrategy} knows ids, not titles), so the sources are cited by name.
     */
    private static KnowledgeHit described(KnowledgeHit hit, String readOutput) {
        boolean untitled = hit.title.isEmpty() || hit.title.equals(hit.id);
        String title = untitled ? KnowledgeToolOutput.header(readOutput, "title") : hit.title;
        String url = hit.url.isEmpty() ? KnowledgeToolOutput.header(readOutput, "url") : hit.url;
        if (title.equals(hit.title) && url.equals(hit.url)) {
            return hit;
        }
        return new KnowledgeHit(hit.id, title.isEmpty() ? hit.title : title, hit.snippet, url, hit.passage,
                hit.source);
    }

    /** The answer of {@code knowledge_read}; {@code null} when this one hit cannot be read (the next one gets its chance). */
    private String readOutput(String id) throws ToolInvoker.EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("id", id);
        try {
            return tools.call(READ_TOOL, args);
        } catch (ToolInvoker.ToolFailure unreadable) {
            return null;
        }
    }

    private String answer(String task, List<KnowledgeHit> read, List<String> excerpts) {
        String sources = numbered(read, excerpts);
        String system = "Du bist der Recherche-Agent. Gib wieder, was die nummerierten Auszüge aus den Wissensquellen "
                + "zum Auftrag sagen, ausschließlich aus diesen Auszügen, in der Sprache des Auftrags. Belege jede "
                + "Aussage am Satzende mit [n]. Nenne Namen, Bezeichner und Termine genau wie in den Auszügen. Erfinde "
                + "nichts und ergänze nichts aus eigenem Wissen. Fehlt etwas, sag knapp, was die Quellen nicht "
                + "beantworten.";
        String text = ResearchQueries.clean(complete(system, "AUFTRAG: " + task + "\n\nAUSZÜGE:\n" + sources,
                0.2d, ANSWER_TOKENS));
        if (!text.isEmpty()) {
            return text;
        }
        // Without a model answer the evidence itself is the result: the excerpts, each with its number.
        return "The model did not summarize the sources; the relevant excerpts:\n\n" + sources.trim();
    }

    private String complete(String system, String user, double temperature, int maxTokens) {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.system(system));
        messages.add(ChatMessage.user(user));
        MainModelChatResult result;
        try {
            result = model.complete(messages, temperature, maxTokens);
        } catch (RuntimeException failed) {
            return "";
        }
        if (result == null || !result.isOk()) {
            return "";
        }
        return result.getText() == null ? "" : result.getText();
    }

    private static boolean containsIgnoreCase(List<String> queries, String query) {
        for (String existing : queries) {
            if (existing.equalsIgnoreCase(query)) {
                return true;
            }
        }
        return false;
    }
}
