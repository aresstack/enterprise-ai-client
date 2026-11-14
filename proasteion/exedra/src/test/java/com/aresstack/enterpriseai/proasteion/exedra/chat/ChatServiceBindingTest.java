package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * AP4 "fertig wenn": die Chat-UI funktioniert vollständig gegen den Fake-ChatCompletionPort von Strang A,
 * über den echten {@link ChatService} – Streaming, Historie, Stop und Fehler, mit EDT-Übergabe.
 */
public class ChatServiceBindingTest {

    private static final Executor EDT = new Executor() {
        @Override
        public void execute(Runnable command) {
            SwingUtilities.invokeLater(command);
        }
    };

    private final FakeChatCompletionPort port = new FakeChatCompletionPort();
    private final ChatService service = new ChatService(port);
    private final ChatConversationId conversation = service.openConversation("Antworte knapp.");

    @Test
    public void streamedAnswerReachesTheBubblesAndTheHistory() throws Exception {
        port.enqueueAnswer("", "Hallo", ", ", "Welt");
        final Fixture fixture = onEdt(new Callable<Fixture>() {
            @Override
            public Fixture call() {
                Fixture f = new Fixture();
                f.shell.composer();
                f.binding.sendRequested("Erkläre REST.", false);
                return f;
            }
        });

        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = onEdt(new Callable<List<TranscriptEntry>>() {
            @Override
            public List<TranscriptEntry> call() {
                return fixture.model.getEntries();
            }
        });
        assertEquals(2, entries.size());
        assertEquals("Erkläre REST.", entries.get(0).getText());
        assertEquals("Hallo, Welt", entries.get(1).getText());
        assertEquals(TranscriptEntry.State.COMPLETE, entries.get(1).getState());

        List<ChatMessage> history = service.conversation(conversation).messages();
        assertEquals(ChatRole.USER, history.get(history.size() - 2).role());
        assertEquals("Hallo, Welt", history.get(history.size() - 1).content());
        assertEquals("Antworte knapp.", port.lastRequest().messages().get(0).content());
    }

