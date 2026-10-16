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
 * AP10: Grenzen der RAG-Orchestrierung in {@code astu.acropolis.rag} und der Indexierung in
 * {@code astu.acropolis.knowledge}, zusätzlich zu den allgemeinen Kernregeln. Retrieval, Kontextaufbau und RAG-Chat
 * sprechen nur über Chat-Use-Case, Embedding- und Index-Port; die Indexierung zusätzlich über den Source-Port. Der
 * normale Chat-Pfad kennt beides nicht.
 */
public class RagBoundaryTest {

    private static final String ROOT = ModuleRegistry.ROOT_PACKAGE;
    private static final String APPLICATION_RAG = ROOT + ".astu.acropolis.rag..";
    private static final String APPLICATION_KNOWLEDGE = ROOT + ".astu.acropolis.knowledge..";

    private static JavaClasses productionClasses;

    @BeforeClass
    public static void importProductionClasses() {
        productionClasses = ProductionClasses.all();
    }

    @Test
    public void ragUseCasesSeeOnlyChatUseCaseAndKnowledgePorts() {
        assertRule(classes().that().resideInAPackage(APPLICATION_RAG)
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java.lang..", "java.util..", "java.net..", "java.time..",
                        ROOT + ".astu.chat..", ROOT + ".astu.embedding..", ROOT + ".astu.knowledge..",
                        ROOT + ".chat.api..", ROOT + ".embedding.api..", ROOT + ".astu.acropolis.chalcotheca..",
                        ROOT + ".astu.acropolis.chat..", ROOT + ".searchrun.api..", APPLICATION_RAG)
                .because("RAG orchestriert nur Ports und den Chat-Use-Case; kein Adapter, keine Quelle, "
                        + "kein Security-, ACP- oder MCP-Typ; the research funnel (#155) only through the run log "
                        + "port"));
    }

    @Test
    public void indexingSeesOnlySourceEmbeddingAndIndexPorts() {
        assertRule(classes().that().resideInAPackage(APPLICATION_KNOWLEDGE)
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java.lang..", "java.util..", "java.time..",
                        ROOT + ".astu.embedding..", ROOT + ".astu.knowledge..",
                        ROOT + ".embedding.api..", ROOT + ".astu.acropolis.chalcotheca..", ROOT + ".source.api..", ROOT + ".astu.resource..",
                        ROOT + ".searchrun.api..", APPLICATION_KNOWLEDGE)
                .because("die Indexierung kennt Quellen nur über den Source-Port; kein Adapter, kein Chat, "
                        + "kein Security-Typ – Zugangsdaten bleiben im Source-Adapter; the search run log (#153) "
                        + "only through its port"));
    }

    @Test
    public void plainChatPathDoesNotKnowRag() {
        assertRule(noClasses().that().resideInAnyPackage(ROOT + ".astu.acropolis.chat..", ROOT + ".astu.chat..",
                        ROOT + ".chat.api..")
                .should().dependOnClassesThat().resideInAnyPackage(APPLICATION_RAG, APPLICATION_KNOWLEDGE,
                        ROOT + ".astu.acropolis.chalcotheca..", ROOT + ".embedding.api..", ROOT + ".source.api..")
                .because("der normale Chat funktioniert ohne Retrieval; RAG legt sich von außen darum"));
    }

    private static void assertRule(ArchRule rule) {
        Violations.assertNone("RAG-Grenze verletzt",
                Violations.of(Collections.singletonList(rule.allowEmptyShould(false)), productionClasses));
    }
}
