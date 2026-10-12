package com.aresstack.enterpriseai.proasteion.exedra.security;

import com.aresstack.enterpriseai.proasteion.exedra.ui.security.OneTimeCodeDialog;
import com.aresstack.enterpriseai.source.api.OneTimeCodePrompt;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Einmalcode-Abfrage der Desktop-Anwendung: der Comic-Dialog {@link OneTimeCodeDialog}, modal auf dem EDT; der
 * Aufrufer (Arbeits-Thread des Lesebereichs) wartet. Ohne Display (headless) gilt die Abfrage als abgebrochen.
 */
public final class DialogOneTimeCodePrompt implements OneTimeCodePrompt {

    @Override
    public Answer ask(final Challenge challenge) {
        if (GraphicsEnvironment.isHeadless()) {
            return Answer.cancel();
        }
        if (SwingUtilities.isEventDispatchThread()) {
            return show(challenge);
        }
        final AtomicReference<Answer> result = new AtomicReference<Answer>(Answer.cancel());
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    result.set(show(challenge));
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Answer.cancel();
        } catch (InvocationTargetException e) {
            return Answer.cancel();
        }
        return result.get();
    }

    private static Answer show(Challenge challenge) {
        OneTimeCodeDialog.Result result = OneTimeCodeDialog.show(challenge.service(), challenge.title(),
                challenge.message(), challenge.error(), challenge.resendAvailable());
        switch (result.choice()) {
            case SUBMIT:
                return Answer.submit(result.code());
            case RESEND:
                return Answer.resend();
            default:
                return Answer.cancel();
        }
    }

    @Override
    public String toString() {
        return "DialogOneTimeCodePrompt";
    }
}
