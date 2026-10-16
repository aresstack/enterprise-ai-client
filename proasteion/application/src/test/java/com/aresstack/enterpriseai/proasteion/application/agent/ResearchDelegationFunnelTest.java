package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.katagogion.McpContextResource;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import com.aresstack.enterpriseai.searchrun.api.CandidateEvent;
import com.aresstack.enterpriseai.searchrun.api.CandidateEventKind;
import com.aresstack.enterpriseai.searchrun.api.CandidateOccurrence;
import com.aresstack.enterpriseai.searchrun.api.CandidateOrigin;
import com.aresstack.enterpriseai.searchrun.api.FunnelResource;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeCandidate;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeFunnel;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRun;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRunPort;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code knowledge_research} counts its funnel (#155) with the same recorder as the chat turn: the live search run's
 * candidates are discovered, its reranked excerpts and every returned hit examined, the read pages relevant and the
 * provenance used.
 */
public class ResearchDelegationFunnelTest {

    private final Map<String, McpToolContribution> tools = new HashMap<String, McpToolContribution>();
    private final RunLog log = new RunLog();

    private void offer(String name, McpToolHandler handler) {
        tools.put(name, McpToolContribution.of(name, name, handler));
    }

    @Test
    public void researchFunnelComesFromTheRunLogTheHitsAndTheProvenance() {
        // the source received three candidates and reranked the excerpts of wiki:a and wiki:c
        final SearchRunId run = log.run("wiki", Arrays.asList(1, 3), "wiki:a", "wiki:b", "wiki:c");
        offer("knowledge_search", call -> McpToolResult.ok("Query: Vault\nSearch run: " + run
                + " (3 live candidates recorded; \"run candidate\" is a hit's number in that run)\n\n"
                + "[1] Vault Überblick\nid: wiki:a\nsource: wiki\nVault wird zentral betrieben.\n\n"
                + "[2] Vault Archiv\nid: wiki:b\nsource: wiki\nAltes.\n"));
        offer("knowledge_read", call -> "wiki:a".equals(call.getString("id"))
                ? McpToolResult.ok("title: Vault Überblick\nid: wiki:a\n\nVault wird zentral betrieben.",
                Collections.singletonList(new McpContextResource("wiki", "wiki:a", "Vault Überblick", null, null,
                        null)))
                : McpToolResult.ok(""));

        McpToolResult result = research();

        assertFalse(result.getText(), result.isError());
        KnowledgeFunnel funnel = log.funnels.values().iterator().next();
        assertEquals(3, funnel.discovered());
        assertEquals(Arrays.asList(new FunnelResource("wiki", "wiki:a"), new FunnelResource("wiki", "wiki:c"),
                new FunnelResource("wiki", "wiki:b")), funnel.examined());
        assertEquals(Collections.singletonList(new FunnelResource("wiki", "wiki:a")), funnel.relevant());
        assertEquals(Collections.singletonList(new FunnelResource("wiki", "wiki:a")), funnel.used());
        assertTrue(funnel.complete());
        assertTrue(result.getText(), result.getText().contains("Research funnel: " + funnel.id()
                + " (3 discovered, 3 examined, 1 relevant, 1 used)"));
        assertEquals("provenance unchanged", 1, result.contextResources().size());
    }

    @Test
    public void unreachableKnowledgeToolsStoreAnIncompleteFunnel() {
        McpToolResult result = research();

        assertTrue(result.isError());
        KnowledgeFunnel funnel = log.funnels.values().iterator().next();
        assertFalse(funnel.complete());
        assertEquals(0, funnel.examined().size());
    }

    private McpToolResult research() {
        MainModelChat model = new MainModelChat() {
            @Override
            public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxTokens) {
                String all = messages.toString();
                return MainModelChatResult.ok(all.contains("queries") ? "{\"queries\": [\"Vault\"]}"
                        : "Vault wird zentral betrieben [1].");
            }

            @Override
            public String modelName() {
                return "scripted";
            }
        };
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("task", "Wie ist der Betrieb von Vault organisiert?");
        return new ResearchDelegationTool(tools::get, model, log)
                .invoke(new McpToolCall(ResearchDelegationTool.NAME, args));
    }

    private static final class RunLog implements KnowledgeSearchRunPort {

        final Map<SearchRunId, KnowledgeSearchRun> runs = new LinkedHashMap<SearchRunId, KnowledgeSearchRun>();
        final List<CandidateEvent> events = new ArrayList<CandidateEvent>();
        final Map<SearchRunId, KnowledgeFunnel> funnels = new LinkedHashMap<SearchRunId, KnowledgeFunnel>();

        SearchRunId run(String source, List<Integer> scored, String... resources) {
            SourceAddress address = SourceAddress.of(source);
            List<KnowledgeCandidate> candidates = new ArrayList<KnowledgeCandidate>();
            for (String resource : resources) {
                candidates.add(new KnowledgeCandidate(candidates.size() + 1, address, KnowledgeResourceId.of(resource),
                        resource, "", null, KnowledgeRevision.unknown(), CandidateOrigin.SOURCE,
                        Collections.singletonList(new CandidateOccurrence(address, candidates.size()))));
            }
            KnowledgeSearchRun run = new KnowledgeSearchRun(SearchRunId.random(), "Vault", Instant.EPOCH,
                    Collections.singletonList(address), Collections.<SourceAddress, String>emptyMap(), candidates);
            runs.put(run.id(), run);
            for (int number : scored) {
                events.add(new CandidateEvent(run.id(), number, CandidateEventKind.EXCERPT_SCORED, Instant.EPOCH,
                        null, 0.5));
            }
            return run.id();
        }

        @Override
        public void record(KnowledgeSearchRun run) {
            runs.put(run.id(), run);
        }

        @Override
        public Optional<KnowledgeSearchRun> find(SearchRunId id) {
            return Optional.ofNullable(runs.get(id));
        }

        @Override
        public void append(CandidateEvent event) {
            events.add(event);
        }

        @Override
        public void appendAll(List<CandidateEvent> list) {
            events.addAll(list);
        }

        @Override
        public List<CandidateEvent> events(SearchRunId id) {
            List<CandidateEvent> result = new ArrayList<CandidateEvent>();
            for (CandidateEvent event : events) {
                if (event.run().equals(id)) {
                    result.add(event);
                }
            }
            return result;
        }

        @Override
        public void recordFunnel(KnowledgeFunnel funnel) {
            funnels.put(funnel.id(), funnel);
        }

        @Override
        public Optional<KnowledgeFunnel> funnel(SearchRunId id) {
            return Optional.ofNullable(funnels.get(id));
        }
    }
}
