package com.aresstack.enterpriseai.proasteion.exedra.security;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.exedra.ui.security.CredentialDialog;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Zugang „Manuell“ in der Desktop-Anwendung: der Comic-Dialog {@link CredentialDialog}, modal auf dem EDT; der
 * Aufrufer (Arbeits-Thread) wartet. Ohne Display (headless) scheitert die Abfrage mit einer Meldung.
 */
public final class DialogCredentialPrompt implements AskedSecretProvider.CredentialPrompt {

    @Override
    public AskedSecretProvider.Answer ask(SecretRef ref, final String label)
            throws AskedSecretProvider.Unavailable {
        if (GraphicsEnvironment.isHeadless()) {
            throw new AskedSecretProvider.Unavailable(
                    "Manuelle Zugangsdaten brauchen ein Fenster zur Eingabe");
        }
        if (SwingUtilities.isEventDispatchThread()) {
            return show(label);
        }
        final AtomicReference<AskedSecretProvider.Answer> result = new AtomicReference<AskedSecretProvider.Answer>();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    result.set(show(label));
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

    private static AskedSecretProvider.Answer show(String label) {
        CredentialDialog.Result result = CredentialDialog.show(label);
        return result == null ? null : new AskedSecretProvider.Answer(result.user(), result.password());
    }

    @Override
    public String toString() {
        return "DialogCredentialPrompt";
    }
}
