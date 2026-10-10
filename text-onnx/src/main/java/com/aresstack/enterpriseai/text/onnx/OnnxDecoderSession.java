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
import com.aresstack.enterpriseai.text.runtime.DecoderSession;
import com.aresstack.enterpriseai.text.runtime.TextRuntimeException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine Generierung: der erste Schritt bekommt einen leeren Zwischenspeicher (Sequenzlänge 0), jeder weitere die
 * {@code present.*}-Ausgänge des vorigen Schritts unverändert als {@code past_key_values.*} (ohne Kopie). Das
 * Ergebnis des vorigen Schritts bleibt dafür offen, bis der nächste gerechnet ist.
 */
final class OnnxDecoderSession implements DecoderSession {

    private final String model;
    private final OrtEnvironment env;
    private final OrtSession session;
    private final Map<String, NodeInfo> inputInfo;
    /** past-Eingang zu seinem present-Ausgang. */
    private final Map<String, String> cache = new LinkedHashMap<String, String>();
    private OrtSession.Result previous;
    private long length;
    private boolean closed;

    OnnxDecoderSession(String model, OrtEnvironment env, OrtSession session) throws TextRuntimeException {
        this.model = model;
        this.env = env;
        this.session = session;
        try {
            this.inputInfo = session.getInputInfo();
            List<String> outputs = new ArrayList<String>(session.getOutputNames());
            for (String input : inputInfo.keySet()) {
                if (input.startsWith("past_key_values") || input.startsWith("past.")) {
                    String present = presentName(input, outputs);
                    if (present == null) {
                        throw new TextRuntimeException(model + ": kein Ausgang zum Zwischenspeicher " + input);
                    }
                    cache.put(input, present);
                }
            }
            if (!inputInfo.containsKey("input_ids") || !outputs.contains("logits")) {
                throw new TextRuntimeException(model + " ist kein Decoder (Eingänge " + inputInfo.keySet() + ")");
            }
            if (cache.isEmpty()) {
                throw new TextRuntimeException(model + " hat keinen KV-Cache (past_key_values); nur Exporte mit "
                        + "Zwischenspeicher werden unterstützt");
            }
        } catch (OrtException failed) {
            throw new TextRuntimeException("ONNX Runtime: " + failed.getMessage(), failed);
        }
    }

    static String presentName(String past, List<String> outputs) {
        String[] candidates = {
                past.replace("past_key_values", "present"),
                past.replace("past_key_values", "present_key_values"),
                past.replace("past.", "present.")
        };
        for (String candidate : candidates) {
            if (outputs.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public float[] next(long[] tokens) throws TextRuntimeException {
        if (closed) {
            throw new IllegalStateException("session closed");
        }
        if (tokens == null || tokens.length == 0) {
            throw new IllegalArgumentException("tokens must not be empty");
        }
        Map<String, OnnxTensorLike> inputs = new HashMap<String, OnnxTensorLike>();
        List<OnnxTensor> created = new ArrayList<OnnxTensor>();
        try {
            int count = tokens.length;
            created.add(put(inputs, "input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(tokens),
                    new long[] {1, count})));
            long total = length + count;
            if (inputInfo.containsKey("attention_mask")) {
                long[] mask = new long[(int) total];
                Arrays.fill(mask, 1L);
                created.add(put(inputs, "attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(mask),
                        new long[] {1, total})));
            }
            if (inputInfo.containsKey("position_ids")) {
                long[] positions = new long[count];
                for (int i = 0; i < count; i++) {
                    positions[i] = length + i;
                }
                created.add(put(inputs, "position_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(positions),
                        new long[] {1, count})));
            }
            if (inputInfo.containsKey("use_cache_branch")) {
                created.add(put(inputs, "use_cache_branch", OnnxTensor.createTensor(env,
                        new boolean[] {previous != null})));
            }
            if (inputInfo.containsKey("num_logits_to_keep")) {
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
            for (String name : inputInfo.keySet()) {
                if (!inputs.containsKey(name)) {
                    throw new TextRuntimeException(model + " verlangt den nicht unterstützten Eingang " + name);
                }
            }
            OrtSession.Result result = session.run(inputs);
            if (previous != null) {
                previous.close();
            }
            previous = result;
            length = total;
            return lastLogits(result);
        } catch (OrtException failed) {
            throw new TextRuntimeException("ONNX Runtime: " + failed.getMessage(), failed);
        } finally {
            for (OnnxTensor tensor : created) {
                tensor.close();
            }
        }
    }

    private static OnnxTensor put(Map<String, OnnxTensorLike> inputs, String name, OnnxTensor tensor) {
        inputs.put(name, tensor);
        return tensor;
    }

    /** Leerer Zwischenspeicher: feste Maße aus dem Graphen, Stapel 1, Sequenz 0. */
    private OnnxTensor emptyCache(String name) throws OrtException, TextRuntimeException {
        TensorInfo info = (TensorInfo) inputInfo.get(name).getInfo();
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
        OnnxJavaType type = info.type;
        ByteBuffer empty = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
        if (type == OnnxJavaType.FLOAT) {
            return OnnxTensor.createTensor(env, empty.asFloatBuffer(), shape);
        }
        if (type == OnnxJavaType.FLOAT16 || type == OnnxJavaType.BFLOAT16) {
            return OnnxTensor.createTensor(env, empty, shape, type);
        }
        throw new TextRuntimeException(model + ": Zwischenspeicher vom Typ " + type + " wird nicht unterstützt");
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

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (previous != null) {
                previous.close();
                previous = null;
            }
        }
    }
}
