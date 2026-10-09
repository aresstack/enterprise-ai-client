package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.app.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.app.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.app.ui.workspace.ShellFrame;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.domain.chat.ChatRole;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;

import javax.swing.SwingUtilities;
import java.util.List;

/**
 * Lokaler Start der Shell ohne Server: {@code ./gradlew :app-swing:runChatDemo}. Die echte Kette
 * Shell → {@link ChatServiceBinding} → {@link ChatService} → Fake-{@link ChatCompletionPort} (Strang A); der Fake
 * streamt die Antwort wortweise, Stop bricht ab, "fehler" im Text simuliert HTTP 500. Liegt bewusst im
 * Testumfang, die echte Composition Root folgt in AP23.
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
                ChatService service = new ChatService(new ScriptedDemoPort(new FakeChatCompletionPort(80L)));
                ChatShellModel model = new ChatShellModel(System::currentTimeMillis);
                ChatServiceBinding binding = new ChatServiceBinding(service, service.openConversation(), model,
                        SwingUtilities::invokeLater);
                ChatShellPanel shell = new ChatShellPanel(model, binding, palette,
                        BubblePalette.windowsPhoneInspired());
                ChatWorkspacePanel workspace = new ChatWorkspacePanel(new ShellModeModel(false), shell, null, palette);
                ShellFrame.create("Enterprise AI Client – Demo", workspace, palette).setVisible(true);
            }
        });
    }

    /** Legt dem Fake vor jeder Anfrage eine wortweise Antwort auf die letzte Nutzernachricht ins Skript. */
    private static final class ScriptedDemoPort implements ChatCompletionPort {

        private final FakeChatCompletionPort fake;

        ScriptedDemoPort(FakeChatCompletionPort fake) {
            this.fake = fake;
        }

        @Override
        public ChatResponse complete(ChatRequest request) {
            script(request);
            return fake.complete(request);
        }

        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            script(request);
            return fake.stream(request, listener);
        }

        private void script(ChatRequest request) {
            String question = lastUserText(request.messages());
            if (question.toLowerCase().contains("fehler")) {
                fake.enqueueError(new ChatCompletionException(ChatErrorKind.PROVIDER_ERROR, 500,
                        "internal_error", null));
                return;
            }
            String[] words = ("Das ist eine gestreamte Demo-Antwort auf: \"" + question + "\". Jedes Wort kommt "
                    + "als eigenes Delta, damit man die Streaming-Aktualisierung der Sprechblase sieht. Der "
                    + "Verlauf umfasst gerade " + request.messages().size() + " Nachrichten.").split(" ");
            String[] deltas = new String[words.length];
            for (int i = 0; i < words.length; i++) {
                deltas[i] = (i == 0 ? "" : " ") + words[i];
            }
            fake.enqueueAnswer(deltas);
        }

        private static String lastUserText(List<ChatMessage> messages) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i).role() == ChatRole.USER) {
                    return messages.get(i).content();
                }
            }
            return "";
        }
    }
}
