package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Prüft die Gradle-Deklarationen gegen die Registry und die Zuordnung Klasse → Modul.
 */
public class BuildModelTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();
    private static BuildModel model;

    @BeforeClass
    public static void loadBuildModel() {
        model = BuildModel.load();
    }

    @Test
    public void everyGradleModuleIsRegistered() {
        Violations.assertNone("Nicht registrierte Module", BuildModelRules.unregisteredModules(model, REGISTRY));
    }

    @Test
    public void everyRegisteredModuleExists() {
        Violations.assertNone("Registrierte, aber fehlende Module",
                BuildModelRules.registeredButMissingModules(model, REGISTRY));
    }

    @Test
    public void declaredProjectDependenciesFollowTheAllowedDirection() {
        Violations.assertNone("Verbotene Projektabhängigkeiten",
                BuildModelRules.forbiddenProjectDependencies(model, REGISTRY));
    }

    @Test
    public void coreModulesDeclareNoExternalLibraries() {
        Violations.assertNone("Externe Bibliotheken im Kern", BuildModelRules.externalLibrariesInCore(model, REGISTRY));
    }

    @Test
    public void confinedLibrariesAreDeclaredOnlyInTheirModules() {
        Violations.assertNone("Begrenzte Bibliothek außerhalb ihres Moduls deklariert",
                BuildModelRules.confinedLibrariesOutsideTheirModules(model));
    }

    @Test
    public void everyModuleHasClassesOnlyInItsOwnBasePackage() {
        List<String> violations = new ArrayList<String>();
        for (String moduleName : model.scannedModules()) {
            if (!REGISTRY.contains(moduleName)) {
                continue; // meldet everyGradleModuleIsRegistered
            }
            ArchitectureModule module = REGISTRY.module(moduleName);
            List<File> directories = model.existingClassDirectoriesOf(moduleName);
            if (directories.isEmpty()) {
                violations.add(moduleName + " hat keine Produktionsklassen (mindestens die Modul-Anker-Klasse "
                        + "in " + module.basePackage() + " behalten)");
                continue;
            }
            JavaClasses classes = new ClassFileImporter().importPaths(toPaths(directories));
            boolean hasClassInBasePackage = false;
            for (JavaClass javaClass : classes) {
                if (module.ownsPackage(javaClass.getPackageName())) {
                    hasClassInBasePackage = true;
                } else {
                    violations.add(javaClass.getName() + " liegt in " + moduleName + ", aber außerhalb von "
                            + module.basePackage());
                }
            }
            if (!hasClassInBasePackage) {
                violations.add(moduleName + " hat keine Klasse in " + module.basePackage());
            }
        }
        Violations.assertNone("Klassen außerhalb ihres Modulpakets", violations);
    }

    static List<java.nio.file.Path> toPaths(List<File> directories) {
        List<java.nio.file.Path> paths = new ArrayList<java.nio.file.Path>();
        for (File directory : directories) {
            paths.add(directory.toPath());
        }
        return paths;
    }
}
