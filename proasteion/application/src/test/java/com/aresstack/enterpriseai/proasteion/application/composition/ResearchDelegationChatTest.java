package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchDelegationTool;
import com.aresstack.enterpriseai.proasteion.application.chat.ToolUsePolicy;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The normal chat delegates research in natural language ({@code knowledge_research}); the Research Agent turns it
 * into a short lexical query before it reaches the knowledge source, then reads the hit and reports its provenance.
 * The low-level knowledge tools stay as they are.
 */
public class ResearchDelegationChatTest {

    private static final String LONG_REQUEST = "Wie wird in HaVdB der PoC mit Hashicorp Vault für PIMA beschrieben?";

    /** Plans with the model's query, then summarizes; records what the model was shown. */
    private static final class ScriptedModel implements MainModelChat {
        final List<String> answers = new ArrayList<String>(Arrays.asList(
                "{\"queries\": [\"PIMA Hashicorp Vault\", \"Hashicorp Vault PoC\"]}",
                "Im PoC wird PIMA Hashicorp Vault an das HaVdB-Backend angebunden [1]."));
        final List<String> prompts = new ArrayList<String>();

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

    /** Matches a page only when it contains the whole query, like a lexical full-text search. */
    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("Vault", "PoC Hashicorp Vault (HaVdB)",
                    "Im PoC wird PIMA Hashicorp Vault an das HaVdB-Backend angebunden. Secrets verteilt der Vault Agent.")
            .add("Kantine", "Kantine", "Die Kantine öffnet um 11 Uhr.");
    private final KnowledgeSourceCatalog live = KnowledgeSourceCatalog.of(
            new KnowledgeSourceRegistration(wiki, SourceScope.of("Vault", "Kantine")));
    private final ToolCatalog catalog = new ToolCatalog();
    private final ScriptedModel model = new ScriptedModel();

    public ResearchDelegationChatTest() {
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        final KnowledgeMcpTools knowledge = new KnowledgeMcpTools(new RetrieveKnowledgeUseCase(index, embeddings,
                embeddings.modelIdentity(), null), new LoadKnowledgeDocumentUseCase(live).withCatalog(() -> live),
                new SearchDrivenIndexingUseCase(indexing), null);
        catalog.register("knowledge", EnumSet.of(ToolChannel.AGENT), knowledge::contributions);
    }

    @Test
    public void naturalLanguageResearchReachesTheLexicalSourceAsShortQueryAndKeepsProvenance() {
        ResearchDelegationTool research = new ResearchDelegationTool(name -> catalog.find(name, ToolChannel.AGENT),
                model);
        McpChatTool chatTool = new McpChatTool(research.contribution());

        String result = chatTool.execute("{\"task\": \"" + LONG_REQUEST + "\"}");

        List<String> searches = new ArrayList<String>();
        for (String call : wiki.calls()) {
            if (call.startsWith("search:")) {
                searches.add(call.substring("search:".length()));
            }
        }
        assertEquals("the source sees the mediated query, not the sentence", Arrays.asList("PIMA Hashicorp Vault"),
                searches);
        assertFalse(searches.contains(LONG_REQUEST));
        String id = wiki.idOf("Vault").value();
        assertTrue(result, result.contains("[1] PoC Hashicorp Vault (HaVdB)"));
        assertTrue(result, result.contains("id: " + id));
        assertTrue(result, result.contains("source: wiki"));
        assertTrue("the answer is written from the read page", model.prompts.get(1).contains("Vault Agent"));
    }

    @Test
    public void withoutKnowledgeToolsTheResearchSaysSoInsteadOfAnswering() {
        ResearchDelegationTool research = new ResearchDelegationTool(name -> null, model);

        String result = new McpChatTool(research.contribution()).execute("{\"task\": \"Was bedeutet IRR?\"}");

        assertTrue(result, result.contains("not available"));
        assertEquals("the model only planned, it never answered", 1, model.prompts.size());
    }

    @Test
    public void chatRulesNameTheResearchBoundaryInsteadOfTheLowLevelSearch() {
        String rules = ToolUsePolicy.context(Arrays.asList(ResearchDelegationTool.NAME,
                KnowledgeMcpTools.KNOWLEDGE_READ));

        assertTrue(rules, rules.contains(ResearchDelegationTool.NAME + " nutzt du"));
        assertFalse(rules, rules.contains(KnowledgeMcpTools.KNOWLEDGE_SEARCH + " nutzt du"));
    }
}
