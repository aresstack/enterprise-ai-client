package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * ArchUnit-Regeln, abgeleitet aus der {@link ModuleRegistry}. Die Regeln sind reine Fabriken, damit
 * {@code ClassBoundaryTest} sie auf die echten Produktionsklassen und {@code RulesDetectViolationsTest}
 * sie auf absichtlich fehlerhafte Fixtures anwenden kann.
 */
final class ArchitectureRules {

    /**
     * JDK-Pakete, die Kernmodule (domain, application, *-api) verwenden dürfen. Alles andere – Swing/AWT,
     * java.net-Verbindungen, java.sql, javax.*, jede Fremdbibliothek – ist im Kern verboten. Eine
     * Erweiterung ist eine Architekturentscheidung (Contract-Commit plus ARCHITECTURE.md).
     */
    static final String[] NEUTRAL_JDK_PACKAGES = {
            "java.lang..", "java.util..", "java.io..", "java.nio..", "java.time..", "java.math..",
            "java.text..", "java.security.."
    };

    /** Einzelne erlaubte Klassen aus sonst gesperrten JDK-Paketen. */
    static final List<String> NEUTRAL_JDK_CLASSES = Arrays.asList(
            "java.net.URI", "java.net.URISyntaxException");

    /**
     * Welche Module eine Fremdbibliothek verwenden dürfen. Adapterpakete selbst (z. B. source.confluence)
     * schützt die Modulmatrix; die Composition Root darf sie instanziieren.
     */
    static Map<Technology, List<String>> confinedTechnologies() {
        Map<Technology, List<String>> confinement = new LinkedHashMap<Technology, List<String>>();
        confinement.put(Technology.SWING, Arrays.asList("app-swing", "comic-controls"));
        confinement.put(Technology.LUCENE, Arrays.asList("knowledge-lucene"));
        confinement.put(Technology.ACP_SDK, Arrays.asList("acp-solon-client", "acp-demo-agent"));
        confinement.put(Technology.SOLON, Arrays.asList("acp-solon-client", "acp-demo-agent", "mcp-solon-runtime"));
        confinement.put(Technology.MCP_SDK, Arrays.asList("mcp-solon-runtime"));
        confinement.put(Technology.JWBF, Arrays.asList("source-mediawiki"));
        confinement.put(Technology.KEEPASS, Arrays.asList("security-keepassrpc"));
        return confinement;
    }

    private ArchitectureRules() {
    }

    /** Je Modul: keine Klassenabhängigkeit auf ein Modul außerhalb seiner erlaubten Abhängigkeiten. */
    static List<ArchRule> moduleDependencyRules(ModuleRegistry registry) {
        List<ArchRule> rules = new ArrayList<ArchRule>();
        for (ArchitectureModule source : registry.modules()) {
            List<ArchitectureModule> forbidden = registry.forbiddenTargetsOf(source);
            if (forbidden.isEmpty()) {
                continue;
            }
            String[] forbiddenPackages = new String[forbidden.size()];
            for (int i = 0; i < forbidden.size(); i++) {
                forbiddenPackages[i] = forbidden.get(i).packagePattern();
            }
            rules.add(noClasses()
                    .that().resideInAPackage(source.packagePattern())
                    .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                    .because(source.name() + " (" + source.kind() + ") darf nur " + source.allowedDependencies()
                            + " sehen")
                    .allowEmptyShould(true));
        }
        return rules;
    }

    /** domain, application und *-api verwenden ausschließlich neutrale JDK-Typen und eigene Projekttypen. */
    static ArchRule coreUsesOnlyNeutralJdk(ModuleRegistry registry) {
        return classes()
                .that().resideInAnyPackage(packagePatterns(registry, coreModules(registry)))
                .should().onlyDependOnClassesThat(neutralForCore())
                .because("der Kern (domain, application, *-api) darf keine Infrastruktur, kein Swing/AWT und "
                        + "keine Fremdbibliothek kennen")
                .allowEmptyShould(true);
    }

    /** AP24-Tabelle: Kernmodule kennen keine der benannten Technologien. */
    static List<ArchRule> coreTechnologyRules(ModuleRegistry registry) {
        List<ArchRule> rules = new ArrayList<ArchRule>();
        String[] corePackages = packagePatterns(registry, coreModules(registry));
        for (Technology technology : Technology.values()) {
            rules.add(noClasses()
                    .that().resideInAnyPackage(corePackages)
                    .should().dependOnClassesThat(technology.predicate())
                    .because("domain, application und *-api dürfen nicht von " + technology.label() + " abhängen")
                    .allowEmptyShould(true));
        }
        return rules;
    }

    /** Lucene nur in knowledge-lucene, ACP SDK nur im ACP-Adapter/Demo-Agent, Solon MCP nur in mcp-solon-runtime ... */
    static List<ArchRule> technologyConfinementRules(ModuleRegistry registry) {
        List<ArchRule> rules = new ArrayList<ArchRule>();
        for (Map.Entry<Technology, List<String>> entry : confinedTechnologies().entrySet()) {
            rules.add(noClasses()
                    .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                    .and().resideOutsideOfPackages(packagePatterns(registry, entry.getValue()))
                    .should().dependOnClassesThat(entry.getKey().libraryPredicate())
                    .because(entry.getKey().label() + " ist auf " + entry.getValue() + " begrenzt")
                    .allowEmptyShould(true));
        }
        return rules;
    }

