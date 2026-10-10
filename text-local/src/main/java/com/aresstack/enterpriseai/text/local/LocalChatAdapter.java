package com.aresstack.enterpriseai.text.local;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.domain.chat.ChatFinishReason;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.domain.chat.ChatRole;
import com.aresstack.enterpriseai.domain.chat.ChatUsage;
import com.aresstack.enterpriseai.text.runtime.CausalLmRuntime;
import com.aresstack.enterpriseai.text.runtime.DecoderSession;
import com.aresstack.enterpriseai.text.runtime.TextRuntimeException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link ChatCompletionPort} für lokal installierte Chat-Modelle (Decoder als ONNX-Export vom Hugging Face Hub, ohne
 * Umwandlung). Das Modell ist die Modellangabe der Anfrage (Ordnername unter dem Modellverzeichnis). Tokenizer aus
 * {@code tokenizer.json} und Chat-Vorlage aus {@code tokenizer_config.json} bzw. {@code chat_template.jinja} in reinem
 * Java, Rechnen Schritt für Schritt mit KV-Cache über {@link CausalLmRuntime}, Abtastung nach Anfrage bzw.
 * {@code generation_config.json} (ohne Angaben gierig). Es läuft immer nur eine Generierung gleichzeitig; weitere
 * warten.
 */
public final class LocalChatAdapter implements ChatCompletionPort {

    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final String CHATML_START = "<|im_start|>";

    private final Path modelRoot;
    private final CausalLmRuntime runtime;
    private final Semaphore generating = new Semaphore(1, true);
    private final Random random = new Random();
    private LoadedModel loaded;

    public LocalChatAdapter(Path modelRoot, CausalLmRuntime runtime) {
        if (modelRoot == null || runtime == null) {
            throw new IllegalArgumentException("modelRoot and runtime must not be null");
        }
        this.modelRoot = modelRoot;
        this.runtime = runtime;
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        return generate(request, null, new AtomicBoolean());
    }

