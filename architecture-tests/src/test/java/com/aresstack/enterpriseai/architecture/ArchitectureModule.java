package com.aresstack.enterpriseai.architecture;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registrierter Modulvertrag: Basispaket, Rolle, Strang und erlaubte Produktionsabhängigkeiten.
 */
final class ArchitectureModule {

    private final String name;
    private final String basePackage;
    private final ModuleKind kind;
    private final String owner;
    private final Set<String> allowedDependencies;

    ArchitectureModule(String name, String basePackage, ModuleKind kind, String owner, Set<String> allowedDependencies) {
        this.name = name;
        this.basePackage = basePackage;
        this.kind = kind;
        this.owner = owner;
        this.allowedDependencies = Collections.unmodifiableSet(new LinkedHashSet<String>(allowedDependencies));
    }

    String name() {
        return name;
    }

    String basePackage() {
        return basePackage;
    }

    /** ArchUnit-Paketmuster für das Basispaket inklusive Unterpakete. */
    String packagePattern() {
        return basePackage + "..";
    }

    ModuleKind kind() {
        return kind;
    }

    String owner() {
        return owner;
    }

    Set<String> allowedDependencies() {
        return allowedDependencies;
    }

    boolean ownsPackage(String packageName) {
        return packageName.equals(basePackage) || packageName.startsWith(basePackage + ".");
    }

    @Override
    public String toString() {
        return name + " (" + kind + ", " + basePackage + ")";
    }
}
