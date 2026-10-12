package com.aresstack.enterpriseai.proasteion.application.retry;

import java.util.function.Predicate;

/**
 * Wie oft und mit welcher Pause ein einzelner Aufruf nach einem vorübergehenden Fehler wiederholt wird: höchstens
 * {@link #retries()} Wiederholungen, die erste nach {@link #initialDelayMillis()}, jede weitere um
 * {@link #BACKOFF_FACTOR} länger (Standard 300 ms, 750 ms), nie länger als {@link #MAX_DELAY_MILLIS}. Welche Fehler
 * vorübergehend sind, entscheidet der Aufrufer ({@link #call}); die Regel selbst kennt weder KeePass noch HTTP.
 *
 * <p>Benutzt von den Decoratorn der Composition Root (Secret-Auflösung, Modellanfrage, Quellenzugriff); UI und Use
 * Cases enthalten keine Wiederholungsschleifen. Unveränderlich.
 */
public final class RetryPolicy {

    /** Faktor, um den jede weitere Pause länger wird. */
    public static final double BACKOFF_FACTOR = 2.5;
    /** Obergrenze einer einzelnen Pause. */
    public static final long MAX_DELAY_MILLIS = 30_000L;
    /** Größte einstellbare Zahl der Wiederholungen. */
    public static final int MAX_RETRIES = 10;
    /** Größte einstellbare Anfangspause. */
    public static final int MAX_INITIAL_DELAY_MILLIS = 30_000;

    private final int retries;
    private final long initialDelayMillis;

    private RetryPolicy(int retries, long initialDelayMillis) {
        this.retries = retries;
        this.initialDelayMillis = initialDelayMillis;
    }

    /** Kein Wiederholen: der erste Fehler gilt. */
    public static RetryPolicy none() {
        return new RetryPolicy(0, 0L);
    }

    /**
     * @param retries            Wiederholungen nach dem ersten Versuch (0–{@link #MAX_RETRIES})
     * @param initialDelayMillis Pause vor der ersten Wiederholung (0–{@link #MAX_INITIAL_DELAY_MILLIS})
     */
    public static RetryPolicy of(int retries, long initialDelayMillis) {
        if (retries < 0 || retries > MAX_RETRIES) {
            throw new IllegalArgumentException("retries must be between 0 and " + MAX_RETRIES);
        }
        if (initialDelayMillis < 0 || initialDelayMillis > MAX_INITIAL_DELAY_MILLIS) {
            throw new IllegalArgumentException("initialDelayMillis must be between 0 and " + MAX_INITIAL_DELAY_MILLIS);
        }
        return retries == 0 ? none() : new RetryPolicy(retries, initialDelayMillis);
    }

    public int retries() {
        return retries;
    }

    /** Versuche insgesamt: der erste plus {@link #retries()}. */
    public int maxAttempts() {
        return retries + 1;
    }

    public long initialDelayMillis() {
        return initialDelayMillis;
    }

    /**
     * Die Pause vor der Wiederholung Nummer {@code retry} (ab 1): gestuft, gedeckelt.
     */
    public long delayBeforeRetry(int retry) {
        if (retry < 1) {
            throw new IllegalArgumentException("retry counts from 1");
        }
        double delay = initialDelayMillis * Math.pow(BACKOFF_FACTOR, retry - 1);
        return (long) Math.min(delay, (double) MAX_DELAY_MILLIS);
    }

    /** Ein Versuch des wiederholbaren Aufrufs. */
    public interface Attempt<T, X extends Exception> {
        T run() throws X;
    }

    /** Wartet die Pause ab; produktiv {@link Thread#sleep}. */
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;

        static Sleeper threadSleep() {
            return Thread::sleep;
        }
    }

    /** Erfährt jede Wiederholung, bevor sie wartet (Protokoll). */
    public interface Observer<X> {
        void retrying(int retry, int maxRetries, long delayMillis, X failure);

        static <X> Observer<X> none() {
            return (retry, maxRetries, delayMillis, failure) -> {
            };
        }
    }

    /** Wie {@link #call(Attempt, Class, Predicate, Observer, Sleeper)} mit {@link Thread#sleep} und ohne Beobachter. */
    public <T, X extends Exception> T call(Attempt<T, X> attempt, Class<X> failureType,
                                           Predicate<? super X> transientFailure) throws X {
        return call(attempt, failureType, transientFailure, Observer.<X>none(), Sleeper.threadSleep());
    }

    /**
     * Führt {@code attempt} aus und wiederholt ihn, solange er mit einem Fehler vom Typ {@code failureType} scheitert,
     * den {@code transientFailure} als vorübergehend einstuft, und Wiederholungen übrig sind. Jeder andere Fehler und
     * der letzte vorübergehende gehen unverändert an den Aufrufer. Eine Unterbrechung während der Pause beendet die
     * Wiederholungen: der letzte Fehler gilt, das Unterbrechungs-Flag bleibt gesetzt.
     */
    public <T, X extends Exception> T call(Attempt<T, X> attempt, Class<X> failureType,
                                           Predicate<? super X> transientFailure, Observer<? super X> observer,
                                           Sleeper sleeper) throws X {
        if (attempt == null || failureType == null || transientFailure == null || observer == null
                || sleeper == null) {
            throw new IllegalArgumentException("attempt, failureType, transientFailure, observer and sleeper are required");
        }
        for (int retry = 1; ; retry++) {
            try {
                return attempt.run();
            } catch (Exception e) {
                if (!failureType.isInstance(e)) {
                    throw unchecked(e);
                }
                X failure = failureType.cast(e);
                if (retry > retries || !transientFailure.test(failure)) {
                    throw failure;
                }
                long delay = delayBeforeRetry(retry);
                observer.retrying(retry, retries, delay, failure);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
    }

    /** Ein Versuch wirft nur {@code X} oder ungeprüfte Fehler; alles andere wäre ein Programmierfehler. */
    private static RuntimeException unchecked(Exception e) {
        if (e instanceof RuntimeException) {
            return (RuntimeException) e;
        }
        return new IllegalStateException("unexpected checked exception " + e.getClass().getName(), e);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RetryPolicy)) {
            return false;
        }
        RetryPolicy that = (RetryPolicy) other;
        return retries == that.retries && initialDelayMillis == that.initialDelayMillis;
    }

    @Override
    public int hashCode() {
        return 31 * retries + (int) (initialDelayMillis ^ (initialDelayMillis >>> 32));
    }

    @Override
    public String toString() {
        return retries == 0 ? "RetryPolicy[none]"
                : "RetryPolicy[retries=" + retries + ", initialDelayMillis=" + initialDelayMillis + "]";
    }
}
