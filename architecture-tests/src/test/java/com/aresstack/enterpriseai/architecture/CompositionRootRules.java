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
 * Composition nur in app-swing (Auftrag AP1/AP23, ARCHITECTURE.md): Nur dort werden Konfiguration gelesen
 * und Adapter verdrahtet; Abhängigkeiten kommen über Konstruktoren, nicht über globale Lookups.
 *
 * <ul>
 *   <li>Umgebungsvariablen, Preferences und Properties-Dateien liest nur die Composition Root (und der
 *       Demo-Agent als eigener Prozess). {@code System.getProperty} für JVM-Schlüssel wie {@code user.dir}
 *       bleibt erlaubt, weil der Schlüssel statisch nicht unterscheidbar ist.</li>
 *   <li>Kein {@code ServiceLoader} im Produktionscode.</li>
 *   <li>Jeder Adapter implementiert mindestens ein Interface seines Ports; "externe Systeme sind über Ports
 *       erreichbar".</li>
 *   <li>{@code main} nur in app-swing und acp-demo-agent: {@link ArchitectureRules#mainMethodsOnlyInCompositionRoot}.</li>
 * </ul>
 */
final class CompositionRootRules {

    private CompositionRootRules() {
    }

    static ArchRule configurationIsReadOnlyInTheCompositionRoot(ModuleRegistry registry) {
        return noClasses()
                .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .and().resideOutsideOfPackages(registry.module("app-swing").packagePattern(),
                        registry.module("acp-demo-agent").packagePattern())
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
        for (ArchitectureModule adapter : registry.modulesOfKind(ModuleKind.ADAPTER)) {
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

    private static List<String> names(List<ArchitectureModule> modules) {
        List<String> names = new ArrayList<String>();
        for (ArchitectureModule module : modules) {
            names.add(module.name());
        }
        return names;
    }
}
