package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

/**
 * Under which circumstances a statement holds, each part grounded in its sentence: a negation ("nicht"), a modality
 * ("kann", "muss") or a condition ("wenn der Betrag ... übersteigt"). A statement with a context is no general fact;
 * a reader must not drop the context.
 */
public final class StatementContext {

    private static final StatementContext NONE = new StatementContext(null, null, null);

    private final GroundedText negation;
    private final GroundedText modality;
    private final GroundedText condition;

    public StatementContext(GroundedText negation, GroundedText modality, GroundedText condition) {
        this.negation = negation;
        this.modality = modality;
        this.condition = condition;
    }

    public static StatementContext none() {
        return NONE;
    }

    /** The negating words, or {@code null}. */
    public GroundedText negation() {
        return negation;
    }

    /** The modal words, or {@code null}. */
    public GroundedText modality() {
        return modality;
    }

    /** The condition, or {@code null}. */
    public GroundedText condition() {
        return condition;
    }

    /** Neither negated nor modal nor conditional. */
    public boolean isPlain() {
        return negation == null && modality == null && condition == null;
    }

    @Override
    public String toString() {
        return isPlain() ? "plain" : "negation=" + negation + ", modality=" + modality + ", condition=" + condition;
    }
}