    @Override
    public ChatTask stream(final ChatRequest request, final ChatStreamListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        final AtomicBoolean cancelled = new AtomicBoolean();
        final CountDownLatch done = new CountDownLatch(1);
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ChatResponse response = generate(request, listener, cancelled);
                    if (cancelled.get()) {
                        listener.onCancelled();
                    } else {
                        listener.onComplete(response);
                    }
                } catch (ChatCompletionException failure) {
                    if (cancelled.get()) {
                        listener.onCancelled();
                    } else {
                        listener.onError(failure);
                    }
                } catch (RuntimeException failure) {
                    listener.onError(new ChatCompletionException(ChatErrorKind.PROVIDER_ERROR,
                            "Lokales Modell: " + failure, failure));
                } finally {
                    done.countDown();
                }
            }
        }, "local-chat");
        worker.setDaemon(true);
        worker.start();
        return new ChatTask() {
            @Override
            public void cancel() {
                cancelled.set(true);
            }

            @Override
            public boolean isDone() {
                return done.getCount() == 0;
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };
    }

    private ChatResponse generate(ChatRequest request, ChatStreamListener listener, AtomicBoolean cancelled) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        ChatOptions options = request.options();
        String modelId = options.model();
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new ChatCompletionException(ChatErrorKind.INVALID_REQUEST, "Kein lokales Chat-Modell gewählt");
        }
        try {
            generating.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ChatCompletionException(ChatErrorKind.TRANSPORT, "Lokale Generierung unterbrochen", interrupted);
        }
        try {
            LoadedModel model = model(modelId.trim());
            int[] prompt = model.tokenizer.encode(model.prompt(request.messages()), false);
            int room = model.contextLength - prompt.length;
            if (room <= 0) {
                throw new ChatCompletionException(ChatErrorKind.INVALID_REQUEST, "Der Verlauf hat " + prompt.length
                        + " Token, das lokale Modell " + modelId + " fasst " + model.contextLength);
            }
            int maxTokens = Math.min(room, options.maxTokens() != null && options.maxTokens() > 0
                    ? options.maxTokens() : DEFAULT_MAX_TOKENS);
            Sampler sampler = model.sampler(options, random);
            if (listener != null) {
                listener.onStart();
            }
            List<Integer> generated = new ArrayList<Integer>();
            String emitted = "";
            ChatFinishReason finish = ChatFinishReason.LENGTH;
            List<String> stops = options.stop() == null ? Collections.<String>emptyList() : options.stop();
            DecoderSession session = runtime.open(model.graph);
            try {
                long[] input = new long[prompt.length];
                for (int i = 0; i < prompt.length; i++) {
                    input[i] = prompt[i];
                }
                float[] logits = session.next(input);
                List<Integer> history = new ArrayList<Integer>(prompt.length + maxTokens);
                for (int id : prompt) {
                    history.add(id);
                }
                while (generated.size() < maxTokens && !cancelled.get()) {
                    int next = sampler.next(logits, history);
                    if (model.endIds.contains(next)) {
                        finish = ChatFinishReason.STOP;
                        break;
                    }
                    generated.add(next);
                    history.add(next);
                    String text = model.tokenizer.decode(toArray(generated), true);
                    int stopAt = stopIndex(text, stops);
                    if (stopAt >= 0) {
                        text = text.substring(0, stopAt);
                    }
                    if (listener != null && !text.endsWith("�") && text.startsWith(emitted)
                            && text.length() > emitted.length()) {
                        listener.onDelta(text.substring(emitted.length()));
                        emitted = text;
                    }
                    if (stopAt >= 0) {
                        finish = ChatFinishReason.STOP;
                        break;
                    }
                    if (generated.size() < maxTokens) {
                        logits = session.next(new long[]{next});
                    }
                }
            } finally {
                session.close();
            }
            String content = model.tokenizer.decode(toArray(generated), true);
            int stopAt = stopIndex(content, stops);
            if (stopAt >= 0) {
                content = content.substring(0, stopAt);
            }
            if (listener != null && content.startsWith(emitted) && content.length() > emitted.length()) {
                listener.onDelta(content.substring(emitted.length()));
            }
            return new ChatResponse(ChatMessage.assistant(content), finish,
                    ChatUsage.of(prompt.length, generated.size(), prompt.length + generated.size()), modelId);
        } catch (TextRuntimeException failure) {
            throw new ChatCompletionException(ChatErrorKind.PROVIDER_ERROR, "Lokales Modell " + modelId + ": "
                    + failure.getMessage(), failure);
        } finally {
            generating.release();
        }
    }

    private static int stopIndex(String text, List<String> stops) {
        int best = -1;
        for (String stop : stops) {
            int index = stop == null || stop.isEmpty() ? -1 : text.indexOf(stop);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static int[] toArray(List<Integer> ids) {
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    private synchronized LoadedModel model(String modelId) {
        if (loaded != null && loaded.id.equals(modelId)) {
            return loaded;
        }
        Path directory;
        try {
            directory = LocalModelFiles.directory(modelRoot, modelId);
        } catch (IllegalArgumentException invalid) {
            throw new ChatCompletionException(ChatErrorKind.INVALID_REQUEST, invalid.getMessage());
        }
        try {
            loaded = new LoadedModel(modelId, directory);
            return loaded;
        } catch (IOException | TokenizerFormatException | RuntimeException unreadable) {
            throw new ChatCompletionException(ChatErrorKind.INVALID_REQUEST, "Lokales Chat-Modell " + modelId
                    + " nicht nutzbar: " + unreadable.getMessage(), unreadable);
        }
    }

    /** Tokenizer, Vorlage, End-Token und Voreinstellungen eines installierten Modells. */
    static final class LoadedModel {
        final String id;
        final Path graph;
        final HfTokenizer tokenizer;
        final ChatTemplate template;
        final Map<String, Object> templateVariables = new LinkedHashMap<String, Object>();
        final Set<Integer> endIds = new LinkedHashSet<Integer>();
        final int contextLength;
        final Map<String, Object> generation;

        LoadedModel(String id, Path directory) throws IOException, TokenizerFormatException {
            this.id = id;
            Map<String, Object> config = Json.read(LocalModelFiles.require(directory, "config.json"));
            this.tokenizer = HfTokenizer.load(LocalModelFiles.require(directory, "tokenizer.json"));
            this.graph = LocalModelFiles.graph(directory);
            Map<String, Object> tokenizerConfig = Json.readIfPresent(directory.resolve("tokenizer_config.json"));
            this.generation = Json.readIfPresent(directory.resolve("generation_config.json"));
            Path templateFile = directory.resolve("chat_template.jinja");
            String source = Files.isRegularFile(templateFile)
                    ? new String(Files.readAllBytes(templateFile), StandardCharsets.UTF_8)
                    : templateSource(tokenizerConfig.get("chat_template"));
            ChatTemplate parsed = null;
            if (source != null) {
                try {
                    parsed = ChatTemplate.parse(source);
                } catch (ChatTemplate.TemplateException unsupported) {
                    parsed = null;
                }
            }
            this.template = parsed;
            String bos = tokenText(tokenizerConfig.get("bos_token"));
            String eos = tokenText(tokenizerConfig.get("eos_token"));
            templateVariables.put("bos_token", bos == null ? "" : bos);
            templateVariables.put("eos_token", eos == null ? "" : eos);
            addIds(generation.get("eos_token_id"));
            addIds(config.get("eos_token_id"));
            if (eos != null && tokenizer.id(eos) != null) {
                endIds.add(tokenizer.id(eos));
            }
            for (String end : new String[]{"<|im_end|>", "<|eot_id|>", "<|end|>", "<end_of_turn>", "<|endoftext|>"}) {
                Integer endId = tokenizer.id(end);
                if (endId != null && tokenizer.isSpecial(endId)) {
                    endIds.add(endId);
                }
            }
            if (endIds.isEmpty()) {
                throw new IllegalArgumentException("kein End-Token in config.json/generation_config.json");
            }
            int positions = Json.integer(config.get("max_position_embeddings"),
                    Json.integer(config.get("n_positions"), 4096));
            int tokenizerLimit = Json.integer(tokenizerConfig.get("model_max_length"), Integer.MAX_VALUE);
            this.contextLength = Math.max(16, Math.min(positions, tokenizerLimit));
        }

        private void addIds(Object value) {
            if (value instanceof Number) {
                endIds.add(((Number) value).intValue());
            }
            for (Object entry : Json.list(value)) {
                if (entry instanceof Number) {
                    endIds.add(((Number) entry).intValue());
                }
            }
        }

        private static String templateSource(Object value) {
            if (value instanceof String) {
                return (String) value;
            }
            String first = null;
            for (Object entry : Json.list(value)) {
                Map<String, Object> named = Json.object(entry);
                String template = Json.text(named.get("template"), null);
                if ("default".equals(named.get("name"))) {
                    return template;
                }
                if (first == null) {
                    first = template;
                }
            }
            return first;
        }

        private static String tokenText(Object value) {
            if (value instanceof String) {
                return (String) value;
            }
            return Json.text(Json.object(value).get("content"), null);
        }

        /** Der Eingabetext nach der Vorlage des Modells, mit Einleitung der Antwort. */
        String prompt(List<ChatMessage> messages) {
            List<Object> rendered = new ArrayList<Object>();
            for (ChatMessage message : messages) {
                rendered.add(message(role(message.role()), message.content()));
            }
            if (template != null) {
                try {
                    return render(rendered);
                } catch (ChatTemplate.TemplateException rejected) {
                    // etwa Gemma: keine System-Rolle → System-Text vor die erste Nutzernachricht
                    List<Object> merged = mergeSystem(rendered);
                    if (merged != null) {
                        try {
                            return render(merged);
                        } catch (ChatTemplate.TemplateException stillRejected) {
                            // weiter mit dem Ersatzformat
                        }
                    }
                }
            }
            return fallback(rendered);
        }

        private String render(List<Object> messages) throws ChatTemplate.TemplateException {
            Map<String, Object> variables = new LinkedHashMap<String, Object>(templateVariables);
            variables.put("messages", messages);
            variables.put("add_generation_prompt", Boolean.TRUE);
            return template.render(variables);
        }

        private static List<Object> mergeSystem(List<Object> messages) {
            StringBuilder system = new StringBuilder();
            List<Object> rest = new ArrayList<Object>();
            for (Object entry : messages) {
                Map<String, Object> message = Json.object(entry);
                if ("system".equals(message.get("role"))) {
                    if (system.length() > 0) {
                        system.append("\n\n");
                    }
                    system.append(message.get("content"));
                } else {
                    rest.add(entry);
                }
            }
            if (system.length() == 0) {
                return null;
            }
            for (int i = 0; i < rest.size(); i++) {
                Map<String, Object> message = Json.object(rest.get(i));
                if ("user".equals(message.get("role"))) {
                    rest.set(i, message("user", system + "\n\n" + message.get("content")));
                    return rest;
                }
            }
            rest.add(0, message("user", system.toString()));
            return rest;
        }

        /** Ohne nutzbare Vorlage: ChatML, wenn das Vokabular es kennt, sonst schlichte Rollenzeilen. */
        private String fallback(List<Object> messages) {
            boolean chatMl = tokenizer.id(CHATML_START) != null;
            StringBuilder prompt = new StringBuilder();
            for (Object entry : messages) {
                Map<String, Object> message = Json.object(entry);
                if (chatMl) {
                    prompt.append(CHATML_START).append(message.get("role")).append('\n')
                            .append(message.get("content")).append("<|im_end|>\n");
                } else {
                    prompt.append(message.get("role")).append(": ").append(message.get("content")).append("\n\n");
                }
            }
            prompt.append(chatMl ? CHATML_START + "assistant\n" : "assistant: ");
            return prompt.toString();
        }

        private static Map<String, Object> message(String role, Object content) {
            Map<String, Object> message = new LinkedHashMap<String, Object>();
            message.put("role", role);
            message.put("content", content == null ? "" : String.valueOf(content));
            return message;
        }

        private static String role(ChatRole role) {
            switch (role) {
                case SYSTEM:
                case DEVELOPER:
                    return "system";
                case ASSISTANT:
                    return "assistant";
                default:
                    return "user";
            }
        }

        Sampler sampler(ChatOptions options, Random random) {
            boolean sample = Json.bool(generation.get("do_sample"), false);
            double temperature = options.temperature() != null ? options.temperature()
                    : sample ? Json.number(generation.get("temperature"), 1.0) : 0.0;
            double topP = options.topP() != null ? options.topP() : Json.number(generation.get("top_p"), 1.0);
            int topK = options.topK() != null ? options.topK() : Json.integer(generation.get("top_k"), 0);
            double penalty = Json.number(generation.get("repetition_penalty"), 1.0);
            return new Sampler(temperature, topP, topK, penalty, random);
        }
    }

    /** Gierig bei Temperatur 0, sonst Top-k/Top-p-Abtastung; Wiederholungsstrafe wie transformers. */
    static final class Sampler {
        private final double temperature;
        private final double topP;
        private final int topK;
        private final double penalty;
        private final Random random;

        Sampler(double temperature, double topP, int topK, double penalty, Random random) {
            this.temperature = temperature;
            this.topP = topP;
            this.topK = topK;
            this.penalty = penalty;
            this.random = random;
        }

        int next(float[] logits, List<Integer> history) {
            float[] scores = logits.clone();
            if (penalty != 1.0) {
                for (int id : new LinkedHashSet<Integer>(history)) {
                    if (id >= 0 && id < scores.length) {
                        scores[id] = scores[id] > 0 ? (float) (scores[id] / penalty) : (float) (scores[id] * penalty);
                    }
                }
            }
            if (temperature <= 0) {
                return argmax(scores);
            }
            Integer[] order = new Integer[scores.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            final float[] ranked = scores;
            java.util.Arrays.sort(order, (a, b) -> Float.compare(ranked[b], ranked[a]));
            int keep = topK > 0 ? Math.min(topK, order.length) : order.length;
            double max = scores[order[0]];
            double[] weights = new double[keep];
            double total = 0;
            for (int i = 0; i < keep; i++) {
                weights[i] = Math.exp((scores[order[i]] - max) / temperature);
                total += weights[i];
            }
            if (topP < 1.0) {
                double cumulative = 0;
                int cut = keep;
                for (int i = 0; i < keep; i++) {
                    cumulative += weights[i] / total;
                    if (cumulative >= topP) {
                        cut = i + 1;
                        break;
                    }
                }
                keep = cut;
                total = 0;
                for (int i = 0; i < keep; i++) {
                    total += weights[i];
                }
            }
            double target = random.nextDouble() * total;
            for (int i = 0; i < keep; i++) {
                target -= weights[i];
                if (target <= 0) {
                    return order[i];
                }
            }
            return order[keep - 1];
        }

        private static int argmax(float[] scores) {
            int best = 0;
            for (int i = 1; i < scores.length; i++) {
                if (scores[i] > scores[best]) {
                    best = i;
                }
            }
            return best;
        }
    }
}
