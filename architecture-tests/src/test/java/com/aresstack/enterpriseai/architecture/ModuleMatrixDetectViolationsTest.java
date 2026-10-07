package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.acp.api.archfixture.FakeAcpSession;
import com.aresstack.enterpriseai.acp.demo.archfixture.FakeDemoAgent;
import com.aresstack.enterpriseai.acp.demo.archfixture.matrix.DemoAgentUsingPort;
import com.aresstack.enterpriseai.app.archfixture.FakeShell;
import com.aresstack.enterpriseai.app.archfixture.matrix.CompositionRootUsingDemoAgent;
import com.aresstack.enterpriseai.app.archfixture.matrix.CompositionRootUsingEverything;
import com.aresstack.enterpriseai.application.archfixture.NeutralUseCase;
import com.aresstack.enterpriseai.application.archfixture.matrix.UseCaseUsingComicControls;
import com.aresstack.enterpriseai.application.archfixture.matrix.UseCaseUsingCompositionRoot;
import com.aresstack.enterpriseai.application.archfixture.matrix.UseCaseUsingDemoAgent;
import com.aresstack.enterpriseai.chat.api.archfixture.FakeChatPort;
import com.aresstack.enterpriseai.chat.api.archfixture.matrix.PortUsingApplication;
import com.aresstack.enterpriseai.chat.api.archfixture.matrix.PortUsingItsAdapter;
import com.aresstack.enterpriseai.chat.api.archfixture.matrix.PortUsingOtherPort;
import com.aresstack.enterpriseai.chat.openai.archfixture.FakeChatAdapter;
import com.aresstack.enterpriseai.chat.openai.archfixture.matrix.AdapterUsingApplication;
import com.aresstack.enterpriseai.chat.openai.archfixture.matrix.AdapterUsingComicControls;
import com.aresstack.enterpriseai.chat.openai.archfixture.matrix.AdapterUsingCompositionRoot;
import com.aresstack.enterpriseai.chat.openai.archfixture.matrix.AdapterUsingDemoAgent;
import com.aresstack.enterpriseai.chat.openai.archfixture.matrix.AdapterUsingOtherAdapter;
import com.aresstack.enterpriseai.domain.archfixture.matrix.DomainUsingApplication;
import com.aresstack.enterpriseai.domain.archfixture.matrix.DomainUsingPort;
import com.aresstack.enterpriseai.domain.chat.archfixture.FakeChatValue;
import com.aresstack.enterpriseai.embedding.api.archfixture.FakeEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneAdapter;
import com.aresstack.enterpriseai.ui.comic.archfixture.FakeComicPanel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modulmatrix (ARCHITECTURE.md, AP1/AP24) mit Gegenbeispiel je verbotener Kante: domain → Port/Application,
 * Port → Adapter/Application/anderer Port, Adapter → Adapter/Application/app-swing/comic-controls,
 * Application → app-swing/comic-controls, jede Kante nach acp-demo-agent. Ergänzt
 * {@code RulesDetectViolationsTest.applicationUsingConcreteAdapterIsDetected}.
 */
public class ModuleMatrixDetectViolationsTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();

    /** Fixture → Zielklasse, deren Paket die Kante verbietet. */
    private static Map<Class<?>, Class<?>> forbiddenEdges() {
        Map<Class<?>, Class<?>> edges = new LinkedHashMap<Class<?>, Class<?>>();
        edges.put(DomainUsingPort.class, FakeChatPort.class);
        edges.put(DomainUsingApplication.class, NeutralUseCase.class);
        edges.put(PortUsingItsAdapter.class, FakeChatAdapter.class);
        edges.put(PortUsingApplication.class, NeutralUseCase.class);
        edges.put(PortUsingOtherPort.class, FakeEmbeddingPort.class);
        edges.put(AdapterUsingOtherAdapter.class, FakeLuceneAdapter.class);
        edges.put(AdapterUsingApplication.class, NeutralUseCase.class);
        edges.put(AdapterUsingCompositionRoot.class, FakeShell.class);
        edges.put(AdapterUsingComicControls.class, FakeComicPanel.class);
        edges.put(AdapterUsingDemoAgent.class, FakeDemoAgent.class);
        edges.put(UseCaseUsingCompositionRoot.class, FakeShell.class);
        edges.put(UseCaseUsingComicControls.class, FakeComicPanel.class);
        edges.put(UseCaseUsingDemoAgent.class, FakeDemoAgent.class);
        edges.put(DemoAgentUsingPort.class, FakeAcpSession.class);
        edges.put(CompositionRootUsingDemoAgent.class, FakeDemoAgent.class);
        return edges;
    }

    @Test
    public void everyForbiddenEdgeIsDetected() {
        List<String> missed = new ArrayList<String>();
        for (Map.Entry<Class<?>, Class<?>> edge : forbiddenEdges().entrySet()) {
            List<String> violations = Violations.of(ArchitectureRules.moduleDependencyRules(REGISTRY),
                    ProductionClasses.of(edge.getKey(), edge.getValue()));
            String report = violations.toString();
            if (violations.size() != 1 || !report.contains(edge.getKey().getSimpleName())
                    || !report.contains(edge.getValue().getSimpleName())) {
                missed.add(edge.getKey().getSimpleName() + " -> " + edge.getValue().getSimpleName() + ": " + report);
            }
        }
        Violations.assertNone("Verbotene Modulkante nicht (oder mehrfach) erkannt", missed);
    }

    @Test
    public void compositionRootMayWireEverythingExceptTheDemoAgent() {
        Violations.assertNone("Composition Root darf verdrahten",
                Violations.of(ArchitectureRules.moduleDependencyRules(REGISTRY), ProductionClasses.of(
                        CompositionRootUsingEverything.class, FakeChatAdapter.class, NeutralUseCase.class,
                        FakeChatPort.class, FakeChatValue.class, FakeComicPanel.class)));
    }
}
