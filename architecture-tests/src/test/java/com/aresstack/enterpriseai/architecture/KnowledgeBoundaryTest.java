package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Strang D (AP7–AP9): Knowledge-spezifische Grenzen, zusätzlich zu den allgemeinen Regeln in
 * {@link ClassBoundaryTest}.
 */
public class KnowledgeBoundaryTest {

    private static final String DOMAIN_KNOWLEDGE = "com.aresstack.enterpriseai.domain.knowledge..";
    private static final String DOMAIN_EMBEDDING = "com.aresstack.enterpriseai.domain.embedding..";
    private static final String KNOWLEDGE_API = "com.aresstack.enterpriseai.knowledge.api..";
    private static final String KNOWLEDGE_LUCENE = "com.aresstack.enterpriseai.knowledge.lucene..";

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

    /** Die Wissensmodelle und der Chunker brauchen nur den JDK-Kern, keinen anderen Domain-Bereich. */
    @Test
    public void domainKnowledgeIsSelfContained() {
        ArchRule rule = classes().that().resideInAPackage(DOMAIN_KNOWLEDGE)
                .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN_KNOWLEDGE, "java.lang..",
                        "java.util..", "java.net..", "java.nio.charset..", "java.security..", "java.text..",
                        "java.time..")
                .allowEmptyShould(true);
        Violations.assertNone("domain.knowledge", Violations.of(Arrays.asList(rule), productionClasses));
    }

    /** Der Index-Port sieht nur Wissens- und Embedding-Wertobjekte und das JDK; kein Lucene, kein Dateisystem. */
    @Test
    public void knowledgeApiSeesOnlyKnowledgeAndEmbeddingValues() {
        ArchRule rule = classes().that().resideInAPackage(KNOWLEDGE_API)
                .should().onlyDependOnClassesThat().resideInAnyPackage(KNOWLEDGE_API, DOMAIN_KNOWLEDGE,
                        DOMAIN_EMBEDDING, "java.lang..", "java.util..")
                .allowEmptyShould(true);
        Violations.assertNone("knowledge.api", Violations.of(Arrays.asList(rule), productionClasses));
    }

    /** Lucene ist ausschließlich in knowledge-lucene sichtbar. */
    @Test
    public void luceneOnlyInKnowledgeLucene() {
        ArchRule rule = noClasses().that().resideOutsideOfPackage(KNOWLEDGE_LUCENE)
                .should().dependOnClassesThat().resideInAPackage("org.apache.lucene..")
                .allowEmptyShould(true);
        Violations.assertNone("Lucene außerhalb von knowledge-lucene",
                Violations.of(Arrays.asList(rule), productionClasses));
    }

    /**
     * Von knowledge-lucene ist nur der Port-Adapter selbst öffentlich; Text-/Vektorindex und Lucene-Abbildung
     * bleiben paketintern, damit keine Adaptertypen das Modul verlassen.
     */
    @Test
    public void knowledgeLuceneExposesOnlyTheAdapter() {
        ArchRule rule = classes().that().resideInAPackage(KNOWLEDGE_LUCENE)
                .and().haveModifier(JavaModifier.PUBLIC)
                .and().areTopLevelClasses()
                .should().haveSimpleName("LuceneKnowledgeIndex")
                .allowEmptyShould(true);
        Violations.assertNone("öffentliche Typen in knowledge-lucene",
                Violations.of(Arrays.asList(rule), productionClasses));
    }
}
