package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Technische DTOs und Bibliothekstypen bleiben im Adapter (Auftrag: "technische DTOs bleiben im jeweiligen
 * Adapter"; ARCHITECTURE.md: Fremdbibliotheken mit {@code implementation}, nicht {@code api}). Für jedes
 * Adaptermodul gilt deshalb: Öffentliche Klassen zeigen in Supertypen (samt Typargumenten), öffentlichen/geschützten Feldern,
 * Konstruktoren und Methoden (Parameter, Rückgabe, Typargumente, Arrays, Exceptions) keinen Typ einer
 * Fremdbibliothek oder des HTTP-Transports. Verallgemeinert {@code SourceBoundaryTest} auf alle Adapter.
 */
final class AdapterSignatureRules {

    static final List<String> LIBRARY_TYPE_PREFIXES = Arrays.asList(
            "com.google.gson.", "org.jsoup.", "net.sourceforge.jwbf.", "okhttp3.", "org.apache.http.",
            "org.apache.hc.", "org.apache.lucene.", "org.noear.", "com.agentclientprotocol.", "reactor.",
            "org.java_websocket.", "io.modelcontextprotocol.", "com.sun.net.httpserver.", "com.fasterxml.jackson.",
            "java.net.URLConnection", "java.net.HttpURLConnection", "java.net.CookieManager",
            "javax.net.ssl.HttpsURLConnection");

    private AdapterSignatureRules() {
    }

    static ArchRule publicAdapterApiExposesNoLibraryTypes(ModuleRegistry registry) {
        List<ArchitectureModule> adapters = registry.modulesOfKind(ModuleKind.ADAPTER);
        String[] packages = new String[adapters.size()];
        for (int i = 0; i < packages.length; i++) {
            packages[i] = adapters.get(i).packagePattern();
        }
        return classes()
                .that().resideInAnyPackage(packages).and().arePublic()
                .should(exposeNoLibraryTypes())
                .because("Bibliotheks- und Transporttypen verlassen den Adapter nicht; Konsumenten sehen nur "
                        + "Port, Konfiguration und Callback-Typen")
                .allowEmptyShould(true);
    }

    /** Adapter und Kern exportieren keine Fremdbibliothek über {@code api}/{@code compileOnlyApi}. */
    static List<String> exportedLibraries(BuildModelExtension extension, ModuleRegistry registry) {
        List<String> violations = new ArrayList<String>();
        for (String module : new TreeSet<String>(extension.modules())) {
            if (!registry.contains(module)) {
                continue;
            }
            ModuleKind kind = registry.module(module).kind();
            if (kind != ModuleKind.ADAPTER && !kind.isCore() && kind != ModuleKind.UI_LIBRARY) {
                continue;
            }
            for (String library : extension.exportedExternalDependenciesOf(module)) {
                violations.add(module + " -> " + library + ": Fremdbibliotheken mit implementation deklarieren, "
                        + "nicht mit api (sonst sickern sie über den Klassenpfad zu Konsumenten)");
            }
        }
        return violations;
    }

    static boolean isLibraryType(JavaClass type) {
        String name = type.getName();
        for (String prefix : LIBRARY_TYPE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static ArchCondition<JavaClass> exposeNoLibraryTypes() {
        return new ArchCondition<JavaClass>("keine Bibliotheks- oder Transporttypen in öffentlichen Signaturen zeigen") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                // Generische Supertypen mitsamt Typargumenten: "implements Port<LibraryType>" zählt ebenfalls.
                if (javaClass.getSuperclass().isPresent()) {
                    for (JavaClass type : javaClass.getSuperclass().get().getAllInvolvedRawTypes()) {
                        report(events, javaClass, javaClass.getName() + " extends", type);
                    }
                }
                for (JavaType implemented : javaClass.getInterfaces()) {
                    for (JavaClass type : implemented.getAllInvolvedRawTypes()) {
                        report(events, javaClass, javaClass.getName() + " implements", type);
                    }
                }
                for (JavaCodeUnit unit : javaClass.getCodeUnits()) {
                    if (!isVisible(unit.getModifiers()) || unit.getName().startsWith("lambda$")) {
                        continue;
                    }
                    List<JavaClass> types = new ArrayList<JavaClass>();
                    for (JavaType parameter : unit.getParameterTypes()) {
                        types.addAll(parameter.getAllInvolvedRawTypes());
                    }
                    types.addAll(unit.getReturnType().getAllInvolvedRawTypes());
                    types.addAll(unit.getExceptionTypes());
                    for (JavaClass type : types) {
                        report(events, javaClass, unit.getFullName(), type);
                    }
                }
                for (JavaField field : javaClass.getFields()) {
                    if (isVisible(field.getModifiers())) {
                        for (JavaClass type : field.getType().getAllInvolvedRawTypes()) {
                            report(events, javaClass, field.getFullName(), type);
                        }
                    }
                }
            }
        };
    }

    private static boolean isVisible(Set<JavaModifier> modifiers) {
        return modifiers.contains(JavaModifier.PUBLIC) || modifiers.contains(JavaModifier.PROTECTED);
    }

    private static void report(ConditionEvents events, JavaClass owner, String member, JavaClass type) {
        if (isLibraryType(type)) {
            events.add(SimpleConditionEvent.violated(owner, member + " zeigt " + type.getName()));
        }
    }
}
