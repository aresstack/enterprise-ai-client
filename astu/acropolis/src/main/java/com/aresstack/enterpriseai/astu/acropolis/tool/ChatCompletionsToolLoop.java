package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.astu.chat.ChatImage;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatToolCall;
import com.aresstack.enterpriseai.astu.chat.ChatToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Die Werkzeug-Schleife über {@code /chat/completions} (gerouteter {@link ChatCompletionPort}): die Anfrage geht mit den Werkzeugen der
 * {@link ToolRegistry} als {@code tools} hinaus; solange die Antwort {@code tool_calls} enthält, führt die Schleife
 * sie aus, hängt Aufruf (assistant) und Ergebnis ({@code role=tool}) an den Verlauf dieser Anfrage und fragt erneut,
 * höchstens {@code maxRounds} Mal. Entscheidend sind die gelieferten {@code tool_calls}, nicht {@code finish_reason};
 * fehlt einem Aufruf die {@code id}, vergibt die Schleife {@code call_<runde>_<index>}. Verlangt das Modell nach
 * {@code maxRounds} Runden noch ein Werkzeug, führt die Schleife es nicht mehr aus, sondern fragt ein letztes Mal ohne
 * {@code tools} mit {@link #FINAL_ROUND_INSTRUCTION} (Abschlussrunde): die bisherigen Ergebnisse werden zur Antwort,
 * statt mit den schon bezahlten Suchen verworfen zu werden. Erst wenn auch diese Runde keinen Text liefert, endet
 * die Anfrage mit einem Fehler; die Benutzeranfrage wird nie von vorn wiederholt. Über {@link #stream} läuft
 * jede Runde gestreamt ({@code stream:true}, die {@code tool_calls} setzt der Adapter aus den Deltas zusammen, wie
 * MainframeMate {@code CloudChatManager}): Text erscheint sofort in der Blase, auch die Schlussantwort;
 * {@link #complete} fragt ohne Streaming.
 *
 * <p>Werkzeugaufrufe und -ergebnisse bleiben in dieser Anfrage; in die Historie kommt nur die fertige Antwort.
 * Unbekannte Werkzeuge und Werkzeugfehler gehen als Text an das Modell zurück. Ein Abbruch meldet sofort
 * {@code onCancelled}; eine noch laufende HTTP-Anfrage endet im Hintergrund, ihr Ergebnis wird verworfen.
 */
public final class ChatCompletionsToolLoop implements ChatCompletionPort {

    /** Höchstzahl der Werkzeugrunden je Anfrage. */
    public static final int DEFAULT_MAX_ROUNDS = 8;
    /** Kleinste und größte einstellbare Zahl der Werkzeugrunden ({@code chat.tools.maxRounds}); 0 gibt es nicht. */
    public static final int MIN_MAX_ROUNDS = 1;
    public static final int MAX_MAX_ROUNDS = 32;

    /**
     * Hinweis der Abschlussrunde, als Nachricht des Benutzers (eine {@code system}-Nachricht mitten im Verlauf lehnen
     * manche Chat-Vorlagen ab, {@code developer} manche Server).
     */
    public static final String FINAL_ROUND_INSTRUCTION = "[Hinweis des Clients] Das Werkzeugbudget ist erschöpft. "
            + "Verwende ausschließlich die bereits vorliegenden Werkzeugergebnisse. Führe keine weiteren Recherchen "
            + "durch und gib jetzt die bestmögliche abschließende Antwort auf die ursprüngliche Benutzerfrage. Weise auf "
            + "fehlende Informationen hin, statt weitere Werkzeuge anzufordern.";

    /** Text of the user message that shows the images of tool results to the model. */
    static final String IMAGE_NOTE = "[Hinweis des Clients] Bild aus dem letzten Werkzeugergebnis (Bildschirmfoto). "
            + "Markierte Bedienelemente tragen ihre Id (z. B. ctl-3); bediene sie über diese Id.";
    static final String IMAGE_REPLACED_NOTE = "[Hinweis des Clients] Älteres Bildschirmfoto entfernt; es gilt das neueste.";
    static final String IMAGE_REFUSED_NOTE = "[Hinweis des Clients] Das Modell nimmt keine Bilder an; das "
            + "Bildschirmfoto ist entfernt. Nutze die Beschreibung im Werkzeugergebnis.";

    private final ChatCompletionPort chat;
    private final ToolRegistry tools;
    private final Executor executor;
    private final ToolActivityListener activity;
    private final int maxRounds;

    public ChatCompletionsToolLoop(ChatCompletionPort chat, ToolRegistry tools, Executor executor,
                                   ToolActivityListener activity, int maxRounds) {
        if (chat == null || tools == null || executor == null) {
            throw new IllegalArgumentException("chat, tools and executor must not be null");
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds must be positive");
        }
        this.chat = chat;
        this.tools = tools;
        this.executor = executor;
        this.activity = activity;
        this.maxRounds = maxRounds;
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        return run(request, null);
    }

    @Override
    public ChatTask stream(final ChatRequest request, final ChatStreamListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        final ToolLoopTask task = new ToolLoopTask(listener);
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (task.isCancelRequested()) {
                        task.finish(null, null);
                        return;
                    }
                    listener.onStart();
                    try {
                        ChatResponse response = ChatCompletionsToolLoop.this.run(request, task);
                        task.finish(response, null);
                    } catch (ChatCompletionException e) {
                        task.finish(null, e);
                    } catch (RuntimeException e) {
                        task.finish(null, new ChatCompletionException(ChatErrorKind.PROTOCOL,
                                "tool loop failed: " + e.getClass().getSimpleName(), e));
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            task.finish(null, new ChatCompletionException(ChatErrorKind.TRANSPORT, "no thread available", e));
        }
        return task;
    }

    /** Die Werkzeuge, die jetzt aktiv sind, im Format des Chat-Ports. */
    private List<ChatToolDefinition> definitions() {
        List<ChatToolDefinition> definitions = new ArrayList<ChatToolDefinition>();
        for (ToolDefinition definition : tools.definitions()) {
            definitions.add(new ChatToolDefinition(definition.name(), definition.description(),
                    definition.parametersSchema()));
        }
        return definitions;
    }

    /**
     * Shows the images of a round's tool results (screenshots) to the model: tool messages carry only text, so they
     * follow as one user message right after the results. Only the newest images stay in the request; earlier ones
     * are replaced by a short note, so a series of screenshots does not grow every following round.
     */
    private static void showImages(List<ChatMessage> messages, List<ChatImage> images) {
        dropImages(messages, IMAGE_REPLACED_NOTE);
        messages.add(ChatMessage.userWithImages(IMAGE_NOTE, images));
    }

    /** Replaces every image message of {@code messages} after the original request by {@code note}. */
    private static boolean dropImages(List<ChatMessage> messages, String note) {
        boolean dropped = false;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (IMAGE_NOTE.equals(message.content()) && !message.images().isEmpty()) {
                messages.set(i, ChatMessage.user(note));
                dropped = true;
            }
        }
        return dropped;
    }

    /**
     * Asks the next round. When it carries tool images and the server rejects the request as invalid (a model
     * without image input), the images are replaced by a note and the round is asked once more; images stay off for
     * the rest of this request, the tool texts describe what they showed.
     */
    private ChatResponse askShowingImages(List<ChatMessage> messages, ChatRequest request, ToolLoopTask task,
                                          boolean[] imagesRefused) {
        try {
            return ask(new ChatRequest(messages, request.options(), definitions()), task);
        } catch (ChatCompletionException e) {
            if (e.kind() != ChatErrorKind.INVALID_REQUEST || !dropImages(messages, IMAGE_REFUSED_NOTE)) {
                throw e;
            }
            imagesRefused[0] = true;
            return ask(new ChatRequest(messages, request.options(), definitions()), task);
        }
    }

    private ChatResponse run(ChatRequest request, ToolLoopTask task) {
        List<ChatMessage> messages = new ArrayList<ChatMessage>(request.messages());
        ChatResponse response = ask(new ChatRequest(messages, request.options(), definitions()), task);
        if (response == null) {
            return null;
        }
        int round = 0;
        boolean[] imagesRefused = new boolean[1];
        while (response.message().hasToolCalls()) {
            if (task != null && task.isCancelRequested()) {
                return null;
            }
            if (++round > maxRounds) {
                response = finalRound(messages, request, task);
                if (response == null) {
                    return null;
                }
                break;
            }
            // Ohne id vom Server vergibt die Schleife eine eigene, damit Aufruf und Ergebnis zusammenpassen (ein
            // strenger Server lehnt eine leere tool_call_id ab).
            List<ChatToolCall> calls = new ArrayList<ChatToolCall>();
            for (int i = 0; i < response.message().toolCalls().size(); i++) {
                ChatToolCall call = response.message().toolCalls().get(i);
                calls.add(call.id().isEmpty()
                        ? new ChatToolCall("call_" + round + "_" + i, call.name(), call.argumentsJson()) : call);
            }
            messages.add(ChatMessage.assistantToolCalls(response.message().content(), calls));
            List<ChatImage> images = new ArrayList<ChatImage>();
            for (ChatToolCall call : calls) {
                ToolOutput output = tools.run(call.name(), call.argumentsJson(), activity);
                messages.add(ChatMessage.tool(call.id(), output.text()));
                images.addAll(output.images());
            }
            if (!images.isEmpty() && !imagesRefused[0]) {
                showImages(messages, images);
            }
            if (task != null && task.isCancelRequested()) {
                return null;
            }
            // Je Runde neu: ein inzwischen abgeschaltetes Werkzeug wird nicht mehr angeboten.
            response = askShowingImages(messages, request, task, imagesRefused);
            if (response == null) {
                return null;
            }
        }
        if (task != null) {
            // Der Blase wurde alles gestreamte gezeigt (auch Text vor einem Werkzeugaufruf): die fertige Antwort ist
            // genau dieser Text.
            String shown = task.streamedText();
            if (!shown.equals(response.content())) {
                return new ChatResponse(ChatMessage.assistant(shown), response.finishReason(), response.usage(),
                        response.model());
            }
        }
        return response;
    }

    /**
     * Abschlussrunde nach erschöpftem Werkzeugbudget: der Verlauf mit allen ausgeführten Aufrufen und Ergebnissen (die
     * zuletzt verlangten Aufrufe laufen nicht mehr und bleiben draußen), dazu {@link #FINAL_ROUND_INSTRUCTION}, ohne
     * {@code tools}, sodass keine weitere Werkzeugrunde entstehen kann. Liefert das Modell dennoch Aufrufe, zählt nur
     * sein Text; ohne Text endet die Anfrage mit einem Fehler. {@code null} nach Abbruch.
     */
    private ChatResponse finalRound(List<ChatMessage> messages, ChatRequest request, ToolLoopTask task) {
        List<ChatMessage> closing = new ArrayList<ChatMessage>(messages);
        closing.add(ChatMessage.user(FINAL_ROUND_INSTRUCTION));
        ChatResponse response = ask(new ChatRequest(closing, request.options(), new ArrayList<ChatToolDefinition>()),
                task);
        if (response == null) {
            return null;
        }
        if (response.content().trim().isEmpty()) {
            throw new ChatCompletionException(ChatErrorKind.PROTOCOL, "das Modell hat nach " + maxRounds
                    + " Werkzeugrunden auch in der Abschlussrunde ohne Werkzeuge keine Antwort gegeben");
        }
        if (response.message().hasToolCalls()) {
            return new ChatResponse(ChatMessage.assistant(response.content()), response.finishReason(),
                    response.usage(), response.model());
        }
        return response;
    }

    /**
     * Eine Runde: ohne {@code task} per {@code complete}, sonst gestreamt (Text sofort über {@code task} an die
     * Blase); wartet auf das Ende der Runde. {@code null} nach Abbruch.
     */
    private ChatResponse ask(ChatRequest request, final ToolLoopTask task) {
        if (task == null) {
            return chat.complete(request);
        }
        final CountDownLatch done = new CountDownLatch(1);
        final ChatResponse[] result = new ChatResponse[1];
        final ChatCompletionException[] failure = new ChatCompletionException[1];
        final boolean[] streamed = new boolean[1];
        ChatTask round = chat.stream(request, new ChatStreamListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onDelta(String text) {
                streamed[0] = true;
                task.delta(text);
            }

            @Override
            public void onComplete(ChatResponse response) {
                result[0] = response;
                done.countDown();
            }

            @Override
            public void onError(ChatCompletionException error) {
                failure[0] = error;
                done.countDown();
            }

            @Override
            public void onCancelled() {
                done.countDown();
            }
        });
        task.round(round);
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            round.cancel();
            return null;
        }
        if (failure[0] != null) {
            throw failure[0];
        }
        if (task.isCancelRequested() || result[0] == null) {
            return null;
        }
        if (!streamed[0] && !result[0].content().isEmpty()) {
            task.delta(result[0].content()); // ein Port ohne Deltas: der Text kommt am Stück
        }
        return result[0];
    }
}
