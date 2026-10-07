package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Lokaler Start der Shell ohne Server: {@code ./gradlew :app-swing:runChatDemo}. Ein Fake streamt die Antwort
 * Wort für Wort per Swing-Timer, Stop bricht ab, "fehler" im Text simuliert einen Serverfehler. Liegt bewusst
 * im Testumfang, die echte Composition Root folgt in AP23.
 */
public final class ChatShellDemo {

    private ChatShellDemo() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ComicPalette palette = ComicPalette.defaultPalette();
                ComicTheme.installMenuDefaults(palette);
                ChatShellModel model = new ChatShellModel(System::currentTimeMillis);
                ChatShellPanel shell = new ChatShellPanel(model, new TimerStreamingActions(model), palette,
                        BubblePalette.windowsPhoneInspired());
                ChatWindow.create("Enterprise AI Client – Demo", shell, palette).setVisible(true);
            }
        });
    }

    private static final class TimerStreamingActions implements ChatShellActions {

        private final ChatShellModel model;
        private Timer timer;

        TimerStreamingActions(ChatShellModel model) {
            this.model = model;
        }

        @Override
        public void sendRequested(String text, boolean ragEnabled) {
            model.addUserMessage(text);
            model.beginAssistantMessage();
            final boolean fail = text.toLowerCase().contains("fehler");
            final String[] words = ("Das ist eine gestreamte Demo-Antwort auf: \"" + text + "\". RAG ist "
                    + (ragEnabled ? "an" : "aus") + ". Jedes Wort kommt als eigenes Delta, damit man die "
                    + "Streaming-Aktualisierung der Sprechblase sieht.").split(" ");
            final int[] index = {0};
            timer = new Timer(90, event -> {
                if (index[0] == words.length / 2 && fail) {
                    stopTimer();
                    model.failAssistantMessage("Simulierter Serverfehler (HTTP 500).");
                } else if (index[0] < words.length) {
                    model.appendAssistantDelta((index[0] == 0 ? "" : " ") + words[index[0]++]);
                } else {
                    stopTimer();
                    model.completeAssistantMessage();
                }
            });
            timer.start();
        }

        @Override
        public void stopRequested() {
            stopTimer();
            model.cancelAssistantMessage();
        }

        private void stopTimer() {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
        }
    }
}
