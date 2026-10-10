package com.aresstack.enterpriseai.localruntime.speech;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the local VITS voices with ONNX Runtime (CPU, in-process Java library, no external program). A
 * voice's session is loaded on first use and kept until {@link #close()}. Inputs are matched by name:
 * {@code input_ids} always, {@code attention_mask} when the graph declares it; the first output is the
 * waveform ({@code [1, samples]} or {@code [samples]}).
 */
public final class LocalSpeechEngine implements AutoCloseable {

    /** Upper bound for one utterance; the host splits answers into sentences anyway. */
    static final int MAX_TOKENS = 4096;

    private final Map<String, OrtSession> sessions = new ConcurrentHashMap<>();
    private OrtEnvironment environment;

    /** @return a mono 16-bit PCM WAV file with the spoken text */
    public byte[] synthesizeWav(LocalSpeechModel model, String text) throws OrtException {
        long[] ids = VitsTokenizer.tokenize(text, model);
        if (ids.length <= 1) {
            throw new IllegalArgumentException("input contains no characters the voice can speak");
        }
        if (ids.length > MAX_TOKENS) {
            throw new IllegalArgumentException("input too long for one utterance (" + ids.length + " tokens)");
        }
        OrtSession session = session(model);
        OrtEnvironment env = environment();
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            long[] shape = {1, ids.length};
            inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape));
            if (session.getInputNames().contains("attention_mask")) {
                long[] mask = new long[ids.length];
                java.util.Arrays.fill(mask, 1L);
                inputs.put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape));
            }
            try (OrtSession.Result result = session.run(inputs)) {
                OnnxValue output = result.get(0);
                return WavEncoder.encode(flatten(output.getValue()), model.sampleRate());
            }
        } finally {
            for (OnnxTensor tensor : inputs.values()) {
                tensor.close();
            }
        }
    }

    private synchronized OrtEnvironment environment() {
        if (environment == null) {
            environment = OrtEnvironment.getEnvironment();
        }
        return environment;
    }

    private OrtSession session(LocalSpeechModel model) throws OrtException {
        String key = model.onnxFile().toAbsolutePath().toString();
        OrtSession existing = sessions.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (sessions) {
            existing = sessions.get(key);
            if (existing != null) {
                return existing;
            }
            OrtSession created = environment().createSession(key, new OrtSession.SessionOptions());
            for (Map.Entry<String, NodeInfo> input : created.getInputInfo().entrySet()) {
                System.err.println("[local-runtime] voice " + model.virtualName() + " input " + input.getKey());
            }
            sessions.put(key, created);
            return created;
        }
    }

    private static float[] flatten(Object value) {
        if (value instanceof float[] samples) {
            return samples;
        }
        if (value instanceof float[][] rows && rows.length > 0) {
            return rows[0];
        }
        if (value instanceof float[][][] cube && cube.length > 0 && cube[0].length > 0) {
            return cube[0][0];
        }
        throw new IllegalStateException("unexpected waveform output " + (value == null ? "null"
                : value.getClass().getSimpleName()));
    }

    @Override
    public void close() {
        synchronized (sessions) {
            for (OrtSession session : sessions.values()) {
                try {
                    session.close();
                } catch (OrtException ignored) {
                    // shutting down anyway
                }
            }
            sessions.clear();
        }
    }
}
