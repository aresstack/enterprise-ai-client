package com.aresstack.enterpriseai.application.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Zeichnet alle Callbacks eines Agent-Auftrags in Reihenfolge auf. */
final class RecordingAgentListener implements AgentTurnListener {

    final List<String> events = new CopyOnWriteArrayList<String>();
    private final CountDownLatch terminal = new CountDownLatch(1);

    @Override
    public void onMessage(String text) {
        events.add("message:" + text);
    }

    @Override
    public void onThought(String text) {
        events.add("thought:" + text);
    }

    @Override
    public void onCompleted() {
        events.add("completed");
        terminal.countDown();
    }

    @Override
    public void onCancelled() {
        events.add("cancelled");
        terminal.countDown();
    }

    @Override
    public void onFailed(AgentFailure failure) {
        events.add("failed:" + failure);
        terminal.countDown();
    }

    boolean awaitTerminal() throws InterruptedException {
        return terminal.await(10, TimeUnit.SECONDS);
    }

    int terminalCount() {
        int count = 0;
        for (String event : events) {
            if (event.equals("completed") || event.equals("cancelled") || event.startsWith("failed:")) {
                count++;
            }
        }
        return count;
    }
}
