package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * {@link StatementVerifier} over the configured chat model: sentence and claim in, one word out (JA, NEIN or
 * UNSICHER). Independent of the extraction request, so the model judges the claim without its own earlier reasoning.
 */
public final class ChatStatementVerifier implements StatementVerifier {

    /** Version of the question; part of {@link #version()}. */
    static final String PROMPT_VERSION = "1";

    static final String INSTRUCTION = "Du prüfst, ob ein Satz eine Aussage ausdrücklich trifft. Maßgeblich ist nur "
            + "der Satz, nicht dein Wissen. Achte auf Rollen: Wer handelt, worauf richtet sich die Handlung? Eine "
            + "Aussage, die der Satz nur verneint, nur als Möglichkeit oder nur unter einer Bedingung trifft, gilt "
            + "nur mit genau dieser Einschränkung. Antworte mit genau einem Wort: JA, NEIN oder UNSICHER.";

    private final ChatCompletionPort chat;
    private final Supplier<String> model;
    private final BooleanSupplier enabled;

    /**
     * @param model   the selected chat model; {@code null} or empty: no check
     * @param enabled whether checks run (read per call)
     */
    public ChatStatementVerifier(ChatCompletionPort chat, Supplier<String> model, BooleanSupplier enabled) {
        if (chat == null || model == null || enabled == null) {
            throw new IllegalArgumentException("chat, model and enabled must not be null");
        }
        this.chat = chat;
        this.model = model;
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        String selected = model.get();
        return enabled.getAsBoolean() && selected != null && !selected.trim().isEmpty();
    }

    @Override
    public String version() {
        return isEnabled() ? "chat-yes-no/" + PROMPT_VERSION + "/" + model.get().trim() : "none";
    }

    @Override
    public Verdict verify(String sentence, String claim) {
        if (!isEnabled()) {
            return Verdict.UNAVAILABLE;
        }
        // No token limit and no sampling options: reasoning models count their thinking against a limit.
        ChatOptions options = ChatOptions.builder().model(model.get().trim()).build();
        String answer;
        try {
            answer = chat.complete(new ChatRequest(Arrays.asList(ChatMessage.system(INSTRUCTION),
                    ChatMessage.user("Satz: " + sentence + "\nAussage: " + claim)), options)).content();
        } catch (RuntimeException unavailable) {
            return Verdict.UNAVAILABLE;
        }
        return parse(answer);
    }

    static Verdict parse(String answer) {
        String word = answer == null ? "" : answer.trim().toUpperCase(Locale.ROOT).replaceAll("^[^A-ZÄÖÜ]+", "");
        if (word.startsWith("JA") || word.startsWith("YES")) {
            return Verdict.YES;
        }
        if (word.startsWith("NEIN") || word.startsWith("NO")) {
            return Verdict.NO;
        }
        return Verdict.UNSURE;
    }
}
