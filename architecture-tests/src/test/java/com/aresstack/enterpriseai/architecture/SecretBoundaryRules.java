package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.lang.ArchRule;

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
     * Module, die Secret-Material sehen dürfen: der Port selbst, der Security-Adapter und Adapter, die sich bei
     * einem externen System authentifizieren. Jedes andere, auch jedes neu registrierte Modul, ist gesperrt;
     * eine Erweiterung ist eine bewusste Architekturentscheidung (ARCHITECTURE.md).
     */
    static List<String> modulesAllowedToUseSecretMaterial() {
        return Collections.unmodifiableList(Arrays.asList(
                "security-api", "security-keepassrpc",
                "source-confluence", "source-mediawiki",
                "chat-openai", "embedding-openai"));
    }

    /** Module, in denen Secret-Material ein Feld sein darf (nur der Port-Typ selbst). */
    static List<String> modulesAllowedToStoreSecretMaterial() {
        return Collections.singletonList("security-api");
    }

    private SecretBoundaryRules() {
    }

    /** Domain, Application, UI, Knowledge, ACP, MCP und alle übrigen Ports referenzieren Secret-Material nicht. */
    static ArchRule secretMaterialOnlyInAllowedModules(ModuleRegistry registry, String secretMaterialType) {
        List<String> forbidden = new ArrayList<String>();
        for (ArchitectureModule module : registry.modules()) {
            if (!modulesAllowedToUseSecretMaterial().contains(module.name())) {
                forbidden.add(module.packagePattern());
            }
        }
        return noClasses()
                .that().resideInAnyPackage(forbidden.toArray(new String[0]))
                .should().dependOnClassesThat().haveFullyQualifiedName(secretMaterialType)
                .because("Secret-Material darf Domain, Application, UI und Knowledge nie erreichen; sie tragen "
                        + "nur den SecretRef (AP13)")
                .allowEmptyShould(true);
    }

    /** Secret-Material ist kurzlebig: kein Feld dieses Typs außerhalb des Port-Typs selbst. */
    static ArchRule secretMaterialIsNeverStoredInFields(ModuleRegistry registry, String secretMaterialType) {
        List<String> allowed = new ArrayList<String>();
        for (String module : modulesAllowedToStoreSecretMaterial()) {
            allowed.add(registry.module(module).packagePattern());
        }
        return fields()
                .that().haveRawType(secretMaterialType)
                .and().areDeclaredInClassesThat().resideInAPackage(ModuleRegistry.ROOT_PACKAGE + "..")
                .should().beDeclaredInClassesThat().resideInAnyPackage(allowed.toArray(new String[0]))
                .because("Secret-Material wird nur für die Dauer eines Aufrufs gehalten (SecretProvider.withSecret), "
                        + "nie in Feldern, Caches oder Indizes")
                .allowEmptyShould(true);
    }

    static List<ArchRule> all(ModuleRegistry registry, String secretMaterialType) {
        return Arrays.asList(
                secretMaterialOnlyInAllowedModules(registry, secretMaterialType),
                secretMaterialIsNeverStoredInFields(registry, secretMaterialType));
    }
}
