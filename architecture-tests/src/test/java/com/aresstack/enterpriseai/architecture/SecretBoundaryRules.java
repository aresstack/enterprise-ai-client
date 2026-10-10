package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Strang F (AP13): Secret-Material bleibt in Adaptern und wird nie gespeichert.
 *
 * <p>Die Regeln arbeiten über den voll qualifizierten Typnamen, damit {@code SecretBoundaryTest} sie auf die
 * echten Produktionsklassen und auf Fixtures mit einem Stellvertretertyp anwenden kann, ohne dass
 * {@code architecture-tests} {@code security-api} auf dem Klassenpfad braucht.
 */
final class SecretBoundaryRules {

    static final String SECRET_MATERIAL = "com.aresstack.enterpriseai.security.api.SecretMaterial";

    /**
     * Module, die Secret-Material sehen dürfen: der Port selbst, der Security-Adapter und Adapter, die sich mit
     * Credentials aus dem Security-Port bei einem externen System anmelden. Das sind genau die Module, die laut
     * {@link ModuleRegistry} {@code security-api} sehen dürfen, ohne Kern zu sein. Jedes andere, auch jedes neu
     * registrierte Modul, ist gesperrt; eine Erweiterung (z. B. API-Key des Chat-Adapters über einen
     * {@code SecretRef}) ist eine bewusste Architekturentscheidung: Registry, ARCHITECTURE.md und diese Liste.
     */
    static List<String> modulesAllowedToUseSecretMaterial() {
        return Collections.unmodifiableList(Arrays.asList("security-api", "security-keepassrpc", "source-confluence",
                "source-ftp", "source-ndv", "source-sharepoint"));
    }

    /**
     * Pakete außerhalb dieser Module, die Secret-Material sehen dürfen (AP23): die Brücken der Composition Root in
     * {@code app.security}, die den API-Key des Chat-/Embedding-Adapters und die Wiki-Anmeldung je Aufruf über
     * {@code SecretProvider.withSecret} holen. Paketgenau, damit Konfiguration, Shell, Bindings und der Rest von
     * {@code app-swing} gesperrt bleiben; die Feldregel gilt auch hier.
     */
    static List<String> packagesAllowedToUseSecretMaterial() {
        return Collections.singletonList(ModuleRegistry.ROOT_PACKAGE + ".app.security..");
    }

    private SecretBoundaryRules() {
    }

    /** Domain, Application, UI, Knowledge, ACP, MCP und alle übrigen Module referenzieren Secret-Material nicht. */
    static ArchRule secretMaterialOnlyInAllowedModules(ModuleRegistry registry, String secretMaterialType) {
        List<String> forbidden = new ArrayList<String>();
        for (ArchitectureModule module : registry.modules()) {
            if (!modulesAllowedToUseSecretMaterial().contains(module.name())) {
                forbidden.add(module.packagePattern());
            }
        }
        return noClasses()
                .that().resideInAnyPackage(forbidden.toArray(new String[0]))
                .and().resideOutsideOfPackages(packagesAllowedToUseSecretMaterial().toArray(new String[0]))
                .should().dependOnClassesThat().haveFullyQualifiedName(secretMaterialType)
                .because("Secret-Material darf Domain, Application, UI und Knowledge nie erreichen; sie tragen "
                        + "nur den SecretRef (AP13); in app-swing sieht es allein app.security (AP23)")
                .allowEmptyShould(true);
    }

    /**
     * Secret-Material ist kurzlebig: Kein Feld in irgendeinem Modul hält es, weder direkt noch als Array oder
     * Typargument ({@code List<SecretMaterial>}, {@code Map<String, SecretMaterial>}, {@code SecretMaterial[]}).
     */
    static ArchRule secretMaterialIsNeverStoredInFields(String secretMaterialType) {
        return fields()
                .that().areDeclaredInClassesThat().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .should(notInvolveType(secretMaterialType))
                .because("Secret-Material wird nur für die Dauer eines Aufrufs gehalten (SecretProvider.withSecret), "
                        + "nie in Feldern, Caches oder Indizes")
                .allowEmptyShould(true);
    }

    static List<ArchRule> all(ModuleRegistry registry, String secretMaterialType) {
        return Arrays.asList(
                secretMaterialOnlyInAllowedModules(registry, secretMaterialType),
                secretMaterialIsNeverStoredInFields(secretMaterialType));
    }

    private static ArchCondition<JavaField> notInvolveType(final String typeName) {
        return new ArchCondition<JavaField>("not hold " + typeName + " (directly, as array or type argument)") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                for (JavaClass involved : field.getType().getAllInvolvedRawTypes()) {
                    if (involved.getName().equals(typeName)) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getFullName() + " hält " + typeName + " (" + field.getType().getName() + ")"));
                        return;
                    }
                }
            }
        };
    }
}
