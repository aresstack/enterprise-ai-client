package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.app.chat.fakeapi.FakeChatCompletionsServer;
import com.aresstack.enterpriseai.app.composition.ApplicationPorts;
import com.aresstack.enterpriseai.app.composition.CompositionRoot;
import com.aresstack.enterpriseai.app.composition.ShellAssembly;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.app.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;

import static com.aresstack.enterpriseai.integration.SliceSupport.assertNoSecretInTranscript;
import static com.aresstack.enterpriseai.integration.SliceSupport.awaitIdle;
import static com.aresstack.enterpriseai.integration.SliceSupport.awaitTextContaining;
import static com.aresstack.enterpriseai.integration.SliceSupport.entries;
import static com.aresstack.enterpriseai.integration.SliceSupport.lastEntry;
import static com.aresstack.enterpriseai.integration.SliceSupport.onEdt;
import static com.aresstack.enterpriseai.integration.SliceSupport.runOnEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Slice A – Chat: Swing (Composer der Shell) → Application ({@code ChatService}, {@code RagChatUseCase} ohne RAG)
 * → Chat-Port → echter OpenAI-kompatibler HTTP-Adapter → Fake-{@code /chat/completions} mit SSE-Streaming →
 * Sprechblase. Zusammengesetzt über die Composition Root aus AP23 ({@code CompositionRoot}, {@code ShellAssembly}),
 * nur der Chat-Port ist der echte Adapter gegen den lokalen Fake-Server.
 */
public class SliceAChatTest {

    private static final String TOKEN = "slice-a-secret-token-9f3a";
    private static final String MODEL = "openai/gpt-oss-120b";
    private static final String SYSTEM_PROMPT = "Antworte kurz und auf Deutsch.";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private FakeChatCompletionsServer chatServer;
    private CompositionRoot root;
    private ShellAssembly.ShellView view;
    private ChatShellModel model;
    private ChatComposerPanel composer;

    @Before
    public void setUp() throws Exception {
        chatServer = new FakeChatCompletionsServer();
        OpenAiCompatibleChatAdapter chat = new OpenAiCompatibleChatAdapter(OpenAiCompatibleChatConfig
                .builder(chatServer.baseUrl(), MODEL)
                .bearerToken(OpenAiCompatibleChatConfig.TokenSource.fixed(TOKEN))
                .build());
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        ApplicationPorts ports = ApplicationPorts.builder()
                .chat(chat)
                .embeddings(embeddings, embeddings.modelIdentity())
                .index(new InMemoryKnowledgeIndex())
                .secrets(new UnavailableSecretProvider("Slice A läuft ohne KeePass"))
                .build();

        Properties p = new Properties();
        p.setProperty("chat.baseUrl", chatServer.baseUrl().toString());
        p.setProperty("chat.model", MODEL);
        p.setProperty("chat.apiKeyRef", "Enterprise AI API");
        p.setProperty("chat.systemPrompt", SYSTEM_PROMPT);
        p.setProperty("embedding.model", "danielheinz/e5-base-sts-en-de");
        p.setProperty("embedding.dimension", "8");
        p.setProperty("knowledge.indexDirectory", temp.getRoot().toString());
        p.setProperty("knowledge.indexOnStartup", "false");
        p.setProperty("security.keepass.enabled", "false");
        p.setProperty("network.proxy.mode", "NONE");
        root = CompositionRoot.compose(AppConfigLoader.fromProperties(p), ports, SwingUtilities::invokeLater,
                System::currentTimeMillis, ZoneId.of("Europe/Berlin"));
        view = onEdt(() -> ShellAssembly.createShell(root, ComicPalette.defaultPalette(),
                BubblePalette.windowsPhoneInspired()));
        model = view.chatModel();
        composer = view.modalShell().chatShell().composer();
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.shutdown().run();
        }
        if (chatServer != null) {
            chatServer.close();
        }
    }

    /** Wie eine Nutzerin: Text in den Composer, Senden-Knopf. RAG bleibt aus, Slice A ist der reine Chat. */
    private void send(final String text) throws Exception {
        runOnEdt(() -> {
            assertFalse("RAG ist in Slice A nicht Gegenstand", model.isRagEnabled());
            composer.editor().setText(text);
            assertTrue("Senden muss möglich sein", composer.sendButton().isEnabled());
            composer.sendButton().doClick();
        });
    }

    @Test
    public void streamedAnswerFlowsFromTheComposerIntoTheBubble() throws Exception {
        chatServer.answerWith("Die ", "Antwort ", "lautet 42.");
        send("Was ist die Antwort?");
        awaitIdle(model);

        List<TranscriptEntry> transcript = entries(model);
        assertEquals(transcript.toString(), 2, transcript.size());
        assertEquals(TranscriptEntry.Author.USER, transcript.get(0).getAuthor());
        assertEquals("Was ist die Antwort?", transcript.get(0).getText());
        TranscriptEntry answer = transcript.get(1);
        assertEquals(TranscriptEntry.Author.ASSISTANT, answer.getAuthor());
        assertEquals(TranscriptEntry.State.COMPLETE, answer.getState());
        assertEquals("Die Antwort lautet 42.", answer.getText());
        assertFalse(answer.hasSources());

        // Die Anfrage auf der Leitung: Bearer-Token, Streaming im Body, Content-Type nach Nachtrag, Systemprompt.
        FakeChatCompletionsServer.Recorded request = chatServer.lastRequest();
        assertEquals("Bearer " + TOKEN, request.authorization());
        assertTrue("stream=true im Body", request.streamRequested());
        assertEquals("application/json; charset=utf-8", request.contentType());
        assertEquals(MODEL, request.body().get("model").getAsString());
        assertEquals(SYSTEM_PROMPT, request.systemContent());
        List<String> messages = request.messages();
        assertEquals("user: Was ist die Antwort?", messages.get(messages.size() - 1));

        // Die Antwort steht auch in der Historie der Application-Schicht.
        List<ChatMessage> history = root.chatService().conversation(view.conversation()).messages();
        assertEquals("Die Antwort lautet 42.", history.get(history.size() - 1).content());
        assertNoSecretInTranscript(model, TOKEN);
    }

    @Test
    public void stopWhileStreamingLeavesACancelledBubbleWithThePartialText() throws Exception {
        chatServer.answerAndHang("Teil eins ", "Teil zwei ");
        send("Erzähl eine lange Geschichte");
        awaitTextContaining(model, "Teil eins");
        runOnEdt(() -> {
            assertTrue(model.canStop());
            composer.stopButton().doClick();
        });
        awaitIdle(model);

        TranscriptEntry answer = lastEntry(model);
        assertEquals(TranscriptEntry.State.CANCELLED, answer.getState());
        assertTrue(answer.getText(), answer.getText().startsWith("Teil eins"));
        assertFalse(root.chatService().isBusy(view.conversation()));
    }

    @Test
    public void serverErrorBecomesAFailedBubbleWithoutLeakingTheToken() throws Exception {
        chatServer.failWith(500, "{\"error\":{\"message\":\"token " + TOKEN + " was rejected\"}}");
        send("Hallo?");
        awaitIdle(model);

        TranscriptEntry answer = lastEntry(model);
        assertEquals(TranscriptEntry.State.FAILED, answer.getState());
        assertEquals("Fehler im KI-Dienst. (Status 500)", answer.getFailureMessage());
        assertNoSecretInTranscript(model, TOKEN);
        for (ChatMessage message : root.chatService().conversation(view.conversation()).messages()) {
            assertFalse(message.content(), message.content().contains(TOKEN));
        }
    }
}
