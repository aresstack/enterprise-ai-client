package com.aresstack.enterpriseai.text.onnx;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.aresstack.enterpriseai.text.runtime.TextRuntimeException;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Geladene Graphen je Datei (einmal laden, bis {@link #close()} halten). */
final class OnnxSessions {

    private final Map<String, OrtSession> sessions = new ConcurrentHashMap<String, OrtSession>();
    private OrtEnvironment environment;

    synchronized OrtEnvironment environment() {
        if (environment == null) {
            environment = OrtEnvironment.getEnvironment();
        }
        return environment;
    }

    OrtSession session(Path modelFile) throws TextRuntimeException {
        String key = modelFile.toAbsolutePath().toString();
        OrtSession existing = sessions.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (sessions) {
            existing = sessions.get(key);
            if (existing != null) {
                return existing;
            }
            try {
                OrtSession created = environment().createSession(key, new OrtSession.SessionOptions());
                sessions.put(key, created);
                return created;
            } catch (OrtException failed) {
                throw new TextRuntimeException("ONNX Runtime lädt " + modelFile.getFileName() + " nicht: "
                        + failed.getMessage(), failed);
            } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError nativeFailure) {
                throw new TextRuntimeException("ONNX Runtime ließ sich nicht starten: " + nativeFailure,
                        nativeFailure);
            }
        }
    }

    void close() {
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
