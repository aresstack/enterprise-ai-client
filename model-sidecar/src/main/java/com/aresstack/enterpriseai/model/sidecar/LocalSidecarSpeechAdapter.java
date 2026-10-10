package com.aresstack.enterpriseai.model.sidecar;

import com.aresstack.enterpriseai.speech.api.SpeechSynthesisException;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;
import com.aresstack.enterpriseai.speech.api.SynthesizedSpeech;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * {@link SpeechSynthesisPort} des lokalen Java-21-Sidecars: {@code POST /v1/audio/speech} (OpenAI-kompatibel,
 * {@code response_format=wav}) auf 127.0.0.1 ohne Proxy. Teilt sich den Prozess mit dem
 * {@link LocalSidecarModelCatalogAdapter}, aus dem er stammt, und startet ihn bei Bedarf.
 */
public final class LocalSidecarSpeechAdapter implements SpeechSynthesisPort {

    private static final int CONNECT_TIMEOUT_MILLIS = 10000;
    /** Das erste Laden einer Stimme kann dauern; das Abspielen beginnt erst mit der Antwort. */
    private static final int READ_TIMEOUT_MILLIS = 120000;
    private static final int MAX_AUDIO_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ERROR_BYTES = 8 * 1024;

    private final LocalSidecarProcess process;

    LocalSidecarSpeechAdapter(LocalSidecarProcess process) {
        this.process = process;
    }

    @Override
    public String catalogId() {
        return LocalSidecarModelCatalogAdapter.CATALOG_ID;
    }

    @Override
    public SynthesizedSpeech synthesize(String modelId, String text) throws SpeechSynthesisException {
        if (modelId == null || modelId.trim().isEmpty() || text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("modelId and text must not be empty");
        }
        JsonObject request = new JsonObject();
        request.addProperty("model", modelId);
        request.addProperty("input", text);
        request.addProperty("response_format", "wav");
        byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
        try {
            String baseUrl = process.ensureStarted();
            HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + "/v1/audio/speech").toURL()
                    .openConnection(Proxy.NO_PROXY);
            try {
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                connection.setRequestMethod("POST");
                connection.setUseCaches(false);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(body.length);
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(body);
                } finally {
                    out.close();
                }
                int status = connection.getResponseCode();
                if (status != 200) {
                    InputStream error = connection.getErrorStream();
                    String detail = error == null ? "" : new String(read(error, MAX_ERROR_BYTES),
                            StandardCharsets.UTF_8);
                    throw new SpeechSynthesisException("Lokale Sprachausgabe: HTTP " + status
                            + (detail.isEmpty() ? "" : " " + detail));
                }
                return new SynthesizedSpeech(read(connection.getInputStream(), MAX_AUDIO_BYTES));
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new SpeechSynthesisException(e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage(), e);
        }
    }

    private static byte[] read(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[16384];
        try {
            int read;
            while ((read = in.read(chunk)) != -1) {
                if (buffer.size() + read > limit) {
                    throw new IOException("Antwort des Sidecars zu groß");
                }
                buffer.write(chunk, 0, read);
            }
        } finally {
            in.close();
        }
        return buffer.toByteArray();
    }

    @Override
    public String toString() {
        return "LocalSidecarSpeechAdapter";
    }
}
