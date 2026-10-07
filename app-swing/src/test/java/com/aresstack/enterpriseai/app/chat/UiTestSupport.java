package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.fail;

/** Gemeinsame Helfer der Binding-Tests: EDT-Übergabe, Warten auf Model-Zustände, Arbeits-Threads. */
final class UiTestSupport {

    static final long TIMEOUT_MILLIS = 10000L;

    /** Reicht Model-Änderungen wie die Anwendung auf den Event Dispatch Thread. */
    static final Executor EDT = new Executor() {
        @Override
        public void execute(Runnable command) {
            SwingUtilities.invokeLater(command);
        }
    };

    private UiTestSupport() {
    }

    /** Ein Daemon-Pool für Retrieval und Indexierung, wie ihn die Composition Root anlegen würde. */
    static ExecutorService workers(final String name) {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    static void awaitIdle(final ChatShellModel model) throws Exception {
        await("Antwort abgeschlossen", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return !model.isStreaming();
            }
        });
    }

    static void awaitActivity(final ChatShellModel model, final String activity) throws Exception {
        await("Aktivität " + activity, new Callable<Boolean>() {
            @Override
            public Boolean call() {
                List<TranscriptEntry> entries = model.getEntries();
                return !entries.isEmpty() && activity.equals(entries.get(entries.size() - 1).getActivity());
            }
        });
    }

    static void awaitText(final ChatShellModel model, final String text) throws Exception {
        await("Text " + text, new Callable<Boolean>() {
            @Override
            public Boolean call() {
                for (TranscriptEntry entry : model.getEntries()) {
                    if (entry.getText().equals(text)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    static void await(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (onEdt(condition)) {
                return;
            }
            Thread.sleep(10L);
        }
        fail("Bedingung nicht innerhalb von " + TIMEOUT_MILLIS + " ms erfüllt: " + what);
    }

    static <T> T onEdt(final Callable<T> callable) throws Exception {
        final AtomicReference<T> result = new AtomicReference<T>();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    try {
                        result.set(callable.call());
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Error) {
                throw (Error) ex.getCause();
            }
            throw (Exception) ex.getCause();
        }
        return result.get();
    }

    static void onEdt(final Runnable runnable) throws Exception {
        onEdt(new Callable<Void>() {
            @Override
            public Void call() {
                runnable.run();
                return null;
            }
        });
    }

    /** Eine Momentaufnahme des Verlaufs vom EDT. */
    static List<TranscriptEntry> entries(final ChatShellModel model) throws Exception {
        return onEdt(new Callable<List<TranscriptEntry>>() {
            @Override
            public List<TranscriptEntry> call() {
                return new java.util.ArrayList<TranscriptEntry>(model.getEntries());
            }
        });
    }
}
