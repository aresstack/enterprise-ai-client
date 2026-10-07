package com.aresstack.enterpriseai.application.chat;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/** Zeichnet alle Callbacks eines Turns in Reihenfolge auf. */
final class RecordingTurnListener implements ChatTurnListener {

    private final List<String> events = new ArrayList<String>();
    private final CountDownLatch terminal = new CountDownLatch(1);
    private final CountDownLatch firstDelta = new CountDownLatch(1);
    private volatile ChatResponse response;
    private volatile ChatCompletionException error;

    @Override
    public synchronized void onDelta(String text) {
        events.add("delta:" + text);
        firstDelta.countDown();
    }

    @Override
    public synchronized void onCompleted(ChatResponse response) {
        this.response = response;
        events.add("completed");
        terminal.countDown();
    }

    @Override
    public synchronized void onFailed(ChatCompletionException error) {
        this.error = error;
        events.add("failed");
        terminal.countDown();
    }

    @Override
    public synchronized void onCancelled() {
        events.add("cancelled");
        terminal.countDown();
    }

    synchronized List<String> events() {
        return new ArrayList<String>(events);
    }

    ChatResponse response() {
        return response;
    }

    ChatCompletionException error() {
        return error;
    }

    RecordingTurnListener awaitTerminal() throws InterruptedException {
        assertTrue("turn did not finish in time", terminal.await(5, TimeUnit.SECONDS));
        return this;
    }

    RecordingTurnListener awaitFirstDelta() throws InterruptedException {
        assertTrue("no delta in time", firstDelta.await(5, TimeUnit.SECONDS));
        return this;
    }
}
