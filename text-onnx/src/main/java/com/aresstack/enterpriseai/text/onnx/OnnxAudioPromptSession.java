package com.aresstack.enterpriseai.text.onnx;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxTensorLike;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.platform.Fp16Conversions;
import com.aresstack.enterpriseai.text.runtime.AudioPromptSession;
import com.aresstack.enterpriseai.text.runtime.TextRuntimeException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine Erkennung wie in transformers.js ({@code Gemma3nForConditionalGeneration.forward}): der Audio-Encoder macht aus
 * den Merkmalen Einbettungen, {@code embed_tokens} bettet den Prompt ein, die Platzhalter-Token bekommen der Reihe
 * nach die Audio-Einbettungen, und der Decoder rechnet mit {@code inputs_embeds}, {@code per_layer_inputs} und
 * KV-Cache. Danach je Token: {@code embed_tokens} für das neue Token, Decoder mit den {@code present.*} des vorigen
 * Schritts als {@code past_key_values.*} (ohne Kopie).
 */
final class OnnxAudioPromptSession implements AudioPromptSession {

    private final OrtEnvironment env;
    private final OrtSession encoder;
    private final OrtSession embed;
    private final OrtSession decoder;
    private final String model;
    private final Map<String, NodeInfo> decoderInputs;
    /** past-Eingang zu seinem present-Ausgang. */
    private final Map<String, String> cache = new LinkedHashMap<String, String>();
    private float[] audio;
    private int audioTokens;
    private OrtSession.Result previous;
    private long length;
    private boolean closed;

    OnnxAudioPromptSession(OrtEnvironment env, OrtSession encoder, OrtSession embed, OrtSession decoder, String model)
            throws TextRuntimeException {
        this.env = env;
        this.encoder = encoder;
        this.embed = embed;
        this.decoder = decoder;
        this.model = model;
        try {
            this.decoderInputs = decoder.getInputInfo();
            List<String> outputs = new ArrayList<String>(decoder.getOutputNames());
            for (String input : decoderInputs.keySet()) {
                if (input.startsWith("past_key_values") || input.startsWith("past.")) {
                    String present = OnnxDecoderSession.presentName(input, outputs);
                    if (present == null) {
                        throw new TextRuntimeException(model + ": kein Ausgang zum Zwischenspeicher " + input);
                    }
                    cache.put(input, present);
                }
            }
            if (!decoderInputs.containsKey("inputs_embeds") || !outputs.contains("logits")) {
                throw new TextRuntimeException(model + " ist kein Decoder mit inputs_embeds (Eingänge "
                        + decoderInputs.keySet() + ")");
            }
            if (!encoder.getInputInfo().containsKey("input_features")) {
                throw new TextRuntimeException("Audio-Encoder ohne Eingang input_features (Eingänge "
                        + encoder.getInputInfo().keySet() + ")");
            }
            if (!embed.getInputInfo().containsKey("input_ids") || !embed.getOutputNames().contains("inputs_embeds")) {
                throw new TextRuntimeException("embed_tokens ohne input_ids/inputs_embeds (Eingänge "
                        + embed.getInputInfo().keySet() + ", Ausgänge " + embed.getOutputNames() + ")");
            }
        } catch (OrtException failed) {
            throw new TextRuntimeException("ONNX Runtime: " + failed.getMessage(), failed);
        }
    }

    @Override
    public int encodeAudio(float[] features, int frames, int bins, boolean[] mask) throws TextRuntimeException {
        ensureOpen();
        if (features == null || features.length != frames * bins || mask == null || mask.length != frames) {
            throw new IllegalArgumentException("features must be frames × bins, mask one entry per frame");
        }
        Map<String, OnnxTensorLike> inputs = new HashMap<String, OnnxTensorLike>();
        List<OnnxTensor> created = new ArrayList<OnnxTensor>();
        try {
            Map<String, NodeInfo> info = encoder.getInputInfo();
            created.add(put(inputs, "input_features", floatTensor(features, new long[] {1, frames, bins},
                    type(info, "input_features"))));
            if (info.containsKey("input_features_mask")) {
                OnnxJavaType maskType = type(info, "input_features_mask");
                if (maskType == OnnxJavaType.BOOL) {
                    created.add(put(inputs, "input_features_mask",
                            OnnxTensor.createTensor(env, new boolean[][] {mask})));
                } else {
                    long[] values = new long[frames];
                    for (int i = 0; i < frames; i++) {
                        values[i] = mask[i] ? 1L : 0L;
                    }
                    created.add(put(inputs, "input_features_mask", OnnxTensor.createTensor(env,
                            LongBuffer.wrap(values), new long[] {1, frames})));
                }
            }
            requireAll(info, inputs, "Audio-Encoder");
            try (OrtSession.Result result = encoder.run(inputs)) {
                OnnxValue value = result.get("audio_features").orElse(null);
                if (value == null) {
                    value = result.get(0);
                }
                OnnxTensor tensor = (OnnxTensor) value;
                long[] shape = tensor.getInfo().getShape();
                float[] data = floats(tensor);
                int hidden = (int) shape[shape.length - 1];
                if (hidden <= 0 || data.length % hidden != 0) {
                    throw new TextRuntimeException("Audio-Encoder liefert unerwartete Form " + Arrays.toString(shape));
                }
                audio = data;
                audioTokens = data.length / hidden;
                return audioTokens;
            }
        } catch (OrtException failed) {
            throw new TextRuntimeException("ONNX Runtime (Audio-Encoder): " + failed.getMessage(), failed);
        } finally {
            closeAll(created);
        }
    }

