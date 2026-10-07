package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.acp.solon.archfixture.signature.ConnectorExposingReactor;
import com.aresstack.enterpriseai.chat.openai.archfixture.signature.AdapterExtendingLibrary;
import com.aresstack.enterpriseai.chat.openai.archfixture.signature.AdapterHidingJson;
import com.aresstack.enterpriseai.chat.openai.archfixture.signature.AdapterLeakingConnection;
import com.aresstack.enterpriseai.chat.openai.archfixture.signature.AdapterParameterizingPort;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.signature.IndexExposingLucene;
import com.aresstack.enterpriseai.mcp.solon.archfixture.signature.RuntimeExposingSolon;
import com.aresstack.enterpriseai.security.keepassrpc.archfixture.signature.AdapterExposingWebSocket;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Technische DTOs und Bibliothekstypen bleiben im Adapter: keine Bibliotheks-/Transporttypen in öffentlichen
 * Signaturen eines Adapters, keine Fremdbibliothek als {@code api}-Abhängigkeit.
 */
public class AdapterSignatureTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();

    @Test
    public void productionAdaptersExposeNoLibraryTypes() {
        Violations.assertNone("Bibliothekstyp in öffentlicher Adapter-Signatur",
                Violations.of(Collections.singletonList(AdapterSignatureRules.publicAdapterApiExposesNoLibraryTypes(REGISTRY)),
                        ProductionClasses.all()));
    }

    @Test
    public void productionModulesExportNoLibraries() {
        Violations.assertNone("Fremdbibliothek als api deklariert",
                AdapterSignatureRules.exportedLibraries(BuildModelExtension.load(), REGISTRY));
    }

    @Test
    public void leakingSignaturesOfEveryKindAreDetected() {
        List<String> violations = Violations.of(
                Collections.singletonList(AdapterSignatureRules.publicAdapterApiExposesNoLibraryTypes(REGISTRY)),
                ProductionClasses.of(AdapterExposingWebSocket.class, RuntimeExposingSolon.class,
                        ConnectorExposingReactor.class, IndexExposingLucene.class, AdapterExtendingLibrary.class,
                        AdapterLeakingConnection.class, AdapterParameterizingPort.class));
        assertEquals(violations.toString(), 1, violations.size());
        String report = violations.get(0);
        assertTrue(report, report.contains("AdapterExposingWebSocket.socket()"));
        assertTrue(report, report.contains("RuntimeExposingSolon.app"));
        assertTrue(report, report.contains("ConnectorExposingReactor.subscribe("));
        assertTrue(report, report.contains("IndexExposingLucene.<init>("));
        assertTrue(report, report.contains("AdapterExtendingLibrary extends"));
        assertTrue(report, report.contains("AdapterLeakingConnection.connection()"));
        assertTrue(report, report.contains("AdapterParameterizingPort implements zeigt com.google.gson.archstub.GsonStub"));
    }

    @Test
    public void libraryTypesInPrivateFieldsAndPackagePrivateMethodsPass() {
        Violations.assertNone("Paketinterne Nutzung muss erlaubt sein",
                Violations.of(Collections.singletonList(AdapterSignatureRules.publicAdapterApiExposesNoLibraryTypes(REGISTRY)),
                        ProductionClasses.of(AdapterHidingJson.class)));
    }

    @Test
    public void libraryDeclaredAsApiIsDetected() {
        Properties properties = new Properties();
        properties.setProperty("modules", "chat-openai,app-swing");
        properties.setProperty("exportedExternalDependencies.chat-openai", "com.google.code.gson:gson");
        properties.setProperty("exportedExternalDependencies.app-swing", "com.google.code.gson:gson");
        List<String> violations = AdapterSignatureRules.exportedLibraries(new BuildModelExtension(properties), REGISTRY);
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("chat-openai -> com.google.code.gson:gson"));
    }
}
