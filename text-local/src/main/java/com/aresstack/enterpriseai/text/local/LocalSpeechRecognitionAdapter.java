package com.aresstack.enterpriseai.text.local;

import com.aresstack.enterpriseai.speech.api.RecordedSpeech;
import com.aresstack.enterpriseai.speech.api.SpeechRecognitionException;
import com.aresstack.enterpriseai.speech.api.SpeechRecognitionPort;
import com.aresstack.enterpriseai.text.runtime.AudioLanguageModelRuntime;
import com.aresstack.enterpriseai.text.runtime.AudioModelGraphs;
import com.aresstack.enterpriseai.text.runtime.AudioPromptSession;
import com.aresstack.enterpriseai.text.runtime.TextRuntimeException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@link SpeechRecognitionPort} für ein lokal installiertes Sprachmodell mit Audio-Eingang (Gemma 4 E2B als
 * ONNX-Export im Layout von transformers.js, ohne Umwandlung): Log-Mel-Merkmale ({@link AudioFeatureExtractor}),
 * Prompt mit der Chat-Vorlage von Gemma ({@code <|turn>} bzw. {@code <start_of_turn>}) und der Bitte um eine reine
 * Abschrift, Tokenizer aus {@code tokenizer.json} in reinem Java, gierige Dekodierung bis zum Turn-Ende. Längere
 * Aufnahmen gehen in Stücken von höchstens 30 s durch das Modell. Blockiert.
 */
public final class LocalSpeechRecognitionAdapter implements SpeechRecognitionPort {

    /** Höchstens so viel Audio je Durchlauf (transformers: {@code max_length = 480000} bei 16 kHz). */
    static final int MAX_SECONDS = 30;
    /** Die Abschrift-Bitte aus der Gemma-Modellkarte (ASR), ohne feste Sprache. */
    static final String TRANSCRIBE_PROMPT = "Transcribe the following speech segment in its original language. "
            + "Follow these specific instructions for formatting the answer:\n"
            + "* Only output the transcription, with no newlines.\n"
            + "* When transcribing numbers, write the digits, i.e. write 1.7 and not one point seven, and write 3 "
            + "instead of three.";
    private static final String[] GRAPH_VARIANTS = {"_q4", "_quantized", "_int8", "_uint8", "_q4f16", "_fp16", ""};
    private static final Logger LOG = Logger.getLogger(LocalSpeechRecognitionAdapter.class.getName());

    private final String catalogId;
    private final Path modelRoot;
    private final AudioLanguageModelRuntime runtime;
    private LoadedModel loaded;

    /**
     * @param catalogId Katalog der Modellauswahl ({@code model.stt = <catalogId>:<id>})
     * @param modelRoot Modellverzeichnis; das Modell liegt in {@code <modelRoot>/<id>}
     */
    public LocalSpeechRecognitionAdapter(String catalogId, Path modelRoot, AudioLanguageModelRuntime runtime) {
        if (catalogId == null || modelRoot == null || runtime == null) {
            throw new IllegalArgumentException("catalogId, modelRoot and runtime are required");
        }
        this.catalogId = catalogId;
        this.modelRoot = modelRoot;
        this.runtime = runtime;
    }

    @Override
    public String catalogId() {
        return catalogId;
    }

