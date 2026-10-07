package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Strang C (AP5/AP6): Embedding-spezifische Grenzen, zusätzlich zu den allgemeinen Regeln in
 * {@link ClassBoundaryTest}.
 */
public class EmbeddingBoundaryTest {

    private static final String DOMAIN_EMBEDDING = "com.aresstack.enterpriseai.domain.embedding..";
    private static final String EMBEDDING_API = "com.aresstack.enterpriseai.embedding.api..";

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

    /** Die Embedding-Wertobjekte sind in sich geschlossen: nur JDK-Kern, keine anderen Domain-Bereiche. */
    @Test
    public void domainEmbeddingIsSelfContained() {
        ArchRule rule = classes().that().resideInAPackage(DOMAIN_EMBEDDING)
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        DOMAIN_EMBEDDING, "java.lang..", "java.util..", "java.nio.charset..", "java.security..")
                .allowEmptyShould(true);
        Violations.assertNone("domain.embedding", Violations.of(Arrays.asList(rule), productionClasses));
    }

    /** Der Port sieht nur seine Wertobjekte und das JDK, keine Adaptertypen, kein JSON, kein HTTP. */
    @Test
    public void embeddingApiSeesOnlyDomainEmbedding() {
        ArchRule rule = classes().that().resideInAPackage(EMBEDDING_API)
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        EMBEDDING_API, DOMAIN_EMBEDDING, "java.lang..", "java.util..", "java.io..")
                .allowEmptyShould(true);
        Violations.assertNone("embedding.api", Violations.of(Arrays.asList(rule), productionClasses));
    }
}
