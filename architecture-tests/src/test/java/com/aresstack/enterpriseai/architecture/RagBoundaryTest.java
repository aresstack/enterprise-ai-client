package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * AP10: Grenzen der RAG-Orchestrierung in {@code application.rag} und der Indexierung in
 * {@code application.knowledge}, zusätzlich zu den allgemeinen Kernregeln. Retrieval, Kontextaufbau und RAG-Chat
 * sprechen nur über Chat-Use-Case, Embedding- und Index-Port; die Indexierung zusätzlich über den Source-Port. Der
 * normale Chat-Pfad kennt beides nicht.
 */
public class RagBoundaryTest {

    private static final String ROOT = ModuleRegistry.ROOT_PACKAGE;
    private static final String APPLICATION_RAG = ROOT + ".application.rag..";
    private static final String APPLICATION_KNOWLEDGE = ROOT + ".application.knowledge..";

    private static JavaClasses productionClasses;

    @BeforeClass
    public static void importProductionClasses() {
        BuildModel model = BuildModel.load();
        List<File> directories = new ArrayList<File>();
        for (String module : model.scannedModules()) {
            directories.addAll(model.existingClassDirectoriesOf(module));
        }
        productionClasses = new ClassFileImporter().importPaths(BuildModelTest.toPaths(directories));
    }

    @Test
    public void ragUseCasesSeeOnlyChatUseCaseAndKnowledgePorts() {
        assertRule(classes().that().resideInAPackage(APPLICATION_RAG)
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java.lang..", "java.util..", "java.net..",
                        ROOT + ".domain.chat..", ROOT + ".domain.embedding..", ROOT + ".domain.knowledge..",
                        ROOT + ".chat.api..", ROOT + ".embedding.api..", ROOT + ".knowledge.api..",
                        ROOT + ".application.chat..", APPLICATION_RAG)
                .because("RAG orchestriert nur Ports und den Chat-Use-Case; kein Adapter, keine Quelle, "
                        + "kein Security-, ACP- oder MCP-Typ"));
    }

    @Test
    public void indexingSeesOnlySourceEmbeddingAndIndexPorts() {
        assertRule(classes().that().resideInAPackage(APPLICATION_KNOWLEDGE)
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java.lang..", "java.util..",
                        ROOT + ".domain.embedding..", ROOT + ".domain.knowledge..",
                        ROOT + ".embedding.api..", ROOT + ".knowledge.api..", ROOT + ".source.api..",
                        APPLICATION_KNOWLEDGE)
                .because("die Indexierung kennt Quellen nur über den Source-Port; kein Adapter, kein Chat, "
                        + "kein Security-Typ – Zugangsdaten bleiben im Source-Adapter"));
    }

    @Test
    public void plainChatPathDoesNotKnowRag() {
        assertRule(noClasses().that().resideInAnyPackage(ROOT + ".application.chat..", ROOT + ".domain.chat..",
                        ROOT + ".chat.api..")
                .should().dependOnClassesThat().resideInAnyPackage(APPLICATION_RAG, APPLICATION_KNOWLEDGE,
                        ROOT + ".knowledge.api..", ROOT + ".embedding.api..", ROOT + ".source.api..")
                .because("der normale Chat funktioniert ohne Retrieval; RAG legt sich von außen darum"));
    }

    private static void assertRule(ArchRule rule) {
        Violations.assertNone("RAG-Grenze verletzt",
                Violations.of(Collections.singletonList(rule.allowEmptyShould(false)), productionClasses));
    }
}
