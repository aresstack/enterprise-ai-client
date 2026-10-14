package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

/**
 * The optional second, compact check of one claim against its sentence (issue #108): does the sentence say exactly
 * this? Agreement is no proof of truth; it only filters claims the sentence does not carry ("der Beleg löst die
 * Zahlung aus" although every word occurs).
 */
public interface StatementVerifier {

    /** The answer of the check. */
    enum Verdict {
        YES,
        NO,
        UNSURE,
        /** The check could not run (model failed or unavailable); the statement keeps its status. */
        UNAVAILABLE
    }

    /** Whether checks run at all. */
    boolean isEnabled();

    /** Identifies the check; part of the stored extractor version. */
    String version();

    /** Blocks (model call). */
    Verdict verify(String sentence, String claim);

    /** No check: every claim keeps its status. */
    static StatementVerifier none() {
        return new StatementVerifier() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public String version() {
                return "none";
            }

            @Override
            public Verdict verify(String sentence, String claim) {
                return Verdict.UNAVAILABLE;
            }
        };
    }
}
