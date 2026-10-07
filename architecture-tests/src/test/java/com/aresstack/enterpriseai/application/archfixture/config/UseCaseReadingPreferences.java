package com.aresstack.enterpriseai.application.archfixture.config;

import java.util.prefs.Preferences;

/** Absichtlicher Verstoß: ein Use Case liest Preferences. */
public final class UseCaseReadingPreferences {

    public String model() {
        return Preferences.userRoot().get("model", "");
    }
}
