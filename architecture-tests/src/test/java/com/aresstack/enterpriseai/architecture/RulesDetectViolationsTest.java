package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.application.archfixture.ApplicationUsingAdapter;
import com.aresstack.enterpriseai.chat.api.archfixture.PortWithMain;
import com.aresstack.enterpriseai.chat.openai.archfixture.GlobalClientHolder;
import com.aresstack.enterpriseai.domain.archfixture.HttpInDomain;
import com.aresstack.enterpriseai.domain.archfixture.NeutralValue;
import com.aresstack.enterpriseai.domain.archfixture.SwingInDomain;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneAdapter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Selbsttest der Architekturregeln: Absichtliche Verstöße (Fixtures in diesem Testmodul) müssen
 * reproduzierbar erkannt werden, ein neutraler Domain-Wert darf nicht anschlagen. Damit kann eine Regel
 * nicht unbemerkt wirkungslos werden.
 */
public class RulesDetectViolationsTest {

    private final ModuleRegistry registry = ModuleRegistry.standard();

    @Test
    public void swingInDomainIsDetected() {
        JavaClasses classes = importClasses(SwingInDomain.class);
        assertDetected(Collections.singletonList(ArchitectureRules.coreUsesOnlyNeutralJdk(registry)), classes);
        assertDetected(ArchitectureRules.coreTechnologyRules(registry), classes);
        assertDetected(ArchitectureRules.technologyConfinementRules(registry), classes);
    }

    @Test
    public void httpInDomainIsDetected() {
        JavaClasses classes = importClasses(HttpInDomain.class);
        List<String> violations = Violations.of(ArchitectureRules.coreTechnologyRules(registry), classes);
        assertTrue(violations.toString(), violations.toString().contains("HTTP"));
        assertDetected(Collections.singletonList(ArchitectureRules.coreUsesOnlyNeutralJdk(registry)), classes);
    }

    @Test
    public void applicationUsingConcreteAdapterIsDetected() {
        JavaClasses classes = importClasses(ApplicationUsingAdapter.class, FakeLuceneAdapter.class);
        List<String> violations = Violations.of(ArchitectureRules.moduleDependencyRules(registry), classes);
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("knowledge.lucene"));
    }

    @Test
    public void singletonsAndMutableStaticsAreDetected() {
        JavaClasses classes = importClasses(GlobalClientHolder.class);
        List<String> violations = Violations.of(
                Collections.singletonList(ArchitectureRules.noGlobalSingletonsOrMutableStatics()), classes);
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("INSTANCE"));
        assertTrue(violations.get(0), violations.get(0).contains("lastModel"));
        assertTrue(violations.get(0), violations.get(0).contains("MODELS"));
        assertFalse(violations.get(0), violations.get(0).contains("PRIVATE_DEFAULTS"));
    }

    @Test
    public void mainMethodOutsideCompositionRootIsDetected() {
        assertDetected(Collections.singletonList(ArchitectureRules.mainMethodsOnlyInCompositionRoot(registry)),
                importClasses(PortWithMain.class));
    }

    @Test
    public void neutralDomainValuePassesAllRules() {
        Violations.assertNone("Neutraler Domain-Wert verletzt Regeln",
                Violations.of(ArchitectureRules.allRules(registry), importClasses(NeutralValue.class)));
    }

    @Test
    public void unregisteredModuleIsDetected() {
        Set<String> modules = new LinkedHashSet<String>(registry.names());
        modules.add("sharepoint-source");
        BuildModel model = new BuildModel(modules, new HashMap<String, Set<String>>(),
                new HashMap<String, Set<String>>(), new HashMap<String, List<File>>());
        List<String> violations = BuildModelRules.unregisteredModules(model, registry);
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("sharepoint-source"));
    }

    @Test
    public void forbiddenGradleDependenciesAreDetected() {
        Map<String, Set<String>> projectDependencies = new HashMap<String, Set<String>>();
        projectDependencies.put("application", set("domain", "knowledge-lucene"));
        projectDependencies.put("chat-api", set("domain", "chat-openai"));
        Map<String, Set<String>> externalDependencies = new HashMap<String, Set<String>>();
        externalDependencies.put("domain", set("org.apache.lucene:lucene-core"));
        externalDependencies.put("chat-openai", set("com.google.code.gson:gson", "org.apache.lucene:lucene-core"));
        externalDependencies.put("knowledge-lucene", set("org.apache.lucene:lucene-core"));
        externalDependencies.put("mcp-solon-runtime", set("org.noear:solon", "org.noear:solon-ai-mcp"));
        externalDependencies.put("acp-solon-client", set("org.noear:acp-sdk", "org.noear:solon-ai-mcp"));
        Map<String, List<File>> classDirectories = new HashMap<String, List<File>>();
        for (String module : Arrays.asList("application", "chat-api", "domain", "chat-openai", "knowledge-lucene",
                "mcp-solon-runtime", "acp-solon-client")) {
            classDirectories.put(module, Collections.<File>emptyList());
        }
        BuildModel model = new BuildModel(registry.names(), projectDependencies, externalDependencies,
                classDirectories);

        List<String> projectViolations = BuildModelRules.forbiddenProjectDependencies(model, registry);
        assertEquals(projectViolations.toString(), 2, projectViolations.size());
        assertTrue(projectViolations.toString(), projectViolations.toString().contains("application -> knowledge-lucene"));
        assertTrue(projectViolations.toString(), projectViolations.toString().contains("chat-api -> chat-openai"));

        List<String> externalViolations = BuildModelRules.externalLibrariesInCore(model, registry);
        assertEquals(externalViolations.toString(), 1, externalViolations.size());
        assertTrue(externalViolations.get(0), externalViolations.get(0).contains("domain -> org.apache.lucene"));

        List<String> confinementViolations = BuildModelRules.confinedLibrariesOutsideTheirModules(model);
        assertEquals(confinementViolations.toString(), 3, confinementViolations.size());
        assertTrue(confinementViolations.toString(),
                confinementViolations.toString().contains("chat-openai -> org.apache.lucene:lucene-core"));
        assertTrue(confinementViolations.toString(),
                confinementViolations.toString().contains("domain -> org.apache.lucene:lucene-core"));
        assertTrue(confinementViolations.toString(),
                confinementViolations.toString().contains("acp-solon-client -> org.noear:solon-ai-mcp"));
    }

    private static JavaClasses importClasses(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }

    private static void assertDetected(List<com.tngtech.archunit.lang.ArchRule> rules, JavaClasses classes) {
        assertFalse("Regel hat den absichtlichen Verstoß nicht erkannt: " + rules,
                Violations.of(rules, classes).isEmpty());
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }
}
