package com.aresstack.enterpriseai.research.runtime.knowledge;

import com.aresstack.enterpriseai.research.runtime.json.MiniJson;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Research Agent's hierarchy exploration: when a read hit is a good but insufficient anchor, the agent opens the
 * area around it with {@value #TREE_TOOL} instead of starting yet another full-text search, picks the related pages
 * that look relevant for the task and reads them together with {@value #READ_MANY_TOOL}.
 *
 * <p>Not a discovery stage: it is triggered by a read hit, never by a query, and only {@link KnowledgeResearch} decides
 * when (after the {@link EvidenceJudge} rejected the evidence). Whether a hit has a hierarchy is whatever
 * {@code knowledge_tree} answers; a source without one fails the call and the hit is simply no anchor. No source,
 * space or page type is known here.
 */
public final class HierarchyExploration {

    public static final String TREE_TOOL = "knowledge_tree";
    public static final String READ_MANY_TOOL = "knowledge_read_many";

    /** Direct children asked per tree call. */
    static final int CHILD_LIMIT = 25;
    /** Related pages read per exploration. */
    static final int MAX_PAGES = 4;

    /** The model behind the page selection: {@code (system, user) -> answer}, empty when it gives nothing. */
    public interface Completion {
        String complete(String system, String user);
    }

    /** A read hit with the unread pages around it (children, then siblings). */
    static final class Anchor {
        final KnowledgeHit hit;
        final String source;
        final List<KnowledgeToolOutput.TreeNode> candidates;

        Anchor(KnowledgeHit hit, String source, List<KnowledgeToolOutput.TreeNode> candidates) {
            this.hit = hit;
            this.source = source;
            this.candidates = candidates;
        }
    }

    /** A related page that was read: the page as a hit and the raw read answer. */
    static final class Explored {
        final KnowledgeHit hit;
        final String readOutput;

        Explored(KnowledgeHit hit, String readOutput) {
            this.hit = hit;
            this.readOutput = readOutput;
        }
    }

    /**
     * The neighbourhood of one read hit, or {@code null} when it is no meaningful anchor: its source has no
     * hierarchy for it, or nothing around it is still unread.
     *
     * @param tried resource ids already read or tried in this research
     * @throws ToolInvoker.EndpointUnavailable when {@value #TREE_TOOL} is not offered at all
     */
    Anchor anchor(ToolInvoker tools, KnowledgeHit hit, Set<String> tried) throws ToolInvoker.EndpointUnavailable {
        KnowledgeToolOutput.TreeNeighbourhood around;
        try {
            around = KnowledgeToolOutput.parseTree(tools.call(TREE_TOOL, treeArgs(hit.id, "both")));
        } catch (ToolInvoker.ToolFailure noHierarchy) {
            return null; // expected for sources without a page tree: no anchor, no note
        }
        Set<String> excluded = new HashSet<String>(tried);
        excluded.add(hit.id);
        if (around.current != null) {
            excluded.add(around.current.id);
        }
        Map<String, KnowledgeToolOutput.TreeNode> candidates = new LinkedHashMap<String, KnowledgeToolOutput.TreeNode>();
        add(candidates, around.children, excluded);
        Map<String, Object> siblings = null;
        if (!around.parents.isEmpty()) {
            siblings = treeArgs(around.parents.get(around.parents.size() - 1).id, "children");
        } else if (around.current != null && !around.space.isEmpty()) {
            // a page directly below the space root: its siblings are the top-level pages of the space
            siblings = spaceArgs(around.source, around.space);
        }
        if (siblings != null) {
            try {
                add(candidates, KnowledgeToolOutput.parseTree(tools.call(TREE_TOOL, siblings)).children, excluded);
            } catch (ToolInvoker.ToolFailure siblingsUnavailable) {
                // the children alone still make the anchor
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return new Anchor(hit, around.source, new ArrayList<KnowledgeToolOutput.TreeNode>(candidates.values()));
    }

    /**
     * Picks up to {@value #MAX_PAGES} candidates for the task (the model by number, otherwise by title terms) and reads
     * them in one {@value #READ_MANY_TOOL} call, or one by one when that tool is not offered. Unreadable pages are
     * left out.
     */
    List<Explored> explore(ToolInvoker tools, Completion model, String task, Anchor anchor)
            throws ToolInvoker.EndpointUnavailable {
        List<KnowledgeToolOutput.TreeNode> chosen = select(model, task, anchor);
        List<String> ids = new ArrayList<String>();
        for (KnowledgeToolOutput.TreeNode node : chosen) {
            ids.add(node.id);
        }
        Map<Integer, String> texts;
        try {
            Map<String, Object> args = new LinkedHashMap<String, Object>();
            args.put("ids", ids);
            texts = KnowledgeToolOutput.readManyTexts(tools.call(READ_MANY_TOOL, args));
        } catch (ToolInvoker.ToolFailure noneReadable) {
            texts = Collections.emptyMap();
        } catch (ToolInvoker.EndpointUnavailable notOffered) {
            texts = readOneByOne(tools, ids);
        }
        List<Explored> explored = new ArrayList<Explored>();
        for (int i = 0; i < chosen.size(); i++) {
            String text = texts.get(Integer.valueOf(i));
            if (text == null || KnowledgeToolOutput.pageText(text).isEmpty()) {
                continue;
            }
            KnowledgeToolOutput.TreeNode node = chosen.get(i);
            explored.add(new Explored(new KnowledgeHit(node.id, node.title, "", "", "", anchor.source), text));
        }
        return explored;
    }

    private static Map<Integer, String> readOneByOne(ToolInvoker tools, List<String> ids)
            throws ToolInvoker.EndpointUnavailable {
        Map<Integer, String> texts = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < ids.size(); i++) {
            Map<String, Object> args = new LinkedHashMap<String, Object>();
            args.put("id", ids.get(i));
            try {
                texts.put(Integer.valueOf(i), tools.call(KnowledgeResearch.READ_TOOL, args));
            } catch (ToolInvoker.ToolFailure unreadable) {
                // the next page gets its chance
            }
        }
        return texts;
    }

    /** The model's choice by number; without a usable answer the candidates sharing most terms with the task. */
    List<KnowledgeToolOutput.TreeNode> select(Completion model, String task, Anchor anchor) {
        List<KnowledgeToolOutput.TreeNode> candidates = anchor.candidates;
        if (candidates.size() <= MAX_PAGES) {
            return candidates;
        }
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            list.append(i + 1).append(". ").append(candidates.get(i).title).append('\n');
        }
        String system = "Du bist der Recherche-Agent. Ein gefundener Treffer liegt in einem Seitenbaum. Wähle aus den "
                + "nummerierten benachbarten Seiten höchstens " + MAX_PAGES + " aus, die zum Auftrag voraussichtlich "
                + "Belege enthalten. Antworte NUR mit JSON: {\"pick\": [1, 2]}.";
        String user = "AUFTRAG: " + task + "\n\nTREFFER: " + anchor.hit.label() + "\n\nSEITEN:\n" + list;
        List<KnowledgeToolOutput.TreeNode> picked = parsePick(model.complete(system, user), candidates);
        return picked.isEmpty() ? byTerms(task, candidates) : picked;
    }

    @SuppressWarnings("unchecked")
    static List<KnowledgeToolOutput.TreeNode> parsePick(String answer, List<KnowledgeToolOutput.TreeNode> candidates) {
        List<KnowledgeToolOutput.TreeNode> picked = new ArrayList<KnowledgeToolOutput.TreeNode>();
        String text = answer == null ? "" : answer;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return picked;
        }
        Object parsed;
        try {
            parsed = MiniJson.parse(text.substring(start, end + 1));
        } catch (RuntimeException notJson) {
            return picked;
        }
        Object pick = parsed instanceof Map ? ((Map<String, Object>) parsed).get("pick") : null;
        if (!(pick instanceof List)) {
            return picked;
        }
        for (Object number : (List<Object>) pick) {
            int index;
            try {
                index = (int) Double.parseDouble(String.valueOf(number)) - 1;
            } catch (NumberFormatException notANumber) {
                continue;
            }
            if (index >= 0 && index < candidates.size() && !picked.contains(candidates.get(index))
                    && picked.size() < MAX_PAGES) {
                picked.add(candidates.get(index));
            }
        }
        return picked;
    }

    /** The candidates with most task terms in their title (stable for ties), at most {@value #MAX_PAGES}. */
    static List<KnowledgeToolOutput.TreeNode> byTerms(String task, List<KnowledgeToolOutput.TreeNode> candidates) {
        final List<String> terms = new ArrayList<String>();
        for (String term : ResearchQueries.keywordQueries(task, Integer.MAX_VALUE)) {
            if (term.indexOf(' ') < 0) {
                terms.add(term.toLowerCase(Locale.ROOT));
            }
        }
        final Map<KnowledgeToolOutput.TreeNode, Integer> score = new LinkedHashMap<KnowledgeToolOutput.TreeNode, Integer>();
        for (KnowledgeToolOutput.TreeNode node : candidates) {
            String title = node.title.toLowerCase(Locale.ROOT);
            int matches = 0;
            for (String term : terms) {
                if (title.contains(term)) {
                    matches++;
                }
            }
            score.put(node, Integer.valueOf(matches));
        }
        List<KnowledgeToolOutput.TreeNode> ranked = new ArrayList<KnowledgeToolOutput.TreeNode>(candidates);
        Collections.sort(ranked, (a, b) -> score.get(b).intValue() - score.get(a).intValue());
        return new ArrayList<KnowledgeToolOutput.TreeNode>(ranked.subList(0, Math.min(MAX_PAGES, ranked.size())));
    }

    private static void add(Map<String, KnowledgeToolOutput.TreeNode> candidates,
                            List<KnowledgeToolOutput.TreeNode> nodes, Set<String> excluded) {
        for (KnowledgeToolOutput.TreeNode node : nodes) {
            if (!excluded.contains(node.id) && !candidates.containsKey(node.id)) {
                candidates.put(node.id, node);
            }
        }
    }

    private static Map<String, Object> spaceArgs(String source, String space) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        if (!source.isEmpty()) {
            args.put("source", source);
        }
        args.put("space", space);
        args.put("direction", "children");
        args.put("limit", Integer.valueOf(CHILD_LIMIT));
        return args;
    }

    private static Map<String, Object> treeArgs(String id, String direction) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("id", id);
        args.put("direction", direction);
        args.put("limit", Integer.valueOf(CHILD_LIMIT));
        return args;
    }
}
