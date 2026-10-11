package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Composition nur in exedra (Auftrag AP1/AP23, ARCHITECTURE.md): Nur dort werden Konfiguration gelesen
 * und Adapter verdrahtet; Abhängigkeiten kommen über Konstruktoren, nicht über globale Lookups.
 *
 * <ul>
 *   <li>Umgebungsvariablen, Preferences und Properties-Dateien liest nur die Composition Root (und der
 *       Demo-Agent als eigener Prozess). {@code System.getProperty} für JVM-Schlüssel wie {@code user.dir}
 *       bleibt erlaubt, weil der Schlüssel statisch nicht unterscheidbar ist.</li>
 *   <li>Kein {@code ServiceLoader} im Produktionscode.</li>
 *   <li>Jeder Adapter implementiert mindestens ein Interface seines Ports; "externe Systeme sind über Ports
 *       erreichbar".</li>
 *   <li>{@code main} nur in exedra und acp-demo-agent: {@link ArchitectureRules#mainMethodsOnlyInCompositionRoot}.</li>
 * </ul>
 */
final class CompositionRootRules {

    private CompositionRootRules() {
    }

    static ArchRule configurationIsReadOnlyInTheCompositionRoot(ModuleRegistry registry) {
        return noClasses()
                .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                // Die Schale (exedra) liest und schreibt die Konfigurationsdatei im Einstellungen-Dialog.
                .and().resideOutsideOfPackages(registry.module("application").packagePattern(),
                        registry.module("exedra").packagePattern(),
                        registry.module("acp-demo-agent").packagePattern(),
                        registry.module("research-agent-acp").packagePattern())
                .should().callMethodWhere(LoggingRules.call(System.class, "getenv"))
                .orShould().dependOnClassesThat().resideInAPackage("java.util.prefs..")
                .orShould().callMethodWhere(LoggingRules.callAny(Properties.class, "load", "loadFromXML"))
                .because("Adapter und Use Cases bekommen eine unveränderliche Konfiguration per Konstruktor; "
                        + "gelesen wird sie nur in der Composition Root (AP23)")
                .allowEmptyShould(true);
    }

    static ArchRule noServiceLoaderLookups() {
        return noClasses()
                .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.ServiceLoader")
                .because("Abhängigkeiten werden über Konstruktoren injiziert, nicht per ServiceLoader gesucht")
                .allowEmptyShould(true);
    }

    static List<ArchRule> all(ModuleRegistry registry) {
        return Arrays.asList(configurationIsReadOnlyInTheCompositionRoot(registry), noServiceLoaderLookups());
    }

    /** Je Adaptermodul: mindestens eine Klasse implementiert ein Interface aus einem seiner Port-Module. */
    static List<String> adaptersWithoutPortImplementation(ModuleRegistry registry, JavaClasses classes) {
        List<String> violations = new ArrayList<String>();
        List<String> registers = registersOfSiblingCatalogs(registry);
        for (ArchitectureModule adapter : registry.modulesOfKind(ModuleKind.ADAPTER)) {
            if (registers.contains(adapter.name())) {
                continue;
            }
            List<ArchitectureModule> ports = new ArrayList<ArchitectureModule>();
            for (String dependency : adapter.allowedDependencies()) {
                if (registry.module(dependency).kind() == ModuleKind.PORT) {
                    ports.add(registry.module(dependency));
                }
            }
            boolean implementsPort = false;
            for (JavaClass javaClass : classes) {
                if (!adapter.ownsPackage(javaClass.getPackageName())) {
                    continue;
                }
                for (JavaClass implemented : javaClass.getAllRawInterfaces()) {
                    for (ArchitectureModule port : ports) {
                        implementsPort |= port.ownsPackage(implemented.getPackageName());
                    }
                }
            }
            if (!implementsPort) {
                violations.add(adapter.name() + " implementiert kein Interface aus " + names(ports)
                        + "; externe Systeme müssen über Ports erreichbar sein");
            }
        }
        return violations;
    }

    /**
     * Adapter, die ein Nachbar-Adapter im selben Elternmodul nutzt, statt selbst einen Port umzusetzen: das
     * lexikalische Register anagraphai, das pinakes (corenth: semantischer Katalog) mit dem Vektorindex verbindet.
     */
    static List<String> registersOfSiblingCatalogs(ModuleRegistry registry) {
        List<String> registers = new ArrayList<String>();
        for (ArchitectureModule adapter : registry.modulesOfKind(ModuleKind.ADAPTER)) {
            for (String dependency : adapter.allowedDependencies()) {
                ArchitectureModule target = registry.module(dependency);
                if (target.kind() == ModuleKind.ADAPTER && parent(target.basePackage()).equals(parent(adapter.basePackage()))
                        && !registers.contains(dependency)) {
                    registers.add(dependency);
                }
            }
        }
        return registers;
    }

    private static String parent(String basePackage) {
        int dot = basePackage.lastIndexOf('.');
        return dot < 0 ? "" : basePackage.substring(0, dot);
    }

    private static List<String> names(List<ArchitectureModule> modules) {
        List<String> names = new ArrayList<String>();
        for (ArchitectureModule module : modules) {
            names.add(module.name());
        }
        return names;
    }
}
