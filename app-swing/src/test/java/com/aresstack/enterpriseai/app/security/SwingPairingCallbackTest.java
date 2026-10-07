package com.aresstack.enterpriseai.app.security;

import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Headless (CI) gilt das Pairing als abgebrochen; mit Eingabe läuft die Abfrage auf dem EDT. */
public class SwingPairingCallbackTest {

    @Test
    public void headlessMeansCancelled() {
        final AtomicBoolean asked = new AtomicBoolean();
        SwingPairingCallback callback = new SwingPairingCallback(new PairingPrompt() {
            @Override
            public char[] requestPairingPassword(String clientDisplayName) {
                asked.set(true);
                return "x".toCharArray();
            }
        });
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            assertNull(callback.requestPairingPassword("Enterprise AI Client"));
            assertTrue("ohne Display wird kein Dialog gefragt", !asked.get());
        } else {
            assertArrayEquals("x".toCharArray(), callback.requestPairingPassword("Enterprise AI Client"));
            assertTrue(asked.get());
        }
    }

    @Test
    public void promptRunsOnTheEventDispatchThreadWhenADisplayExists() {
        org.junit.Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        final AtomicBoolean onEdt = new AtomicBoolean();
        SwingPairingCallback callback = new SwingPairingCallback(new PairingPrompt() {
            @Override
            public char[] requestPairingPassword(String clientDisplayName) {
                onEdt.set(SwingUtilities.isEventDispatchThread());
                return null;
            }
        });
        assertNull(callback.requestPairingPassword("Enterprise AI Client"));
        assertTrue(onEdt.get());
    }
}
