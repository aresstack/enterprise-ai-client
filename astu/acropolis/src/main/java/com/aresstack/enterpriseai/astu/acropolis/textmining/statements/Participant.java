package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

/** One participant of an {@link Event}: its semantic role and the {@link Mention} that fills it. */
public final class Participant {

    private final ParticipantRole role;
    private final Mention mention;

    public Participant(ParticipantRole role, Mention mention) {
        if (role == null || mention == null) {
            throw new IllegalArgumentException("role and mention must not be null");
        }
        this.role = role;
        this.mention = mention;
    }

    public ParticipantRole role() {
        return role;
    }

    public Mention mention() {
        return mention;
    }

    @Override
    public String toString() {
        return role + "=" + mention.key();
    }
}
