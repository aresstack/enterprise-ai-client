package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.application.agent.archfixture.AgentUseCaseUsingAcp;
import com.aresstack.enterpriseai.application.agent.archfixture.AgentUseCaseUsingMcp;
import com.aresstack.enterpriseai.application.chat.archfixture.ChatUseCaseUsingIndex;
import com.aresstack.enterpriseai.application.knowledge.archfixture.IndexingUsingChat;
import com.aresstack.enterpriseai.application.rag.archfixture.RagUsingSource;
import com.aresstack.enterpriseai.app.ui.agent.archfixture.ModeSwitchUsingUseCase;
import com.aresstack.enterpriseai.chat.api.archfixture.FakeChatPort;
import com.aresstack.enterpriseai.chat.api.archfixture.boundary.PortUsingEmbedding;
import com.aresstack.enterpriseai.chat.openai.archfixture.GlobalClientHolder;
import com.aresstack.enterpriseai.chat.openai.archfixture.signature.AdapterLeakingConnection;
import com.aresstack.enterpriseai.chat.openai.archfixture.tech.LuceneInChatAdapter;
import com.aresstack.enterpriseai.domain.archfixture.NeutralValue;
import com.aresstack.enterpriseai.domain.chat.archfixture.ChatValueUsingEmbedding;
import com.aresstack.enterpriseai.domain.chat.archfixture.FakeChatValue;
import com.aresstack.enterpriseai.domain.embedding.archfixture.FakeVector;
import com.aresstack.enterpriseai.domain.embedding.archfixture.VectorUsingChat;
import com.aresstack.enterpriseai.domain.knowledge.archfixture.ChunkUsingChat;
import com.aresstack.enterpriseai.domain.knowledge.archfixture.FakeChunk;
import com.aresstack.enterpriseai.embedding.api.archfixture.FakeEmbeddingPort;
import com.aresstack.enterpriseai.embedding.api.archfixture.boundary.PortUsingKnowledge;
import com.aresstack.enterpriseai.embedding.openai.archfixture.PublicWireDto;
import com.aresstack.enterpriseai.embedding.openai.archfixture.signature.AdapterExposingGson;
import com.aresstack.enterpriseai.knowledge.api.archfixture.FakeKnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.archfixture.boundary.PortUsingSource;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneAdapter;
import com.aresstack.enterpriseai.mcp.api.archfixture.FakeMcpRegistry;
import com.aresstack.enterpriseai.source.api.archfixture.FakeKnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.archfixture.PortKnowingAdapter;
import com.aresstack.enterpriseai.source.mediawiki.archfixture.FakeWikiAdapter;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Gegenbeispiele für die Strang-Regelklassen und die Registry-/Build-Modell-Tests, die ihre Regeln inline
 * definieren. Jede Regelmethode läuft unverändert ({@link ExistingRuleProbe}); nur ihre Eingabe ist das
 * Gegenbeispiel. {@code AgentModeBoundaryTest} stellt seine Regeln als Fabriken bereit und wird direkt
 * geprüft.
 */
public class ExistingRulesDetectViolationsTest {

    private static final ModuleRegistry STANDARD = ModuleRegistry.standard();

    // ---- ChatBoundaryTest (Strang A) ----

    @Test
    public void chatModelUsingEmbeddingIsDetected() {
        ExistingRuleProbe.assertRuleFails(ChatBoundaryTest.class, "chatDomainModelIsSelfContained",
                ProductionClasses.of(ChatValueUsingEmbedding.class, FakeVector.class), "ChatValueUsingEmbedding");
    }

    @Test
    public void chatPortUsingAnotherCapabilityIsDetected() {
        ExistingRuleProbe.assertRuleFails(ChatBoundaryTest.class, "chatPortSeesOnlyTheChatModel",
                ProductionClasses.of(PortUsingEmbedding.class, FakeEmbeddingPort.class), "PortUsingEmbedding");
    }

    @Test
    public void chatUseCaseUsingTheIndexIsDetected() {
        ExistingRuleProbe.assertRuleFails(ChatBoundaryTest.class, "chatUseCaseSeesOnlyChatPortAndModel",
                ProductionClasses.of(ChatUseCaseUsingIndex.class, FakeKnowledgeIndexPort.class), "ChatUseCaseUsingIndex");
    }

