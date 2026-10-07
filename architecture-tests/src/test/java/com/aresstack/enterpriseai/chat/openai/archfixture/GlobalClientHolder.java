package com.aresstack.enterpriseai.chat.openai.archfixture;

/** Absichtlicher Verstoß: Singleton und globaler veränderlicher Zustand. Nur für RulesDetectViolationsTest. */
public final class GlobalClientHolder {

    public static final GlobalClientHolder INSTANCE = new GlobalClientHolder();

    static String lastModel;

    private GlobalClientHolder() {
    }

    public void remember(String model) {
        lastModel = model;
    }
}
