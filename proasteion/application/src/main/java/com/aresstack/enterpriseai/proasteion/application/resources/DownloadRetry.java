package com.aresstack.enterpriseai.proasteion.application.resources;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wie oft ein Download nach einem vorübergehenden Netzwerkfehler (Zeitüberschreitung, Verbindungsaufbau, abgebrochene
 * Verbindung, HTTP 408/429/5xx) erneut versucht wird und wie lange davor gewartet wird. Falsche Größe, falscher
 * SHA-256 und andere HTTP-Antworten (etwa 404) werden nie wiederholt. Die Timeouts selbst bleiben unverändert.
 */
public final class DownloadRetry {

    private final int attempts;
    private final List<Long> pausesMillis;

    /**
     * @param attempts     Versuche insgesamt, mindestens 1
     * @param pausesMillis Pause vor dem 2., 3., … Versuch; fehlt eine, gilt die letzte
     */
    public DownloadRetry(int attempts, List<Long> pausesMillis) {
        this.attempts = Math.max(1, attempts);
        this.pausesMillis = Collections.unmodifiableList(new ArrayList<Long>(pausesMillis));
    }

    /** Ein Versuch, keine Wiederholung. */
    public static DownloadRetry once() {
        return new DownloadRetry(1, Collections.<Long>emptyList());
    }

    /** Aus Texten wie {@code 3} und {@code 1000,2000}; Unlesbares gilt als „ein Versuch“. */
    public static DownloadRetry parse(String attempts, String pausesMillis) {
        try {
            List<Long> pauses = new ArrayList<Long>();
            for (String pause : (pausesMillis == null ? "" : pausesMillis).split(",")) {
                if (!pause.trim().isEmpty()) {
                    pauses.add(Math.max(0L, Long.parseLong(pause.trim())));
                }
            }
            return new DownloadRetry(attempts == null || attempts.trim().isEmpty() ? 1
                    : Integer.parseInt(attempts.trim()), pauses);
        } catch (NumberFormatException unreadable) {
            return once();
        }
    }

    public int attempts() {
        return attempts;
    }

    /** Die Pause vor Versuch {@code attempt} (ab 2). */
    public long pauseBefore(int attempt) {
        if (pausesMillis.isEmpty() || attempt < 2) {
            return 0L;
        }
        return pausesMillis.get(Math.min(attempt - 2, pausesMillis.size() - 1));
    }

    /** Ob der Fehler vorübergehend ist und ein weiterer Versuch lohnt. */
    public static boolean isTransient(IOException failure) {
        return failure instanceof InterruptedIOException // SocketTimeoutException included
                || failure instanceof ConnectException
                || failure instanceof NoRouteToHostException
                || failure instanceof SocketException // connection reset, broken pipe
                || failure instanceof TransientHttpException;
    }

    /** Eine HTTP-Antwort, die auf einen vorübergehenden Zustand des Servers hinweist (408, 429, 5xx). */
    static final class TransientHttpException extends IOException {
        private static final long serialVersionUID = 1L;

        TransientHttpException(String message) {
            super(message);
        }

        static boolean applies(int code) {
            return code == 408 || code == 429 || code >= 500;
        }
    }
}
