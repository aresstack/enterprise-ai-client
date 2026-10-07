package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Testcode bleibt Testcode: Produktionsklassen kennen weder Testbibliotheken noch Testfixtures
 * ({@code testFixtures}-Source-Sets, Fake-/Testkit-Pakete), und keine Produktionskonfiguration fordert
 * {@code testFixtures(project(...))} an. Der Demo-Agent (Modulrolle TEST_FIXTURE) wird über die Modulmatrix
 * ferngehalten.
 */
final class TestCodeIsolationRules {

    static final String[] TEST_LIBRARY_PACKAGES = {
            "org.junit..", "org.hamcrest..", "com.tngtech.archunit..", "org.mockito..", "org.assertj.."};

    /** Paketnamen, die in diesem Projekt Testcode kennzeichnen. */
    static final String[] TEST_PACKAGE_PATTERNS = {"..testing..", "..testkit..", "..fake..", "..archfixture..", "..archstub.."};

    private TestCodeIsolationRules() {
    }

    static ArchRule productionUsesNoTestLibraries() {
        return noClasses()
                .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAnyPackage(TEST_LIBRARY_PACKAGES)
                .because("JUnit, ArchUnit und Mocking-Bibliotheken sind Testabhängigkeiten")
                .allowEmptyShould(true);
    }

    static ArchRule productionUsesNoTestPackages() {
        return noClasses()
                .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAnyPackage(TEST_PACKAGE_PATTERNS)
                .because("Fakes, Testkits und Fixtures liegen in testFixtures bzw. im Testcode und sind kein "
                        + "Produktionscode")
                .allowEmptyShould(true);
    }

    /** Präzise Variante: die tatsächlich kompilierten Testfixture-Klassen aller Module. */
    static ArchRule productionUsesNoTestFixtureClasses(final Set<String> testFixtureClassNames) {
        return noClasses()
                .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .should().dependOnClassesThat(new DescribedPredicate<JavaClass>("Testfixture-Klassen") {
                    @Override
                    public boolean test(JavaClass target) {
                        return testFixtureClassNames.contains(target.getName());
                    }
                })
                .because("testFixtures-Source-Sets sind nur für Testklassenpfade gedacht")
                .allowEmptyShould(true);
    }

    static List<String> productionConfigurationsRequestingTestFixtures(BuildModelExtension extension) {
        List<String> violations = new ArrayList<String>();
        for (String module : new TreeSet<String>(extension.modules())) {
            for (String target : extension.testFixtureDependenciesOf(module)) {
                violations.add(module + " -> testFixtures(" + target + "): Testfixtures nur mit testImplementation "
                        + "oder testFixturesImplementation anfordern, nie in einer Produktionskonfiguration");
            }
        }
        return violations;
    }
}
