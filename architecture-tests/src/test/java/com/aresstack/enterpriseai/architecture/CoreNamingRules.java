package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Nachtrag 1 und 19: Der Kern (domain, application, *-api) kennt genau einen Port je Fähigkeit und keinen
 * Provider. Weder Klassennamen noch Enum-Konstanten dürfen einen Anbieter (OpenAI, Ollama, Claude, llama.cpp
 * ...) oder eine Multi-Provider-Abstraktion benennen. Adapter dürfen den Provider im Namen tragen
 * ({@code OpenAiCompatibleChatAdapter}).
 */
final class CoreNamingRules {

    /** Anbieter, die laut Nachtrag nicht als produktive Provider modelliert werden. */
    static final List<String> PROVIDER_NAMES = Arrays.asList(
            "openai", "ollama", "claude", "anthropic", "llamacpp", "gemini", "mistral", "azure", "bedrock");

    /** Namen, die eine Provider-Auswahl im Kern verraten. */
    static final List<String> MULTI_PROVIDER_NAMES = Arrays.asList(
            "multiprovider", "providerkind", "providertype", "aiprovider", "chatprovider", "llmprovider",
            "modelprovider");

    private CoreNamingRules() {
    }

    static ArchRule coreClassesAreNotNamedAfterProviders(ModuleRegistry registry) {
        return classes()
                .that().resideInAnyPackage(corePackages(registry))
                .should(notBeNamedAfterAProvider())
                .because("der Kern kennt ChatCompletionPort und EmbeddingPort, keine Provider und keine "
                        + "Multi-Provider-Abstraktion (Nachtrag 1, 19)")
                .allowEmptyShould(true);
    }

    static ArchRule coreEnumsHaveNoProviderConstants(ModuleRegistry registry) {
        return classes()
                .that().resideInAnyPackage(corePackages(registry))
                .and().areEnums()
                .should(haveNoProviderConstant())
                .because("Provider-Enums wie OPENAI, CLAUDE, OLLAMA, LLAMA_CPP gehören nicht in den Kern (Nachtrag 1)")
                .allowEmptyShould(true);
    }

    static List<ArchRule> all(ModuleRegistry registry) {
        return Arrays.asList(coreClassesAreNotNamedAfterProviders(registry), coreEnumsHaveNoProviderConstants(registry));
    }

    static String[] corePackages(ModuleRegistry registry) {
        List<String> core = ArchitectureRules.coreModules(registry);
        String[] patterns = new String[core.size()];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = registry.module(core.get(i)).packagePattern();
        }
        return patterns;
    }

    /** Vergleicht ohne Groß-/Kleinschreibung und Unterstriche: {@code LLAMA_CPP} trifft {@code llamacpp}. */
    static String providerNamedBy(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "");
        for (String provider : PROVIDER_NAMES) {
            if (normalized.contains(provider)) {
                return provider;
            }
        }
        for (String multi : MULTI_PROVIDER_NAMES) {
            if (normalized.contains(multi)) {
                return multi;
            }
        }
        return null;
    }

    private static ArchCondition<JavaClass> notBeNamedAfterAProvider() {
        return new ArchCondition<JavaClass>("keinen Provider oder keine Provider-Auswahl im Namen tragen") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String provider = providerNamedBy(javaClass.getSimpleName());
                if (provider != null) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName()
                            + " benennt einen Provider (" + provider + ") im Kern"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> haveNoProviderConstant() {
        return new ArchCondition<JavaClass>("keine Provider-Konstante deklarieren") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaField field : javaClass.getFields()) {
                    if (!field.getModifiers().contains(JavaModifier.ENUM)) {
                        continue;
                    }
                    String provider = providerNamedBy(field.getName());
                    if (provider != null) {
                        events.add(SimpleConditionEvent.violated(field, field.getFullName()
                                + " ist eine Provider-Konstante (" + provider + ") im Kern"));
                    }
                }
            }
        };
    }
}
