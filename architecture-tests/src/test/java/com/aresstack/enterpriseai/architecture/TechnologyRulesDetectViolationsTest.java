package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.acp.api.archfixture.tech.PortUsingAcpSdk;
import com.aresstack.enterpriseai.acp.api.archfixture.tech.PortUsingReactor;
import com.aresstack.enterpriseai.acp.api.archfixture.tech.PortUsingSolon;
import com.aresstack.enterpriseai.acp.api.archfixture.tech.PortUsingSwing;
import com.aresstack.enterpriseai.acp.demo.archfixture.tech.ReactorInsideDemoAgent;
import com.aresstack.enterpriseai.acp.solon.archfixture.tech.AcpSdkInsideItsAdapter;
import com.aresstack.enterpriseai.acp.solon.archfixture.tech.SolonMcpInAcpAdapter;
import com.aresstack.enterpriseai.app.archfixture.tech.McpSdkInCompositionRoot;
import com.aresstack.enterpriseai.app.ui.archfixture.tech.SwingInsideApp;
import com.aresstack.enterpriseai.application.archfixture.tech.UseCaseUsingHttp;
import com.aresstack.enterpriseai.application.archfixture.tech.UseCaseUsingLucene;
import com.aresstack.enterpriseai.application.archfixture.tech.UseCaseUsingSwing;
import com.aresstack.enterpriseai.chat.api.archfixture.tech.PortUsingGson;
import com.aresstack.enterpriseai.chat.openai.archfixture.tech.LuceneInChatAdapter;
import com.aresstack.enterpriseai.chat.openai.archfixture.tech.SolonInChatAdapter;
import com.aresstack.enterpriseai.domain.archfixture.HttpInDomain;
import com.aresstack.enterpriseai.domain.archfixture.NeutralValue;
import com.aresstack.enterpriseai.domain.archfixture.SwingInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.AcpSdkInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.ConfluenceAdapterInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.GsonInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.JsoupInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.JwbfInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.LuceneInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.McpSdkInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.MediaWikiAdapterInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.OkHttpInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.ReactorInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.SolonInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.SolonMcpInDomain;
import com.aresstack.enterpriseai.domain.archfixture.tech.WebSocketInDomain;
import com.aresstack.enterpriseai.embedding.api.archfixture.tech.PortUsingOkHttp;
import com.aresstack.enterpriseai.knowledge.api.archfixture.tech.PortUsingLucene;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.tech.LuceneInsideItsAdapter;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.tech.SwingInLuceneAdapter;
import com.aresstack.enterpriseai.mcp.api.archfixture.tech.PortUsingMcpSdk;
import com.aresstack.enterpriseai.mcp.api.archfixture.tech.PortUsingSolonMcp;
import com.aresstack.enterpriseai.mcp.solon.archfixture.tech.AcpSdkInMcpRuntime;
import com.aresstack.enterpriseai.mcp.solon.archfixture.tech.ReactorInMcpRuntime;
import com.aresstack.enterpriseai.mcp.solon.archfixture.tech.SolonMcpInsideItsRuntime;
import com.aresstack.enterpriseai.security.api.archfixture.tech.PortUsingWebSocket;
import com.aresstack.enterpriseai.security.keepassrpc.archfixture.tech.WebSocketInsideKeePass;
import com.aresstack.enterpriseai.source.api.archfixture.tech.PortUsingJwbf;
import com.aresstack.enterpriseai.source.confluence.archfixture.tech.JwbfInConfluence;
import com.aresstack.enterpriseai.source.mediawiki.archfixture.tech.JwbfInsideMediaWiki;
import com.aresstack.enterpriseai.source.mediawiki.archfixture.tech.WebSocketInMediaWiki;
import com.aresstack.enterpriseai.ui.comic.archfixture.tech.SwingInsideComic;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * AP24-Tabelle, Zeile für Zeile mit Gegenbeispiel: Der Kern (domain, application, *-api) kennt weder Swing,
 * HTTP, Lucene, Solon, ACP SDK, MCP SDK, KeePass(-Transport), JWBF/MediaWiki noch Confluence (auch nicht
 * Gson, jsoup, OkHttp, Reactor); begrenzte Technologien bleiben in ihrem Adapter. Die Bibliothekstypen sind
 * Stubs mit den echten Paketnamen ({@code org.apache.lucene.archstub} ...), weil die Regeln über Pakete
 * arbeiten. Ergänzt {@code RulesDetectViolationsTest} (Swing und HTTP in domain).
 */
public class TechnologyRulesDetectViolationsTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();

    /** Fixture im Kern → Technologie-Label, das die Kernregel melden muss. */
    private static Map<Class<?>, String> coreViolations() {
        Map<Class<?>, String> expected = new LinkedHashMap<Class<?>, String>();
        expected.put(SwingInDomain.class, "Swing");
        expected.put(HttpInDomain.class, "HTTP");
        expected.put(LuceneInDomain.class, "Lucene");
        expected.put(SolonInDomain.class, "Solon");
        expected.put(SolonMcpInDomain.class, "MCP SDK");
        expected.put(McpSdkInDomain.class, "MCP SDK");
        expected.put(AcpSdkInDomain.class, "ACP SDK");
        expected.put(ReactorInDomain.class, "ACP SDK");
        expected.put(WebSocketInDomain.class, "KeePass");
        expected.put(JwbfInDomain.class, "JWBF");
        expected.put(MediaWikiAdapterInDomain.class, "JWBF/MediaWiki");
        expected.put(ConfluenceAdapterInDomain.class, "Confluence");
        expected.put(UseCaseUsingLucene.class, "Lucene");
        expected.put(UseCaseUsingHttp.class, "HTTP");
        expected.put(UseCaseUsingSwing.class, "Swing");
        expected.put(PortUsingLucene.class, "Lucene");
        expected.put(PortUsingAcpSdk.class, "ACP SDK");
        expected.put(PortUsingReactor.class, "ACP SDK");
        expected.put(PortUsingSolon.class, "Solon");
        expected.put(PortUsingSwing.class, "Swing");
        expected.put(PortUsingSolonMcp.class, "MCP SDK");
        expected.put(PortUsingMcpSdk.class, "MCP SDK");
        expected.put(PortUsingWebSocket.class, "KeePass");
        expected.put(PortUsingJwbf.class, "JWBF");
        return expected;
    }

    @Test
    public void everyTechnologyInTheCoreIsDetectedByTheTechnologyRule() {
        List<String> missed = new ArrayList<String>();
        for (Map.Entry<Class<?>, String> entry : coreViolations().entrySet()) {
            String report = Violations.of(ArchitectureRules.coreTechnologyRules(REGISTRY),
                    ProductionClasses.of(entry.getKey())).toString();
            if (!report.contains(entry.getValue()) || !report.contains(entry.getKey().getSimpleName())) {
                missed.add(entry.getKey().getSimpleName() + " (erwartet " + entry.getValue() + "): " + report);
            }
        }
        Violations.assertNone("Technologie im Kern nicht erkannt", missed);
    }

    @Test
    public void everyForeignLibraryInTheCoreIsDetectedByThePositiveList() {
        List<String> missed = new ArrayList<String>();
        List<Class<?>> fixtures = new ArrayList<Class<?>>(coreViolations().keySet());
        // Adapterpakete sind Projekttypen: sie fängt die Modulmatrix, nicht die Positivliste.
        fixtures.remove(MediaWikiAdapterInDomain.class);
        fixtures.remove(ConfluenceAdapterInDomain.class);
        Collections.addAll(fixtures, GsonInDomain.class, JsoupInDomain.class, OkHttpInDomain.class, PortUsingGson.class,
                PortUsingOkHttp.class);
        for (Class<?> fixture : fixtures) {
            if (Violations.of(Collections.singletonList(ArchitectureRules.coreUsesOnlyNeutralJdk(REGISTRY)),
                    ProductionClasses.of(fixture)).isEmpty()) {
                missed.add(fixture.getSimpleName());
            }
        }
        Violations.assertNone("Positivliste des Kerns hat Fixture nicht erkannt", missed);
    }

    /** Fixture außerhalb des erlaubten Moduls → Label der Begrenzungsregel. */
    private static Map<Class<?>, String> confinementViolations() {
        Map<Class<?>, String> expected = new LinkedHashMap<Class<?>, String>();
        expected.put(LuceneInChatAdapter.class, "Lucene");
        expected.put(SolonInChatAdapter.class, "Solon");
        expected.put(AcpSdkInMcpRuntime.class, "ACP SDK");
        expected.put(ReactorInMcpRuntime.class, "ACP SDK");
        expected.put(SolonMcpInAcpAdapter.class, "MCP SDK");
        expected.put(JwbfInConfluence.class, "JWBF");
        expected.put(WebSocketInMediaWiki.class, "KeePass");
        expected.put(SwingInLuceneAdapter.class, "Swing");
        expected.put(SwingInDomain.class, "Swing");
        expected.put(McpSdkInCompositionRoot.class, "MCP SDK");
        return expected;
    }

    @Test
    public void everyConfinedTechnologyOutsideItsModuleIsDetected() {
        List<String> missed = new ArrayList<String>();
        for (Map.Entry<Class<?>, String> entry : confinementViolations().entrySet()) {
            String report = Violations.of(ArchitectureRules.technologyConfinementRules(REGISTRY),
                    ProductionClasses.of(entry.getKey())).toString();
            if (!report.contains(entry.getValue()) || !report.contains(entry.getKey().getSimpleName())) {
                missed.add(entry.getKey().getSimpleName() + " (erwartet " + entry.getValue() + "): " + report);
            }
        }
        Violations.assertNone("Begrenzte Technologie außerhalb ihres Moduls nicht erkannt", missed);
    }

    @Test
    public void confinedTechnologiesInsideTheirModulesPass() {
        Violations.assertNone("Technologie im eigenen Modul darf nicht anschlagen",
                Violations.of(ArchitectureRules.technologyConfinementRules(REGISTRY), ProductionClasses.of(
                        LuceneInsideItsAdapter.class, AcpSdkInsideItsAdapter.class, ReactorInsideDemoAgent.class,
                        SolonMcpInsideItsRuntime.class, WebSocketInsideKeePass.class, JwbfInsideMediaWiki.class,
                        SwingInsideComic.class, SwingInsideApp.class, NeutralValue.class)));
    }

    @Test
    public void technologyTableCoversEveryRegisteredTechnology() {
        // Jede Technologie mit Bibliothekspaketen hat hier ein Gegenbeispiel im Kern; wer Technology erweitert,
        // ergänzt die Tabelle.
        for (Technology technology : Technology.values()) {
            boolean covered = false;
            for (String label : coreViolations().values()) {
                covered |= technology.label().contains(label);
            }
            assertTrue("kein Gegenbeispiel für " + technology.label(), covered);
        }
        assertFalse(ArchitectureRules.confinedTechnologies().isEmpty());
    }
}
