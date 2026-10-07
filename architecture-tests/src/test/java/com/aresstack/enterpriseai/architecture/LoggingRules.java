package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.lang.ArchRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Auftrag: keine Secrets in Logs. Statisch prüfbar ist, wo überhaupt geloggt werden kann: Der Kern (domain,
 * application, *-api) und die Comic-Bibliothek loggen nicht; Logging-Frameworks (SLF4J, Log4j, Logback)
 * kommen im Produktionscode nirgends vor (Adapter dürfen {@code java.util.logging} nutzen); Konsolausgaben
 * und {@code printStackTrace} gibt es nur im Demo-Agenten, dessen STDERR das Log ist (AP17).
 */
final class LoggingRules {

    static final String[] LOGGING_FRAMEWORK_PACKAGES = {
            "org.slf4j..", "org.apache.logging..", "org.apache.log4j..", "ch.qos.logback..",
            "org.apache.commons.logging..", "java.util.logging.."};

    private LoggingRules() {
    }

    /** domain, application, *-api und comic-controls loggen nicht, auch nicht über java.util.logging. */
    static ArchRule coreAndUiLibraryDoNotLog(ModuleRegistry registry) {
        List<String> packages = new ArrayList<String>(Arrays.asList(CoreNamingRules.corePackages(registry)));
        packages.add(registry.module("comic-controls").packagePattern());
        return noClasses()
                .that().resideInAnyPackage(packages.toArray(new String[0]))
                .should().dependOnClassesThat().resideInAnyPackage(LOGGING_FRAMEWORK_PACKAGES)
                .because("der Kern hat keine Infrastruktur und kann so auch keine Secrets, Tokens oder "
                        + "Chat-Inhalte loggen; Logging ist Sache der Adapter und der Composition Root")
                .allowEmptyShould(true);
    }

    /** Kein Logging-Framework im Produktionscode; Adapter nutzen höchstens java.util.logging. */
    static ArchRule noLoggingFrameworksInProduction() {
        String[] frameworks = Arrays.copyOf(LOGGING_FRAMEWORK_PACKAGES, LOGGING_FRAMEWORK_PACKAGES.length - 1);
        return noClasses()
                .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAnyPackage(frameworks)
                .because("Logging-Frameworks sind nicht eingebunden (slf4j-nop nur zur Laufzeit der Tests); "
                        + "Adapter loggen höchstens über java.util.logging")
                .allowEmptyShould(true);
    }

    /** Keine Konsolausgabe und kein printStackTrace außerhalb des Demo-Agenten. */
    static ArchRule noConsoleOutputOutsideTheDemoAgent(ModuleRegistry registry) {
        return noClasses()
                .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .and().resideOutsideOfPackage(registry.module("acp-demo-agent").packagePattern())
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .orShould().callMethodWhere(call(Throwable.class, "printStackTrace"))
                .because("Ausgaben gehen über Listener und Ports an die Oberfläche; nur der Demo-Agent "
                        + "schreibt Logs auf STDERR (STDOUT gehört ACP)")
                .allowEmptyShould(true);
    }

    static List<ArchRule> all(ModuleRegistry registry) {
        return Arrays.asList(coreAndUiLibraryDoNotLog(registry), noLoggingFrameworksInProduction(),
                noConsoleOutputOutsideTheDemoAgent(registry));
    }

    /** Aufruf einer Methode mit diesem Namen auf dem Typ oder einem Untertyp. */
    static DescribedPredicate<JavaCall<?>> call(Class<?> owner, String methodName) {
        return JavaCall.Predicates.target(HasName.Predicates.name(methodName))
                .and(JavaCall.Predicates.target(HasOwner.Predicates.With.owner(
                        JavaClass.Predicates.assignableTo(owner))))
                .as(owner.getSimpleName() + "." + methodName + "(..)");
    }

    /** Aufruf einer Methode mit einem dieser Namen auf dem Typ oder einem Untertyp. */
    static DescribedPredicate<JavaCall<?>> callAny(Class<?> owner, String... methodNames) {
        DescribedPredicate<JavaCall<?>> result = null;
        for (String methodName : methodNames) {
            DescribedPredicate<JavaCall<?>> one = call(owner, methodName);
            result = result == null ? one : result.or(one);
        }
        return result.as(owner.getSimpleName() + "." + Arrays.toString(methodNames));
    }
}
