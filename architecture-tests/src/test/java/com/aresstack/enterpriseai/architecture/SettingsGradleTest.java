package com.aresstack.enterpriseai.architecture;

import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Jedes Modul aus settings.gradle ist in der ModuleRegistry registriert und umgekehrt; die Gradle-Modulliste
 * im Build-Modell stimmt mit den include-Zeilen überein. Ergänzt {@code BuildModelTest}, das die Liste aus dem
 * Gradle-Objektmodell nimmt.
 */
public class SettingsGradleTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();

    @Test
    public void settingsGradleAndRegistryAgree() {
        File settings = new File(BuildModelExtension.load().rootDir(), "settings.gradle");
        assertTrue(settings.getAbsolutePath(), settings.isFile());
        Set<String> includes = SettingsGradleRules.includes(settings);
        assertTrue("keine include-Zeilen gefunden", includes.size() >= 20);
        Violations.assertNone("settings.gradle ↔ ModuleRegistry", SettingsGradleRules.unregisteredIncludes(includes, REGISTRY));
        Violations.assertNone("ModuleRegistry ↔ settings.gradle", SettingsGradleRules.registeredButNotIncluded(includes, REGISTRY));
        assertEquals(new TreeSet<String>(BuildModel.load().modules()), new TreeSet<String>(includes));
    }

    @Test
    public void unregisteredIncludeIsDetected() {
        Set<String> includes = SettingsGradleRules.includes(
                "rootProject.name = 'x'\ninclude 'domain'\n  include \"source-sharepoint\"\ninclude(':chat-api')\n// include 'commented'\n"
                        + "include 'knowledge-api', 'knowledge-lucene' // 'kommentar'\ninclude(':embedding-api', \":embedding-openai\")\n"
                        + "includeBuild 'tooling'\n");
        assertEquals(includes.toString(), 7, includes.size());
        assertTrue(includes.toString(), includes.contains("knowledge-lucene"));
        assertTrue(includes.toString(), includes.contains("embedding-openai"));
        assertTrue(includes.toString(), !includes.contains("tooling") && !includes.contains("kommentar"));
        List<String> violations = SettingsGradleRules.unregisteredIncludes(includes, REGISTRY);
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("source-sharepoint"));
        assertTrue(SettingsGradleRules.registeredButNotIncluded(includes, REGISTRY).toString().contains("application"));
    }
}
