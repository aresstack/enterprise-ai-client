package com.aresstack.enterpriseai.app.ui.chat;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

/** Führt Swing-Testcode auf dem EDT aus und reicht Fehler unverpackt weiter. */
final class Edt {

    private Edt() {
    }

    static void run(Runnable runnable) throws Exception {
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            }
            if (ex.getCause() instanceof Error) {
                throw (Error) ex.getCause();
            }
            throw ex;
        }
    }

    /** Leert die EDT-Warteschlange (z. B. nach invokeLater beim Scrollen). */
    static void flush() throws Exception {
        run(new Runnable() {
            @Override
            public void run() {
            }
        });
    }
}
