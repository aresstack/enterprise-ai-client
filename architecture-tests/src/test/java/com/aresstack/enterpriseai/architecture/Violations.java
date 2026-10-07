package com.aresstack.enterpriseai.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * Sammelt Verstöße mehrerer Regeln, damit ein roter Test alle Befunde auf einmal zeigt.
 */
final class Violations {

    private Violations() {
    }

    static List<String> of(List<ArchRule> rules, JavaClasses classes) {
        List<String> violations = new ArrayList<String>();
        for (ArchRule rule : rules) {
            EvaluationResult result = rule.evaluate(classes);
            if (result.hasViolation()) {
                violations.add(rule.getDescription() + "\n    " + join(result.getFailureReport().getDetails(), "\n    "));
            }
        }
        return violations;
    }

    static void assertNone(String title, List<String> violations) {
        if (!violations.isEmpty()) {
            fail(title + " (" + violations.size() + "):\n  " + join(violations, "\n  "));
        }
    }

    private static String join(List<String> parts, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(part);
        }
        return builder.toString();
    }
}
