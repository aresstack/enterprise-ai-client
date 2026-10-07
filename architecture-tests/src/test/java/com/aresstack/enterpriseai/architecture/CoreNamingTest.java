package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.application.archfixture.naming.MultiProviderChatService;
import com.aresstack.enterpriseai.chat.api.archfixture.naming.OllamaChatPort;
import com.aresstack.enterpriseai.chat.openai.archfixture.naming.OpenAiCompatibleFake;
import com.aresstack.enterpriseai.domain.archfixture.naming.ChatProvider;
import com.aresstack.enterpriseai.domain.archfixture.naming.NeutralRole;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Nachtrag 1 und 19: kein Provider und keine Multi-Provider-Abstraktion im Kern. Prüft die Produktionsklassen
 * und, als Gegenbeispiel, Fixtures mit Provider-Namen.
 */
public class CoreNamingTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();

    @Test
    public void productionCoreNamesNoProvider() {
        Violations.assertNone("Provider im Kern", Violations.of(CoreNamingRules.all(REGISTRY), ProductionClasses.all()));
    }

    @Test
    public void providerEnumInDomainIsDetected() {
        List<String> violations = Violations.of(
                Collections.singletonList(CoreNamingRules.coreEnumsHaveNoProviderConstants(REGISTRY)),
                ProductionClasses.of(ChatProvider.class));
        assertEquals(violations.toString(), 1, violations.size());
        for (String constant : new String[] {"OPENAI", "OLLAMA", "CLAUDE", "LLAMA_CPP"}) {
            assertTrue(constant + " nicht erkannt: " + violations.get(0), violations.get(0).contains(constant));
        }
        // Der Klassenname ChatProvider verrät ebenfalls eine Provider-Auswahl.
        assertEquals(1, Violations.of(Collections.singletonList(
                CoreNamingRules.coreClassesAreNotNamedAfterProviders(REGISTRY)), ProductionClasses.of(ChatProvider.class)).size());
    }

    @Test
    public void providerNamedTypesInApplicationAndPortsAreDetected() {
        List<String> violations = Violations.of(
                Collections.singletonList(CoreNamingRules.coreClassesAreNotNamedAfterProviders(REGISTRY)),
                ProductionClasses.of(MultiProviderChatService.class, OllamaChatPort.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("MultiProviderChatService"));
        assertTrue(violations.get(0), violations.get(0).contains("OllamaChatPort"));
    }

    @Test
    public void neutralRolesAndProviderNamedAdaptersPass() {
        Violations.assertNone("Neutrale Namen dürfen nicht anschlagen",
                Violations.of(CoreNamingRules.all(REGISTRY), ProductionClasses.of(NeutralRole.class, OpenAiCompatibleFake.class)));
    }

    @Test
    public void secretProviderIsNotAProvider() {
        assertEquals(null, CoreNamingRules.providerNamedBy("SecretProvider"));
        assertEquals(null, CoreNamingRules.providerNamedBy("MediaWikiCredentialsProvider"));
        assertEquals("llamacpp", CoreNamingRules.providerNamedBy("LLAMA_CPP"));
        assertEquals("multiprovider", CoreNamingRules.providerNamedBy("MultiProviderEmbeddingClient"));
    }
}
