package com.aresstack.enterpriseai.app.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die Konfiguration ist unvollständig oder ungültig. Jede Meldung nennt den Schlüssel und das Problem, nie den
 * konfigurierten Wert, damit Fehlermeldungen ohne Bedenken angezeigt und geloggt werden können.
 */
public final class AppConfigException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public AppConfigException(List<String> problems) {
        super(describe(problems));
        this.problems = Collections.unmodifiableList(new ArrayList<String>(problems));
    }

    public AppConfigException(String problem) {
        this(Collections.singletonList(problem));
    }

    /** Alle Probleme in Reihenfolge ihres Auftretens, je eines pro Zeile. */
    public List<String> problems() {
        return problems;
    }

    private static String describe(List<String> problems) {
        if (problems == null || problems.isEmpty()) {
            return "Konfiguration ungültig";
        }
        StringBuilder text = new StringBuilder("Konfiguration ungültig:");
        for (String problem : problems) {
            text.append(System.lineSeparator()).append("  - ").append(problem);
        }
        return text.toString();
    }
}
