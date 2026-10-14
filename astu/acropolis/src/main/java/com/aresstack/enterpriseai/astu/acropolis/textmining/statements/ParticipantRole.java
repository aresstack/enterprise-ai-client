package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

/**
 * Semantic role of a participant in an {@link Event}: who acts, what is affected, who receives. Not the grammatical
 * function: in a passive sentence the grammatical subject is the affected object.
 */
public enum ParticipantRole {
    /** Who carries out the action (in a passive sentence the "durch"/"von" phrase). */
    ACTOR,
    /** What the action is applied to or brings about. */
    AFFECTED_OBJECT,
    /** Who receives something (often a dative). */
    RECIPIENT,
    /** By what means the action happens. */
    INSTRUMENT,
    /** Where the action happens. */
    LOCATION;

    /** The role named {@code name}, or {@code null} if there is none. */
    public static ParticipantRole parse(String name) {
        if (name == null) {
            return null;
        }
        for (ParticipantRole role : values()) {
            if (role.name().equals(name.trim())) {
                return role;
            }
        }
        return null;
    }
}
