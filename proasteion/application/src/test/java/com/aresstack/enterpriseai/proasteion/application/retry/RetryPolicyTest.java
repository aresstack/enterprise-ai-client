package com.aresstack.enterpriseai.proasteion.application.retry;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RetryPolicyTest {

    private final List<Long> pauses = new ArrayList<Long>();
    private final RetryPolicy.Sleeper recording = pauses::add;

    @Test
    public void pausesAreStagedAndCapped() {
        RetryPolicy policy = RetryPolicy.of(3, 300);
        assertEquals(300L, policy.delayBeforeRetry(1));
        assertEquals(750L, policy.delayBeforeRetry(2));
        assertEquals(1875L, policy.delayBeforeRetry(3));
        assertEquals(RetryPolicy.MAX_DELAY_MILLIS, RetryPolicy.of(10, 30_000).delayBeforeRetry(5));
        assertEquals(4, policy.maxAttempts());
    }

    @Test
    public void zeroRetriesIsNone() {
        assertEquals(RetryPolicy.none(), RetryPolicy.of(0, 500));
        assertEquals(1, RetryPolicy.none().maxAttempts());
    }

    @Test(expected = IllegalArgumentException.class)
    public void tooManyRetriesAreRejected() {
        RetryPolicy.of(RetryPolicy.MAX_RETRIES + 1, 0);
    }

    @Test
    public void transientFailuresAreRetriedUntilSuccess() throws IOException {
        final AtomicInteger attempts = new AtomicInteger();
        String result = RetryPolicy.of(2, 300).call(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IOException("kurz weg");
            }
            return "ok";
        }, IOException.class, e -> true, RetryPolicy.Observer.none(), recording);

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
        assertEquals(Arrays.asList(300L, 750L), pauses);
    }

    @Test
    public void lastTransientFailureIsRethrownAfterAllRetries() {
        final AtomicInteger attempts = new AtomicInteger();
        try {
            RetryPolicy.of(2, 10).call(() -> {
                attempts.incrementAndGet();
                throw new IOException("weg " + attempts.get());
            }, IOException.class, e -> true, RetryPolicy.Observer.none(), recording);
            fail();
        } catch (IOException e) {
            assertEquals("weg 3", e.getMessage());
        }
        assertEquals(3, attempts.get());
    }

    @Test
    public void permanentFailureIsNotRetried() {
        final AtomicInteger attempts = new AtomicInteger();
        try {
            RetryPolicy.of(5, 10).call(() -> {
                attempts.incrementAndGet();
                throw new IOException("fachlich");
            }, IOException.class, e -> false, RetryPolicy.Observer.none(), recording);
            fail();
        } catch (IOException expected) {
            // erwartet
        }
        assertEquals(1, attempts.get());
        assertTrue(pauses.isEmpty());
    }

    @Test
    public void foreignRuntimeExceptionsPassUnchanged() throws IOException {
        try {
            RetryPolicy.of(5, 10).<String, IOException>call(() -> {
                throw new IllegalStateException("Bug");
            }, IOException.class, e -> true, RetryPolicy.Observer.none(), recording);
            fail();
        } catch (IllegalStateException expected) {
            assertTrue(pauses.isEmpty());
        }
    }

    @Test
    public void interruptionEndsTheRetries() {
        final AtomicInteger attempts = new AtomicInteger();
        try {
            RetryPolicy.of(5, 10).call(() -> {
                attempts.incrementAndGet();
                throw new IOException("weg");
            }, IOException.class, e -> true, RetryPolicy.Observer.none(), millis -> {
                throw new InterruptedException();
            });
            fail();
        } catch (IOException expected) {
            assertTrue(Thread.interrupted());
        }
        assertEquals(1, attempts.get());
    }
}
