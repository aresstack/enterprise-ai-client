package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.app.chat.archfixture.ChatBindingStartingAgent;
import com.aresstack.enterpriseai.acp.api.archfixture.FakeAcpSession;
import com.aresstack.enterpriseai.application.agent.archfixture.AgentUseCaseUsingAcp;
import com.aresstack.enterpriseai.application.archfixture.agentleak.UseCaseUsingAcp;
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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * AP21: Der normale Chat funktioniert vollständig ohne ACP und MCP; der Agent-Modus ist ein getrennter Zusatz.
 *
 * <ul>
 *   <li>Der Chat-Pfad (Modell, Port, Adapter, Use Case, Anbindung, Oberfläche) kennt weder ACP noch MCP noch
 *       den Agent-Modus.</li>
 *   <li>ACP- und MCP-Typen tauchen außerhalb ihrer eigenen Module nur in {@code application.agent},
 *       {@code application.mcp} (AP20), {@code app.agent} und der Composition Root ({@code app}) auf.</li>
 *   <li>Der Agent-Use-Case sieht nur den ACP-Port, keine MCP-Typen und nicht den Chat.</li>
 *   <li>Die Modus-Umschaltung ({@code app.ui.agent}) ist reine Oberfläche: keine Use Cases, keine Ports.</li>
 * </ul>
 *
 * AP20 (MCP-Wissenswerkzeuge in {@code application.mcp}) ist in {@link #ACP_MCP_ALLOWED} eingetragen; seine
 * eigenen Grenzen prüft {@link McpKnowledgeToolsBoundaryTest}.
 */
public class AgentModeBoundaryTest {

    private static final String ROOT = ModuleRegistry.ROOT_PACKAGE;
    private static final String[] CHAT_PATH = {
            ROOT + ".domain.chat..", ROOT + ".chat.api..", ROOT + ".chat.openai..", ROOT + ".application.chat..",
            ROOT + ".app.chat..", ROOT + ".app.ui.chat.."};
    private static final String[] AGENT_AND_PROTOCOLS = {
            ROOT + ".acp..", ROOT + ".mcp..", ROOT + ".application.agent..", ROOT + ".app.agent..",
            ROOT + ".app.ui.agent..", "com.agentclientprotocol..", "io.modelcontextprotocol..", "org.noear.."};
    private static final String[] ACP_AND_MCP = {ROOT + ".acp..", ROOT + ".mcp.."};
    /** Wo ACP-/MCP-Typen außerhalb ihrer eigenen Module vorkommen dürfen. */
    private static final String[] ACP_MCP_ALLOWED = {
            ROOT + ".acp..", ROOT + ".mcp..", ROOT + ".application.agent..", ROOT + ".application.mcp..",
            ROOT + ".app.agent..", ROOT + ".app"};

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

    static ArchRule chatPathKnowsNoAgentModeAcpOrMcp() {
        return noClasses().that().resideInAnyPackage(CHAT_PATH)
                .should().dependOnClassesThat().resideInAnyPackage(AGENT_AND_PROTOCOLS)
                .because("der normale Chat muss ohne ACP, MCP und Agent-Modus funktionieren")
                .allowEmptyShould(true);
    }

    static ArchRule acpAndMcpStayInAgentPackagesAndCompositionRoot() {
        return noClasses().that().resideOutsideOfPackages(ACP_MCP_ALLOWED)
                .should().dependOnClassesThat().resideInAnyPackage(ACP_AND_MCP)
                .because("ACP und MCP sind eigenständige Fähigkeiten und leaken nicht in Chat-, Knowledge- "
                        + "oder Domain-Kern")
                .allowEmptyShould(true);
    }

    static ArchRule agentUseCaseSeesOnlyTheAcpPort() {
        return classes().that().resideInAPackage(ROOT + ".application.agent..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java.lang..", "java.util..",
                        ROOT + ".acp.api..", ROOT + ".application.agent..")
                .because("Tool-Endpunkte und Tokens legt die Composition Root an; der Use Case kennt nur ACP")
                .allowEmptyShould(true);
    }

    static ArchRule modeSwitchIsPureUi() {
        return classes().that().resideInAPackage(ROOT + ".app.ui.agent..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "javax..",
                        ROOT + ".app.ui..", ROOT + ".ui.comic..")
                .because("die Modus-Umschaltung zeigt nur an; angebunden wird in app.agent bzw. app.chat")
                .allowEmptyShould(true);
    }

    static List<ArchRule> rules() {
        return Arrays.asList(chatPathKnowsNoAgentModeAcpOrMcp(), acpAndMcpStayInAgentPackagesAndCompositionRoot(),
                agentUseCaseSeesOnlyTheAcpPort(), modeSwitchIsPureUi());
    }

    @Test
    public void productionCodeKeepsTheAgentModeSeparate() {
        Violations.assertNone("Grenze des Agent-Modus verletzt", Violations.of(rules(), productionClasses));
    }

    @Test
    public void agentModeClassesExist() {
        // Die Regeln oben dürfen nicht leer laufen, weil ein Paket umbenannt wurde.
        assertTrue(productionClasses.containPackage(ROOT + ".application.agent"));
        assertTrue(productionClasses.containPackage(ROOT + ".app.agent"));
        assertTrue(productionClasses.containPackage(ROOT + ".app.ui.agent"));
        assertTrue(productionClasses.containPackage(ROOT + ".app.chat"));
        assertTrue(productionClasses.containPackage(ROOT + ".application.chat"));
    }

    @Test
    public void chatBindingUsingAcpIsDetected() {
        List<String> violations = Violations.of(Collections.singletonList(chatPathKnowsNoAgentModeAcpOrMcp()),
                new ClassFileImporter().importClasses(ChatBindingStartingAgent.class, FakeAcpSession.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("ChatBindingStartingAgent"));
    }

    @Test
    public void acpOutsideTheAgentPackagesIsDetectedButAllowedInside() {
        List<String> violations = Violations.of(
                Collections.singletonList(acpAndMcpStayInAgentPackagesAndCompositionRoot()),
                new ClassFileImporter().importClasses(UseCaseUsingAcp.class, AgentUseCaseUsingAcp.class,
                        FakeAcpSession.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("UseCaseUsingAcp"));
        assertTrue(violations.get(0), !violations.get(0).contains("AgentUseCaseUsingAcp"));
    }
}
