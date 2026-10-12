package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * A message whose text came from speech recognition leaves the composer marked as a transcript, also after manual
 * edits; a draft typed anew after clearing the editor is typed again.
 */
public class DictationOriginTest {

    @Test
    public void dictatedDraftIsSentAsSpeechTranscript() throws Exception {
        Edt.run(() -> {
            Composer composer = new Composer();
            composer.dictate("Aklima bitte erstmal das Programm");
            composer.panel.composer().sendButton().doClick();

            assertEquals("Aklima bitte erstmal das Programm", composer.actions.drafts.get(0).text());
            assertEquals(MessageOrigin.SPEECH_TRANSCRIPT, composer.actions.drafts.get(0).origin());
        });
    }

    @Test
    public void manualCorrectionKeepsTheTranscriptMark() throws Exception {
        Edt.run(() -> {
            Composer composer = new Composer();
            composer.dictate("Aklima bitte erstmal das Programm");
            javax.swing.JTextArea editor = (javax.swing.JTextArea) composer.panel.composer().editor();
            editor.select(0, "Aklima".length());
            editor.replaceSelection("Erklär mir");
            composer.panel.composer().sendButton().doClick();

            assertEquals(MessageOrigin.SPEECH_TRANSCRIPT, composer.actions.drafts.get(0).origin());
        });
    }

    @Test
    public void typedDraftsAreTypedAlsoAfterADictatedOne() throws Exception {
        Edt.run(() -> {
            Composer composer = new Composer();
            ChatComposerPanel panel = composer.panel.composer();
            panel.editor().setText("Was ist IAR?");
            panel.sendButton().doClick();
            composer.dictate("Hallo");
            panel.sendButton().doClick();
            panel.editor().setText("Wie lange gilt das?");
            panel.sendButton().doClick();
            composer.dictate("verworfen");
            panel.editor().setText("");
            panel.editor().setText("Neu getippt");
            panel.sendButton().doClick();

            List<MessageOrigin> origins = new ArrayList<MessageOrigin>();
            for (ChatDraft draft : composer.actions.drafts) {
                origins.add(draft.origin());
            }
            assertEquals(java.util.Arrays.asList(MessageOrigin.TYPED, MessageOrigin.SPEECH_TRANSCRIPT,
                    MessageOrigin.TYPED, MessageOrigin.TYPED), origins);
        });
    }

    private static final class Composer {
        final DraftActions actions = new DraftActions();
        final FakeDictation dictation = new FakeDictation();
        final ChatShellPanel panel;

        Composer() {
            ChatShellModel model = new ChatShellModel(() -> 1_700_000_000_000L);
            panel = new ChatShellPanel(model, actions, ComicPalette.defaultPalette(),
                    BubblePalette.windowsPhoneInspired());
            panel.setSize(900, 700);
            panel.composer().setDictation(dictation);
        }

        /** Record with the microphone, stop, and let recognition deliver {@code transcript}. */
        void dictate(String transcript) {
            ChatComposerPanel composer = panel.composer();
            composer.micButton().doClick(); // start recording
            composer.micButton().doClick(); // stop and recognize
            dictation.result.recognized(transcript);
        }
    }

    private static final class DraftActions implements ChatShellActions {
        final List<ChatDraft> drafts = new ArrayList<ChatDraft>();

        @Override
        public void sendRequested(String text, boolean ragEnabled) {
            sendRequested(ChatDraft.of(text, ragEnabled));
        }

        @Override
        public void sendRequested(ChatDraft draft) {
            drafts.add(draft);
        }

        @Override
        public void stopRequested() {
        }
    }

    private static final class FakeDictation implements DictationControl {
        Result result;

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String description() {
            return "Testdiktat";
        }

        @Override
        public void start(Result value) {
            result = value;
        }

        @Override
        public void finish() {
        }

        @Override
        public void cancel() {
        }

        @Override
        public int level() {
            return 0;
        }

        @Override
        public boolean clipping() {
            return false;
        }

        @Override
        public boolean canRetry() {
            return false;
        }

        @Override
        public void retry(Result value) {
            result = value;
        }

        @Override
        public void addListener(Listener listener) {
        }
    }
}