    @Test
    public void stopCancelsTheTurnAndKeepsThePartialAnswerOutOfTheHistory() throws Exception {
        port.enqueueHanging("Ich überlege");
        final Fixture fixture = onEdt(new Callable<Fixture>() {
            @Override
            public Fixture call() {
                Fixture f = new Fixture();
                f.binding.sendRequested("Lange Frage", false);
                return f;
            }
        });
        awaitText(fixture.model, "Ich überlege");
        assertTrue(onEdt(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return fixture.model.canStop();
            }
        }));

        onEdt(new Callable<Void>() {
            @Override
            public Void call() {
                fixture.binding.stopRequested();
                return null;
            }
        });
        awaitIdle(fixture.model);

        TranscriptEntry answer = onEdt(new Callable<TranscriptEntry>() {
            @Override
            public TranscriptEntry call() {
                return fixture.model.getEntries().get(1);
            }
        });
        assertEquals(TranscriptEntry.State.CANCELLED, answer.getState());
        assertEquals("Ich überlege", answer.getText());
        List<ChatMessage> history = service.conversation(conversation).messages();
        assertEquals("nur die Nutzernachricht", ChatRole.USER, history.get(history.size() - 1).role());
        assertFalse(service.isBusy(conversation));
    }

    @Test
    public void portFailureBecomesAReadableErrorBubble() throws Exception {
        port.enqueueError(new ChatCompletionException(ChatErrorKind.PROVIDER_ERROR, 500, "internal_error", null));
        final Fixture fixture = onEdt(new Callable<Fixture>() {
            @Override
            public Fixture call() {
                Fixture f = new Fixture();
                f.binding.sendRequested("Frage", true);
                return f;
            }
        });
        awaitIdle(fixture.model);

        TranscriptEntry answer = onEdt(new Callable<TranscriptEntry>() {
            @Override
            public TranscriptEntry call() {
                return fixture.model.getEntries().get(1);
            }
        });
        assertEquals(TranscriptEntry.State.FAILED, answer.getState());
        assertEquals("Fehler im KI-Dienst. (Status 500)\nTechnische Ursache: internal_error",
                answer.getFailureMessage());
    }

    /**
     * Issue #223: Endet die Antwort am Ausgabelimit ({@code finish_reason = length}), bleibt der Teiltext sichtbar und
     * in der Historie, die Blase ist unvollständig, ein Hinweis steht darunter, und es folgt keine zweite Anfrage.
     */
    @Test
    public void answerCutAtTheTokenLimitIsShownAsIncomplete() throws Exception {
        port.enqueueAnswerEnding(ChatFinishReason.LENGTH, "<svg>", "<path d=\"M0");
        final Fixture fixture = onEdt(new Callable<Fixture>() {
            @Override
            public Fixture call() {
                Fixture f = new Fixture();
                f.binding.sendRequested("Zeichne eine Pfingstrose.", true);
                return f;
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = onEdt(new Callable<List<TranscriptEntry>>() {
            @Override
            public List<TranscriptEntry> call() {
                return fixture.model.getEntries();
            }
        });
        assertEquals(3, entries.size());
        assertEquals("<svg><path d=\"M0", entries.get(1).getText());
        assertEquals(TranscriptEntry.State.COMPLETE, entries.get(1).getState());
        assertTrue(entries.get(1).isIncomplete());
        assertEquals(TranscriptEntry.Author.NOTICE, entries.get(2).getAuthor());
        assertTrue(entries.get(2).getText(), entries.get(2).getText().startsWith(AnswerEnd.LENGTH_NOTICE));
        assertEquals("keine automatische zweite Anfrage", 1, port.requests().size());
        List<ChatMessage> history = service.conversation(conversation).messages();
        assertEquals("<svg><path d=\"M0", history.get(history.size() - 1).content());
    }

    @Test
    public void regularStopIsNotMarkedIncomplete() throws Exception {
        port.enqueueAnswerEnding(ChatFinishReason.STOP, "fertig");
        final Fixture fixture = onEdt(new Callable<Fixture>() {
            @Override
            public Fixture call() {
                Fixture f = new Fixture();
                f.binding.sendRequested("Frage", true);
                return f;
            }
        });
        awaitIdle(fixture.model);
        List<TranscriptEntry> entries = onEdt(new Callable<List<TranscriptEntry>>() {
            @Override
            public List<TranscriptEntry> call() {
                return fixture.model.getEntries();
            }
        });
        assertEquals(2, entries.size());
        assertFalse(entries.get(1).isIncomplete());
    }

    @Test
    public void noticeNamesTheTokensWhenTheServiceReportsThem() {
        ChatResponse cut = new ChatResponse(ChatMessage.assistant("x"), ChatFinishReason.LENGTH,
                ChatUsage.of(10, 8192, 8202), "m");
        assertEquals(AnswerEnd.LENGTH_NOTICE + " (8192 Tokens)" + AnswerEnd.LENGTH_ADVICE,
                AnswerEnd.incompleteNotice(cut));
        assertEquals(AnswerEnd.FILTER_NOTICE, AnswerEnd.incompleteNotice(new ChatResponse(
                ChatMessage.assistant("x"), ChatFinishReason.CONTENT_FILTER, null, null)));
        assertEquals(null, AnswerEnd.incompleteNotice(new ChatResponse(ChatMessage.assistant("x"),
                ChatFinishReason.UNKNOWN, null, null)));
    }

    @Test
    public void followUpQuestionsCarryTheHistory() throws Exception {
        port.enqueueAnswer("Erste Antwort").enqueueAnswer("Zweite Antwort");
        final Fixture fixture = onEdt(new Callable<Fixture>() {
            @Override
            public Fixture call() {
                Fixture f = new Fixture();
                f.binding.sendRequested("Eins", false);
                return f;
            }
        });
        awaitIdle(fixture.model);
        onEdt(new Callable<Void>() {
            @Override
            public Void call() {
                fixture.binding.sendRequested("Zwei", false);
                return null;
            }
        });
        awaitIdle(fixture.model);

        assertEquals(4, onEdt(new Callable<Integer>() {
            @Override
            public Integer call() {
                return fixture.model.getEntries().size();
            }
        }).intValue());
        // System + Eins + Erste Antwort + Zwei
        assertEquals(4, port.lastRequest().messages().size());
    }

    @Test
    public void errorTextsNameTheKindThenTheCauseAndMaskAnythingTokenLike() {
        assertEquals("Anmeldung am KI-Dienst fehlgeschlagen. (Status 401)\nTechnische Ursache: HTTP 401: Bearer ***",
                ChatServiceBinding.describe(new ChatCompletionException(ChatErrorKind.AUTHENTICATION, 401,
                        "HTTP 401: Bearer abc", null)));
        assertEquals("Der KI-Dienst ist nicht erreichbar.\nTechnische Ursache: connect timed out",
                ChatServiceBinding.describe(new ChatCompletionException(ChatErrorKind.TRANSPORT, "connect timed out")));
        assertEquals("Der KI-Dienst ist nicht erreichbar.", ChatServiceBinding.headline(
                new ChatCompletionException(ChatErrorKind.TRANSPORT, "connect timed out")));
    }

    /** Regression Erststart gegen die echte API: "nicht erreichbar" nennt jetzt Ursache (TLS) und Hinweis. */
    @Test
    public void certificateProblemsShowCauseAndRemedyInTheBubble() {
        javax.net.ssl.SSLHandshakeException handshake = new javax.net.ssl.SSLHandshakeException(
                "PKIX path building failed: unable to find valid certification path to requested target");
        String text = ChatServiceBinding.describe(new ChatCompletionException(ChatErrorKind.TRANSPORT,
                "connection to ki.example failed: SSLHandshakeException", handshake));
        String[] lines = text.split("\n");
        assertEquals(3, lines.length);
        assertEquals("Der KI-Dienst ist nicht erreichbar.", lines[0]);
        assertTrue(lines[1], lines[1].startsWith(ChatServiceBinding.DETAIL_PREFIX
                + "connection to ki.example failed: SSLHandshakeException | SSLHandshakeException: PKIX"));
        assertTrue(lines[2], lines[2].startsWith(ChatServiceBinding.HINT_PREFIX + "Java vertraut dem Zertifikat"));
        String keePass = ChatServiceBinding.describe(new ChatCompletionException(ChatErrorKind.AUTHENTICATION,
                "token source failed: SecretAccessException"));
        assertTrue(keePass, keePass.contains(ChatServiceBinding.HINT_PREFIX + "Der API-Key konnte nicht aus KeePass"));
    }

    /** Ein KeePass-Aussetzer nach allen Wiederholungen ist kein falscher API-Key; ein echtes 401 bleibt Anmeldefehler. */
    @Test
    public void transientKeePassFailureIsNotShownAsLoginFailure() {
        String text = ChatServiceBinding.describe(new ChatCompletionException(ChatErrorKind.CREDENTIALS_UNAVAILABLE,
                "token source temporarily unavailable: SecretAccessException"));
        String[] lines = text.split("\n");
        assertEquals("Der API-Key konnte nach mehreren Versuchen nicht aus KeePass gelesen werden. KeePassRPC war "
                + "vorübergehend nicht verfügbar.", lines[0]);
        assertFalse(text, text.contains("Anmeldung am KI-Dienst fehlgeschlagen"));
        assertTrue(lines[2], lines[2].startsWith(ChatServiceBinding.HINT_PREFIX + "KeePassRPC hat auch nach"));
        assertTrue(ChatServiceBinding.headline(new ChatCompletionException(ChatErrorKind.AUTHENTICATION, 401,
                "HTTP 401", null)).startsWith("Anmeldung am KI-Dienst fehlgeschlagen."));
    }

    private final class Fixture {
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        final ChatServiceBinding binding = new ChatServiceBinding(service, conversation, model, EDT);
        final ChatShellPanel shell = new ChatShellPanel(model, binding, ComicPalette.defaultPalette(),
                BubblePalette.windowsPhoneInspired());
    }

    private static void awaitIdle(final ChatShellModel model) throws Exception {
        await(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return !model.isStreaming();
            }
        });
    }

    private static void awaitText(final ChatShellModel model, final String text) throws Exception {
        await(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                List<TranscriptEntry> entries = model.getEntries();
                return !entries.isEmpty() && entries.get(entries.size() - 1).getText().equals(text);
            }
        });
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (onEdt(condition)) {
                return;
            }
            Thread.sleep(10L);
        }
        fail("Bedingung nicht innerhalb von 5 s erfüllt");
    }

    private static <T> T onEdt(final Callable<T> callable) throws Exception {
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
}
