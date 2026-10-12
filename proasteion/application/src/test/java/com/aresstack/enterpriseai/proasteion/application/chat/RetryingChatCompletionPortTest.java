package com.aresstack.enterpriseai.proasteion.application.chat;

import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Wiederholung je Modellanfrage: nur vorübergehende Fehler, beim Streaming nur vor dem ersten Textstück. */
public class RetryingChatCompletionPortTest {

    private final ScriptedPort port = new ScriptedPort();
    private final RetryingChatCompletionPort retrying = new RetryingChatCompletionPort(port, RetryPolicy.of(2, 0),
            millis -> {
            });

    @Test
    public void http503IsRetriedAndSucceeds() {
        port.fail(ChatErrorKind.PROVIDER_ERROR, 503).answer("Antwort");

        assertEquals("Antwort", retrying.complete(request()).content());
        assertEquals(2, port.requests.size());
        assertEquals("dieselbe Anfrage", port.requests.get(0), port.requests.get(1));
    }

    @Test
    public void http429IsRetriedUpToThePolicy() {
        port.fail(ChatErrorKind.RATE_LIMITED, 429).fail(ChatErrorKind.RATE_LIMITED, 429)
                .fail(ChatErrorKind.RATE_LIMITED, 429).answer("zu spät");
        try {
            retrying.complete(request());
            fail();
        } catch (ChatCompletionException e) {
            assertEquals(ChatErrorKind.RATE_LIMITED, e.kind());
        }
        assertEquals("erster Versuch plus zwei Wiederholungen", 3, port.requests.size());
    }

    @Test
    public void http400And401AreNeverRetried() {
        assertNotRetried(ChatErrorKind.INVALID_REQUEST, 400);
        assertNotRetried(ChatErrorKind.AUTHENTICATION, 401);
        assertNotRetried(ChatErrorKind.AUTHENTICATION, 403);
        assertNotRetried(ChatErrorKind.PROTOCOL, -1);
        assertNotRetried(ChatErrorKind.CREDENTIALS_UNAVAILABLE, -1);
    }

    @Test
    public void streamFailureBeforeTheFirstDeltaIsRetried() throws Exception {
        port.fail(ChatErrorKind.TRANSPORT, -1).answer("Hallo", " Welt");
        Recorder recorder = new Recorder();
        ChatTask task = retrying.stream(request(), recorder);
        recorder.await();

        assertEquals(Arrays.asList("start", "delta:Hallo", "delta: Welt", "complete:Hallo Welt"), recorder.events);
        assertEquals(2, port.requests.size());
        assertTrue(task.isDone());
    }

    @Test
    public void streamFailureAfterTheFirstDeltaIsNotReplayed() throws Exception {
        port.failAfter(ChatErrorKind.TRANSPORT, "Hal").answer("Hallo");
        Recorder recorder = new Recorder();
        retrying.stream(request(), recorder);
        recorder.await();

        assertEquals(Arrays.asList("start", "delta:Hal", "error:TRANSPORT"), recorder.events);
        assertEquals("kein zweiter Versuch, der Text doppelt zeigen würde", 1, port.requests.size());
    }

    @Test
    public void cancelEndsThePauseBetweenAttempts() throws Exception {
        RetryingChatCompletionPort slow = new RetryingChatCompletionPort(port, RetryPolicy.of(2, 30_000));
        port.fail(ChatErrorKind.TRANSPORT, -1).answer("nie");
        port.async = true;
        Recorder recorder = new Recorder();
        ChatTask task = slow.stream(request(), recorder);
        assertTrue(port.failed.await(5, TimeUnit.SECONDS));
        Thread.sleep(50L);
        task.cancel();
        recorder.await();

        assertEquals(Arrays.asList("start", "cancelled"), recorder.events);
        assertTrue(task.isCancelled());
        assertEquals(1, port.requests.size());
    }

    private void assertNotRetried(ChatErrorKind kind, int status) {
        port.requests.clear();
        port.steps.clear();
        port.fail(kind, status).answer("nie");
        try {
            retrying.complete(request());
            fail();
        } catch (ChatCompletionException e) {
            assertEquals(kind, e.kind());
        }
        assertEquals(kind + " einmal", 1, port.requests.size());
    }

    private static ChatRequest request() {
        return new ChatRequest(Collections.singletonList(ChatMessage.user("Frage")), ChatOptions.defaults());
    }

    /** Antwortet nach Skript: Fehler (optional nach Textstücken) oder Antwort. */
    private static final class ScriptedPort implements ChatCompletionPort {
        final List<ChatRequest> requests = new ArrayList<ChatRequest>();
        final LinkedList<Object[]> steps = new LinkedList<Object[]>();
        final CountDownLatch failed = new CountDownLatch(1);
        volatile boolean async;

        ScriptedPort fail(ChatErrorKind kind, int status) {
            steps.add(new Object[]{new ChatCompletionException(kind, status, kind.name(), null), new String[0]});
            return this;
        }

        ScriptedPort failAfter(ChatErrorKind kind, String... deltas) {
            steps.add(new Object[]{new ChatCompletionException(kind, kind.name()), deltas});
            return this;
        }

        ScriptedPort answer(String... deltas) {
            steps.add(new Object[]{null, deltas});
            return this;
        }

        @Override
        public ChatResponse complete(ChatRequest request) {
            requests.add(request);
            Object[] step = steps.poll();
            if (step[0] != null) {
                throw (ChatCompletionException) step[0];
            }
            return response(String.join("", (String[]) step[1]));
        }

        @Override
        public ChatTask stream(ChatRequest request, final ChatStreamListener listener) {
            requests.add(request);
            final Object[] step = steps.poll();
            final boolean[] cancelled = new boolean[1];
            Runnable play = () -> {
                listener.onStart();
                StringBuilder text = new StringBuilder();
                for (String delta : (String[]) step[1]) {
                    text.append(delta);
                    listener.onDelta(delta);
                }
                if (step[0] != null) {
                    failed.countDown();
                    listener.onError((ChatCompletionException) step[0]);
                } else {
                    listener.onComplete(response(text.toString()));
                }
            };
            if (async) {
                new Thread(play, "scripted-chat").start();
            } else {
                play.run();
            }
            return new ChatTask() {
                @Override
                public void cancel() {
                    cancelled[0] = true;
                }

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled[0];
                }
            };
        }

        private static ChatResponse response(String text) {
            return new ChatResponse(ChatMessage.assistant(text), ChatFinishReason.STOP, ChatUsage.notReported(), "m");
        }
    }

    private static final class Recorder implements ChatStreamListener {
        final List<String> events = Collections.synchronizedList(new ArrayList<String>());
        final CountDownLatch done = new CountDownLatch(1);

        void await() throws InterruptedException {
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        @Override
        public void onStart() {
            events.add("start");
        }

        @Override
        public void onDelta(String text) {
            events.add("delta:" + text);
        }

        @Override
        public void onComplete(ChatResponse response) {
            events.add("complete:" + response.content());
            done.countDown();
        }

        @Override
        public void onError(ChatCompletionException error) {
            events.add("error:" + error.kind());
            done.countDown();
        }

        @Override
        public void onCancelled() {
            events.add("cancelled");
            done.countDown();
        }
    }
}
