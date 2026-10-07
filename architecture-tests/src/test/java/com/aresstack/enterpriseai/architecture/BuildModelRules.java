package com.aresstack.enterpriseai.architecture;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Prüfungen auf Ebene der Gradle-Deklarationen. Sie greifen auch dann, wenn ein Modul eine verbotene
 * Abhängigkeit nur deklariert, aber (noch) keine Klasse sie verwendet.
 */
final class BuildModelRules {

    private BuildModelRules() {
    }

    static List<String> unregisteredModules(BuildModel model, ModuleRegistry registry) {
        List<String> violations = new ArrayList<String>();
        for (String module : model.modules()) {
            if (!registry.contains(module)) {
                violations.add("Gradle-Modul '" + module + "' ist nicht in ModuleRegistry registriert. "
                        + "Neue Module müssen dort mit Basispaket, Rolle, Strang und erlaubten Abhängigkeiten "
                        + "eingetragen und in ARCHITECTURE.md beschrieben werden.");
            }
        }
        return violations;
    }

    static List<String> registeredButMissingModules(BuildModel model, ModuleRegistry registry) {
        List<String> violations = new ArrayList<String>();
        for (String module : registry.names()) {
            if (!model.modules().contains(module)) {
                violations.add("Registriertes Modul '" + module + "' existiert nicht in settings.gradle.");
            }
        }
        return violations;
    }

    static List<String> forbiddenProjectDependencies(BuildModel model, ModuleRegistry registry) {
        List<String> violations = new ArrayList<String>();
        for (String module : new TreeSet<String>(model.scannedModules())) {
            if (!registry.contains(module)) {
                continue;
            }
            ArchitectureModule source = registry.module(module);
            for (String target : model.projectDependenciesOf(module)) {
                if (!source.allowedDependencies().contains(target)) {
                    violations.add(module + " -> " + target + ": nicht erlaubte Projektabhängigkeit ("
                            + source.kind() + " darf nur " + source.allowedDependencies() + " sehen)");
                }
            }
        }
        return violations;
    }

    static List<String> externalLibrariesInCore(BuildModel model, ModuleRegistry registry) {
        List<String> violations = new ArrayList<String>();
        for (String module : new TreeSet<String>(model.scannedModules())) {
            if (!registry.contains(module) || !registry.module(module).kind().isCore()) {
                continue;
            }
            for (String library : model.externalDependenciesOf(module)) {
                violations.add(module + " -> " + library + ": " + registry.module(module).kind()
                        + "-Module dürfen keine externen Bibliotheken deklarieren");
            }
        }
        return violations;
    }
}
