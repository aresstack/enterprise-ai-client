package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovalDecision;
import com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovalPromptPort;
import com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovalRequest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Tool approvals in the chat: an {@code ASK} call shows an amber bubble with "Bestätigen"/"Abbrechen" in the transcript
 * while the tool thread waits. The first answer wins; timeout, an interrupted turn or a missing surface end the
 * question without execution, and the bubble then shows that outcome. The question carries only the request's
 * description (tool, action, scope), never argument values.
 */
public final class ChatApprovalPrompt implements ApprovalPromptPort {

    private static final Logger LOG = Logger.getLogger(ChatApprovalPrompt.class.getName());

    static final String TITLE = "Bestätigung nötig";
    static final String APPROVED = "bestätigt";
    static final String REJECTED = "abgelehnt";
    static final String TIMED_OUT = "keine Antwort, nicht ausgeführt";
    static final String CANCELLED = "abgebrochen, nicht ausgeführt";

    /** Where the question appears. Only on the EDT. */
    public interface Surface {
        /** Shows the question; {@code answer} gets the first click ({@code true} = approved). */
        Handle show(String title, String description, Consumer<Boolean> answer);
    }

    /** A shown question. Only on the EDT. */
    public interface Handle {
        void close(boolean approved, String outcome);
    }

    private final Surface surface;
    private final Executor uiExecutor;

    public ChatApprovalPrompt(Surface surface, Executor uiExecutor) {
        if (surface == null || uiExecutor == null) {
            throw new IllegalArgumentException("surface and uiExecutor must not be null");
        }
        this.surface = surface;
        this.uiExecutor = uiExecutor;
    }

    @Override
    public ApprovalDecision ask(final ApprovalRequest request, long timeoutMillis) {
        final CompletableFuture<Boolean> answer = new CompletableFuture<Boolean>();
        final CompletableFuture<Handle> shown = new CompletableFuture<Handle>();
        try {
            uiExecutor.execute(() -> {
                try {
                    shown.complete(surface.show(TITLE, request.description(), answer::complete));
                } catch (RuntimeException e) {
                    shown.completeExceptionally(e);
                    answer.completeExceptionally(e);
                }
            });
        } catch (RuntimeException e) {
            return ApprovalDecision.of(request, ApprovalDecision.Outcome.UNAVAILABLE);
        }
        ApprovalDecision.Outcome outcome;
        try {
            outcome = answer.get(timeoutMillis, TimeUnit.MILLISECONDS)
                    ? ApprovalDecision.Outcome.APPROVED : ApprovalDecision.Outcome.REJECTED;
        } catch (TimeoutException e) {
            outcome = ApprovalDecision.Outcome.TIMEOUT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // the chat turn was stopped
            outcome = ApprovalDecision.Outcome.CANCELLED;
        } catch (ExecutionException e) {
            LOG.warning("Werkzeug-Rückfrage nicht angezeigt: " + e.getCause().getClass().getSimpleName());
            outcome = ApprovalDecision.Outcome.UNAVAILABLE;
        }
        // A click after the timeout lands in a completed future and changes nothing.
        answer.complete(Boolean.FALSE);
        final ApprovalDecision.Outcome result = outcome;
        uiExecutor.execute(() -> {
            Handle handle = shown.isCompletedExceptionally() ? null : shown.getNow(null);
            if (handle != null) {
                handle.close(result == ApprovalDecision.Outcome.APPROVED, text(result));
            }
        });
        return ApprovalDecision.of(request, result);
    }

    static String text(ApprovalDecision.Outcome outcome) {
        switch (outcome) {
            case APPROVED:
                return APPROVED;
            case REJECTED:
                return REJECTED;
            case TIMEOUT:
                return TIMED_OUT;
            default:
                return CANCELLED;
        }
    }
}
