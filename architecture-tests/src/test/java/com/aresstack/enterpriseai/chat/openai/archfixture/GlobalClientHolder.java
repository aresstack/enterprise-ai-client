package com.aresstack.enterpriseai.chat.openai.archfixture;

/** Absichtlicher Verstoß: Singleton und globaler veränderlicher Zustand. Nur für RulesDetectViolationsTest. */
public final class GlobalClientHolder {

    public static final GlobalClientHolder INSTANCE = new GlobalClientHolder();

    static String lastModel;

    public static final java.util.Map<String, String> MODELS = new java.util.HashMap<String, String>();

    private static final java.util.List<String> PRIVATE_DEFAULTS = java.util.Collections.singletonList("default");

    private GlobalClientHolder() {
    }

    public void remember(String model) {
        lastModel = model + PRIVATE_DEFAULTS.size();
    }
}
