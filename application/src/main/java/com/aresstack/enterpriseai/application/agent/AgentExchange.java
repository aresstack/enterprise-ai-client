package com.aresstack.enterpriseai.application.agent;

/**
 * Ein Eintrag des Agent-Transkripts: der Auftrag des Nutzers und die Antwort des Agenten. Unveränderlich; der
 * {@link AgentService} ersetzt den Eintrag, wenn der Auftrag endet.
 *
 * <p>Anders als die Chat-Historie wird das Transkript nicht an ein Modell zurückgeschickt (den Kontext hält
 * der Agent selbst in seiner ACP-Session). Deshalb bleibt auch eine abgebrochene Teilantwort sichtbar.
 */
public final class AgentExchange {

    private final String prompt;
    private final String reply;
    private final String thoughts;
    private final AgentTurnState state;
    private final AgentFailure failure;

    AgentExchange(String prompt, String reply, String thoughts, AgentTurnState state, AgentFailure failure) {
        this.prompt = prompt;
        this.reply = reply == null ? "" : reply;
        this.thoughts = thoughts == null ? "" : thoughts;
        this.state = state;
        this.failure = failure;
    }

    static AgentExchange running(String prompt) {
        return new AgentExchange(prompt, "", "", AgentTurnState.RUNNING, null);
    }

    public String prompt() {
        return prompt;
    }

    public String reply() {
        return reply;
    }

    public String thoughts() {
        return thoughts;
    }

    public AgentTurnState state() {
        return state;
    }

    /** Grund des Scheiterns bei {@link AgentTurnState#FAILED}, sonst {@code null}. */
    public AgentFailure failure() {
        return failure;
    }

    @Override
    public String toString() {
        // Ohne Inhalte: Aufträge und Antworten gehören nicht in Logs.
        return "AgentExchange[" + state + (failure == null ? "" : ", " + failure)
                + ", prompt=" + prompt.length() + " chars, reply=" + reply.length() + " chars]";
    }
}
