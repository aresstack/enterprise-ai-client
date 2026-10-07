package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.application.archfixture.secret.UseCaseTouchingSecret;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.secret.IndexStoringSecret;
import com.aresstack.enterpriseai.security.api.archfixture.secret.FakeSecretMaterial;
import com.aresstack.enterpriseai.security.api.archfixture.secret.PortCachingSecret;
import com.aresstack.enterpriseai.source.confluence.archfixture.secret.AdapterStoringSecret;
import com.aresstack.enterpriseai.source.confluence.archfixture.secret.AdapterUsingSecretBriefly;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Strang F (AP13): Secret-Material wird in Domain, Application, UI und Knowledge weder referenziert noch
 * gespeichert. Prüft die echten Produktionsklassen und, als Selbsttest, absichtliche Verstöße.
 */
public class SecretBoundaryTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();
    private static final String FAKE_SECRET_MATERIAL = FakeSecretMaterial.class.getName();
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

    @Test
    public void productionSecretMaterialTypeExists() {
        assertTrue("SecretMaterial nicht gefunden; Regeln würden ins Leere laufen",
                productionClasses.contain(SecretBoundaryRules.SECRET_MATERIAL));
    }

    @Test
    public void secretMaterialStaysInAllowedAdapters() {
        Violations.assertNone("Secret-Material außerhalb erlaubter Adapter",
                Violations.of(Collections.singletonList(SecretBoundaryRules.secretMaterialOnlyInAllowedModules(
                        REGISTRY, SecretBoundaryRules.SECRET_MATERIAL)), productionClasses));
    }

    @Test
    public void secretMaterialIsNeverStored() {
        Violations.assertNone("Secret-Material in Feldern",
                Violations.of(Collections.singletonList(SecretBoundaryRules.secretMaterialIsNeverStoredInFields(
                        SecretBoundaryRules.SECRET_MATERIAL)), productionClasses));
    }

    @Test
    public void allowedModulesAreRegisteredAndMaySeeTheSecurityPort() {
        for (String module : SecretBoundaryRules.modulesAllowedToUseSecretMaterial()) {
            assertTrue(module, REGISTRY.contains(module));
            assertTrue(module + " darf security-api laut Registry nicht sehen", module.equals("security-api")
                    || REGISTRY.module(module).allowedDependencies().contains("security-api"));
        }
        for (String forbidden : new String[] {"domain", "application", "app-swing", "comic-controls",
                "knowledge-api", "knowledge-lucene", "mcp-runtime-api", "mcp-solon-runtime"}) {
            assertTrue(forbidden, !SecretBoundaryRules.modulesAllowedToUseSecretMaterial().contains(forbidden));
        }
    }

    @Test
    public void useCaseReferencingSecretMaterialIsDetected() {
        List<String> violations = Violations.of(Collections.singletonList(
                SecretBoundaryRules.secretMaterialOnlyInAllowedModules(REGISTRY, FAKE_SECRET_MATERIAL)),
                importClasses(UseCaseTouchingSecret.class, FakeSecretMaterial.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("UseCaseTouchingSecret"));
    }

    @Test
    public void knowledgeIndexStoringSecretMaterialIsDetectedByBothRules() {
        JavaClasses classes = importClasses(IndexStoringSecret.class, FakeSecretMaterial.class);
        List<String> violations = Violations.of(SecretBoundaryRules.all(REGISTRY, FAKE_SECRET_MATERIAL), classes);
        assertEquals(violations.toString(), 2, violations.size());
    }

    @Test
    public void adapterStoringSecretMaterialDirectlyInCollectionsOrArraysIsDetected() {
        List<String> violations = Violations.of(SecretBoundaryRules.all(REGISTRY, FAKE_SECRET_MATERIAL),
                importClasses(AdapterStoringSecret.class, FakeSecretMaterial.class));
        assertEquals(violations.toString(), 1, violations.size());
        for (String field : new String[] {"cachedLogin", "cachedList", "cachedBySpace", "cachedArray"}) {
            assertTrue(field + " nicht erkannt: " + violations.get(0), violations.get(0).contains(field));
        }
    }

    @Test
    public void cacheInsideTheSecurityPortIsDetected() {
        List<String> violations = Violations.of(SecretBoundaryRules.all(REGISTRY, FAKE_SECRET_MATERIAL),
                importClasses(PortCachingSecret.class, FakeSecretMaterial.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("last"));
    }

    @Test
    public void adapterUsingSecretMaterialBrieflyPasses() {
        Violations.assertNone("Kurzlebige Verwendung im Adapter muss erlaubt sein",
                Violations.of(SecretBoundaryRules.all(REGISTRY, FAKE_SECRET_MATERIAL),
                        importClasses(AdapterUsingSecretBriefly.class, FakeSecretMaterial.class)));
    }

    private static JavaClasses importClasses(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }
}
