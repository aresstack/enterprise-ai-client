package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.OneTimeCodePrompt;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderSignIn;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderSignInPanel;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.OneTimeCodeChallenge;

import javax.swing.JComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Die Einmalcode-Abfrage im Chat: braucht ein Lesezugriff (etwa {@code knowledge_read} auf einen Confluence-Anhang)
 * einen Code, erscheint über dem Verlauf dieselbe Ansicht wie im Lesebereich ({@link ReaderSignInPanel}) mit
 * „Abbrechen“. Der wartende Werkzeug-Thread sendet Code und „Neu senden“ selbst an die Quelle; der Code geht direkt an
 * {@link OneTimeCodeChallenge#submit(char[])}, wird danach überschrieben und erreicht weder Modell noch Verlauf,
 * Export oder Protokoll.
 *
 * <p>Abgebrochen gilt je Quelle bis zur nächsten eigenen Nachricht, damit ein Turn nicht bei jedem Anhang erneut
 * fragt.</p>
 */
public final class ChatOneTimeCodePrompt implements OneTimeCodePrompt, ChatShellModelListener {

    private static final Logger LOG = Logger.getLogger(ChatOneTimeCodePrompt.class.getName());

    static final String TITLE = "Einmalcode für Dateianhänge";
    static final String CANCEL_TEXT = "Abbrechen";
    static final String SEND_FAILED = "Senden fehlgeschlagen (Protokoll).";

    /** Wo die Abfrage erscheint (der Chat-Verlauf). Nur auf dem EDT. */
    public interface Overlay {
        /** Zeigt die Ansicht; {@code closed} läuft, sobald sie schließt, gleich wodurch. */
        void show(JComponent content, String title, Runnable closed);

        void close();
    }

    private final Overlay overlay;
    private final Executor uiExecutor;
    private final Set<SourceAddress> declined = ConcurrentHashMap.newKeySet();

    public ChatOneTimeCodePrompt(Overlay overlay, Executor uiExecutor) {
        if (overlay == null || uiExecutor == null) {
            throw new IllegalArgumentException("overlay and uiExecutor must not be null");
        }
        this.overlay = overlay;
        this.uiExecutor = uiExecutor;
    }

    @Override
    public Outcome unlock(SourceAddress source, OneTimeCodeChallenge challenge) {
        if (source == null || challenge == null || declined.contains(source)) {
            return Outcome.DECLINED;
        }
        final BlockingQueue<Step> steps = new LinkedBlockingQueue<Step>();
        uiExecutor.execute(() -> {
            // The waiting thread closes the overlay itself once the source accepted the code.
            final ReaderSignInPanel panel = new ReaderSignInPanel(new QueuedSignIn(challenge, steps), () -> {
            });
            panel.addAction(CANCEL_TEXT, () -> {
                steps.add(Step.cancel());
                overlay.close();
            });
            overlay.show(panel, TITLE, () -> steps.add(Step.cancel()));
            panel.focusCode();
        });
        OneTimeCodeChallenge current = challenge;
        try {
            while (true) {
                Step step = steps.take();
                if (step.cancel) {
                    declined.add(source);
                    uiExecutor.execute(overlay::close);
                    return Outcome.DECLINED;
                }
                OneTimeCodeChallenge next;
                try {
                    next = step.code != null ? current.submit(step.code) : current.resend();
                } catch (KnowledgeSourceException e) {
                    LOG.warning("Einmalcode nicht gesendet (" + e.kind() + ")");
                    final String reason = e.getMessage() == null ? SEND_FAILED : e.getMessage();
                    uiExecutor.execute(() -> step.failed.accept(reason));
                    continue;
                } catch (RuntimeException e) {
                    LOG.warning("Einmalcode nicht gesendet: " + e.getClass().getSimpleName());
                    uiExecutor.execute(() -> step.failed.accept(SEND_FAILED));
                    continue;
                } finally {
                    if (step.code != null) {
                        Arrays.fill(step.code, '\0');
                    }
                }
                if (next == null && step.code != null) {
                    uiExecutor.execute(overlay::close);
                    return Outcome.UNLOCKED;
                }
                current = next == null ? current : next;
                final ReaderSignIn shown = new QueuedSignIn(current, steps);
                uiExecutor.execute(() -> step.next.accept(shown));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // the chat turn was stopped
            uiExecutor.execute(overlay::close);
            return Outcome.DECLINED;
        }
    }

    /** Eine eigene Nachricht: abgebrochene Quellen dürfen wieder fragen. */
    @Override
    public void entryAdded(TranscriptEntry entry) {
        if (entry != null && entry.getAuthor() == TranscriptEntry.Author.USER) {
            declined.clear();
        }
    }

    @Override
    public void entryUpdated(TranscriptEntry entry) {
    }

    @Override
    public void stateChanged() {
    }

    @Override
    public void entriesCleared() {
        declined.clear();
    }

    /** Ein Wunsch der Oberfläche an den wartenden Thread: Code senden, neu senden oder abbrechen. */
    private static final class Step {
        final char[] code;
        final Consumer<ReaderSignIn> next;
        final Consumer<String> failed;
        final boolean cancel;

        static Step cancel() {
            return new Step(null, null, null, true);
        }

        Step(char[] code, Consumer<ReaderSignIn> next, Consumer<String> failed, boolean cancel) {
            this.code = code;
            this.next = next;
            this.failed = failed;
            this.cancel = cancel;
        }
    }

    /** Die Abfrage für {@link ReaderSignInPanel}; Senden reiht nur ein, das Netz bedient der wartende Thread. */
    private static final class QueuedSignIn implements ReaderSignIn {
        private final OneTimeCodeChallenge challenge;
        private final BlockingQueue<Step> steps;

        QueuedSignIn(OneTimeCodeChallenge challenge, BlockingQueue<Step> steps) {
            this.challenge = challenge;
            this.steps = steps;
        }

        @Override
        public String service() {
            return challenge.service();
        }

        @Override
        public String message() {
            return challenge.message();
        }

        @Override
        public String error() {
            return challenge.error();
        }

        @Override
        public boolean resendAvailable() {
            return challenge.resendAvailable();
        }

        @Override
        public void submit(char[] code, Consumer<ReaderSignIn> next, Consumer<String> failed) {
            steps.add(new Step(Arrays.copyOf(code, code.length), next, failed, false));
        }

        @Override
        public void resend(Consumer<ReaderSignIn> next, Consumer<String> failed) {
            steps.add(new Step(null, next, failed, false));
        }
    }
}
