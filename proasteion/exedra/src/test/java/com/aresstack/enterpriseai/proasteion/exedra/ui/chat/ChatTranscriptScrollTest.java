package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JScrollBar;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertTrue;

/**
 * Der Chat rollt weiterhin ans Ende, wenn eine Antwort ankommt: das Rollen macht der Verlauf selbst
 * ({@code refresh(true)}), nicht die Schreibmarke der Markdown-Flächen, die seit 0.5.0 beim Einfügen stehen bleibt.
 */
public class ChatTranscriptScrollTest {

    @Test
    public void newAnswerScrollsToTheEnd() throws Exception {
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        final AtomicReference<ChatShellPanel> shell = new AtomicReference<ChatShellPanel>();
        Edt.run(() -> {
            shell.set(new ChatShellPanel(model, new RecordingActions(), ComicPalette.defaultPalette(),
                    BubblePalette.windowsPhoneInspired()));
            shell.get().setSize(700, 400);
            for (int i = 0; i < 8; i++) {
                model.addUserMessage("Frage " + i);
                model.beginAssistantMessage();
                model.appendAssistantDelta("## Antwort " + i + "\n\nEin längerer Absatz mit Inhalt, damit der Verlauf "
                        + "deutlich höher wird als das Fenster. Noch ein Satz. Und noch einer.\n\n- Punkt eins\n"
                        + "- Punkt zwei\n");
                model.completeAssistantMessage();
            }
            layout(shell.get());
        });
        Edt.flush(); // gebündelte Aktualisierung und das Rollen ans Ende (invokeLater)
        Edt.run(() -> layout(shell.get()));
        Edt.flush();
        Edt.run(() -> {
            JScrollBar bar = shell.get().transcript().scrollPane().getVerticalScrollBar();
            assertTrue("Verlauf höher als das Fenster", bar.getMaximum() > bar.getVisibleAmount());
            assertTrue("am Ende: " + bar.getValue() + "+" + bar.getVisibleAmount() + " von " + bar.getMaximum(),
                    bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 2);
        });
    }

    private static void layout(Component component) {
        for (int pass = 0; pass < 3; pass++) {
            layoutTree(component);
        }
    }

    private static void layoutTree(Component component) {
        if (component instanceof Container) {
            ((Container) component).doLayout();
            for (Component child : ((Container) component).getComponents()) {
                layoutTree(child);
            }
        }
    }
}
