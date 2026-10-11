package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
    /**
     * Basispakete der Module, die wie in corenth innerhalb dieses Moduls liegen (astu → astu.acropolis →
     * astu.acropolis.chalcotheca …). Deren Klassen gehören dem inneren Modul, nicht diesem.
     */
    private final Set<String> nestedBasePackages;

    ArchitectureModule(String name, String basePackage, ModuleKind kind, String owner, Set<String> allowedDependencies) {
        this(name, basePackage, kind, owner, allowedDependencies, Collections.<String>emptySet());
    }

    ArchitectureModule(String name, String basePackage, ModuleKind kind, String owner, Set<String> allowedDependencies,
                       Set<String> nestedBasePackages) {
        this.name = name;
        this.basePackage = basePackage;
        this.kind = kind;
        this.owner = owner;
        this.allowedDependencies = Collections.unmodifiableSet(new LinkedHashSet<String>(allowedDependencies));
        this.nestedBasePackages = Collections.unmodifiableSet(new LinkedHashSet<String>(nestedBasePackages));
    }

    /** Dasselbe Modul mit den Basispaketen der darin geschachtelten Module. */
    ArchitectureModule withNested(Set<String> nested) {
        return new ArchitectureModule(name, basePackage, kind, owner, allowedDependencies, nested);
    }

    Set<String> nestedBasePackages() {
        return nestedBasePackages;
    }

    String name() {
        return name;
    }

    String basePackage() {
        return basePackage;
    }

    /**
     * ArchUnit-Paketmuster für das Basispaket inklusive Unterpakete. Schließt geschachtelte Module ein; für
     * Regeln über genau dieses Modul {@link #classes()} verwenden.
     */
    String packagePattern() {
        return basePackage + "..";
    }

    /** Die Klassen genau dieses Moduls (ohne die geschachtelten Module). */
    DescribedPredicate<JavaClass> classes() {
        return new DescribedPredicate<JavaClass>("Klassen von " + name) {
            @Override
            public boolean test(JavaClass input) {
                JavaClass type = input.isArray() ? input.getBaseComponentType() : input;
                return ownsPackage(type.getPackageName());
            }
        };
    }

    /** Die Klassen eines der Module. */
    static DescribedPredicate<JavaClass> classesOf(final List<ArchitectureModule> modules) {
        StringBuilder description = new StringBuilder("Klassen von ");
        for (int i = 0; i < modules.size(); i++) {
            description.append(i == 0 ? "" : ", ").append(modules.get(i).name());
        }
        return new DescribedPredicate<JavaClass>(description.toString()) {
            @Override
            public boolean test(JavaClass input) {
                for (ArchitectureModule module : modules) {
                    if (module.classes().test(input)) {
                        return true;
                    }
                }
                return false;
            }
        };
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
        if (!covers(basePackage, packageName)) {
            return false;
        }
        for (String nested : nestedBasePackages) {
            if (covers(nested, packageName)) {
                return false;
            }
        }
        return true;
    }

    static boolean covers(String base, String packageName) {
        return packageName.equals(base) || packageName.startsWith(base + ".");
    }

    @Override
    public String toString() {
        return name + " (" + kind + ", " + basePackage + ")";
    }
}
