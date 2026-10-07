package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.chat.openai.archfixture.config.HardcodedEndpoint;
import com.aresstack.enterpriseai.mcp.solon.archfixture.config.LoopbackOnly;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Nachtrag 1, 5 und 18: Base-URL, Modellnamen, API-Keys sind Konfiguration; MCP bindet nur an 127.0.0.1. */
public class HardcodedConfigurationTest {

    @Test
    public void productionHardcodesNoUrlsModelsKeysOrBindAllAddresses() {
        BuildModel model = BuildModel.load();
        List<File> directories = new ArrayList<File>();
        for (String module : model.scannedModules()) {
            directories.addAll(model.existingClassDirectoriesOf(module));
        }
        List<File> classFiles = ClassFileInfo.classFilesUnder(directories);
        assertTrue("keine Klassendateien gefunden", classFiles.size() > 100);
        Violations.assertNone("Hart codierte Konfiguration", HardcodedConfigurationRules.violations(classFiles));
    }

    @Test
    public void hardcodedEndpointModelKeyAndBindAllAreDetected() {
        List<String> findings = HardcodedConfigurationRules.findings(ClassFileInfo.read(HardcodedEndpoint.class));
        assertEquals(findings.toString(), 5, findings.size());
        assertTrue(findings.toString(), findings.toString().contains("api.openai.com"));
        assertTrue(findings.toString(), findings.toString().contains("[2001:db8::1]"));
        assertTrue(findings.toString(), findings.toString().contains("gpt-oss-120b"));
        assertTrue(findings.toString(), findings.toString().contains("API-Key"));
        assertTrue(findings.toString(), findings.toString().contains("0.0.0.0"));
    }

    @Test
    public void loopbackUrlsPass() {
        ClassFileInfo info = ClassFileInfo.read(LoopbackOnly.class);
        assertTrue(info.stringConstants().toString(), info.stringConstants().toString().contains("127.0.0.1"));
        assertTrue(info.stringConstants().toString(), info.stringConstants().toString().contains("[::1]"));
        Violations.assertNone("Loopback ist erlaubt", HardcodedConfigurationRules.findings(info));
    }
}
