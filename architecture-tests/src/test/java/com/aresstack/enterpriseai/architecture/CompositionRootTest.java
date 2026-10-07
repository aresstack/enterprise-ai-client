package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.acp.demo.archfixture.config.DemoAgentReadingEnvironment;
import com.aresstack.enterpriseai.app.archfixture.config.CompositionRootReadingEnvironment;
import com.aresstack.enterpriseai.application.archfixture.config.UseCaseReadingPreferences;
import com.aresstack.enterpriseai.application.archfixture.config.UseCaseUsingServiceLoader;
import com.aresstack.enterpriseai.chat.api.archfixture.FakeChatPort;
import com.aresstack.enterpriseai.chat.openai.archfixture.AdapterImplementingItsPort;
import com.aresstack.enterpriseai.chat.openai.archfixture.FakeChatAdapter;
import com.aresstack.enterpriseai.chat.openai.archfixture.config.AdapterReadingEnvironment;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.config.IndexLoadingProperties;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Composition nur in app-swing: Konfiguration wird nur dort gelesen, keine ServiceLoader-Lookups, jeder
 * Adapter steht hinter einem Port-Interface. {@code main} nur in app-swing und acp-demo-agent prüft
 * {@code ClassBoundaryTest.mainMethodsOnlyInCompositionRoot}.
 */
public class CompositionRootTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();

    @Test
    public void productionReadsConfigurationOnlyInTheCompositionRoot() {
        Violations.assertNone("Konfiguration außerhalb der Composition Root",
                Violations.of(CompositionRootRules.all(REGISTRY), ProductionClasses.all()));
    }

    @Test
    public void everyProductionAdapterImplementsItsPort() {
        Violations.assertNone("Adapter ohne Port-Implementierung",
                CompositionRootRules.adaptersWithoutPortImplementation(REGISTRY, ProductionClasses.all()));
    }

    @Test
    public void environmentPreferencesAndPropertiesOutsideTheCompositionRootAreDetected() {
        List<String> violations = Violations.of(
                Collections.singletonList(CompositionRootRules.configurationIsReadOnlyInTheCompositionRoot(REGISTRY)),
                ProductionClasses.of(AdapterReadingEnvironment.class, UseCaseReadingPreferences.class,
                        IndexLoadingProperties.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("AdapterReadingEnvironment"));
        assertTrue(violations.get(0), violations.get(0).contains("UseCaseReadingPreferences"));
        assertTrue(violations.get(0), violations.get(0).contains("IndexLoadingProperties"));
    }

    @Test
    public void serviceLoaderIsDetected() {
        List<String> violations = Violations.of(Collections.singletonList(CompositionRootRules.noServiceLoaderLookups()),
                ProductionClasses.of(UseCaseUsingServiceLoader.class));
        assertEquals(violations.toString(), 1, violations.size());
    }

    @Test
    public void compositionRootAndDemoAgentMayReadTheirEnvironment() {
        Violations.assertNone("Composition Root darf Konfiguration lesen",
                Violations.of(CompositionRootRules.all(REGISTRY),
                        ProductionClasses.of(CompositionRootReadingEnvironment.class, DemoAgentReadingEnvironment.class)));
    }

    @Test
    public void adapterWithoutPortInterfaceIsDetected() {
        List<String> violations = CompositionRootRules.adaptersWithoutPortImplementation(REGISTRY,
                ProductionClasses.of(FakeChatAdapter.class, AdapterImplementingItsPort.class, FakeChatPort.class));
        // Alle Adapter außer chat-openai fehlen in dieser Klassenmenge; chat-openai ist durch das Fixture gedeckt.
        assertEquals(violations.toString(), REGISTRY.modulesOfKind(ModuleKind.ADAPTER).size() - 1, violations.size());
        assertTrue(violations.toString(), !violations.toString().contains("chat-openai "));

        List<String> withoutImplementation = CompositionRootRules.adaptersWithoutPortImplementation(REGISTRY,
                ProductionClasses.of(FakeChatAdapter.class));
        assertTrue(withoutImplementation.toString(), withoutImplementation.toString().contains("chat-openai implementiert kein Interface aus [chat-api]"));
    }
}
