package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.text.BreakIterator;
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

    private static final String DOMAIN_KNOWLEDGE = "com.aresstack.enterpriseai.astu.knowledge..";
    private static final String DOMAIN_EMBEDDING = "com.aresstack.enterpriseai.astu.embedding..";
    private static final String KNOWLEDGE_API = "com.aresstack.enterpriseai.astu.acropolis.chalcotheca..";
    private static final String KNOWLEDGE_LUCENE = "com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai..";
    private static final String KNOWLEDGE_CATALOG = "com.aresstack.enterpriseai.astu.acropolis.chalcotheca.pinakes..";

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
        Violations.assertNone("astu.knowledge", Violations.of(Arrays.asList(rule), productionClasses));
    }

    /**
     * Zeichenklassen (Buchstabe, Ziffer, Leerraum, Groß-/Kleinschreibung ...) kommen in domain.knowledge nur aus
     * dem festen, versionierten Vertrag {@code UnicodeClasses}; die Unicode-Daten des laufenden JDK
     * ({@code Character.isLetter}, {@code getType}, {@code UnicodeBlock}, {@code BreakIterator} ...) sind tabu,
     * damit Chunks, Tokenzahlen und Satzgrenzen auf JDK 8 und JDK 21 identisch sind.
     */
    @Test
    public void domainKnowledgeClassifiesCharactersOnlyThroughUnicodeClasses() {
        DescribedPredicate<JavaCall<?>> jdkCharacterData = JavaCall.Predicates.target(HasName.Predicates.nameMatching(
                        "is(Letter|Digit|LetterOrDigit|Alphabetic|Ideographic|Whitespace|SpaceChar|UpperCase|LowerCase"
                                + "|TitleCase|Defined|Mirrored|UnicodeIdentifier.*|JavaIdentifier.*|JavaLetter.*)"
                                + "|getType|getNumericValue|digit|to(Upper|Lower|Title)Case"))
                .and(JavaCall.Predicates.target(HasOwner.Predicates.With.owner(
                        JavaClass.Predicates.type(Character.class))))
                .as("Character.is*/getType/to*Case(..)");
        ArchRule rule = noClasses().that().resideInAPackage(DOMAIN_KNOWLEDGE)
                .and().doNotHaveSimpleName("UnicodeClasses")
                .should().callMethodWhere(jdkCharacterData)
                .orShould().dependOnClassesThat().belongToAnyOf(BreakIterator.class, Character.UnicodeBlock.class,
                        Character.UnicodeScript.class)
                .because("Zeichenklassen kommen nur aus UnicodeClasses, nicht aus den Unicode-Daten des JDK")
                .allowEmptyShould(true);
        Violations.assertNone("JDK-Zeichenklassen in domain.knowledge",
                Violations.of(Arrays.asList(rule), productionClasses));
    }

    /**
     * Die Chalcotheca (Index-Port und Bronze-Archiv, ohne die in ihr liegenden anagraphai und tamias) sieht nur
     * Wissens-, Embedding- und Ressourcen-Wertobjekte, den Wächter und das JDK; kein Lucene.
     */
    @Test
    public void knowledgeApiSeesOnlyKnowledgeAndEmbeddingValues() {
        ArchRule rule = classes().that(ModuleRegistry.standard().module("chalcotheca").classes())
                .should().onlyDependOnClassesThat().resideInAnyPackage(KNOWLEDGE_API, DOMAIN_KNOWLEDGE,
                        DOMAIN_EMBEDDING, "com.aresstack.enterpriseai.astu.resource..", "java.lang..", "java.util..",
                        "java.io..", "java.net..", "java.nio.charset..", "java.security..", "java.time..")
                .allowEmptyShould(true);
        Violations.assertNone("astu.acropolis.chalcotheca", Violations.of(Arrays.asList(rule), productionClasses));
    }

    /** Lucene ist ausschließlich in anagraphai sichtbar. */
    @Test
    public void luceneOnlyInKnowledgeLucene() {
        ArchRule rule = noClasses().that().resideOutsideOfPackage(KNOWLEDGE_LUCENE)
                .should().dependOnClassesThat().resideInAPackage("org.apache.lucene..")
                .allowEmptyShould(true);
        Violations.assertNone("Lucene außerhalb von anagraphai",
                Violations.of(Arrays.asList(rule), productionClasses));
    }

    /**
     * Von pinakes ist nur der Port-Adapter selbst öffentlich; das lexikalische Register anagraphai sieht außer
     * pinakes niemand, damit keine Adaptertypen die chalcotheca verlassen. anagraphai kennt pinakes nicht (corenth).
     */
    @Test
    public void knowledgeLuceneExposesOnlyTheAdapter() {
        ArchRule exposed = classes().that().resideInAPackage(KNOWLEDGE_CATALOG)
                .and().haveModifier(JavaModifier.PUBLIC)
                .and().areTopLevelClasses()
                .should().haveSimpleName("LuceneKnowledgeIndex")
                .allowEmptyShould(true);
        ArchRule register = noClasses().that().resideOutsideOfPackages(KNOWLEDGE_LUCENE, KNOWLEDGE_CATALOG)
                .should().dependOnClassesThat().resideInAPackage(KNOWLEDGE_LUCENE)
                .allowEmptyShould(true);
        ArchRule lexicalOnly = noClasses().that().resideInAPackage(KNOWLEDGE_LUCENE)
                .should().dependOnClassesThat().resideInAPackage(KNOWLEDGE_CATALOG)
                .allowEmptyShould(true);
        Violations.assertNone("öffentliche Typen in anagraphai/pinakes",
                Violations.of(Arrays.asList(exposed, register, lexicalOnly), productionClasses));
    }
}
