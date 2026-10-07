package com.aresstack.enterpriseai.architecture;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Schützt die Registry selbst: Wer die erlaubten Abhängigkeiten lockert, darf die Grundrichtung
 * UI/Adapter → Application → Domain + Ports nicht unterlaufen.
 */
public class ModuleRegistryTest {

    private final ModuleRegistry registry = ModuleRegistry.standard();

    /** Die in AP24 ausdrücklich verbotenen Modulkanten. */
    private static final List<String[]> AP24_FORBIDDEN_EDGES = Arrays.asList(
            new String[] {"chat-api", "chat-openai"},
            new String[] {"embedding-api", "embedding-openai"},
            new String[] {"knowledge-api", "knowledge-lucene"},
            new String[] {"source-api", "source-mediawiki"},
            new String[] {"source-api", "source-confluence"},
            new String[] {"security-api", "security-keepassrpc"},
            new String[] {"acp-client-api", "acp-solon-client"},
            new String[] {"mcp-runtime-api", "mcp-solon-runtime"});

    @Test
    public void allowedDependenciesReferToRegisteredModules() {
        List<String> violations = new ArrayList<String>();
        for (ArchitectureModule module : registry.modules()) {
            for (String target : module.allowedDependencies()) {
                if (!registry.contains(target)) {
                    violations.add(module.name() + " erlaubt unbekanntes Modul " + target);
                }
                if (target.equals(module.name())) {
                    violations.add(module.name() + " erlaubt sich selbst");
                }
            }
        }
        Violations.assertNone("Ungültige Registry-Einträge", violations);
    }

    @Test
    public void allowedDependenciesFollowTheLayering() {
        List<String> violations = new ArrayList<String>();
        for (ArchitectureModule module : registry.modules()) {
            Set<ModuleKind> permitted = permittedTargetKinds(module.kind());
            for (String target : module.allowedDependencies()) {
                ModuleKind targetKind = registry.module(target).kind();
                if (!permitted.contains(targetKind)) {
                    violations.add(module.name() + " (" + module.kind() + ") darf " + target + " ("
                            + targetKind + ") nicht sehen");
                }
            }
        }
        Violations.assertNone("Registry verletzt die Schichtung", violations);
    }

    @Test
    public void ap24ForbiddenModuleEdgesStayForbidden() {
        List<String> violations = new ArrayList<String>();
        for (ArchitectureModule module : registry.modulesOfKind(ModuleKind.DOMAIN)) {
            if (!module.allowedDependencies().isEmpty()) {
                violations.add("domain darf keine Abhängigkeiten haben, erlaubt aber " + module.allowedDependencies());
            }
        }
        for (ArchitectureModule adapter : registry.modulesOfKind(ModuleKind.ADAPTER)) {
            if (registry.module("application").allowedDependencies().contains(adapter.name())) {
                violations.add("application -> " + adapter.name() + " (konkreter Adapter) ist erlaubt");
            }
        }
        for (String[] edge : AP24_FORBIDDEN_EDGES) {
            if (registry.module(edge[0]).allowedDependencies().contains(edge[1])) {
                violations.add(edge[0] + " -> " + edge[1] + " ist erlaubt");
            }
        }
        Violations.assertNone("AP24-Kanten sind erlaubt", violations);
    }

    @Test
    public void everyAdapterMaySeeAtLeastOnePort() {
        for (ArchitectureModule adapter : registry.modulesOfKind(ModuleKind.ADAPTER)) {
            boolean seesPort = false;
            for (String target : adapter.allowedDependencies()) {
                seesPort |= registry.module(target).kind() == ModuleKind.PORT;
            }
            assertTrue(adapter.name() + " implementiert keinen Port", seesPort);
        }
    }

    @Test
    public void allowedDependenciesAreAcyclic() {
        for (ArchitectureModule module : registry.modules()) {
            assertAcyclic(module.name(), new ArrayList<String>());
        }
    }

    @Test
    public void basePackagesAreDisjoint() {
        List<String> violations = new ArrayList<String>();
        for (ArchitectureModule left : registry.modules()) {
            for (ArchitectureModule right : registry.modules()) {
                if (left != right && left.ownsPackage(right.basePackage())) {
                    violations.add(left + " überdeckt " + right);
                }
            }
        }
        Violations.assertNone("Überlappende Basispakete", violations);
    }

    private void assertAcyclic(String current, List<String> path) {
        if (path.contains(current)) {
            throw new AssertionError("Zyklus in erlaubten Abhängigkeiten: " + path + " -> " + current);
        }
        path.add(current);
        for (String next : registry.module(current).allowedDependencies()) {
            assertAcyclic(next, path);
        }
        path.remove(path.size() - 1);
    }

    private static Set<ModuleKind> permittedTargetKinds(ModuleKind kind) {
        switch (kind) {
            case DOMAIN:
            case UI_LIBRARY:
            case TEST_FIXTURE:
            case ARCHITECTURE_TESTS:
            case INTEGRATION_TESTS:
                return EnumSet.noneOf(ModuleKind.class);
            case PORT:
                return EnumSet.of(ModuleKind.DOMAIN);
            case APPLICATION:
            case ADAPTER:
                return EnumSet.of(ModuleKind.DOMAIN, ModuleKind.PORT);
            case COMPOSITION_ROOT:
                return EnumSet.of(ModuleKind.DOMAIN, ModuleKind.PORT, ModuleKind.APPLICATION,
                        ModuleKind.ADAPTER, ModuleKind.UI_LIBRARY);
            default:
                throw new IllegalStateException("Unbekannte Modulrolle " + kind);
        }
    }
}
