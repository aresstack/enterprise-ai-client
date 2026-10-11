package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.proasteion.exedra.chat.archfixture.ChatBindingBuildingAdapter;
import com.aresstack.enterpriseai.proasteion.exedra.composition.archfixture.RootBuildingAdapter;
import com.aresstack.enterpriseai.proasteion.exedra.config.archfixture.ConfigBuildingAdapter;
import com.aresstack.enterpriseai.proasteion.exedra.security.archfixture.BridgeBuildingAdapterValue;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.archfixture.FakeIndexPortContract;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.FakeLuceneAdapter;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.FakeLuceneIndex;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * AP23: Adapter werden nur in der Composition Root instanziiert. Ein Adapter ist eine Klasse eines
 * Adaptermoduls, die eine Port-Schnittstelle implementiert ({@code LuceneKnowledgeIndex},
 * {@code KeePassRpcSecretProvider}, {@code SolonAcpAgentConnector} …). Außerhalb der Adaptermodule selbst ruft
 * allein {@code proasteion.exedra.composition} ihre Konstruktoren auf; Konfiguration ({@code proasteion.exedra.config}), Brücken
 * ({@code proasteion.exedra.security}), Bindings, Shell und Kern bekommen Ports per Konstruktor. Wert- und
 * Konfigurationstypen der Adaptermodule ({@code KeePassRpcConfig}, {@code MediaWikiSiteConfig},
 * {@code MediaWikiCredentials}) bleiben überall in exedra baubar; die Modulmatrix erlaubt das.
 */
public class CompositionRootBoundaryTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();
    static final String COMPOSITION_PACKAGE = ModuleRegistry.ROOT_PACKAGE + ".proasteion.exedra.composition..";
    /** Kompositionspunkt (proasteion:application, corenth ADR-0001) und die Schale, die ihn nutzt (exedra). */
    static final String[] COMPOSITION_PACKAGES = {
            ModuleRegistry.ROOT_PACKAGE + ".proasteion.application.composition..", COMPOSITION_PACKAGE};
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

    /** Pakete der Module einer Art laut Registry. */
    static List<String> packagesOfKind(ModuleRegistry registry, ModuleKind kind) {
        List<String> packages = new ArrayList<String>();
        for (ArchitectureModule module : registry.modules()) {
            if (module.kind() == kind) {
                packages.add(module.packagePattern());
            }
        }
        return packages;
    }

    static List<String> adapterPackages(ModuleRegistry registry) {
        return packagesOfKind(registry, ModuleKind.ADAPTER);
    }

    static ArchRule adapterConstructorsOnlyCalledFromCompositionRoot(ModuleRegistry registry) {
        String[] adapters = adapterPackages(registry).toArray(new String[0]);
        // Genau die Klassen der Port-Module: in einem Port geschachtelte Adapter (anagraphai in chalcotheca) zählen nicht.
        DescribedPredicate<JavaClass> ports = ArchitectureModule.classesOf(registry.modulesOfKind(ModuleKind.PORT));
        // Ein eigener Agentenprozess (AGENT_PROCESS) ist die Composition Root seiner JVM.
        String[] agentProcesses = packagesOfKind(registry, ModuleKind.AGENT_PROCESS).toArray(new String[0]);
        return noClasses()
                .that().resideOutsideOfPackages(adapters)
                .and().resideOutsideOfPackages(COMPOSITION_PACKAGES)
                .and().resideOutsideOfPackages(agentProcesses)
                .should().callConstructorWhere(target(owner(
                        resideInAnyPackage(adapters).and(assignableTo(ports)))))
                .because("Adapter (Klassen eines Adaptermoduls, die einen Port implementieren) entstehen nur in "
                        + "der Composition Root (app.composition); alles andere arbeitet gegen Ports (AP23)")
                .allowEmptyShould(true);
    }

    @Test
    public void adapterModulesAreKnown() {
        List<String> adapters = adapterPackages(REGISTRY);
        assertTrue(adapters.toString(), adapters.contains(ModuleRegistry.ROOT_PACKAGE + ".astu.acropolis.chalcotheca.anagraphai.."));
        assertTrue(adapters.toString(), adapters.contains(ModuleRegistry.ROOT_PACKAGE + ".proasteion.platform.security.keepassrpc.."));
        assertEquals(ModuleKind.COMPOSITION_ROOT, REGISTRY.module("exedra").kind());
    }

    @Test
    public void productionAdaptersAreOnlyConstructedInTheCompositionRoot() {
        Violations.assertNone("Adapterkonstruktor außerhalb von app.composition",
                Violations.of(Collections.singletonList(adapterConstructorsOnlyCalledFromCompositionRoot(REGISTRY)),
                        productionClasses));
    }

    @Test
    public void bindingOrConfigurationConstructingAnAdapterIsDetected() {
        List<String> violations = Violations.of(
                Collections.singletonList(adapterConstructorsOnlyCalledFromCompositionRoot(REGISTRY)),
                importClasses(ChatBindingBuildingAdapter.class, ConfigBuildingAdapter.class, FakeLuceneIndex.class,
                        FakeIndexPortContract.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("ChatBindingBuildingAdapter"));
        assertTrue(violations.get(0), violations.get(0).contains("ConfigBuildingAdapter"));
    }

    @Test
    public void compositionRootConstructingAnAdapterPasses() {
        Violations.assertNone("proasteion.exedra.composition darf Adapter bauen",
                Violations.of(Collections.singletonList(adapterConstructorsOnlyCalledFromCompositionRoot(REGISTRY)),
                        importClasses(RootBuildingAdapter.class, FakeLuceneIndex.class, FakeIndexPortContract.class)));
    }

    @Test
    public void valueTypesOfAdapterModulesMayBeBuiltAnywhere() {
        Violations.assertNone("Wert-/Konfigurationstypen eines Adaptermoduls sind keine Adapter",
                Violations.of(Collections.singletonList(adapterConstructorsOnlyCalledFromCompositionRoot(REGISTRY)),
                        importClasses(BridgeBuildingAdapterValue.class, FakeLuceneAdapter.class)));
    }

    private static JavaClasses importClasses(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }
}
