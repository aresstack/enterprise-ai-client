package com.aresstack.enterpriseai.speech.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.aresstack.enterpriseai.speech.runtime.LocalSpeechRuntime;
import com.aresstack.enterpriseai.speech.runtime.SpeechGraphRequest;
import com.aresstack.enterpriseai.speech.runtime.SpeechRuntimeException;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pilot-Backend von {@link LocalSpeechRuntime}: rechnet die Graphen der Stimmen mit ONNX Runtime (CPU, Java-Bibliothek
 * im Prozess, kein externes Programm). Alles ONNX-Spezifische bleibt in dieser Klasse; sie wird später durch eine
 * eigene Inferenz-Engine ersetzt. Die Sitzung einer Stimme wird beim ersten Vorlesen geladen und bis {@link #close()}
 * gehalten.
 *
 * <p>Eingänge nach Namen: Piper-Exporte nehmen {@code input}, {@code input_lengths}, {@code scales} (Rauschen, Länge,
 * Rauschbreite) und bei Mehrsprecher-Stimmen {@code sid}; Hugging-Face-VITS-Exporte nehmen {@code input_ids} und
 * optional {@code attention_mask}. Der erste Ausgang ist die Wellenform, beliebiger Rang.
 */
public final class OnnxSpeechRuntime implements LocalSpeechRuntime {

    private final Map<String, OrtSession> sessions = new ConcurrentHashMap<String, OrtSession>();
    private OrtEnvironment environment;

    @Override
    public String name() {
        return "onnxruntime";
    }

    @Override
    public float[] synthesize(SpeechGraphRequest request) throws SpeechRuntimeException {
        long[] ids = request.tokenIds();
        try {
            OrtSession session = session(request);
            OrtEnvironment env = environment();
            Set<String> names = session.getInputNames();
            Map<String, OnnxTensor> inputs = new HashMap<String, OnnxTensor>();
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
                    inputs.put("input", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape));
                    inputs.put("input_lengths", OnnxTensor.createTensor(env,
                            LongBuffer.wrap(new long[] {ids.length}), new long[] {1}));
                    if (names.contains("scales")) {
                        float[] scales = {request.noiseScale(), request.lengthScale(), request.noiseWidth()};
                        inputs.put("scales", OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), new long[] {3}));
                    }
                    if (names.contains("sid")) {
                        long speaker = Math.max(0L, request.speakerId());
                        inputs.put("sid", OnnxTensor.createTensor(env, LongBuffer.wrap(new long[] {speaker}),
                                new long[] {1}));
                    }
                } else {
                    throw new SpeechRuntimeException("Stimme " + request.modelFile().getFileName()
                            + " hat nicht unterstützte Graph-Eingänge " + names);
                }
                OrtSession.Result result = session.run(inputs);
                try {
                    OnnxValue output = result.get(0);
                    return flatten(output.getValue());
                } finally {
                    result.close();
                }
            } finally {
                for (OnnxTensor tensor : inputs.values()) {
                    tensor.close();
                }
            }
        } catch (OrtException failed) {
            throw new SpeechRuntimeException("ONNX Runtime: " + failed.getMessage(), failed);
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError nativeFailure) {
            throw new SpeechRuntimeException("ONNX Runtime ließ sich nicht starten: " + nativeFailure, nativeFailure);
        }
    }

    private synchronized OrtEnvironment environment() {
        if (environment == null) {
            environment = OrtEnvironment.getEnvironment();
        }
        return environment;
    }

    private OrtSession session(SpeechGraphRequest request) throws OrtException {
        String key = request.modelFile().toAbsolutePath().toString();
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
            sessions.put(key, created);
            return created;
        }
    }

    private static float[] flatten(Object value) {
        Object current = value;
        while (current instanceof Object[] && ((Object[]) current).length > 0) {
            current = ((Object[]) current)[0];
        }
        if (current instanceof float[]) {
            return (float[]) current;
        }
        throw new IllegalStateException("unerwartete Ausgabe des Graphen " + (value == null ? "null"
                : value.getClass().getSimpleName()));
    }

    @Override
    public void close() {
        synchronized (sessions) {
            for (OrtSession session : sessions.values()) {
                try {
                    session.close();
                } catch (OrtException ignored) {
                    // wird ohnehin beendet
                }
            }
            sessions.clear();
        }
    }
}
