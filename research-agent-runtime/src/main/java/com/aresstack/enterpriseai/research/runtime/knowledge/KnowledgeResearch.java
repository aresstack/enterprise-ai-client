package com.aresstack.enterpriseai.research.runtime.knowledge;

import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The Research Agent's mediation between a research task in natural language and the technical knowledge
 * operations. The caller (the normal chat, or the agent process) speaks natural language; this class speaks the
 * search language of the knowledge tools: it lets the model formulate one concise query (literal identifiers kept,
 * {@link ResearchQueries#RULES}), searches with {@code knowledge_search}, reads the best hits with {@code
 * knowledge_read}, searches once more with a materially different query only when nothing readable came back, and
 * writes the answer exclusively from the read excerpts, each statement tied to its source.
 *
 * <p>Fundamental rule: every task is searched. Model knowledge helps to understand the task, choose terms and write
 * up the evidence, but is never evidence itself; without read resources there is no model answer.
 *
 * <p>Talks only through the two existing seams of the research runtime, {@link ToolInvoker} (the knowledge tools of
 * the host, source-independent) and {@link MainModelChat}; it knows no source, no CQL and no project. Later research
 * steps (acronym or fact lookup, semantic retrieval, relations) belong here, behind the same {@link #research} call.
 */
public final class KnowledgeResearch {

    private static final Logger LOG = Logger.getLogger(KnowledgeResearch.class.getName());

    public static final String SEARCH_TOOL = "knowledge_search";
    public static final String READ_TOOL = "knowledge_read";

    /** Hits per search. */
    static final int HITS_PER_QUERY = 6;
    /** Resources read per task. */
    static final int MAX_READS = 3;
    /** The initial query and at most one refinement. */
    static final int MAX_SEARCHES = 2;
    private static final int PLAN_TOKENS = 200;
    private static final int ANSWER_TOKENS = 2000;

    private final ToolInvoker tools;
    private final MainModelChat model;

    public KnowledgeResearch(ToolInvoker tools, MainModelChat model) {
        if (tools == null || model == null) {
            throw new IllegalArgumentException("tools and model must not be null");
        }
        this.tools = tools;
        this.model = model;
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
        List<String> planned = plan(request);
        List<String> searched = new ArrayList<String>();
        Map<String, KnowledgeHit> found = new LinkedHashMap<String, KnowledgeHit>();
        List<KnowledgeHit> read = new ArrayList<KnowledgeHit>();
        List<String> excerpts = new ArrayList<String>();
        Set<String> tried = new HashSet<String>();
        String failure = null;
        for (String query : planned) {
            if (searched.size() >= MAX_SEARCHES || !read.isEmpty()) {
                break; // a refinement only when the evidence so far is insufficient
            }
            searched.add(query);
            List<KnowledgeHit> hits;
            try {
                hits = search(query);
            } catch (ToolInvoker.ToolFailure failed) {
                LOG.info("[research] search failed: " + failed.getMessage());
                failure = failed.getMessage();
                continue;
            }
            for (KnowledgeHit hit : hits) {
                if (!found.containsKey(hit.id)) {
                    found.put(hit.id, hit);
                }
            }
            for (KnowledgeHit hit : hits) {
                if (read.size() >= MAX_READS) {
                    break;
                }
                if (!tried.add(hit.id)) {
                    continue;
                }
                String page = readPage(hit.id);
                if (page == null || page.isEmpty()) {
                    continue;
                }
                read.add(hit);
                excerpts.add(ResearchQueries.excerpt(page, request + " " + query));
            }
        }
        List<KnowledgeHit> unread = new ArrayList<KnowledgeHit>();
        for (KnowledgeHit hit : found.values()) {
            if (!read.contains(hit)) {
                unread.add(hit);
            }
        }
        if (read.isEmpty()) {
            if (found.isEmpty() && failure != null) {
                return new KnowledgeResearchResult(request, searched, read, unread,
                        "The knowledge search failed: " + failure, KnowledgeResearchResult.Outcome.FAILED);
            }
            String text = found.isEmpty()
                    ? "No hits in the enabled knowledge sources. Do not answer from general knowledge; say that "
                    + "nothing was found and ask for other terms if useful."
                    : "Hits were found but none could be read; they are listed below as unverified hits.";
            return new KnowledgeResearchResult(request, searched, read, unread, text,
                    KnowledgeResearchResult.Outcome.NO_EVIDENCE);
        }
        return new KnowledgeResearchResult(request, searched, read, unread, answer(request, read, excerpts),
                KnowledgeResearchResult.Outcome.ANSWERED);
    }

    /**
     * The search expressions for the task: the model's concise initial query and one materially different
     * alternative; sentences the model may return are dropped; without a usable plan the literal terms of the task.
     */
    List<String> plan(String task) {
        String system = "Du bist der Recherche-Agent eines Unternehmens-Clients. Du formulierst Suchanfragen für die "
                + "Wissensquellen des Unternehmens (z. B. Confluence, Dateien), beantwortest aber nichts selbst. "
                + "Erkenne im Auftrag die starken wörtlichen Begriffe: Bezeichner, Abkürzungen, System-, Projekt- und "
                + "Produktnamen. " + ResearchQueries.RULES + " Antworte NUR mit JSON: {\"queries\": [\"...\", \"...\"]}. "
                + "Die erste Anfrage ist die eine prägnante Erstsuche mit den stärksten Begriffen; die zweite eine "
                + "wesentlich andere Anfrage, falls die erste nichts Brauchbares findet.";
        String answer = complete(system, "AUFTRAG: " + task, 0.0d, PLAN_TOKENS);
        List<String> parsed = ResearchQueries.parsePlan(answer, MAX_SEARCHES + 2);
        List<String> queries = new ArrayList<String>();
        if (parsed != null) {
            for (String query : parsed) {
                if (ResearchQueries.isConcise(query) && !containsIgnoreCase(queries, query)) {
                    queries.add(query);
                }
            }
        }
        if (queries.isEmpty()) {
            LOG.info("[research] no usable plan from the model; literal terms of the task");
            queries.add(ResearchQueries.literalQuery(task));
        }
        return queries.size() > MAX_SEARCHES ? new ArrayList<String>(queries.subList(0, MAX_SEARCHES)) : queries;
    }

    private List<KnowledgeHit> search(String query) throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("query", query);
        args.put("limit", Integer.valueOf(HITS_PER_QUERY));
        return KnowledgeToolOutput.parseHits(tools.call(SEARCH_TOOL, args));
    }

    /** The page text of a hit; {@code null} when this one hit cannot be read (the next one gets its chance). */
    private String readPage(String id) throws ToolInvoker.EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("id", id);
        try {
            return KnowledgeToolOutput.pageText(tools.call(READ_TOOL, args));
        } catch (ToolInvoker.ToolFailure unreadable) {
            LOG.info("[research] not readable: " + id + " " + unreadable.getMessage());
            return null;
        }
    }

    private String answer(String task, List<KnowledgeHit> read, List<String> excerpts) {
        StringBuilder sources = new StringBuilder();
        for (int i = 0; i < read.size(); i++) {
            sources.append('[').append(i + 1).append("] ").append(read.get(i).label()).append(":\n")
                    .append(excerpts.get(i)).append("\n\n");
        }
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
        return "The model did not summarize the sources; the relevant excerpts:\n\n" + sources.toString().trim();
    }

    private String complete(String system, String user, double temperature, int maxTokens) {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.system(system));
        messages.add(ChatMessage.user(user));
        MainModelChatResult result;
        try {
            result = model.complete(messages, temperature, maxTokens);
        } catch (RuntimeException failed) {
            LOG.warning("[research] model failed: " + failed);
            return "";
        }
        if (result == null || !result.isOk()) {
            LOG.warning("[research] model failed: " + (result == null ? "no result"
                    : result.getStatus() + " " + result.getDetail()));
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
