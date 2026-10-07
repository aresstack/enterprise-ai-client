package com.aresstack.enterpriseai.architecture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die einzige Stelle, an der Module und ihre erlaubten Abhängigkeiten festgelegt sind.
 *
 * <p>Ein neues Gradle-Modul muss hier bewusst eingetragen werden (Name, Basispaket, Rolle, Strang,
 * erlaubte Abhängigkeiten). Fehlt der Eintrag, wird {@code BuildModelTest.everyGradleModuleIsRegistered}
 * rot. Eine Lockerung der erlaubten Richtung ist eine Architekturentscheidung und gehört in
 * ARCHITECTURE.md; {@link ModuleRegistryTest} verhindert, dass die Grundregeln dabei unterlaufen werden.
 */
final class ModuleRegistry {

    static final String ROOT_PACKAGE = "com.aresstack.enterpriseai";

    private final Map<String, ArchitectureModule> modules;

    private ModuleRegistry(Map<String, ArchitectureModule> modules) {
        this.modules = Collections.unmodifiableMap(new LinkedHashMap<String, ArchitectureModule>(modules));
    }

    static ModuleRegistry standard() {
        return new Builder()
                .module("domain", "domain", ModuleKind.DOMAIN, "Kern")
                .module("application", "application", ModuleKind.APPLICATION, "Kern (AP10, AP20, AP21, AP23)",
                        "domain", "chat-api", "embedding-api", "knowledge-api", "source-api", "security-api",
                        "acp-client-api", "mcp-runtime-api")

                .module("chat-api", "chat.api", ModuleKind.PORT, "A", "domain")
                .module("chat-openai", "chat.openai", ModuleKind.ADAPTER, "A", "domain", "chat-api")

                .module("embedding-api", "embedding.api", ModuleKind.PORT, "C", "domain")
                .module("embedding-openai", "embedding.openai", ModuleKind.ADAPTER, "C", "domain", "embedding-api")

                .module("knowledge-api", "knowledge.api", ModuleKind.PORT, "D", "domain")
                .module("knowledge-lucene", "knowledge.lucene", ModuleKind.ADAPTER, "D", "domain", "knowledge-api")

                .module("source-api", "source.api", ModuleKind.PORT, "E", "domain")
                .module("source-mediawiki", "source.mediawiki", ModuleKind.ADAPTER, "E", "domain", "source-api")
                .module("source-confluence", "source.confluence", ModuleKind.ADAPTER, "F",
                        "domain", "source-api", "security-api")

                .module("security-api", "security.api", ModuleKind.PORT, "F", "domain")
                .module("security-keepassrpc", "security.keepassrpc", ModuleKind.ADAPTER, "F", "domain", "security-api")

                .module("acp-client-api", "acp.api", ModuleKind.PORT, "G", "domain")
                .module("acp-solon-client", "acp.solon", ModuleKind.ADAPTER, "G", "domain", "acp-client-api")
                .module("acp-demo-agent", "acp.demo", ModuleKind.TEST_FIXTURE, "G")

                .module("mcp-runtime-api", "mcp.api", ModuleKind.PORT, "H", "domain")
                .module("mcp-solon-runtime", "mcp.solon", ModuleKind.ADAPTER, "H", "domain", "mcp-runtime-api")

                .module("comic-controls", "ui.comic", ModuleKind.UI_LIBRARY, "B")
                .module("app-swing", "app", ModuleKind.COMPOSITION_ROOT, "B (AP4, AP22, AP23)",
                        "domain", "application",
                        "chat-api", "embedding-api", "knowledge-api", "source-api", "security-api",
                        "acp-client-api", "mcp-runtime-api",
                        "chat-openai", "embedding-openai", "knowledge-lucene", "source-mediawiki",
                        "source-confluence", "security-keepassrpc", "acp-solon-client", "mcp-solon-runtime",
                        "comic-controls")

                .module("architecture-tests", "architecture", ModuleKind.ARCHITECTURE_TESTS, "AP24")
                .module("integration-tests", "integration", ModuleKind.INTEGRATION_TESTS, "AP25")
                .build();
    }

    Collection<ArchitectureModule> modules() {
        return modules.values();
    }

    Set<String> names() {
        return modules.keySet();
    }

    boolean contains(String name) {
        return modules.containsKey(name);
    }

    ArchitectureModule module(String name) {
        ArchitectureModule module = modules.get(name);
        if (module == null) {
            throw new IllegalArgumentException("Modul nicht registriert: " + name);
        }
        return module;
    }

    List<ArchitectureModule> modulesOfKind(ModuleKind... kinds) {
        List<ModuleKind> wanted = Arrays.asList(kinds);
        List<ArchitectureModule> result = new ArrayList<ArchitectureModule>();
        for (ArchitectureModule module : modules.values()) {
            if (wanted.contains(module.kind())) {
                result.add(module);
            }
        }
        return result;
    }

    /** Module, deren Klassen von {@code source} aus nicht referenziert werden dürfen. */
    List<ArchitectureModule> forbiddenTargetsOf(ArchitectureModule source) {
        List<ArchitectureModule> result = new ArrayList<ArchitectureModule>();
        for (ArchitectureModule candidate : modules.values()) {
            if (candidate != source && !source.allowedDependencies().contains(candidate.name())) {
                result.add(candidate);
            }
        }
        return result;
    }

    /** Liefert das Modul, dem ein Paket gehört, oder {@code null}. */
    ArchitectureModule ownerOfPackage(String packageName) {
        for (ArchitectureModule module : modules.values()) {
            if (module.ownsPackage(packageName)) {
                return module;
            }
        }
        return null;
    }

    static final class Builder {

        private final Map<String, ArchitectureModule> modules = new LinkedHashMap<String, ArchitectureModule>();

        Builder module(String name, String packageSuffix, ModuleKind kind, String owner, String... allowedDependencies) {
            if (modules.containsKey(name)) {
                throw new IllegalArgumentException("Modul doppelt registriert: " + name);
            }
            modules.put(name, new ArchitectureModule(name, ROOT_PACKAGE + "." + packageSuffix, kind, owner,
                    new LinkedHashSet<String>(Arrays.asList(allowedDependencies))));
            return this;
        }

        ModuleRegistry build() {
            return new ModuleRegistry(modules);
        }
    }
}