    @Override
    public float[] prefill(long[] tokens, long audioTokenId) throws TextRuntimeException {
        ensureOpen();
        if (previous != null) {
            throw new IllegalStateException("prefill already done");
        }
        if (tokens == null || tokens.length == 0) {
            throw new IllegalArgumentException("tokens must not be empty");
        }
        try (OrtSession.Result embedded = embedTokens(tokens)) {
            OnnxTensor embeds = (OnnxTensor) embedded.get("inputs_embeds").get();
            long[] shape = embeds.getInfo().getShape();
            float[] values = floats(embeds);
            int hidden = (int) shape[shape.length - 1];
            int placeholders = 0;
            for (long token : tokens) {
                if (token == audioTokenId) {
                    placeholders++;
                }
            }
            if (audio != null || placeholders > 0) {
                if (audio == null || placeholders != audioTokens) {
                    throw new TextRuntimeException("Prompt hat " + placeholders + " Audio-Platzhalter, der Encoder "
                            + "lieferte " + audioTokens);
                }
                if (audio.length / audioTokens != hidden) {
                    throw new TextRuntimeException("Audio-Einbettung (" + audio.length / audioTokens
                            + ") passt nicht zur Token-Einbettung (" + hidden + ")");
                }
                int next = 0;
                for (int i = 0; i < tokens.length; i++) {
                    if (tokens[i] == audioTokenId) {
                        System.arraycopy(audio, next * hidden, values, i * hidden, hidden);
                        next++;
                    }
                }
            }
            try (OnnxTensor merged = floatTensor(values, shape, type(decoderInputs, "inputs_embeds"))) {
                return decode(merged, perLayer(embedded), tokens.length);
            }
        } catch (OrtException failed) {
            throw new TextRuntimeException("ONNX Runtime: " + failed.getMessage(), failed);
        }
    }

    @Override
    public float[] next(long token) throws TextRuntimeException {
        ensureOpen();
        if (previous == null) {
            throw new IllegalStateException("prefill first");
        }
        try (OrtSession.Result embedded = embedTokens(new long[] {token})) {
            return decode((OnnxTensor) embedded.get("inputs_embeds").get(), perLayer(embedded), 1);
        } catch (OrtException failed) {
            throw new TextRuntimeException("ONNX Runtime: " + failed.getMessage(), failed);
        }
    }

