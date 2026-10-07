package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.application.archfixture.testleak.UseCaseUsingFixture;
import com.aresstack.enterpriseai.application.archfixture.testleak.UseCaseUsingTestkitPackage;
import com.aresstack.enterpriseai.chat.api.archfixture.testleak.FakeTestFixturePort;
import com.aresstack.enterpriseai.chat.openai.archfixture.testleak.AdapterUsingArchUnit;
import com.aresstack.enterpriseai.chat.openai.archfixture.testleak.AdapterUsingJUnit;
import com.aresstack.enterpriseai.mcp.api.testkit.archfixture.FakeToolkit;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Testcode bleibt Testcode: weder Testbibliotheken noch Testfixtures im Produktionscode, keine
 * {@code testFixtures(...)}-Anforderung in einer Produktionskonfiguration.
 */
public class TestCodeIsolationTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();
    private static BuildModelExtension extension;
    private static Set<String> testFixtureClassNames;

    @BeforeClass
    public static void loadTestFixtures() {
        extension = BuildModelExtension.load();
        testFixtureClassNames = new TreeSet<String>();
        List<File> directories = new ArrayList<File>();
        for (String module : extension.modulesWithTestFixtures()) {
            directories.addAll(extension.testFixtureClassDirectoriesOf(module));
        }
        for (JavaClass javaClass : new ClassFileImporter().importPaths(BuildModelTest.toPaths(directories))) {
            testFixtureClassNames.add(javaClass.getName());
        }
    }

    @Test
    public void testFixturesExist() {
        assertFalse("Keine Testfixtures gefunden; die Fixture-Regel liefe ins Leere", testFixtureClassNames.isEmpty());
        assertTrue(testFixtureClassNames.toString(),
                testFixtureClassNames.contains("com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort"));
    }

    @Test
    public void productionUsesNoTestLibrariesOrFixtures() {
        Violations.assertNone("Testcode im Produktionscode", Violations.of(java.util.Arrays.asList(
                TestCodeIsolationRules.productionUsesNoTestLibraries(),
                TestCodeIsolationRules.productionUsesNoTestPackages(),
                TestCodeIsolationRules.productionUsesNoTestFixtureClasses(testFixtureClassNames)),
                ProductionClasses.all()));
    }

    @Test
    public void productionConfigurationsRequestNoTestFixtures() {
        Violations.assertNone("testFixtures in Produktionskonfiguration",
                TestCodeIsolationRules.productionConfigurationsRequestingTestFixtures(extension));
    }

    @Test
    public void adapterUsingTestLibrariesIsDetected() {
        List<String> violations = Violations.of(Collections.singletonList(TestCodeIsolationRules.productionUsesNoTestLibraries()),
                ProductionClasses.of(AdapterUsingJUnit.class, AdapterUsingArchUnit.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("AdapterUsingJUnit"));
        assertTrue(violations.get(0), violations.get(0).contains("AdapterUsingArchUnit"));
    }

    @Test
    public void useCaseUsingATestFixtureClassIsDetected() {
        Set<String> fixtureNames = Collections.singleton(FakeTestFixturePort.class.getName());
        List<String> violations = Violations.of(
                Collections.singletonList(TestCodeIsolationRules.productionUsesNoTestFixtureClasses(fixtureNames)),
                ProductionClasses.of(UseCaseUsingFixture.class, FakeTestFixturePort.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("UseCaseUsingFixture"));
    }

    @Test
    public void useCaseUsingATestkitPackageIsDetected() {
        List<String> violations = Violations.of(Collections.singletonList(TestCodeIsolationRules.productionUsesNoTestPackages()),
                ProductionClasses.of(UseCaseUsingTestkitPackage.class, FakeToolkit.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("UseCaseUsingTestkitPackage"));
    }

    @Test
    public void testFixturesInAProductionConfigurationAreDetected() {
        Properties properties = new Properties();
        properties.setProperty("modules", "application,chat-api");
        properties.setProperty("testFixtureDependencies.application", "chat-api");
        List<String> violations = TestCodeIsolationRules.productionConfigurationsRequestingTestFixtures(
                new BuildModelExtension(properties));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("application -> testFixtures(chat-api)"));
    }
}
