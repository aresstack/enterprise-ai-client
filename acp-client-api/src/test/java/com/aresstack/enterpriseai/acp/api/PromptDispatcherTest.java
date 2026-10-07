package com.aresstack.enterpriseai.acp.api;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Sequence monotonicity, exactly-one-terminal, late-update drop, listener-exception isolation. */
public class PromptDispatcherTest {

    private static final class Rec implements AcpUpdateListener {
        final List<Long> sequences = new ArrayList<Long>();
        final List<AcpPromptState> terminals = new ArrayList<AcpPromptState>();
        boolean throwOnUpdate;

        public void onUpdate(AcpUpdate update) {
            sequences.add(update.getSequenceNumber());
            if (throwOnUpdate) {
                throw new IllegalStateException("consumer bug");
            }
        }

        public void onTerminal(String promptId, AcpPromptState state, String detail) {
            terminals.add(state);
        }
    }

    @Test
    public void updatesAreMonotonicThenExactlyOneTerminal() {
        Rec rec = new Rec();
        PromptDispatcher d = new PromptDispatcher("s1", "p1", rec);
        assertTrue(d.update(AcpUpdate.Kind.MESSAGE, "a"));
        assertTrue(d.update(AcpUpdate.Kind.THOUGHT, "b"));
        assertTrue(d.update(AcpUpdate.Kind.MESSAGE, "c"));
        assertTrue(d.terminal(AcpPromptState.COMPLETED, ""));
        assertEquals(java.util.Arrays.asList(1L, 2L, 3L), rec.sequences);
        assertEquals(1, rec.terminals.size());

        // Late SDK callbacks after the terminal are dropped; a second terminal is a no-op.
        assertFalse(d.update(AcpUpdate.Kind.MESSAGE, "late"));
        assertFalse(d.terminal(AcpPromptState.CANCELLED, "late cancel"));
        assertEquals(3, rec.sequences.size());
        assertEquals(1, rec.terminals.size());
        assertEquals(AcpPromptState.COMPLETED, d.getState());
    }

    @Test
    public void cancelVersusCompletionRaceYieldsOneTerminal() {
        Rec rec = new Rec();
        PromptDispatcher d = new PromptDispatcher("s1", "p1", rec);
        assertTrue(d.cancelling());
        assertTrue(d.cancelling() == false); // idempotent-ish: second cancelling rejected, state unchanged
        // Completion arriving while CANCELLING is a legal single terminal.
        assertTrue(d.terminal(AcpPromptState.COMPLETED, ""));
        assertFalse(d.terminal(AcpPromptState.CANCELLED, ""));
        assertEquals(java.util.Arrays.asList(AcpPromptState.COMPLETED), rec.terminals);
    }

    @Test
    public void listenerExceptionDoesNotKillTheDispatcher() {
        Rec rec = new Rec();
        rec.throwOnUpdate = true;
        PromptDispatcher d = new PromptDispatcher("s1", "p1", rec);
        assertTrue(d.update(AcpUpdate.Kind.MESSAGE, "a")); // listener throws, dispatcher survives
        assertTrue(d.update(AcpUpdate.Kind.MESSAGE, "b"));
        assertTrue(d.terminal(AcpPromptState.FAILED, "agent died"));
        assertEquals(2, rec.sequences.size());
    }

    @Test
    public void concurrentTerminalsYieldExactlyOneCallback() throws Exception {
        for (int round = 0; round < 50; round++) {
            final AtomicInteger terminals = new AtomicInteger();
            final PromptDispatcher d = new PromptDispatcher("s", "p", new AcpUpdateListener() {
                public void onUpdate(AcpUpdate update) {
                }

                public void onTerminal(String promptId, AcpPromptState state, String detail) {
                    terminals.incrementAndGet();
                }
            });
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(3);
            final List<AcpPromptState> outcomes = Collections.synchronizedList(new ArrayList<AcpPromptState>());
            for (final AcpPromptState state : new AcpPromptState[] {
                    AcpPromptState.COMPLETED, AcpPromptState.CANCELLED, AcpPromptState.FAILED}) {
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        try {
                            start.await();
                            if (d.terminal(state, "")) {
                                outcomes.add(state);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    }
                });
                t.start();
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertEquals(1, terminals.get());
            assertEquals(1, outcomes.size());
            assertEquals(outcomes.get(0), d.getState());
        }
    }

    @Test
    public void nonTerminalStateIsNotAcceptedAsTerminal() {
        Rec rec = new Rec();
        PromptDispatcher d = new PromptDispatcher("s1", "p1", rec);
        assertFalse(d.terminal(AcpPromptState.RUNNING, ""));
        assertFalse(d.terminal(AcpPromptState.CANCELLING, ""));
        assertTrue(rec.terminals.isEmpty());
        assertEquals(AcpPromptState.RUNNING, d.getState());
    }

    @Test
    public void quiescenceClockStartsWithFirstUpdate() {
        PromptDispatcher d = new PromptDispatcher("s1", "p1", new Rec());
        assertEquals(Long.MAX_VALUE, d.nanosSinceLastUpdate());
        d.update(AcpUpdate.Kind.MESSAGE, "x");
        assertTrue(d.nanosSinceLastUpdate() < TimeUnit.SECONDS.toNanos(60));
    }

    @Test(expected = NullPointerException.class)
    public void listenerIsRequired() {
        new PromptDispatcher("s1", "p1", null);
    }
}
