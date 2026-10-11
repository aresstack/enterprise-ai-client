package com.aresstack.enterpriseai.acp.api;

/**
 * Ein Schritt eines Agentenplans (ACP {@code plan}-Update, {@code PlanEntry}), neutral: Inhalt, Priorität und
 * Zustand als Text, wie ACP sie nennt ({@code high|medium|low}, {@code pending|in_progress|completed}). Ein
 * Plan-Update trägt immer den ganzen Plan; der Empfänger ersetzt den bekannten.
 */
public final class AcpPlanEntry {

    public static final String PENDING = "pending";
    public static final String IN_PROGRESS = "in_progress";
    public static final String COMPLETED = "completed";

    private final String content;
    private final String priority;
    private final String status;

    public AcpPlanEntry(String content, String priority, String status) {
        this.content = content == null ? "" : content;
        this.priority = priority == null ? "" : priority;
        this.status = status == null || status.isEmpty() ? PENDING : status;
    }

    public String getContent() { return content; }
    public String getPriority() { return priority; }
    /** {@code pending}, {@code in_progress} oder {@code completed} (unbekannte Werte bleiben, wie sie kamen). */
    public String getStatus() { return status; }

    public boolean isCompleted() {
        return COMPLETED.equals(status);
    }

    public boolean isInProgress() {
        return IN_PROGRESS.equals(status);
    }

    @Override
    public String toString() {
        // Ohne Inhalt: er kann aus Quellen stammen.
        return "AcpPlanEntry[" + priority + ", " + status + "]";
    }
}