    /** Keine globalen Singletons und kein globaler veränderlicher Zustand im Produktionscode. */
    static ArchRule noGlobalSingletonsOrMutableStatics() {
        return classes()
                .that().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .should(notHoldGlobalState())
                .because("Abhängigkeiten werden über Konstruktoren injiziert; globale Singletons sind verboten")
                .allowEmptyShould(true);
    }

    /** Programmeinstiege nur in der Composition Root und im separaten Demo-Agent-Prozess. */
    static ArchRule mainMethodsOnlyInCompositionRoot(ModuleRegistry registry) {
        return noMethods()
                .that().haveName("main").and().areStatic()
                .and().areDeclaredInClassesThat().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .and().areDeclaredInClassesThat().resideOutsideOfPackages(packagePatterns(registry,
                        Arrays.asList("app-swing", "acp-demo-agent")))
                .should().haveRawParameterTypes(String[].class)
                .because("die Anwendung wird ausschließlich in app-swing zusammengesetzt und gestartet")
                .allowEmptyShould(true);
    }

    static List<ArchRule> allRules(ModuleRegistry registry) {
        List<ArchRule> rules = new ArrayList<ArchRule>();
        rules.addAll(moduleDependencyRules(registry));
        rules.add(coreUsesOnlyNeutralJdk(registry));
        rules.addAll(coreTechnologyRules(registry));
        rules.addAll(technologyConfinementRules(registry));
        rules.add(noGlobalSingletonsOrMutableStatics());
        rules.add(mainMethodsOnlyInCompositionRoot(registry));
        return rules;
    }

    static List<String> coreModules(ModuleRegistry registry) {
        List<String> names = new ArrayList<String>();
        for (ArchitectureModule module : registry.modules()) {
            if (module.kind().isCore()) {
                names.add(module.name());
            }
        }
        return names;
    }

    private static String[] packagePatterns(ModuleRegistry registry, List<String> moduleNames) {
        String[] patterns = new String[moduleNames.size()];
        for (int i = 0; i < moduleNames.size(); i++) {
            patterns[i] = registry.module(moduleNames.get(i)).packagePattern();
        }
        return patterns;
    }

    private static DescribedPredicate<JavaClass> neutralForCore() {
        return new DescribedPredicate<JavaClass>("neutrale JDK-Typen oder Projekttypen") {
            @Override
            public boolean test(JavaClass target) {
                JavaClass type = target.isArray() ? target.getBaseComponentType() : target;
                if (type.isPrimitive()) {
                    return true;
                }
                String packageName = type.getPackageName();
                if (packageName.equals(ModuleRegistry.ROOT_PACKAGE)
                        || packageName.startsWith(ModuleRegistry.ROOT_PACKAGE + ".")) {
                    return true;
                }
                if (NEUTRAL_JDK_CLASSES.contains(type.getName())) {
                    return true;
                }
                for (String pattern : NEUTRAL_JDK_PACKAGES) {
                    String base = pattern.substring(0, pattern.length() - 2);
                    if (packageName.equals(base) || packageName.startsWith(base + ".")) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /**
     * Typen, deren Instanzen veränderlich sind, auch wenn das Feld final ist. Private Konstanten dieser
     * Typen (z. B. eine unveränderliche Stoppwortliste) bleiben erlaubt; nicht-private gelten als globaler
     * Zustand, weil jeder Aufrufer sie ändern kann.
     */
    private static boolean isMutableType(JavaClass type) {
        return type.isArray()
                || type.isAssignableTo(java.util.Collection.class)
                || type.isAssignableTo(java.util.Map.class)
                || type.getPackageName().equals("java.util.concurrent.atomic")
                || type.isAssignableTo(StringBuilder.class)
                || type.isAssignableTo(StringBuffer.class);
    }

    private static ArchCondition<JavaClass> notHoldGlobalState() {
        return new ArchCondition<JavaClass>("keine Singleton-Instanz, kein nicht-finales statisches Feld und kein öffentlich veränderliches statisches Objekt halten") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaField field : javaClass.getFields()) {
                    if (!field.getModifiers().contains(JavaModifier.STATIC)
                            || field.getModifiers().contains(JavaModifier.SYNTHETIC)
                            || field.getName().startsWith("$")) {
                        continue;
                    }
                    if (!field.getModifiers().contains(JavaModifier.FINAL)) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getFullName() + " ist ein nicht-finales statisches Feld (globaler Zustand)"));
                    } else if (!javaClass.isEnum() && field.getRawType().equals(javaClass)) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getFullName() + " hält eine statische Instanz der eigenen Klasse (Singleton)"));
                    } else if (!field.getModifiers().contains(JavaModifier.PRIVATE) && isMutableType(field.getRawType())) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getFullName() + " veröffentlicht ein veränderliches Objekt statisch (globaler Zustand)"));
                    }
                }
            }
        };
    }
}
