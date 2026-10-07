package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.application.mcp.archfixture.ToolTouchingSecret;
import com.aresstack.enterpriseai.application.mcp.archfixture.ToolUsingIndexPort;
import com.aresstack.enterpriseai.application.mcp.archfixture.ToolUsingLuceneAdapter;
import com.aresstack.enterpriseai.application.mcp.archfixture.ToolUsingOnlyUseCases;
import com.aresstack.enterpriseai.application.mcp.archfixture.ToolUsingSourcePort;
import com.aresstack.enterpriseai.application.mcp.archfixture.usecase.FakeUseCase;
import com.aresstack.enterpriseai.knowledge.api.archfixture.FakeIndexPort;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneAdapter;
import com.aresstack.enterpriseai.security.api.archfixture.secret.FakeSecretMaterial;
import com.aresstack.enterpriseai.source.api.archfixture.FakeSourcePort;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * AP20: Die MCP-Wissenswerkzeuge in {@code application.mcp} sprechen ausschließlich mit Application-Use-Cases.
 *
 * <ul>
 *   <li>Erlaubt sind nur JDK (java.lang, java.util, java.time, java.net.URI), Knowledge-Domain, die Fehlerart des
 *       Source-Ports ({@code KnowledgeSourceException} samt {@code Kind}), der MCP-Port-Vertrag und die Use-Case-Pakete
 *       {@code application.rag} und {@code application.knowledge}. Kein Adapter (Lucene, Solon, HTTP, Swing,
 *       MediaWiki, Confluence), kein Chat, kein ACP, kein Security-Typ.</li>
 *   <li>Index-, Embedding- und Quell-Port (samt {@code SourceScope}) erreichen die Werkzeuge nur über die Use Cases,
 *       nie direkt; aus {@code source.api} bleibt ihnen allein die Fehlerart.</li>
 *   <li>Die Use Cases und der Chat-Pfad kennen die Werkzeuge nicht (Richtung: Werkzeug → Use Case).</li>
 * </ul>
 * Secret-Material ist für application insgesamt bereits durch {@link SecretBoundaryTest} gesperrt; die Regel hier
 * macht zusätzlich jeden Security-Typ (auch den loggbaren {@code SecretRef}) für die Werkzeuge unerreichbar.
 */
public class McpKnowledgeToolsBoundaryTest {

    private static final String ROOT = ModuleRegistry.ROOT_PACKAGE;
    private static final String APPLICATION_MCP = ROOT + ".application.mcp..";
    private static final String SOURCE_EXCEPTION = ROOT + ".source.api.KnowledgeSourceException";

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

    /** Aus {@code source.api} dürfen die Werkzeuge nur die Fehlerart kennen, um sie in Meldungen zu übersetzen. */
    private static DescribedPredicate<JavaClass> sourceErrorKind() {
        return name(SOURCE_EXCEPTION).or(name(SOURCE_EXCEPTION + "$Kind"))
                .as("KnowledgeSourceException oder KnowledgeSourceException.Kind").<JavaClass>forSubtype();
    }

    static ArchRule toolsSeeOnlyUseCasesKnowledgeDomainAndMcpContract() {
        DescribedPredicate<JavaClass> allowed = resideInAnyPackage(
                "java.lang..", "java.util..", "java.time..", "java.net..",
                ROOT + ".domain.knowledge..", ROOT + ".mcp.api..",
                ROOT + ".application.rag..", ROOT + ".application.knowledge..", APPLICATION_MCP)
                .or(sourceErrorKind());
        return classes().that().resideInAPackage(APPLICATION_MCP)
                .should().onlyDependOnClassesThat(allowed)
                .because("MCP-Handler rufen nur Application-Use-Cases auf: kein Index-, Embedding- oder Quell-Port, "
                        + "kein Adapter, kein Transport (Solon/HTTP), kein Swing, kein Chat, kein ACP und kein "
                        + "Security-Typ")
                .allowEmptyShould(true);
    }

    static ArchRule toolsReachPortsOnlyThroughUseCases() {
        DescribedPredicate<JavaClass> ports = resideInAnyPackage(ROOT + ".knowledge.api..", ROOT + ".embedding.api..")
                .or(resideInAnyPackage(ROOT + ".source.api..").and(DescribedPredicate.not(sourceErrorKind())))
                .as("Index-, Embedding- oder Quell-Port (außer der Fehlerart KnowledgeSourceException)");
        return noClasses().that().resideInAPackage(APPLICATION_MCP)
                .should().dependOnClassesThat(ports)
                .because("Index, Embedding und Quellen (Port wie SourceScope) erreichen die Werkzeuge nur über "
                        + "RetrieveKnowledgeUseCase, LoadKnowledgeDocumentUseCase und RefreshKnowledgeSourceUseCase")
                .allowEmptyShould(true);
    }