    @Test
    public void publicTypeInChatAdapterIsDetected() {
        ExistingRuleProbe.assertRuleFails(ChatBoundaryTest.class, "openAiAdapterExposesOnlyItsEntryTypes",
                ProductionClasses.of(GlobalClientHolder.class), "GlobalClientHolder");
    }

    @Test
    public void httpTypeInChatAdapterSignatureIsDetected() {
        ExistingRuleProbe.assertRuleFails(ChatBoundaryTest.class, "jsonAndHttpTypesNeverLeakThroughTheAdapterApi",
                ProductionClasses.of(AdapterLeakingConnection.class), "AdapterLeakingConnection");
    }

    // ---- EmbeddingBoundaryTest (Strang C) ----

    @Test
    public void embeddingModelUsingChatIsDetected() {
        ExistingRuleProbe.assertRuleFails(EmbeddingBoundaryTest.class, "domainEmbeddingIsSelfContained",
                ProductionClasses.of(VectorUsingChat.class, FakeChatValue.class), "VectorUsingChat");
    }

    @Test
    public void embeddingPortUsingKnowledgeIsDetected() {
        ExistingRuleProbe.assertRuleFails(EmbeddingBoundaryTest.class, "embeddingApiSeesOnlyDomainEmbedding",
                ProductionClasses.of(PortUsingKnowledge.class, FakeChunk.class), "PortUsingKnowledge");
    }

    @Test
    public void publicWireTypeInEmbeddingAdapterIsDetected() {
        ExistingRuleProbe.assertRuleFails(EmbeddingBoundaryTest.class, "embeddingOpenAiExposesOnlyItsEntryPoints",
                ProductionClasses.of(PublicWireDto.class), "PublicWireDto");
    }

    @Test
    public void gsonInPublicEmbeddingAdapterClassIsDetected() {
        ExistingRuleProbe.assertRuleFails(EmbeddingBoundaryTest.class, "embeddingOpenAiLeaksNoJsonTypes",
                ProductionClasses.of(AdapterExposingGson.class), "AdapterExposingGson");
    }

    // ---- KnowledgeBoundaryTest (Strang D) ----

    @Test
    public void knowledgeModelUsingChatIsDetected() {
        ExistingRuleProbe.assertRuleFails(KnowledgeBoundaryTest.class, "domainKnowledgeIsSelfContained",
                ProductionClasses.of(ChunkUsingChat.class, FakeChatValue.class), "ChunkUsingChat");
    }

    @Test
    public void indexPortUsingSourcePortIsDetected() {
        ExistingRuleProbe.assertRuleFails(KnowledgeBoundaryTest.class, "knowledgeApiSeesOnlyKnowledgeAndEmbeddingValues",
                ProductionClasses.of(PortUsingSource.class, FakeKnowledgeSourcePort.class), "PortUsingSource");
    }

    @Test
    public void luceneOutsideKnowledgeLuceneIsDetectedByTheStrandRule() {
        ExistingRuleProbe.assertRuleFails(KnowledgeBoundaryTest.class, "luceneOnlyInKnowledgeLucene",
                ProductionClasses.of(LuceneInChatAdapter.class), "LuceneInChatAdapter");
    }

    @Test
    public void secondPublicTypeInKnowledgeLuceneIsDetected() {
        ExistingRuleProbe.assertRuleFails(KnowledgeBoundaryTest.class, "knowledgeLuceneExposesOnlyTheAdapter",
                ProductionClasses.of(FakeLuceneAdapter.class), "FakeLuceneAdapter");
    }

    // ---- RagBoundaryTest (AP10) ----

    @Test
    public void ragUsingTheSourcePortIsDetected() {
        ExistingRuleProbe.assertRuleFails(RagBoundaryTest.class, "ragUseCasesSeeOnlyChatUseCaseAndKnowledgePorts",
                ProductionClasses.of(RagUsingSource.class, FakeKnowledgeSourcePort.class), "RagUsingSource");
    }

