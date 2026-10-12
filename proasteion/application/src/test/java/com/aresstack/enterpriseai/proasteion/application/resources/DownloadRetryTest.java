package com.aresstack.enterpriseai.proasteion.application.resources;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Nur vorübergehende Netzwerkfehler werden wiederholt, mit den Pausen aus der Ressourcenliste. */
public class DownloadRetryTest {

    @Test
    public void readsAttemptsAndPauses() {
        DownloadRetry retry = DownloadRetry.parse("3", "1000,2000");
        assertEquals(3, retry.attempts());
        assertEquals(1000L, retry.pauseBefore(2));
        assertEquals(2000L, retry.pauseBefore(3));
        assertEquals("fehlende Pause = letzte", 2000L, retry.pauseBefore(4));
    }

    @Test
    public void unreadableOrMissingMeansOneAttempt() {
        assertEquals(1, DownloadRetry.parse(null, null).attempts());
        assertEquals(1, DownloadRetry.parse("drei", "1000").attempts());
    }

    @Test
    public void onlyTransientNetworkFailuresAreRetried() {
        assertTrue(DownloadRetry.isTransient(new SocketTimeoutException("Read timed out")));
        assertTrue(DownloadRetry.isTransient(new ConnectException("Connection refused")));
        assertTrue(DownloadRetry.isTransient(new SocketException("Connection reset")));
        assertTrue(DownloadRetry.isTransient(new DownloadRetry.TransientHttpException("HTTP 503")));
        assertFalse("SHA-256/Größe", DownloadRetry.isTransient(new IOException("SHA-256 stimmt nicht")));
        assertFalse("404", DownloadRetry.TransientHttpException.applies(404));
        assertTrue(DownloadRetry.TransientHttpException.applies(429));
        assertTrue(DownloadRetry.TransientHttpException.applies(502));
    }
}
