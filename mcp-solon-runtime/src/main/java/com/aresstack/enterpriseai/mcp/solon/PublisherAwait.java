package com.aresstack.enterpriseai.mcp.solon;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wartet genau einmal auf das erste Element eines Reactive-Streams-{@link Publisher}s.
 *
 * <p>Der Client ruft das asynchrone MCP-SDK bewusst direkt auf, statt über
 * {@code McpClientProvider.callToolRequest}: Dessen {@code executeWithRetry} wiederholt einen Tool-Aufruf nach
 * Transportfehlern (ein Handler mit Seiteneffekten liefe doppelt) und loggt Fehlermeldungen samt URL, bevor
 * dieser Adapter sie bereinigen kann. Abhängig ist diese Klasse nur von der Reactive-Streams-Schnittstelle,
 * nicht von Reactor.
 */
final class PublisherAwait {

    private PublisherAwait() {
    }

    static <T> T first(Publisher<T> publisher, Duration timeout) throws TimeoutException, InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<T> value = new AtomicReference<T>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final AtomicReference<Subscription> subscription = new AtomicReference<Subscription>();
        publisher.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription s) {
                subscription.set(s);
                s.request(1);
            }

            @Override
            public void onNext(T item) {
                if (value.compareAndSet(null, item)) {
                    Subscription s = subscription.get();
                    if (s != null) {
                        s.cancel();
                    }
                    done.countDown();
                }
            }

            @Override
            public void onError(Throwable error) {
                failure.compareAndSet(null, error);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        if (!done.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            Subscription s = subscription.get();
            if (s != null) {
                s.cancel();
            }
            throw new TimeoutException("MCP request timed out");
        }
        Throwable error = failure.get();
        if (error instanceof RuntimeException) {
            throw (RuntimeException) error;
        }
        if (error instanceof Error) {
            throw (Error) error;
        }
        if (error != null) {
            throw new IllegalStateException(error.getClass().getSimpleName(), error);
        }
        return value.get();
    }
}