    static ArchRule useCasesAndChatPathDoNotKnowTheTools() {
        return noClasses().that().resideInAnyPackage(ROOT + ".application.rag..", ROOT + ".application.knowledge..",
                        ROOT + ".application.chat..", ROOT + ".application.agent..", ROOT + ".domain..",
                        ROOT + ".chat..", ROOT + ".knowledge..", ROOT + ".embedding..", ROOT + ".source..")
                .should().dependOnClassesThat().resideInAPackage(APPLICATION_MCP)
                .because("die Werkzeuge liegen außen um die Use Cases; Kern, Ports und Chat-Pfad kennen MCP nicht")
                .allowEmptyShould(true);
    }

    static List<ArchRule> rules() {
        return Arrays.asList(toolsSeeOnlyUseCasesKnowledgeDomainAndMcpContract(),
                toolsReachPortsOnlyThroughUseCases(), useCasesAndChatPathDoNotKnowTheTools());
    }

    @Test
    public void productionToolsRespectTheBoundary() {
        Violations.assertNone("Grenze der MCP-Wissenswerkzeuge verletzt", Violations.of(rules(), productionClasses));
    }

    @Test
    public void toolClassesExist() {
        // Die Regeln oben dürfen nicht leer laufen, weil das Paket umbenannt wurde.
        assertTrue(productionClasses.containPackage(ROOT + ".application.mcp"));
        assertTrue(productionClasses.contain(ROOT + ".application.mcp.KnowledgeMcpTools"));
        assertTrue(productionClasses.contain(ROOT + ".application.knowledge.LoadKnowledgeDocumentUseCase"));
        assertTrue(productionClasses.contain(ROOT + ".application.knowledge.RefreshKnowledgeSourceUseCase"));
        // Die Werkzeuge nutzen die erlaubte Fehlerart tatsächlich; die Ausnahme in der Regel ist also nicht leer.
        assertTrue(productionClasses.get(ROOT + ".application.mcp.GetKnowledgeDocumentTool")
                .getDirectDependenciesFromSelf().toString().contains(SOURCE_EXCEPTION));
    }

    @Test
    public void toolUsingAdapterPortOrSecretIsDetectedButUseCaseOnlyToolIsAllowed() {
        JavaClasses fixtures = new ClassFileImporter().importClasses(ToolUsingLuceneAdapter.class,
                ToolUsingIndexPort.class, ToolUsingSourcePort.class, ToolTouchingSecret.class,
                ToolUsingOnlyUseCases.class, FakeUseCase.class, FakeLuceneAdapter.class, FakeIndexPort.class,
                FakeSourcePort.class, FakeSecretMaterial.class);

        List<String> violations = Violations.of(
                Collections.singletonList(toolsSeeOnlyUseCasesKnowledgeDomainAndMcpContract()), fixtures);

        assertEquals(violations.toString(), 1, violations.size());
        String report = violations.get(0);
        assertTrue(report, report.contains("ToolUsingLuceneAdapter"));
        assertTrue(report, report.contains("ToolUsingIndexPort"));
        assertTrue(report, report.contains("ToolUsingSourcePort"));
        assertTrue(report, report.contains("ToolTouchingSecret"));
        assertFalse(report, report.contains("ToolUsingOnlyUseCases"));
    }

    @Test
    public void directIndexPortAccessIsDetected() {
        List<String> violations = Violations.of(
                Collections.singletonList(toolsReachPortsOnlyThroughUseCases()),
                new ClassFileImporter().importClasses(ToolUsingIndexPort.class, FakeIndexPort.class,
                        ToolUsingOnlyUseCases.class, FakeUseCase.class));

        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("ToolUsingIndexPort"));
        assertFalse(violations.get(0), violations.get(0).contains("ToolUsingOnlyUseCases"));
    }

    @Test
    public void directSourcePortAccessIsDetected() {
        List<String> violations = Violations.of(
                Collections.singletonList(toolsReachPortsOnlyThroughUseCases()),
                new ClassFileImporter().importClasses(ToolUsingSourcePort.class, FakeSourcePort.class,
                        ToolUsingOnlyUseCases.class, FakeUseCase.class));

        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("ToolUsingSourcePort"));
        assertFalse(violations.get(0), violations.get(0).contains("ToolUsingOnlyUseCases"));
    }
}
