package com.aresstack.enterpriseai.architecture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * Begrenzte Bibliotheken (Lucene, ACP SDK, Solon, Solon MCP, JWBF, KeePassRPC-Transport) dürfen auch
     * ohne Klassenreferenz, etwa als {@code runtimeOnly}, nur in ihren erlaubten Modulen deklariert werden.
     */
    static List<String> confinedLibrariesOutsideTheirModules(BuildModel model) {
        List<String> violations = new ArrayList<String>();
        for (String module : new TreeSet<String>(model.scannedModules())) {
            for (String library : model.externalDependenciesOf(module)) {
                for (Map.Entry<Technology, List<String>> entry : ArchitectureRules.confinedTechnologies().entrySet()) {
                    if (entry.getKey().matchesGradleCoordinate(library) && !entry.getValue().contains(module)) {
                        violations.add(module + " -> " + library + ": " + entry.getKey().label()
                                + " ist auf " + entry.getValue() + " begrenzt");
                    }
                }
            }
        }
        return violations;
    }
}
