package com.aresstack.enterpriseai.text.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.aresstack.enterpriseai.text.runtime.EncoderBatch;
import com.aresstack.enterpriseai.text.runtime.EncoderOutput;
import com.aresstack.enterpriseai.text.runtime.TextEncoderRuntime;
import com.aresstack.enterpriseai.text.runtime.TextRuntimeException;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link TextEncoderRuntime} mit ONNX Runtime. Eingänge nach Namen: {@code input_ids}, {@code attention_mask},
 * {@code token_type_ids} (Nullen) und {@code position_ids} (0, 1, …). Ausgang: {@code sentence_embedding}, wenn der
 * Export den Satzvektor schon rechnet, sonst {@code last_hidden_state}/{@code token_embeddings} oder der erste.
 */
public final class OnnxTextEncoderRuntime implements TextEncoderRuntime {

    private final OnnxSessions sessions = new OnnxSessions();

    @Override
    public String name() {
        return "onnxruntime";
    }

    @Override
    public EncoderOutput encode(Path modelFile, EncoderBatch batch) throws TextRuntimeException {
        OrtSession session = sessions.session(modelFile);
        OrtEnvironment env = sessions.environment();
        long[] shape = {batch.rows(), batch.length()};
        Map<String, OnnxTensor> inputs = new HashMap<String, OnnxTensor>();
        try {
            Set<String> names = session.getInputNames();
            if (!names.contains("input_ids")) {
                throw new TextRuntimeException(modelFile.getFileName() + " ist kein Text-Encoder (Eingänge " + names
                        + ")");
            }
            inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(batch.flatInputIds()), shape));
            if (names.contains("attention_mask")) {
                inputs.put("attention_mask", OnnxTensor.createTensor(env,
                        LongBuffer.wrap(batch.flatAttentionMask()), shape));
            }
            if (names.contains("token_type_ids")) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(env,
                        LongBuffer.wrap(new long[batch.rows() * batch.length()]), shape));
            }
            if (names.contains("position_ids")) {
                long[] positions = new long[batch.rows() * batch.length()];
                for (int i = 0; i < positions.length; i++) {
                    positions[i] = i % batch.length();
                }
                inputs.put("position_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(positions), shape));
            }
            for (String name : names) {
                if (!inputs.containsKey(name)) {
                    throw new TextRuntimeException(modelFile.getFileName() + " verlangt den nicht unterstützten "
                            + "Eingang " + name);
                }
            }
            OrtSession.Result result = session.run(inputs);
            try {
                return output(result, session.getOutputNames(), batch);
            } finally {
                result.close();
            }
        } catch (OrtException failed) {
            throw new TextRuntimeException("ONNX Runtime: " + failed.getMessage(), failed);
        } finally {
            for (OnnxTensor tensor : inputs.values()) {
                tensor.close();
            }
        }
    }

    private static EncoderOutput output(OrtSession.Result result, Set<String> names, EncoderBatch batch)
            throws TextRuntimeException {
        String chosen = names.contains("sentence_embedding") ? "sentence_embedding"
                : names.contains("last_hidden_state") ? "last_hidden_state"
                : names.contains("token_embeddings") ? "token_embeddings" : null;
        OnnxValue value = chosen == null ? result.get(0) : result.get(chosen).orElse(result.get(0));
        if (!(value instanceof OnnxTensor)) {
            throw new TextRuntimeException("Ausgang des Encoders ist kein Tensor");
        }
        OnnxTensor tensor = (OnnxTensor) value;
        long[] shape = tensor.getInfo().getShape();
        FloatBuffer buffer = tensor.getFloatBuffer();
        if (buffer == null) {
            throw new TextRuntimeException("Ausgang des Encoders ist keine Gleitkommazahl (" + tensor.getInfo() + ")");
        }
        float[] values = new float[buffer.remaining()];
        buffer.get(values);
        if (shape.length == 2 && shape[0] == batch.rows()) {
            return EncoderOutput.pooled(values, batch.rows(), (int) shape[1]);
        }
        if (shape.length == 3 && shape[0] == batch.rows() && shape[1] == batch.length()) {
            return EncoderOutput.perToken(values, batch.rows(), batch.length(), (int) shape[2]);
        }
        throw new TextRuntimeException("Unerwartete Form des Encoder-Ausgangs " + java.util.Arrays.toString(shape));
    }

    @Override
    public void close() {
        sessions.close();
    }
}
