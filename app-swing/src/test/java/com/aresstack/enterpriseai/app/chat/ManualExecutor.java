package com.aresstack.enterpriseai.app.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import static org.junit.Assert.fail;

/**
 * Executor, dessen Aufträge der Test selbst und in Reihenfolge ausführt: damit lassen sich Abläufe auf dem
 * UI-Thread und dem Arbeits-Thread Schritt für Schritt nachstellen (z. B. eine Antwort, die fertig ist, bevor
 * ihre Quellen ankommen). {@link #execute(Runnable)} darf von jedem Thread gerufen werden.
 */
final class ManualExecutor implements Executor {

    private final Deque<Runnable> queue = new ArrayDeque<Runnable>();

    @Override
    public synchronized void execute(Runnable command) {
        queue.add(command);
    }

    synchronized int pending() {
        return queue.size();
    }

    private synchronized Runnable poll() {
        return queue.poll();
    }

    /** Führt alles aus, was gerade ansteht (auch, was dabei neu eingereiht wird). */
    void runAll() {
        for (Runnable next = poll(); next != null; next = poll()) {
            next.run();
        }
    }

    /** Führt Aufträge einzeln aus, solange die Bedingung gilt; der erste Auftrag danach bleibt liegen. */
    void runWhile(Callable<Boolean> condition) throws Exception {
        while (condition.call()) {
            Runnable next = poll();
            if (next == null) {
                return;
            }
            next.run();
        }
    }

    /** Führt eintreffende Aufträge aus, bis die Bedingung gilt; wartet dabei auf Aufträge anderer Threads. */
    void runUntil(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + UiTestSupport.TIMEOUT_MILLIS;
        while (!condition.call()) {
            Runnable next = poll();
            if (next != null) {
                next.run();
            } else if (System.currentTimeMillis() > deadline) {
                fail("Bedingung nicht innerhalb von " + UiTestSupport.TIMEOUT_MILLIS + " ms erfüllt: " + what);
            } else {
                Thread.sleep(10L);
            }
        }
    }
}
