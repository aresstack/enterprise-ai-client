package com.aresstack.enterpriseai.localruntime.speech;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pilot backend of {@link LocalSpeechRuntime}: runs the voice graphs with ONNX Runtime (CPU, in-process Java
 * library, no external program). Everything ONNX-specific stays in this class; it is meant to be replaced by an
 * in-house inference engine. A voice's session is loaded on first use and kept until {@link #close()}.
 *
 * <p>Graph inputs are matched by name: Piper exports take {@code input}, {@code input_lengths}, {@code scales}
 * (noise, length, noise width) and {@code sid} for multi-speaker voices; Hugging Face VITS exports take
 * {@code input_ids} and optionally {@code attention_mask}. The first output is the waveform, any rank.
 */
public final class OnnxSpeechRuntime implements LocalSpeechRuntime {

    /** Upper bound for one utterance; the host splits answers into sentences anyway. */
    static final int MAX_TOKENS = 4096;

    private final Map<String, OrtSession> sessions = new ConcurrentHashMap<>();
    private OrtEnvironment environment;

    @Override
    public String name() {
        return "onnxruntime";
    }

    @Override
    public byte[] synthesizeWav(LocalVoice voice, String text) throws SpeechRuntimeException {
        long[] ids = voice.encoder().encode(text);
        if (ids.length <= 1) {
            throw new IllegalArgumentException("input contains nothing the voice can speak");
        }
        if (ids.length > MAX_TOKENS) {
            throw new IllegalArgumentException("input too long for one utterance (" + ids.length + " tokens)");
        }
        try {
            OrtSession session = session(voice);
            OrtEnvironment env = environment();
            Set<String> names = session.getInputNames();
            Map<String, OnnxTensor> inputs = new HashMap<>();
            try {
                long[] shape = {1, ids.length};
                if (names.contains("input_ids")) {
                    inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape));
                    if (names.contains("attention_mask")) {
                        long[] mask = new long[ids.length];
                        Arrays.fill(mask, 1L);
                        inputs.put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape));
                    }
                } else if (names.contains("input")) {
                    VoiceParameters parameters = voice.parameters();
                    inputs.put("input", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape));
                    inputs.put("input_lengths", OnnxTensor.createTensor(env, LongBuffer.wrap(new long[]{ids.length}),
                            new long[]{1}));
                    if (names.contains("scales")) {
                        float[] scales = {parameters.noiseScale(), parameters.lengthScale(), parameters.noiseWidth()};
                        inputs.put("scales", OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), new long[]{3}));
                    }
                    if (names.contains("sid")) {
                        long speaker = Math.max(0L, parameters.speakerId());
                        inputs.put("sid", OnnxTensor.createTensor(env, LongBuffer.wrap(new long[]{speaker}),
                                new long[]{1}));
                    }
                } else {
                    throw new SpeechRuntimeException("voice " + voice.virtualName() + " has unsupported graph inputs "
                            + names);
                }
                try (OrtSession.Result result = session.run(inputs)) {
                    OnnxValue output = result.get(0);
                    return WavEncoder.encode(flatten(output.getValue()), voice.sampleRate());
                }
            } finally {
                for (OnnxTensor tensor : inputs.values()) {
                    tensor.close();
                }
            }
        } catch (OrtException failed) {
            throw new SpeechRuntimeException(failed.getMessage(), failed);
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError nativeFailure) {
            throw new SpeechRuntimeException("ONNX Runtime could not start: " + nativeFailure, nativeFailure);
        }
    }

    private synchronized OrtEnvironment environment() {
        if (environment == null) {
            environment = OrtEnvironment.getEnvironment();
        }
        return environment;
    }

    private OrtSession session(LocalVoice voice) throws OrtException {
        String key = voice.modelFile().toAbsolutePath().toString();
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
            System.err.println("[local-runtime] voice " + voice.virtualName() + " (" + voice.family()
                    + ") loaded, inputs " + created.getInputNames());
            sessions.put(key, created);
            return created;
        }
    }

    private static float[] flatten(Object value) {
        Object current = value;
        while (current instanceof Object[] nested && nested.length > 0 && !(current instanceof float[])) {
            current = nested[0];
        }
        if (current instanceof float[] samples) {
            return samples;
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
