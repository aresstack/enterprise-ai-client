package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.approval.ApprovalDecision;
import com.aresstack.enterpriseai.astu.approval.ApprovalRequest;
import com.aresstack.enterpriseai.astu.approval.ApprovalTarget;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatApprovalPromptTest {

    private final Executor direct = Runnable::run;
    private final List<String> closed = new ArrayList<String>();
    private final List<String> shownTexts = new ArrayList<String>();

    private ApprovalRequest request() {
        return new ApprovalRequest("memory_delete", new ApprovalTarget("delete", "project", "m2", "3", ""));
    }

    private ChatApprovalPrompt prompt(final ApprovalDecision.Outcome... clicks) {
        return new ChatApprovalPrompt((title, description, offersAlways, answer) -> {
            shownTexts.add(title + " | " + description);
            for (ApprovalDecision.Outcome click : clicks) {
                answer.accept(click);
            }
            return (approved, outcome) -> closed.add(approved + ":" + outcome);
        }, direct);
    }

    @Test
    public void approveRunsTheCallAndTheBubbleShowsIt() {
        ApprovalRequest request = request();
        ApprovalDecision decision = prompt(ApprovalDecision.Outcome.APPROVED).ask(request, 1000);
        assertTrue(decision.approves(request));
        assertEquals("true:" + ChatApprovalPrompt.APPROVED, closed.get(0));
        assertTrue(shownTexts.get(0), shownTexts.get(0).contains("memory_delete"));
        assertFalse(shownTexts.get(0), shownTexts.get(0).contains("m2"));
    }

    @Test
    public void firstClickWins() {
        ApprovalRequest request = request();
        ApprovalDecision decision = prompt(ApprovalDecision.Outcome.REJECTED, ApprovalDecision.Outcome.APPROVED,
                ApprovalDecision.Outcome.APPROVED).ask(request, 1000);
        assertEquals(ApprovalDecision.Outcome.REJECTED, decision.outcome());
        assertEquals(1, closed.size());
    }

    @Test
    public void noAnswerTimesOutAndALateClickChangesNothing() {
        final List<Consumer<ApprovalDecision.Outcome>> later = new ArrayList<Consumer<ApprovalDecision.Outcome>>();
        ChatApprovalPrompt prompt = new ChatApprovalPrompt((title, description, offersAlways, answer) -> {
            later.add(answer);
            return (approved, outcome) -> closed.add(approved + ":" + outcome);
        }, direct);
        ApprovalRequest request = request();
        ApprovalDecision decision = prompt.ask(request, 20);
        assertEquals(ApprovalDecision.Outcome.TIMEOUT, decision.outcome());
        later.get(0).accept(ApprovalDecision.Outcome.APPROVED);
        assertFalse(decision.approves(request));
        assertEquals("false:" + ChatApprovalPrompt.TIMED_OUT, closed.get(0));
    }

    @Test
    public void missingSurfaceMeansUnavailable() {
        ChatApprovalPrompt prompt = new ChatApprovalPrompt((title, description, offersAlways, answer) -> {
            throw new IllegalStateException("no transcript");
        }, direct);
        assertEquals(ApprovalDecision.Outcome.UNAVAILABLE, prompt.ask(request(), 1000).outcome());
    }

    @Test
    public void interruptedTurnCancels() {
        ChatApprovalPrompt prompt = new ChatApprovalPrompt((title, description, offersAlways, answer) ->
                (approved, outcome) -> closed.add(approved + ":" + outcome), direct);
        Thread.currentThread().interrupt();
        try {
            assertEquals(ApprovalDecision.Outcome.CANCELLED, prompt.ask(request(), 1000).outcome());
        } finally {
            assertTrue(Thread.interrupted());
        }
        assertEquals("false:" + ChatApprovalPrompt.CANCELLED, closed.get(0));
    }

    @Test
    public void alwaysAnswersCountOnlyWhenOffered() {
        ApprovalRequest plain = request();
        assertEquals(ApprovalDecision.Outcome.REJECTED,
                prompt(ApprovalDecision.Outcome.APPROVED_ALWAYS).ask(plain, 1000).outcome());
        ApprovalRequest offering = new ApprovalRequest("browser_navigate",
                new ApprovalTarget("open", null, "example.org", null, ""), true);
        ApprovalDecision decision = prompt(ApprovalDecision.Outcome.APPROVED_ALWAYS).ask(offering, 1000);
        assertEquals(ApprovalDecision.Outcome.APPROVED_ALWAYS, decision.outcome());
        assertTrue(decision.approves(offering));
        assertEquals(ApprovalDecision.Outcome.REJECTED_ALWAYS,
                prompt(ApprovalDecision.Outcome.REJECTED_ALWAYS).ask(offering, 1000).outcome());
    }
}
