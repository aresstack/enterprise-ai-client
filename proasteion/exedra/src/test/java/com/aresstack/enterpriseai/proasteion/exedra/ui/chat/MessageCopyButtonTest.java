package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Der Kopierknopf der Blasen: Nutzer-, Antwort- und Fehlerblase tragen ihn, Hinweise und Werkzeugwolken nicht. Er
 * kopiert den ursprünglichen Text bzw. Markdown, ohne Kopfzeile und ohne Quellenleiste, und bestätigt kurz.
 */
public class MessageCopyButtonTest {

    @Test
    public void copiesTheOriginalMessageOfPersistentBubblesOnly() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                ChatShellPanel shell = new ChatShellPanel(model, new RecordingActions(),
                        ComicPalette.defaultPalette(), BubblePalette.windowsPhoneInspired());
                shell.setSize(800, 600);
                shell.doLayout();
                final List<String> clipboard = new ArrayList<String>();
                shell.transcript().setClipboard(new Consumer<String>() {
                    @Override
                    public void accept(String text) {
                        clipboard.add(text);
                    }
                });

                TranscriptEntry user = model.addUserMessage("Was ist IAR?");
                TranscriptEntry answer = model.beginAssistantMessage();
                model.appendAssistantDelta("**IAR** ist die [Plattform](https://example.invalid/iar).");
                model.completeAssistantMessage();
                model.attachSources(answer, java.util.Collections.singletonList(
                        new SourceReference(1, "IAR Überblick", "", "https://example.invalid/iar", "", 1.0d, 1, 0)));
                TranscriptEntry notice = model.addNotice("Die Wissenssuche ist aus.");
                shell.transcript().flushPendingUpdates();

                MessageCopyButton userCopy = shell.transcript().copyButtonFor(user.getId());
                MessageCopyButton answerCopy = shell.transcript().copyButtonFor(answer.getId());
                assertNotNull(userCopy);
                assertNotNull(answerCopy);
                assertNull("keine Kopie an Hinweiszeilen", shell.transcript().copyButtonFor(notice.getId()));
                assertEquals(MessageCopyButton.TOOLTIP, answerCopy.getToolTipText());

                userCopy.doClick();
                answerCopy.doClick();
                assertEquals("Was ist IAR?", clipboard.get(0));
                assertEquals("Markdown-Quelltext, ohne Kopfzeile und Quellenleiste",
                        "**IAR** ist die [Plattform](https://example.invalid/iar).", clipboard.get(1));
                assertTrue(answerCopy.isConfirming());
                assertEquals(MessageCopyButton.COPIED, answerCopy.getText());
                answerCopy.showIdle();
                assertFalse(answerCopy.isConfirming());

                model.addUserMessage("Und weiter?");
                TranscriptEntry failed = model.beginAssistantMessage();
                model.failAssistantMessage("Der KI-Dienst antwortet nicht.\nTechnische Ursache: Zeitüberschreitung");
                shell.transcript().flushPendingUpdates();
                MessageCopyButton failedCopy = shell.transcript().copyButtonFor(failed.getId());
                assertNotNull("auch die Fehlerblase", failedCopy);
                failedCopy.doClick();
                assertEquals("Der KI-Dienst antwortet nicht.\nTechnische Ursache: Zeitüberschreitung",
                        clipboard.get(2));
            }
        });
    }
}
