package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wendet alle Klassenregeln auf die kompilierten Produktionsklassen aller Module an.
 */
public class ClassBoundaryTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();
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
    public void moduleClassDependenciesFollowTheAllowedDirection() {
        Violations.assertNone("Verbotene Modulabhängigkeiten",
                Violations.of(ArchitectureRules.moduleDependencyRules(REGISTRY), productionClasses));
    }

    @Test
    public void coreUsesOnlyNeutralJdkTypes() {
        Violations.assertNone("Nicht neutrale Typen im Kern",
                Violations.of(Collections.singletonList(ArchitectureRules.coreUsesOnlyNeutralJdk(REGISTRY)),
                        productionClasses));
    }

    @Test
    public void coreDoesNotDependOnInfrastructureTechnology() {
        Violations.assertNone("Infrastruktur im Kern",
                Violations.of(ArchitectureRules.coreTechnologyRules(REGISTRY), productionClasses));
    }

    @Test
    public void technologiesStayInTheirAdapter() {
        Violations.assertNone("Technologie außerhalb ihres Adapters",
                Violations.of(ArchitectureRules.technologyConfinementRules(REGISTRY), productionClasses));
    }

    @Test
    public void noGlobalSingletonsOrMutableStatics() {
        Violations.assertNone("Globaler Zustand",
                Violations.of(Collections.singletonList(ArchitectureRules.noGlobalSingletonsOrMutableStatics()),
                        productionClasses));
    }

    @Test
    public void mainMethodsOnlyInCompositionRoot() {
        Violations.assertNone("Programmeinstieg außerhalb der Composition Root",
                Violations.of(Collections.singletonList(ArchitectureRules.mainMethodsOnlyInCompositionRoot(REGISTRY)),
                        productionClasses));
    }
}