    @Test
    public void indexingUsingTheChatPortIsDetected() {
        ExistingRuleProbe.assertRuleFails(RagBoundaryTest.class, "indexingSeesOnlySourceEmbeddingAndIndexPorts",
                ProductionClasses.of(IndexingUsingChat.class, FakeChatPort.class), "IndexingUsingChat");
    }

    @Test
    public void chatPathUsingRagIsDetected() {
        ExistingRuleProbe.assertRuleFails(RagBoundaryTest.class, "plainChatPathDoesNotKnowRag",
                ProductionClasses.of(ChatUseCaseUsingIndex.class, FakeKnowledgeIndexPort.class), "ChatUseCaseUsingIndex");
    }

    // ---- SourceBoundaryTest (Strang E) ----

    @Test
    public void sourceApiKnowingAnAdapterIsDetected() {
        ExistingRuleProbe.assertRuleFails(SourceBoundaryTest.class, "sourceApiDoesNotKnowAnySourceAdapter",
                ProductionClasses.of(PortKnowingAdapter.class, FakeWikiAdapter.class), "PortKnowingAdapter");
    }

    // ---- AgentModeBoundaryTest (AP21): Fabriken direkt ----

    @Test
    public void agentUseCaseUsingMcpIsDetected() {
        List<String> violations = Violations.of(Collections.singletonList(AgentModeBoundaryTest.agentUseCaseSeesOnlyTheAcpPort()),
                ProductionClasses.of(AgentUseCaseUsingMcp.class, FakeMcpRegistry.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("AgentUseCaseUsingMcp"));
    }

    @Test
    public void modeSwitchUsingAUseCaseIsDetected() {
        List<String> violations = Violations.of(Collections.singletonList(AgentModeBoundaryTest.modeSwitchIsPureUi()),
                ProductionClasses.of(ModeSwitchUsingUseCase.class, AgentUseCaseUsingAcp.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("ModeSwitchUsingUseCase"));
    }

    // ---- ModuleRegistryTest: fehlerhafte Registries ----

    @Test
    public void unknownAllowedDependencyIsDetected() {
        ExistingRuleProbe.assertRuleFails(ModuleRegistryTest.class, "allowedDependenciesReferToRegisteredModules",
                withAllowedDependencies("chat-api", "domain", "source-sharepoint"), "source-sharepoint");
    }

    @Test
    public void adapterSeeingAnotherAdapterInTheRegistryIsDetected() {
        ExistingRuleProbe.assertRuleFails(ModuleRegistryTest.class, "allowedDependenciesFollowTheLayering",
                withAllowedDependencies("knowledge-lucene", "domain", "knowledge-api", "chat-openai"),
                "knowledge-lucene (ADAPTER) darf chat-openai (ADAPTER) nicht sehen");
    }

    @Test
    public void portSeeingItsAdapterInTheRegistryIsDetected() {
        ExistingRuleProbe.assertRuleFails(ModuleRegistryTest.class, "ap24ForbiddenModuleEdgesStayForbidden",
                withAllowedDependencies("chat-api", "domain", "chat-openai"), "chat-api -> chat-openai");
    }

    @Test
    public void applicationSeeingAnAdapterInTheRegistryIsDetected() {
        Set<String> allowed = new LinkedHashSet<String>(STANDARD.module("application").allowedDependencies());
        allowed.add("knowledge-lucene");
        ExistingRuleProbe.assertRuleFails(ModuleRegistryTest.class, "ap24ForbiddenModuleEdgesStayForbidden",
                withAllowedDependencies("application", allowed.toArray(new String[0])), "application -> knowledge-lucene");
    }

    @Test
    public void adapterWithoutPortInTheRegistryIsDetected() {
        ExistingRuleProbe.assertRuleFails(ModuleRegistryTest.class, "everyAdapterMaySeeAtLeastOnePort",
                withAllowedDependencies("chat-openai", "domain"), "chat-openai implementiert keinen Port");
    }

    @Test
    public void cycleInTheRegistryIsDetected() {
        ModuleRegistry.Builder builder = new ModuleRegistry.Builder();
        for (ArchitectureModule module : STANDARD.modules()) {
            String[] allowed = module.allowedDependencies().toArray(new String[0]);
            if (module.name().equals("domain")) {
                allowed = new String[] {"chat-api"};
            }
            builder.module(module.name(), suffix(module), module.kind(), module.owner(), allowed);
        }
        ExistingRuleProbe.assertRuleFails(ModuleRegistryTest.class, "allowedDependenciesAreAcyclic", builder.build(), "Zyklus");
    }

    @Test
    public void overlappingBasePackagesAreDetected() {
        ModuleRegistry.Builder builder = copyOfStandard();
        builder.module("chat-all", "chat", ModuleKind.PORT, "Test", "domain");
        ExistingRuleProbe.assertRuleFails(ModuleRegistryTest.class, "basePackagesAreDisjoint", builder.build(), "überdeckt");
    }

    // ---- BuildModelTest ----

    @Test
    public void registeredModuleMissingInGradleIsDetected() {
        Set<String> modules = new LinkedHashSet<String>(STANDARD.names());
        modules.remove("domain");
        BuildModel model = new BuildModel(modules, new HashMap<String, Set<String>>(),
                new HashMap<String, Set<String>>(), new HashMap<String, List<File>>());
        ExistingRuleProbe.assertRuleFails(BuildModelTest.class, "everyRegisteredModuleExists", model, "domain");
    }

    @Test
    public void classOutsideItsModulePackageIsDetected() throws IOException {
        File directory = Files.createTempDirectory("archfixture").toFile();
        try {
            // NeutralValue (domain.archfixture) als Klasse des Moduls chat-api ausgeben.
            File target = new File(directory, NeutralValue.class.getName().replace('.', '/') + ".class");
            assertTrue(target.getParentFile().mkdirs());
            copyClassFile(NeutralValue.class, target);
            Map<String, List<File>> classDirectories = new HashMap<String, List<File>>();
            classDirectories.put("chat-api", Collections.singletonList(directory));
            BuildModel model = new BuildModel(STANDARD.names(), new HashMap<String, Set<String>>(),
                    new HashMap<String, Set<String>>(), classDirectories);
            ExistingRuleProbe.assertRuleFails(BuildModelTest.class, "everyModuleHasClassesOnlyInItsOwnBasePackage", model,
                    "NeutralValue liegt in chat-api, aber außerhalb von");
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    public void moduleWithoutClassesIsDetected() {
        Map<String, List<File>> classDirectories = new HashMap<String, List<File>>();
        classDirectories.put("chat-api", Collections.singletonList(new File("does-not-exist")));
        BuildModel model = new BuildModel(STANDARD.names(), new HashMap<String, Set<String>>(),
                new HashMap<String, Set<String>>(), classDirectories);
        ExistingRuleProbe.assertRuleFails(BuildModelTest.class, "everyModuleHasClassesOnlyInItsOwnBasePackage", model,
                "chat-api hat keine Produktionsklassen");
    }

    // ---- Hilfen ----

    private static ModuleRegistry withAllowedDependencies(String moduleName, String... allowed) {
        ModuleRegistry.Builder builder = new ModuleRegistry.Builder();
        for (ArchitectureModule module : STANDARD.modules()) {
            String[] dependencies = module.name().equals(moduleName) ? allowed
                    : module.allowedDependencies().toArray(new String[0]);
            builder.module(module.name(), suffix(module), module.kind(), module.owner(), dependencies);
        }
        return builder.build();
    }

    private static ModuleRegistry.Builder copyOfStandard() {
        ModuleRegistry.Builder builder = new ModuleRegistry.Builder();
        for (ArchitectureModule module : STANDARD.modules()) {
            builder.module(module.name(), suffix(module), module.kind(), module.owner(),
                    module.allowedDependencies().toArray(new String[0]));
        }
        return builder;
    }

    private static String suffix(ArchitectureModule module) {
        return module.basePackage().substring(ModuleRegistry.ROOT_PACKAGE.length() + 1);
    }

    private static void copyClassFile(Class<?> type, File target) throws IOException {
        InputStream in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class");
        OutputStream out = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
            out.close();
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
