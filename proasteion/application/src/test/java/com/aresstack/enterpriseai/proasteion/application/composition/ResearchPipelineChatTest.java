package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.astu.acropolis.passage.SentenceSegmenter;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymExtractor;
import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymSettings;
import com.aresstack.enterpriseai.astu.acropolis.textmining.DocumentAcronymsUseCase;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolContextResource;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolOutput;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.fact.h2.H2FactStore;
import com.aresstack.enterpriseai.proasteion.application.agent.KnowledgeTermLookupTool;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchDelegationTool;
import com.aresstack.enterpriseai.proasteion.application.knowledge.FactCatalogTextFindings;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The staged research behind {@code knowledge_research}: an acronym whose long form the metadata catalog (embedded
 * H2) already holds is answered after one term lookup and one read, without a source search; otherwise the task
 * falls through to the lexical search. Provenance stays structured in both cases. Long forms are invented.
 */
public class ResearchPipelineChatTest {

    private static final String TASK = "Finde heraus, welche Bedeutung IRR im internen Unternehmenswissen hat.";
    private static final String GUIDELINE = "Die Interne Revisionsrichtlinie (IRR) regelt die Prüfungen im Haus.";

    private static final class ScriptedModel implements MainModelChat {
        final List<String> answers;
        final List<String> prompts = new ArrayList<String>();

        ScriptedModel(String... answers) {
            this.answers = new ArrayList<String>(Arrays.asList(answers));
        }

        @Override
        public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxOutputTokens) {
            prompts.add(messages.get(messages.size() - 1).getContent());
            return answers.isEmpty() ? MainModelChatResult.failure(MainModelChatResult.Status.PROVIDER_FAILURE, "-")
                    : MainModelChatResult.ok(answers.remove(0));
        }

        @Override
        public String modelName() {
            return "scripted";
        }
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("Richtlinien", "Richtlinien", GUIDELINE)
            .add("Pruefplan", "Prüfplan 2026", "Der Prüfplan nennt die IRR als Grundlage.");
    private final KnowledgeSourceCatalog live = KnowledgeSourceCatalog.of(
            new KnowledgeSourceRegistration(wiki, SourceScope.of("Richtlinien", "Pruefplan")));
    private final ToolCatalog catalog = new ToolCatalog();
    private H2FactStore facts;
    private IndexKnowledgeUseCase indexing;

    @Before
    public void setUp() throws Exception {
        facts = new H2FactStore(folder.newFolder("facts").toPath(), 2000);
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        indexing = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        final KnowledgeMcpTools knowledge = new KnowledgeMcpTools(new RetrieveKnowledgeUseCase(index, embeddings,
                embeddings.modelIdentity(), null), new LoadKnowledgeDocumentUseCase(live).withCatalog(() -> live),
                new SearchDrivenIndexingUseCase(indexing), null);
        catalog.register("knowledge", EnumSet.of(ToolChannel.AGENT), knowledge::contributions);
        catalog.register("index", EnumSet.of(ToolChannel.AGENT),
                () -> Collections.singletonList(knowledge.knowledgeIndexSearch()));
        final KnowledgeTermLookupTool terms = new KnowledgeTermLookupTool(facts);
        catalog.register("terms", EnumSet.of(ToolChannel.AGENT),
                () -> Collections.singletonList(terms.contribution()));
    }

    @After
    public void tearDown() throws Exception {
        facts.close();
    }

    /** The acronym analysis of the reader stored the guideline's definition in the catalog earlier. */
    private void guidelineAnalysed() {
        KnowledgeResourceId id = wiki.idOf("Richtlinien");
        new DocumentAcronymsUseCase(new AcronymExtractor(SentenceSegmenter.splitter()), AcronymSettings::defaults,
                new FactCatalogTextFindings(facts), () -> true).analyze(KnowledgeDocument.of(
                KnowledgeResource.builder(id, SourceAddress.of("wiki")).title("Richtlinien").build(), GUIDELINE));
    }

    private List<String> searches() {
        List<String> searches = new ArrayList<String>();
        for (String call : wiki.calls()) {
            if (call.startsWith("search:")) {
                searches.add(call.substring("search:".length()));
            }
        }
        return searches;
    }

    private ToolOutput research(MainModelChat model) {
        ResearchDelegationTool research = new ResearchDelegationTool(name -> catalog.find(name, ToolChannel.AGENT),
                model);
        return new McpChatTool(research.contribution()).executeWithContext("{\"task\": \"" + TASK + "\"}");
    }

    @Test
    public void anAcronymInTheCatalogIsAnsweredAfterOneLookupWithoutSourceSearch() {
        guidelineAnalysed();
        ScriptedModel model = new ScriptedModel("JA", "IRR steht für Interne Revisionsrichtlinie [1].");

        ToolOutput output = research(model);
        String result = output.text();

        assertTrue("no source search", searches().isEmpty());
        assertEquals("evidence check and answer, no query planning", 2, model.prompts.size());
        assertTrue(result, result.contains("Searched with: \"term: IRR\""));
        assertTrue(result, result.contains("[1] Richtlinien"));
        assertTrue("the answer is written from the read page", model.prompts.get(1).contains("Prüfungen im Haus"));
        assertEquals(1, output.context().size());
        ToolContextResource used = output.context().get(0);
        assertEquals("wiki", used.sourceId());
        assertEquals(wiki.idOf("Richtlinien").value(), used.resourceId());
        assertEquals("Richtlinien", used.title());
    }

    @Test
    public void withoutACatalogEntryTheTaskFallsThroughToTheLexicalSearch() {
        ScriptedModel model = new ScriptedModel("{\"queries\": [\"IRR\"]}", "Der Prüfplan nennt die IRR [1].");

        ToolOutput output = research(model);
        String result = output.text();

        assertEquals(Arrays.asList("IRR"), searches());
        assertTrue(result, result.contains("Searched with: \"term: IRR\", \"index: " + TASK + "\", \"index: IRR\", "
                + "\"IRR\""));
        assertFalse(output.context().isEmpty());
        for (ToolContextResource used : output.context()) {
            assertEquals("wiki", used.sourceId());
        }
    }

    @Test
    public void aTaskAnsweredFromTheLocalIndexNeedsNoLiveSearch() throws Exception {
        indexing.indexDocumentIfChanged(wiki.load(wiki.idOf("Pruefplan")));
        ScriptedModel model = new ScriptedModel("JA", "Der Prüfplan nennt die IRR als Grundlage [1].");

        ToolOutput output = research(model);
        String result = output.text();

        assertTrue("no live source search", searches().isEmpty());
        assertTrue(result, result.contains("Searched with: \"term: IRR\", \"index: " + TASK + "\""));
        assertTrue(result, result.contains("Prüfplan 2026"));
        assertEquals(1, output.context().size());
        ToolContextResource used = output.context().get(0);
        assertEquals("wiki", used.sourceId());
        assertEquals(wiki.idOf("Pruefplan").value(), used.resourceId());
        assertEquals("Prüfplan 2026", used.title());
    }

    @Test
    public void theLocalStageAsksTheIndexToolOfTheHost() {
        assertEquals(com.aresstack.enterpriseai.research.runtime.knowledge.LocalIndexStrategy.TOOL,
                KnowledgeMcpTools.KNOWLEDGE_INDEX_SEARCH);
    }
}