    @Override
    public String transcribe(String modelId, RecordedSpeech speech) throws SpeechRecognitionException {
        if (speech == null) {
            throw new IllegalArgumentException("speech must not be null");
        }
        LoadedModel model = load(modelId);
        float[] samples = speech.samples();
        int chunk = MAX_SECONDS * model.features.sampleRate();
        StringBuilder text = new StringBuilder();
        for (int start = 0; start < samples.length; start += chunk) {
            float[] part = Arrays.copyOfRange(samples, start, Math.min(samples.length, start + chunk));
            String recognized = model.transcribe(runtime, part);
            if (!recognized.isEmpty()) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(recognized);
            }
        }
        return text.toString().trim();
    }

    private synchronized LoadedModel load(String modelId) throws SpeechRecognitionException {
        if (loaded != null && loaded.id.equals(modelId)) {
            return loaded;
        }
        Path directory;
        try {
            directory = LocalModelFiles.directory(modelRoot, modelId);
        } catch (IllegalArgumentException invalid) {
            throw new SpeechRecognitionException(invalid.getMessage(), invalid);
        }
        if (!Files.isDirectory(directory)) {
            throw new SpeechRecognitionException("Spracheingabe-Modell " + modelId + " ist nicht installiert "
                    + "(Einstellungen → Modelle → Lokale Modelle)");
        }
        try {
            loaded = new LoadedModel(modelId, directory);
            return loaded;
        } catch (IOException | TokenizerFormatException | IllegalArgumentException e) {
            throw new SpeechRecognitionException("Spracheingabe-Modell " + modelId + " nicht lesbar: "
                    + e.getMessage(), e);
        }
    }

    /** Der Graph {@code <prefix><variante>.onnx} im Modellordner oder in {@code onnx/}; bevorzugt quantisiert. */
    static Path graph(Path directory, String prefix) throws IOException {
        return graphs(directory, prefix).get(0);
    }

    /**
     * Alle vorhandenen Varianten von {@code <prefix>*.onnx} in der Reihenfolge der Vorliebe; lädt die Laufzeit die
     * erste nicht, versucht die Spracheingabe die nächste.
     */
    static List<Path> graphs(Path directory, String prefix) throws IOException {
        List<Path> found = new ArrayList<Path>();
        for (String variant : GRAPH_VARIANTS) {
            for (Path folder : new Path[] {directory.resolve("onnx"), directory}) {
                Path file = folder.resolve(prefix + variant + ".onnx");
                if (Files.isRegularFile(file)) {
                    found.add(file);
                }
            }
        }
        if (found.isEmpty()) {
            throw new IOException(prefix + "*.onnx fehlt in " + directory);
        }
        return found;
    }

    /** Ein geladenes Modell: Tokenizer, Merkmale, Prompt-Bausteine und Stopp-Token. */
    private static final class LoadedModel {
        final String id;
        /** Je Graph (Audio-Encoder, Token-Einbettung, Decoder) die vorhandenen Varianten und die gerade gewählte. */
        final List<List<Path>> candidates = new ArrayList<List<Path>>();
        final int[] chosen = new int[3];
        final HfTokenizer tokenizer;
        final AudioFeatureExtractor features;
        final long bos;
        final long audioToken;
        final long beginAudio;
        final long endAudio;
        final String userTurn;
        final String turnEnd;
        final String modelTurn;
        final Integer channelEnd;
        final Set<Integer> stops = new HashSet<Integer>();

        LoadedModel(String id, Path directory) throws IOException, TokenizerFormatException {
            this.id = id;
            candidates.add(graphs(directory, "audio_encoder"));
            candidates.add(graphs(directory, "embed_tokens"));
            candidates.add(graphs(directory, "decoder_model_merged"));
            Map<String, Object> config = Json.read(LocalModelFiles.require(directory, "config.json"));
            Map<String, Object> textConfig = Json.object(config.get("text_config"));
            tokenizer = HfTokenizer.load(LocalModelFiles.require(directory, "tokenizer.json"));
            Map<String, Object> tokenizerConfig = Json.readIfPresent(directory.resolve("tokenizer_config.json"));
            Map<String, Object> processor = Json.readIfPresent(directory.resolve("processor_config.json"));
            Map<String, Object> extractor = Json.object(processor.get("feature_extractor"));
            if (extractor.isEmpty()) {
                extractor = Json.readIfPresent(directory.resolve("preprocessor_config.json"));
            }
            features = new AudioFeatureExtractor(extractor);

            audioToken = tokenId(config.get("audio_token_id"), tokenizerConfig.get("audio_token"), "audio_token");
            beginAudio = tokenId(config.get("boa_token_id"), tokenizerConfig.get("boa_token"), "boa_token");
            endAudio = tokenId(config.containsKey("eoa_token_id") ? config.get("eoa_token_id")
                    : config.get("eoa_token_index"), tokenizerConfig.get("eoa_token"), "eoa_token");

            Integer bosId = tokenizer.id(Json.text(tokenizerConfig.get("bos_token"), "<bos>"));
            bos = Json.integer(config.get("bos_token_id"), Json.integer(textConfig.get("bos_token_id"),
                    bosId == null ? -1 : bosId));
            if (tokenizer.id("<|turn>") != null && tokenizer.id("<turn|>") != null) {
                userTurn = "<|turn>user\n";
                turnEnd = "<turn|>\n";
                modelTurn = "<|turn>model\n";
                stops.add(tokenizer.id("<turn|>"));
            } else if (tokenizer.id("<start_of_turn>") != null && tokenizer.id("<end_of_turn>") != null) {
                userTurn = "<start_of_turn>user\n";
                turnEnd = "<end_of_turn>\n";
                modelTurn = "<start_of_turn>model\n";
                stops.add(tokenizer.id("<end_of_turn>"));
            } else {
                throw new IllegalArgumentException("Unbekannte Chat-Vorlage (weder <|turn> noch <start_of_turn> "
                        + "im Tokenizer); unterstützt sind Gemma-Modelle mit Audio-Eingang");
            }
            channelEnd = tokenizer.id("<channel|>");
            Integer eos = tokenizer.id("<eos>");
            if (eos != null) {
                stops.add(eos);
            }
            addIds(stops, config.get("eos_token_id"));
            addIds(stops, textConfig.get("eos_token_id"));
            addIds(stops, Json.readIfPresent(directory.resolve("generation_config.json")).get("eos_token_id"));
        }

        private long tokenId(Object configured, Object tokenText, String name) {
            if (configured instanceof Number) {
                return ((Number) configured).longValue();
            }
            Integer id = tokenizer.id(Json.text(tokenText, ""));
            if (id == null) {
                throw new IllegalArgumentException("Kein Modell mit Audio-Eingang: " + name + " fehlt in config.json "
                        + "und tokenizer_config.json");
            }
            return id;
        }

        private static void addIds(Set<Integer> into, Object value) {
            if (value instanceof Number) {
                into.add(((Number) value).intValue());
            }
            for (Object entry : Json.list(value)) {
                if (entry instanceof Number) {
                    into.add(((Number) entry).intValue());
                }
            }
        }

        String transcribe(AudioLanguageModelRuntime runtime, float[] samples) throws SpeechRecognitionException {
            AudioFeatureExtractor.Features extracted = features.extract(samples);
            if (extracted.frames == 0) {
                return "";
            }
            try (AudioPromptSession session = open(runtime)) {
                int audioTokens = session.encodeAudio(extracted.values, extracted.frames, extracted.bins,
                        extracted.mask);
                if (audioTokens <= 0) {
                    return "";
                }
                // <bos><|turn>user\n<boa><audio>…<eoa>Bitte<turn|>\n<|turn>model\n, Audio-Token als Ids (wie der
                // Gemma-Prozessor nach dem Einsetzen der Platzhalter).
                int[] head = tokenizer.encode(userTurn, false);
                int[] tail = tokenizer.encode(TRANSCRIBE_PROMPT + turnEnd + modelTurn, false);
                long[] tokens = new long[(bos >= 0 ? 1 : 0) + head.length + audioTokens + 2 + tail.length];
                int offset = 0;
                if (bos >= 0) {
                    tokens[offset++] = bos;
                }
                for (int value : head) {
                    tokens[offset++] = value;
                }
                tokens[offset++] = beginAudio;
                for (int i = 0; i < audioTokens; i++) {
                    tokens[offset++] = audioToken;
                }
                tokens[offset++] = endAudio;
                for (int value : tail) {
                    tokens[offset++] = value;
                }
                int limit = Math.min(512, 32 + audioTokens / 2);
                List<Integer> generated = new ArrayList<Integer>();
                float[] logits = session.prefill(tokens, audioToken);
                for (int step = 0; step < limit; step++) {
                    int next = argmax(logits);
                    if (stops.contains(next)) {
                        break;
                    }
                    generated.add(next);
                    logits = session.next(next);
                }
                return text(generated);
            } catch (TextRuntimeException failed) {
                throw new SpeechRecognitionException(failed.getMessage(), failed);
            }
        }

        /**
         * Öffnet die gewählten Graphen; lädt die Laufzeit einen davon nicht (z. B. ein Operator-Stand, den diese
         * ONNX-Runtime-Version nicht kennt), kommt die nächste vorhandene Variante dieses Graphen dran, mit Grund im
         * Protokoll. Ohne weitere Variante geht der Fehler mit verständlichem Text an den Benutzer.
         */
        private AudioPromptSession open(AudioLanguageModelRuntime runtime) throws SpeechRecognitionException {
            while (true) {
                try {
                    return runtime.open(new AudioModelGraphs(current(0), current(1), current(2)));
                } catch (TextRuntimeException failed) {
                    String message = failed.getMessage() == null ? "" : failed.getMessage();
                    int broken = -1;
                    for (int i = 0; i < chosen.length; i++) {
                        if (message.contains(current(i).getFileName().toString())) {
                            broken = i;
                        }
                    }
                    if (broken < 0 || chosen[broken] + 1 >= candidates.get(broken).size()) {
                        throw new SpeechRecognitionException(explain(broken, message), failed);
                    }
                    Path rejected = current(broken);
                    chosen[broken]++;
                    LOG.warning("Spracheingabe: " + rejected.getFileName() + " lädt nicht (" + message + "); nehme "
                            + current(broken).getFileName());
                }
            }
        }

        private Path current(int graph) {
            return candidates.get(graph).get(chosen[graph]);
        }

        private String explain(int broken, String message) {
            if (broken < 0) {
                return message;
            }
            String file = current(broken).getFileName().toString();
            if (message.contains("ORT_INVALID_GRAPH") || message.contains("ORT_NOT_IMPLEMENTED")) {
                return "Die ONNX Runtime im Client kann " + file + " von " + id + " nicht laden: das Modell nutzt "
                        + "einen Operator-Stand, den diese Laufzeit nicht kennt, und eine andere Variante dieses "
                        + "Graphen ist nicht installiert. Technisch: " + message;
            }
            return message;
        }

        /** Ohne Denk-Kanal ({@code <|channel>…<channel|>}) und ohne Sonder-Token; eine Zeile. */
        private String text(List<Integer> generated) {
            int from = 0;
            if (channelEnd != null) {
                int end = generated.lastIndexOf(channelEnd);
                if (end >= 0) {
                    from = end + 1;
                }
            }
            int[] ids = new int[generated.size() - from];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = generated.get(from + i);
            }
            return tokenizer.decode(ids, true).replaceAll("\\s+", " ").trim();
        }

        private static int argmax(float[] logits) {
            int best = 0;
            for (int i = 1; i < logits.length; i++) {
                if (logits[i] > logits[best]) {
                    best = i;
                }
            }
            return best;
        }
    }
}
