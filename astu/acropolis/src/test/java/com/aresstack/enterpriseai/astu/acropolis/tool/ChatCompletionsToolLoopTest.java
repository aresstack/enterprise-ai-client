package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatImage;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.astu.chat.ChatToolCall;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Eine Werkzeugrunde über /chat/completions: Echo der tool_calls, role=tool mit tool_call_id, Rundenlimit, Abbruch. */
public class ChatCompletionsToolLoopTest {

    private static final Executor DIRECT = Runnable::run;

    private final ScriptedChat chat = new ScriptedChat();
    private final List<String> executed = new ArrayList<String>();
    private final ToolRegistry tools = new ToolRegistry(Collections.<AiTool>singletonList(new AiTool() {
        @Override
        public String name() {
            return "knowledge_read";
        }

        @Override
        public ToolDefinition definition() {
            return ToolDefinition.function("knowledge_read", "liest", "{\"type\":\"object\",\"properties\":{}}");
        }

        @Override
        public String execute(String arguments) {
            executed.add(arguments);
            return "Inhalt";
        }
    }));

    @Test
    public void oneRoundEchoesTheCallAndAnswersWithItsId() {
        chat.script.add(toolCall(""));
        chat.script.add(answer("Fertig"));
        ChatResponse response = loop(8).complete(request());

        assertEquals("Fertig", response.content());
        assertEquals(1, executed.size());
        assertEquals(2, chat.requests.size());
        assertEquals("knowledge_read", chat.requests.get(0).tools().get(0).name());
        List<ChatMessage> second = chat.requests.get(1).messages();
        ChatMessage echo = second.get(second.size() - 2);
        ChatMessage result = second.get(second.size() - 1);
        assertEquals(ChatRole.ASSISTANT, echo.role());
        String id = echo.toolCalls().get(0).id();
        assertEquals("fehlende id wird vergeben", "call_1_0", id);
        assertEquals(ChatRole.TOOL, result.role());
        assertEquals(id, result.toolCallId());
        assertEquals("Inhalt", result.content());
    }

    @Test
    public void serverIdIsKept() {
        chat.script.add(toolCall("abc"));
        chat.script.add(answer("Fertig"));
        loop(8).complete(request());
        List<ChatMessage> second = chat.requests.get(1).messages();
        assertEquals("abc", second.get(second.size() - 1).toolCallId());
    }

    /** Reiter „Tools“ während der Runde: das abgeschaltete Werkzeug fehlt ab der nächsten Anfrage und läuft nicht mehr. */
    @Test
    public void toolSwitchedOffDuringTheRoundIsNeitherOfferedNorRunAgain() {
        final AtomicBoolean active = new AtomicBoolean(true);
        ToolRegistry switchable = new ToolRegistry(Collections.<AiTool>singletonList(new AiTool() {
            @Override
            public String name() {
                return "knowledge_read";
            }

            @Override
            public ToolDefinition definition() {
                return ToolDefinition.function("knowledge_read", "liest", "{\"type\":\"object\",\"properties\":{}}");
            }

            @Override
            public String execute(String arguments) {
                executed.add(arguments);
                active.set(false); // der Benutzer schaltet ab, während das Werkzeug läuft
                return "Inhalt";
            }
        }), name -> active.get());
        chat.script.add(toolCall("a"));
        chat.script.add(toolCall("b"));
        chat.script.add(answer("Fertig"));

        ChatResponse response = new ChatCompletionsToolLoop(chat, switchable, DIRECT, null, 8).complete(request());

        assertEquals("Fertig", response.content());
        assertEquals(1, chat.requests.get(0).tools().size());
        assertTrue("nach dem Abschalten nicht mehr angeboten", chat.requests.get(1).tools().isEmpty());
        assertEquals("nur der erste Aufruf lief", 1, executed.size());
        List<ChatMessage> third = chat.requests.get(2).messages();
        assertTrue(third.get(third.size() - 1).content().startsWith(ToolRegistry.ERROR_PREFIX));
    }

