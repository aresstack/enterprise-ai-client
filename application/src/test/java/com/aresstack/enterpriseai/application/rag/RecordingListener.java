package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.application.chat.ChatTurnListener;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/** Wartet auf den Abschluss eines Turns und merkt sich die Antwort. */
final class RecordingListener implements ChatTurnListener {

    private final CountDownLatch done = new CountDownLatch(1);
    private final StringBuilder text = new StringBuilder();
    private volatile String outcome;

    @Override
    public synchronized void onDelta(String delta) {
        text.append(delta);
    }

    @Override
    public void onCompleted(ChatResponse response) {
        outcome = "completed";
        done.countDown();
    }

    @Override
    public void onFailed(ChatCompletionException error) {
        outcome = "failed";
        done.countDown();
    }

    @Override
    public void onCancelled() {
        outcome = "cancelled";
        done.countDown();
    }

    RecordingListener await() throws InterruptedException {
        assertTrue("Turn nicht rechtzeitig beendet", done.await(5, TimeUnit.SECONDS));
        return this;
    }

    String outcome() {
        return outcome;
    }

    synchronized String text() {
        return text.toString();
    }
}
