package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SourceCalls;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.searchrun.api.CandidateEvent;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRun;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRunPort;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** #153 review: a live hit names its own number in the recorded run, whatever its position in the answer. */
public class KnowledgeSearchRunNumberingTest {

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("a", "Drucker A", "Drucker im Erdgeschoss")
            .add("b", "Drucker B", "Drucker im ersten Stock");
    private final KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(
            new KnowledgeSourceRegistration(wiki, SourceScope.of("a", "b")));
    private final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings,
            embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
    /** Prefetching never loads here, so the live hits stay live hits instead of racing into the index. */
    private final KnowledgePrefetchService prefetch = new KnowledgePrefetchService(indexing,
            (owner, source, id) -> {
                throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNAVAILABLE, "not in this test");
            }, KnowledgePrefetchService.Settings::defaults);
    private final Map<SearchRunId, KnowledgeSearchRun> runs = new LinkedHashMap<SearchRunId, KnowledgeSearchRun>();

    @After
    public void stop() {
        prefetch.shutdown();
    }

    @Test
    public void liveHitsNameTheirRunCandidateNumber() {
        SearchDrivenIndexingUseCase live = new SearchDrivenIndexingUseCase(prefetch, SourceCalls.direct(), port());
        KnowledgeMcpTools tools = new KnowledgeMcpTools(
                new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(), null),
                new LoadKnowledgeDocumentUseCase(catalog), live, null);
        McpToolContribution search = tools.knowledgeSearch();
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("query", "Drucker");
        String text = search.getHandler().invoke(new McpToolCall(search.getName(), arguments)).getText();

        assertEquals(1, runs.size());
        KnowledgeSearchRun run = runs.values().iterator().next();
        assertTrue(text, text.contains("Search run: " + run.id().value()));
        Matcher block = Pattern.compile("id: (\\S+)\\n(?:url: .*\\n)?source: .*\\(not read yet\\)\\nrun candidate: (\\d+)")
                .matcher(text);
        int checked = 0;
        while (block.find()) {
            int number = Integer.parseInt(block.group(2));
            assertEquals(block.group(1), run.candidate(number).get().resourceId().value());
            checked++;
        }
        assertTrue(text, checked > 0);
    }

    private KnowledgeSearchRunPort port() {
        return new KnowledgeSearchRunPort() {
            @Override
            public synchronized void record(KnowledgeSearchRun run) {
                runs.put(run.id(), run);
            }

            @Override
            public synchronized Optional<KnowledgeSearchRun> find(SearchRunId id) {
                return Optional.ofNullable(runs.get(id));
            }

            @Override
            public void append(CandidateEvent event) {
            }

            @Override
            public void appendAll(List<CandidateEvent> events) {
            }

            @Override
            public List<CandidateEvent> events(SearchRunId id) {
                return new ArrayList<CandidateEvent>();
            }
        };
    }
}