    /** Über stream() läuft jede Runde gestreamt; die Schlussantwort kommt in Stücken, nicht am Stück. */
    @Test
    public void streamDeliversTheFinalAnswerInDeltasAfterToolRounds() throws Exception {
        chat.script.add(toolCall("a"));
        chat.script.add(answer("Fertig"));
        final List<String> deltas = new ArrayList<String>();
        final ChatResponse[] completed = new ChatResponse[1];
        loop(8).stream(request(), new ChatStreamListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onDelta(String text) {
                deltas.add(text);
            }

            @Override
            public void onComplete(ChatResponse response) {
                completed[0] = response;
            }

            @Override
            public void onError(ChatCompletionException error) {
                fail(error.getMessage());
            }

            @Override
            public void onCancelled() {
                fail("cancelled");
            }
        });
        assertEquals(1, executed.size());
        assertEquals("zwei Deltas der Schlussantwort", java.util.Arrays.asList("Fer", "tig"), deltas);
        assertEquals("Fertig", completed[0].content());
        assertEquals("tools auch in der gestreamten Runde", "knowledge_read",
                chat.requests.get(1).tools().get(0).name());
    }

    /** Runden 1–8 verlangen je ein Werkzeug, danach noch eins: keine Ausnahme, sondern Abschlussrunde ohne tools. */
    @Test
    public void afterMaxRoundsAFinalRoundWithoutToolsAnswersFromTheResults() {
        for (int i = 0; i < 9; i++) {
            chat.script.add(toolCall("x" + i));
        }
        chat.script.add(answer("Aus den Ergebnissen"));

        ChatResponse response = loop(8).complete(request());

        assertEquals("Aus den Ergebnissen", response.content());
        assertEquals("der neunte Aufruf läuft nicht mehr", 8, executed.size());
        assertEquals(10, chat.requests.size());
        ChatRequest closing = chat.requests.get(9);
        assertTrue("keine Werkzeuge in der Abschlussrunde", closing.tools().isEmpty());
        List<ChatMessage> messages = closing.messages();
        ChatMessage hint = messages.get(messages.size() - 1);
        assertEquals(ChatRole.USER, hint.role());
        assertEquals(ChatCompletionsToolLoop.FINAL_ROUND_INSTRUCTION, hint.content());
        int calls = 0;
        int results = 0;
        for (ChatMessage message : messages) {
            calls += message.hasToolCalls() ? 1 : 0;
            results += message.role() == ChatRole.TOOL ? 1 : 0;
        }
        assertEquals("alle ausgeführten Aufrufe bleiben im Verlauf", 8, calls);
        assertEquals("alle Ergebnisse bleiben im Verlauf", 8, results);
    }

    /** Auch gestreamt: die Antwort der Abschlussrunde erscheint in der Blase und ist die fertige Antwort. */
    @Test
    public void streamedFinalRoundCompletesTheAnswer() {
        for (int i = 0; i < 3; i++) {
            chat.script.add(toolCall("s" + i));
        }
        chat.script.add(answer("Schluss"));
        final ChatResponse[] completed = new ChatResponse[1];
        final ChatCompletionException[] failed = new ChatCompletionException[1];
        loop(2).stream(request(), new ChatStreamListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onDelta(String text) {
            }

            @Override
            public void onComplete(ChatResponse response) {
                completed[0] = response;
            }

            @Override
            public void onError(ChatCompletionException error) {
                failed[0] = error;
            }

            @Override
            public void onCancelled() {
            }
        });
        assertEquals(null, failed[0]);
        assertEquals("Schluss", completed[0].content());
        assertEquals(2, executed.size());
        assertTrue(chat.requests.get(3).tools().isEmpty());
    }

    /** Liefert auch die Abschlussrunde keinen Text, endet die Anfrage mit einem sauberen Fehler. */
    @Test
    public void failsCleanlyWhenTheFinalRoundGivesNoAnswer() {
        for (int i = 0; i < 4; i++) {
            chat.script.add(toolCall("x" + i));
        }
        chat.script.add(toolCall("trotzdem"));
        try {
            loop(3).complete(request());
            fail("ohne Text in der Abschlussrunde");
        } catch (ChatCompletionException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("3 Werkzeugrunden"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("Abschlussrunde"));
        }
        assertEquals(3, executed.size());
        assertEquals("genau eine Abschlussrunde, kein Neustart", 5, chat.requests.size());
    }

    /** Ein Transportfehler in der Abschlussrunde wird nicht verschluckt und nicht wiederholt. */
    @Test
    public void transportErrorInTheFinalRoundIsReported() {
        for (int i = 0; i < 2; i++) {
            chat.script.add(toolCall("x" + i));
        }
        chat.failOnRequest = 3;
        try {
            loop(1).complete(request());
            fail("Transportfehler");
        } catch (ChatCompletionException expected) {
            assertEquals(com.aresstack.enterpriseai.chat.api.ChatErrorKind.TRANSPORT, expected.kind());
        }
        assertEquals(3, chat.requests.size());
    }

    /** Text neben trotzdem verlangten Aufrufen in der Abschlussrunde zählt als Antwort; Aufrufe laufen nicht. */
    @Test
    public void textOfAFinalRoundWithCallsIsTheAnswer() {
        chat.script.add(toolCall("a"));
        chat.script.add(toolCall("b"));
        chat.script.add(new ChatResponse(ChatMessage.assistantToolCalls("Teilantwort", Collections.singletonList(
                new ChatToolCall("c", "knowledge_read", "{}"))), ChatFinishReason.TOOL_CALLS,
                ChatUsage.notReported(), "m"));

        ChatResponse response = loop(1).complete(request());

        assertEquals("Teilantwort", response.content());
        assertTrue(!response.message().hasToolCalls());
        assertEquals(1, executed.size());
    }

    @Test
    public void cancelBeforeStartReportsCancelled() {
        final List<Runnable> queued = new ArrayList<Runnable>();
        ChatCompletionsToolLoop loop = new ChatCompletionsToolLoop(chat, tools, queued::add, null, 8);
        final List<String> events = new ArrayList<String>();
        ChatTask task = loop.stream(request(), new ChatStreamListener() {
            @Override
            public void onStart() {
                events.add("start");
            }

            @Override
            public void onDelta(String text) {
                events.add("delta");
            }

            @Override
            public void onComplete(ChatResponse response) {
                events.add("complete");
            }

            @Override
            public void onError(ChatCompletionException error) {
                events.add("error");
            }

            @Override
            public void onCancelled() {
                events.add("cancelled");
            }
        });
        task.cancel();
        queued.get(0).run();
        assertEquals(Collections.singletonList("cancelled"), events);
        assertTrue(task.isCancelled());
        assertTrue("kein Modellaufruf nach Abbruch", chat.requests.isEmpty());
    }

    /** A tool that returns a screenshot along with its text description. */
    private final ToolRegistry screenshots = new ToolRegistry(Collections.<AiTool>singletonList(new AiTool() {
        @Override
        public String name() {
            return "browser_screenshot";
        }

        @Override
        public ToolDefinition definition() {
            return ToolDefinition.function("browser_screenshot", "Bild", "{\"type\":\"object\",\"properties\":{}}");
        }

        @Override
        public String execute(String arguments) {
            return executeWithContext(arguments).text();
        }

        @Override
        public ToolOutput executeWithContext(String arguments) {
            executed.add(arguments);
            return new ToolOutput("ctl-1 | search | Suche | oben rechts", null, Collections.singletonList(
                    new ChatImage("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) executed.size()})));
        }
    }));

    private static ChatResponse screenshotCall(String id) {
        return new ChatResponse(ChatMessage.assistantToolCalls("", Collections.singletonList(
                new ChatToolCall(id, "browser_screenshot", "{}"))), ChatFinishReason.TOOL_CALLS,
                ChatUsage.notReported(), "m");
    }

    @Test
    public void screenshotIsShownToTheModelAfterTheToolResultAndOnlyTheNewestStays() {
        chat.script.add(screenshotCall("a"));
        chat.script.add(screenshotCall("b"));
        chat.script.add(answer("ctl-1"));

        ChatResponse response = new ChatCompletionsToolLoop(chat, screenshots, DIRECT, null, 8).complete(request());

        assertEquals("ctl-1", response.content());
        assertEquals(java.util.Arrays.asList(0, 1, 1), chat.imageCounts);
        List<ChatMessage> second = chat.requests.get(1).messages();
        ChatMessage shown = second.get(second.size() - 1);
        assertEquals(ChatRole.USER, shown.role());
        assertEquals(ChatCompletionsToolLoop.IMAGE_NOTE, shown.content());
        assertEquals(ChatRole.TOOL, second.get(second.size() - 2).role());
        assertEquals("ctl-1 | search | Suche | oben rechts", second.get(second.size() - 2).content());
        List<ChatMessage> third = chat.requests.get(2).messages();
        assertEquals(2, third.get(third.size() - 1).images().get(0).bytes()[2]);
    }

    @Test
    public void modelWithoutImageInputGetsTheRoundAgainWithoutTheImage() {
        chat.rejectImages = true;
        chat.script.add(screenshotCall("a"));
        chat.script.add(screenshotCall("b"));
        chat.script.add(answer("Suche oben rechts ist ctl-1"));

        ChatResponse response = new ChatCompletionsToolLoop(chat, screenshots, DIRECT, null, 8).complete(request());

        assertEquals("Suche oben rechts ist ctl-1", response.content());
        // Rejected once, asked again without the image; later screenshots are no longer attached.
        assertEquals(java.util.Arrays.asList(0, 1, 0, 0), chat.imageCounts);
        assertEquals(2, executed.size());
    }

    private ChatCompletionsToolLoop loop(int rounds) {
        return new ChatCompletionsToolLoop(chat, tools, DIRECT, null, rounds);
    }

    private static ChatRequest request() {
        return new ChatRequest(Collections.singletonList(ChatMessage.user("Was steht auf der Seite?")), null);
    }

    private static ChatResponse toolCall(String id) {
        return new ChatResponse(ChatMessage.assistantToolCalls("", Collections.singletonList(
                new ChatToolCall(id, "knowledge_read", "{\"id\":\"wiki://x/Seite\"}"))), ChatFinishReason.TOOL_CALLS,
                ChatUsage.notReported(), "m");
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(ChatMessage.assistant(text), ChatFinishReason.STOP, ChatUsage.notReported(), "m");
    }

    private static final class ScriptedChat implements ChatCompletionPort {
        final LinkedList<ChatResponse> script = new LinkedList<ChatResponse>();
        final List<ChatRequest> requests = new ArrayList<ChatRequest>();
        /** Die wievielte Anfrage (ab 1) mit einem Transportfehler scheitert; 0 = keine. */
        int failOnRequest;
        /** A model without image input: requests with images are rejected as invalid. */
        boolean rejectImages;
        /** Per request the number of images it carried, counted when it was sent. */
        final List<Integer> imageCounts = new ArrayList<Integer>();

        @Override
        public ChatResponse complete(ChatRequest request) {
            requests.add(request);
            int images = 0;
            for (ChatMessage message : request.messages()) {
                images += message.images().size();
            }
            imageCounts.add(images);
            if (rejectImages && images > 0) {
                throw new ChatCompletionException(com.aresstack.enterpriseai.chat.api.ChatErrorKind.INVALID_REQUEST,
                        "image input not supported");
            }
            if (requests.size() == failOnRequest) {
                throw new ChatCompletionException(com.aresstack.enterpriseai.chat.api.ChatErrorKind.TRANSPORT,
                        "503");
            }
            return script.removeFirst();
        }

        /** Streamt die nächste Antwort synchron: Text in zwei Deltas, dann onComplete (mit tool_calls). */
        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            ChatResponse response = complete(request);
            listener.onStart();
            String text = response.content();
            if (!text.isEmpty()) {
                int half = Math.max(1, text.length() / 2);
                listener.onDelta(text.substring(0, half));
                if (half < text.length()) {
                    listener.onDelta(text.substring(half));
                }
            }
            listener.onComplete(response);
            return new ChatTask() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }
}
