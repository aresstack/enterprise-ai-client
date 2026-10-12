package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchControlTools;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationPorts;
import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;
import com.aresstack.enterpriseai.proasteion.application.config.AppPaths;
import com.aresstack.enterpriseai.proasteion.application.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.domain.state.ResearchCommandType;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.io.File;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Abnahmeszenario über die echte Verdrahtung (Composition Root + Shell, headless, Fakes statt Adapter): gespeicherten
 * Chat A öffnen, danach Chat B → keinerlei Research-Kontext von A in B; Neuer Chat, Wechsel A → B → A, Löschen des
 * offenen Chats samt Research-Projekt beim Beenden.
 */
public class ShellConversationLifecycleTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private final FakeChatCompletionPort chatPort = new FakeChatCompletionPort(0L);
    private String previousConfig;
    private File home;

    @Before
    public void isolateConfigDirectory() throws Exception {
        previousConfig = System.getProperty(AppPaths.CONFIG_PROPERTY);
        home = temp.newFolder("home");
        System.setProperty(AppPaths.CONFIG_PROPERTY, new File(home, "client.properties").getAbsolutePath());
    }

    @After
    public void restoreConfigDirectory() {
        if (previousConfig == null) {
            System.clearProperty(AppPaths.CONFIG_PROPERTY);
        } else {
            System.setProperty(AppPaths.CONFIG_PROPERTY, previousConfig);
        }
    }

    @Test
    public void chatAgentAndResearchProjectSwitchTogether() throws Exception {
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        final CompositionRoot root = CompositionRoot.compose(TestConfigs.withoutAgent(temp.newFolder("index").toPath()),
                ApplicationPorts.builder().chat(chatPort).embeddings(embeddings, embeddings.modelIdentity())
                        .index(new InMemoryKnowledgeIndex())
                        .sources(new KnowledgeSourceCatalog(Collections.<KnowledgeSourceRegistration>emptyList()))
                        .secrets(new UnavailableSecretProvider("Test ohne KeePass")).build(),
                SwingUtilities::invokeLater, System::currentTimeMillis, ZoneId.of("Europe/Berlin"));
        final ResearchAgentHost host = root.researchHost();
        final ShellAssembly.ShellView view = onEdt(() -> ShellAssembly.createShell(root,
                ComicPalette.defaultPalette(), BubblePalette.windowsPhoneInspired()));
        File research = new File(home, "research");

        // Chat A: Projekt gehört zur Unterhaltung, ohne Agent-Turn noch ohne Ordner
        final String chatA = onEdt(() -> view.conversation().value());
        assertEquals(chatA, host.chatId());
        ask(view, "Frage A", "Antwort A");
        fill(host, "Karte A", "page-a", "Quelle A");
        assertTrue(new File(research, chatA).isDirectory());

        // Neuer Chat (B): neues, leeres Projekt
        onEdt(() -> {
            view.workspace().newChatButton().doClick();
            return null;
        });
        final String chatB = onEdt(() -> view.conversation().value());
        assertFalse(chatA.equals(chatB));
        assertEquals(chatB, host.chatId());
        assertTrue(host.sourceRefs().isEmpty());
        assertFalse(concept(host).contains("Karte A"));
        ask(view, "Frage B", "Antwort B");
        fill(host, "Karte B", "page-b", "Quelle B");

        // gespeicherten Chat A öffnen: Verlauf und Projekt von A
        assertTrue(onEdt(() -> view.lifecycle().openChat(chatA)));
        assertEquals(chatA, host.chatId());
        assertEquals("Quelle A", host.sourceRefs().get(0).title());
        assertTrue(concept(host).contains("Karte A"));
        assertFalse(concept(host).contains("Karte B"));
        assertEquals("Frage A", onEdt(() -> view.chatModel().getEntries().get(0).getText()));
        // chat_history liest den gespeicherten Verlauf genau dieses Chats
        String history = chatHistory(host);
        assertTrue(history, history.contains("[user] Frage A"));
        assertTrue(history, history.contains("[assistant] Antwort A"));
        assertFalse(history, history.contains("Frage B"));

        // danach Chat B: keinerlei Research-Kontext von A
        assertTrue(onEdt(() -> view.lifecycle().openChat(chatB)));
        assertEquals(chatB, host.chatId());
        assertEquals(1, host.sourceRefs().size());
        assertEquals("Quelle B", host.sourceRefs().get(0).title());
        assertFalse(concept(host).contains("Karte A"));

        // den offenen Chat B löschen: sofort ein neuer, leerer Kontext; beim Beenden ist das Projekt weg
        assertTrue(onEdt(() -> view.lifecycle().deleteChat(chatB)));
        String fresh = onEdt(() -> view.conversation().value());
        assertFalse(chatB.equals(fresh));
        assertEquals(fresh, host.chatId());
        assertTrue(host.sourceRefs().isEmpty());
        assertNull(host.existingConceptService());
        view.lifecycle().purgeDeleted();
        assertFalse(new File(research, chatB).exists());
        assertFalse(new File(new File(home, "chats"), chatB + ".json").exists());
        assertTrue(new File(research, chatA).isDirectory());
    }

    private void ask(final ShellAssembly.ShellView view, String question, String answer) throws Exception {
        chatPort.enqueueAnswer("{\"intent\": \"QUESTION\", \"informationNeed\": \"GENERAL_KNOWLEDGE\", "
                + "\"confidence\": 0.9, \"nextAction\": \"ANSWER_DIRECTLY\"}"); // the answer plan
        chatPort.enqueueAnswer(answer);
        onEdt(() -> {
            view.chatActions().sendRequested(question, false);
            return null;
        });
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            TranscriptEntry last = onEdt(() -> {
                java.util.List<TranscriptEntry> entries = view.chatModel().getEntries();
                return entries.isEmpty() ? null : entries.get(entries.size() - 1);
            });
            if (last != null && last.getAuthor() == TranscriptEntry.Author.ASSISTANT
                    && last.getState() == TranscriptEntry.State.COMPLETE) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("keine Antwort auf " + question);
    }

    private static void fill(ResearchAgentHost host, String card, String page, String title) {
        host.markGreeted();
        assertTrue(host.existingConceptService().addCards(Collections.<String>emptyList(),
                Collections.singletonList(card)).isApplied());
        assertNull(host.dispatch(ResearchCommandType.SUBMIT_SCOPE));
        assertNull(host.dispatch(ResearchCommandType.START_RESEARCH));
        assertTrue(host.acceptSource(page, title, "").startsWith("source_id="));
    }

    private static String chatHistory(ResearchAgentHost host) {
        for (McpToolContribution tool : ResearchControlTools.toolsFor(host, new MainModelChat() {
            @Override
            public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxTokens) {
                return MainModelChatResult.ok("");
            }

            @Override
            public String modelName() {
                return "fake";
            }
        })) {
            if (tool.getName().equals(ResearchControlTools.CHAT_HISTORY)) {
                Map<String, Object> args = new HashMap<String, Object>();
                args.put("raw", "true");
                return tool.getHandler().invoke(new McpToolCall(tool.getName(), args)).getText();
            }
        }
        throw new AssertionError("chat_history fehlt im Werkzeugsatz");
    }

    private static String concept(ResearchAgentHost host) {
        ConceptBranchService service = host.existingConceptService();
        return service == null ? "" : service.snapshot().getDocumentJson();
    }

    private static <T> T onEdt(final Callable<T> work) throws Exception {
        final AtomicReference<T> result = new AtomicReference<T>();
        final AtomicReference<Exception> failure = new AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }
}
