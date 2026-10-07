package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.app.ui.security.KeePassPairingDialog;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingCallback;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link KeePassPairingCallback} der Desktop-Anwendung: Der KeePassRPC-Adapter ruft ihn auf einem Arbeits-Thread
 * (Chat, Indexierung) auf; der Dialog läuft modal auf dem EDT, der Aufrufer wartet. Ohne Display (headless) oder
 * bei Unterbrechung gilt das Pairing als abgebrochen ({@code null}), genau wie beim Abbrechen im Dialog.
 */
public final class SwingPairingCallback implements KeePassPairingCallback {

    private final PairingPrompt prompt;

    /** Produktiv: der Comic-Dialog, der die KeePassRPC-Adresse zur Orientierung anzeigt. */
    public SwingPairingCallback(final String keePassAddress) {
        this(new PairingPrompt() {
            @Override
            public char[] requestPairingPassword(String clientDisplayName) {
                return KeePassPairingDialog.show(clientDisplayName, keePassAddress);
            }
        });
    }

    /** Mit eigener Eingabe (Tests, andere Oberflächen). */
    public SwingPairingCallback(PairingPrompt prompt) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }
        this.prompt = prompt;
    }

    @Override
    public char[] requestPairingPassword(final String clientDisplayName) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            return prompt.requestPairingPassword(clientDisplayName);
        }
        final AtomicReference<char[]> result = new AtomicReference<char[]>();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    result.set(prompt.requestPairingPassword(clientDisplayName));
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (InvocationTargetException e) {
            return null;
        }
        return result.get();
    }

    @Override
    public String toString() {
        return "SwingPairingCallback";
    }
}