    private OrtSession.Result embedTokens(long[] tokens) throws OrtException {
        try (OnnxTensor ids = OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), new long[] {1, tokens.length})) {
            Map<String, OnnxTensorLike> inputs = new HashMap<String, OnnxTensorLike>();
            inputs.put("input_ids", ids);
            return embed.run(inputs);
        }
    }

    private static OnnxTensor perLayer(OrtSession.Result embedded) {
        OnnxValue value = embedded.get("per_layer_inputs").orElse(null);
        return value instanceof OnnxTensor ? (OnnxTensor) value : null;
    }

    private float[] decode(OnnxTensor embeds, OnnxTensor perLayer, int count) throws TextRuntimeException {
        Map<String, OnnxTensorLike> inputs = new HashMap<String, OnnxTensorLike>();
        List<OnnxTensor> created = new ArrayList<OnnxTensor>();
        try {
            inputs.put("inputs_embeds", embeds);
            if (decoderInputs.containsKey("per_layer_inputs")) {
                if (perLayer == null) {
                    throw new TextRuntimeException(model + " verlangt per_layer_inputs, embed_tokens liefert keine");
                }
                inputs.put("per_layer_inputs", perLayer);
            }
            long total = length + count;
            if (decoderInputs.containsKey("attention_mask")) {
                long[] mask = new long[(int) total];
                Arrays.fill(mask, 1L);
                created.add(put(inputs, "attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(mask),
                        new long[] {1, total})));
            }
            if (decoderInputs.containsKey("position_ids")) {
                long[] positions = new long[count];
                for (int i = 0; i < count; i++) {
                    positions[i] = length + i;
                }
                created.add(put(inputs, "position_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(positions),
                        new long[] {1, count})));
            }
            if (decoderInputs.containsKey("use_cache_branch")) {
                created.add(put(inputs, "use_cache_branch", OnnxTensor.createTensor(env,
                        new boolean[] {previous != null})));
            }
            if (decoderInputs.containsKey("num_logits_to_keep")) {
                created.add(put(inputs, "num_logits_to_keep", OnnxTensor.createTensor(env, 1L)));
            }
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                if (previous == null) {
                    created.add(put(inputs, entry.getKey(), emptyCache(entry.getKey())));
                } else {
                    OnnxValue present = previous.get(entry.getValue()).orElse(null);
                    if (!(present instanceof OnnxTensor)) {
                        throw new TextRuntimeException(model + ": Ausgang " + entry.getValue() + " fehlt");
                    }
                    inputs.put(entry.getKey(), (OnnxTensor) present);
                }
            }
            requireAll(decoderInputs, inputs, model);
            OrtSession.Result result = decoder.run(inputs);
            if (previous != null) {
                previous.close();
            }
            previous = result;
            length = total;
            return lastLogits(result);
        } catch (OrtException failed) {
            throw new TextRuntimeException("ONNX Runtime: " + failed.getMessage(), failed);
        } finally {
            closeAll(created);
        }
    }

    private static void requireAll(Map<String, NodeInfo> info, Map<String, OnnxTensorLike> inputs, String graph)
            throws TextRuntimeException {
        for (String name : info.keySet()) {
            if (!inputs.containsKey(name)) {
                throw new TextRuntimeException(graph + " verlangt den nicht unterstützten Eingang " + name);
            }
        }
    }

    private static OnnxJavaType type(Map<String, NodeInfo> info, String name) {
        NodeInfo node = info.get(name);
        return node != null && node.getInfo() instanceof TensorInfo ? ((TensorInfo) node.getInfo()).type
                : OnnxJavaType.FLOAT;
    }

    /** Ein Tensor im Typ des Eingangs (float32, float16 oder bfloat16) aus float-Werten. */
    private OnnxTensor floatTensor(float[] values, long[] shape, OnnxJavaType type)
            throws OrtException, TextRuntimeException {
        if (type == OnnxJavaType.FLOAT) {
            return OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape);
        }
        if (type == OnnxJavaType.FLOAT16 || type == OnnxJavaType.BFLOAT16) {
            ShortBuffer converted = type == OnnxJavaType.FLOAT16
                    ? Fp16Conversions.convertFloatBufferToFp16Buffer(FloatBuffer.wrap(values))
                    : Fp16Conversions.convertFloatBufferToBf16Buffer(FloatBuffer.wrap(values));
            return OnnxTensor.createTensor(env, converted, shape, type);
        }
        throw new TextRuntimeException(model + ": Eingang vom Typ " + type + " wird nicht unterstützt");
    }

    private static float[] floats(OnnxTensor tensor) throws TextRuntimeException {
        FloatBuffer buffer = tensor.getFloatBuffer();
        if (buffer == null) {
            throw new TextRuntimeException("Ausgang vom Typ " + tensor.getInfo().type + " ist kein Gleitkomma");
        }
        float[] values = new float[buffer.remaining()];
        buffer.get(values);
        return values;
    }

    /** Leerer Zwischenspeicher: feste Maße aus dem Graphen, Stapel 1, Sequenz 0. */
    private OnnxTensor emptyCache(String name) throws OrtException, TextRuntimeException {
        TensorInfo info = (TensorInfo) decoderInputs.get(name).getInfo();
        long[] shape = info.getShape().clone();
        if (shape.length != 4) {
            throw new TextRuntimeException(model + ": unerwartete Form des Zwischenspeichers " + name + " "
                    + Arrays.toString(shape));
        }
        shape[0] = 1;
        shape[2] = 0;
        for (long dimension : shape) {
            if (dimension < 0) {
                throw new TextRuntimeException(model + ": Zwischenspeicher " + name + " ohne feste Maße "
                        + Arrays.toString(info.getShape()));
            }
        }
        ByteBuffer empty = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
        if (info.type == OnnxJavaType.FLOAT) {
            return OnnxTensor.createTensor(env, empty.asFloatBuffer(), shape);
        }
        if (info.type == OnnxJavaType.FLOAT16 || info.type == OnnxJavaType.BFLOAT16) {
            return OnnxTensor.createTensor(env, empty, shape, info.type);
        }
        throw new TextRuntimeException(model + ": Zwischenspeicher vom Typ " + info.type + " wird nicht unterstützt");
    }

    private float[] lastLogits(OrtSession.Result result) throws TextRuntimeException {
        OnnxValue value = result.get("logits").orElse(null);
        if (!(value instanceof OnnxTensor)) {
            throw new TextRuntimeException(model + ": Ausgang logits fehlt");
        }
        OnnxTensor logits = (OnnxTensor) value;
        long[] shape = logits.getInfo().getShape();
        FloatBuffer buffer = logits.getFloatBuffer();
        if (buffer == null || shape.length < 2) {
            throw new TextRuntimeException(model + ": unerwartete Logits " + logits.getInfo());
        }
        int vocabulary = (int) shape[shape.length - 1];
        float[] last = new float[vocabulary];
        buffer.position(buffer.limit() - vocabulary);
        buffer.get(last);
        return last;
    }

    private static OnnxTensor put(Map<String, OnnxTensorLike> inputs, String name, OnnxTensor tensor) {
        inputs.put(name, tensor);
        return tensor;
    }

    private static void closeAll(List<OnnxTensor> tensors) {
        for (OnnxTensor tensor : tensors) {
            tensor.close();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("session closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            audio = null;
            if (previous != null) {
                previous.close();
                previous = null;
            }
        }
    }
}
