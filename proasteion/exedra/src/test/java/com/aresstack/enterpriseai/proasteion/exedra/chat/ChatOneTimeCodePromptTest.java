package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.OneTimeCodePrompt;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.source.api.OneTimeCodeChallenge;
import org.junit.Test;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Die Einmalcode-Abfrage im Chat: der wartende Lesezugriff zeigt die Ansicht über dem Verlauf, der Code geht nur an
 * die Quelle; „Abbrechen“ gilt bis zur nächsten eigenen Nachricht.
 */
public class ChatOneTimeCodePromptTest {

    private static final SourceAddress SOURCE = SourceAddress.of("confluence-dc");

    private final FakeOverlay overlay = new FakeOverlay();
    private final ChatOneTimeCodePrompt prompt = new ChatOneTimeCodePrompt(overlay, Runnable::run);
    private final RecordingChallenge challenge = new RecordingChallenge();
    private final ExecutorService tool = Executors.newSingleThreadExecutor();

    @Test
    public void theCodeGoesStraightToTheSourceAndTheOverlayCloses() throws Exception {
        Future<OneTimeCodePrompt.Outcome> outcome = tool.submit(unlock());
        JComponent shown = overlay.awaitContent();
        assertEquals(ChatOneTimeCodePrompt.TITLE, overlay.title);

        type(shown, "482913");
        press(shown, "Bestätigen");

        assertEquals(OneTimeCodePrompt.Outcome.UNLOCKED, outcome.get(5, TimeUnit.SECONDS));
        assertEquals(Collections.singletonList("482913"), challenge.codes);
        assertTrue(overlay.closed);
    }

    @Test
    public void cancelDeclinesUntilTheNextOwnMessage() throws Exception {
        Future<OneTimeCodePrompt.Outcome> outcome = tool.submit(unlock());
        press(overlay.awaitContent(), ChatOneTimeCodePrompt.CANCEL_TEXT);
        assertEquals(OneTimeCodePrompt.Outcome.DECLINED, outcome.get(5, TimeUnit.SECONDS));

        overlay.content = null;
        assertEquals("kein zweites Fragen im selben Turn", OneTimeCodePrompt.Outcome.DECLINED,
                prompt.unlock(SOURCE, challenge));
        assertNull(overlay.content);

        ChatShellModel model = new ChatShellModel(() -> 0L);
        model.addListener(prompt);
        model.addUserMessage("Noch einmal bitte");
        Future<OneTimeCodePrompt.Outcome> again = tool.submit(unlock());
        assertNotNull("nach eigener Nachricht wird wieder gefragt", overlay.awaitContent());
        overlay.closeByUser();
        assertEquals(OneTimeCodePrompt.Outcome.DECLINED, again.get(5, TimeUnit.SECONDS));
        assertTrue(challenge.codes.isEmpty());
    }

    private Callable<OneTimeCodePrompt.Outcome> unlock() {
        return () -> prompt.unlock(SOURCE, challenge);
    }

    private static void type(Container root, String code) {
        for (Component child : all(root)) {
            if (child instanceof JPasswordField) {
                ((JPasswordField) child).setText(code);
                return;
            }
        }
        throw new AssertionError("kein Codefeld");
    }

    private static void press(Container root, String text) {
        for (Component child : all(root)) {
            if (child instanceof AbstractButton && text.equals(((AbstractButton) child).getText())) {
                ((AbstractButton) child).doClick();
                return;
            }
        }
        throw new AssertionError("kein Knopf " + text);
    }

    private static List<Component> all(Container root) {
        List<Component> found = new ArrayList<Component>();
        for (Component child : root.getComponents()) {
            found.add(child);
            if (child instanceof Container) {
                found.addAll(all((Container) child));
            }
        }
        return found;
    }

    private static final class FakeOverlay implements ChatOneTimeCodePrompt.Overlay {
        volatile JComponent content;
        volatile String title;
        volatile Runnable whenClosed;
        volatile boolean closed;

        @Override
        public synchronized void show(JComponent shown, String heading, Runnable onClose) {
            content = shown;
            title = heading;
            whenClosed = onClose;
            closed = false;
            notifyAll();
        }

        @Override
        public void close() {
            closed = true;
            Runnable run = whenClosed;
            whenClosed = null;
            if (run != null) {
                run.run();
            }
        }

        void closeByUser() {
            close();
        }

        synchronized JComponent awaitContent() throws InterruptedException {
            long end = System.currentTimeMillis() + 5000;
            while (content == null && System.currentTimeMillis() < end) {
                wait(100);
            }
            return content;
        }
    }

    private static final class RecordingChallenge implements OneTimeCodeChallenge {
        final List<String> codes = Collections.synchronizedList(new ArrayList<String>());

        @Override
        public String service() {
            return "confluence.invalid";
        }

        @Override
        public String title() {
            return "Einmalcode bestätigen";
        }

        @Override
        public String message() {
            return "Code per E-Mail gesendet.";
        }

        @Override
        public String error() {
            return "";
        }

        @Override
        public boolean resendAvailable() {
            return true;
        }

        @Override
        public OneTimeCodeChallenge submit(char[] code) {
            codes.add(new String(code));
            return null;
        }

        @Override
        public OneTimeCodeChallenge resend() {
            return this;
        }
    }
}
