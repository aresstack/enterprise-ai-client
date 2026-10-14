package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

/** What a {@link Relation} says. */
public enum RelationKind {
    /** A lasting property of a mention ("die zuständige Sachbearbeitung": zuständig). */
    ATTRIBUTE,
    /** A state a mention is in ("der eingereichte Beleg": eingereicht). */
    STATE,
    /** One event happens before another, by an explicit cue in the text ("anschließend", "danach"). */
    BEFORE;

    /** The kind named {@code name}, or {@code null} if there is none. */
    public static RelationKind parse(String name) {
        if (name == null) {
            return null;
        }
        for (RelationKind kind : values()) {
            if (kind.name().equals(name.trim())) {
                return kind;
            }
        }
        return null;
    }
}
