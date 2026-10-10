package com.aresstack.enterpriseai.model.kipitz;

import com.aresstack.enterpriseai.speech.api.SpeechSynthesisException;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;
import com.aresstack.enterpriseai.speech.api.SynthesizedSpeech;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * {@link SpeechSynthesisPort} der Enterprise-API für TTS-Modelle aus demselben Katalog: {@code POST
 * <baseUrl>/audio/speech} (OpenAI-kompatibel, {@code response_format=wav}) über Route, TLS und Token des Katalogs.
 * UNVERIFIED: der Endpunkt ist mit den KIPITZ-TTS-Modellen noch nicht praktisch getestet. Fehlermeldungen nennen nur
 * den Status, nie Token oder Antwortkörper.
 */
public final class KipitzSpeechAdapter implements SpeechSynthesisPort {

    /** Ein Absatz Sprache kann serverseitig dauern; das Abspielen beginnt erst mit der Antwort. */
    private static final int MIN_READ_TIMEOUT_MILLIS = 120000;
    private static final int MAX_AUDIO_BYTES = 64 * 1024 * 1024;

    private final KipitzModelCatalogConfig config;

    public KipitzSpeechAdapter(KipitzModelCatalogConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    @Override
    public String catalogId() {
        return KipitzModelCatalogAdapter.CATALOG_ID;
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
        URI target = config.endpoint("audio/speech");
        try {
            HttpURLConnection connection = KipitzConnections.open(config, target);
            try {
                connection.setConnectTimeout(config.connectTimeoutMillis());
                connection.setReadTimeout(Math.max(MIN_READ_TIMEOUT_MILLIS, config.readTimeoutMillis()));
                connection.setRequestMethod("POST");
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                String token = token();
                if (token != null && !token.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                connection.setFixedLengthStreamingMode(body.length);
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(body);
                } finally {
                    out.close();
                }
                int status = connection.getResponseCode();
                if (status != 200) {
                    throw new SpeechSynthesisException("Sprachausgabe der Enterprise-API: HTTP " + status);
                }
                return new SynthesizedSpeech(read(connection.getInputStream()));
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new SpeechSynthesisException("Sprachausgabe der Enterprise-API nicht erreichbar ("
                    + e.getClass().getSimpleName() + ")", e);
        }
    }

    private String token() throws SpeechSynthesisException {
        if (config.bearerToken() == null) {
            return null;
        }
        try {
            return config.bearerToken().get();
        } catch (RuntimeException e) {
            // Nur der Klassenname: eine fremde Meldung könnte Secret-Material enthalten.
            throw new SpeechSynthesisException("API-Key nicht verfügbar (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static byte[] read(InputStream in) throws IOException {
        if (in == null) {
            throw new IOException("empty response");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[16384];
        try {
            int read;
            while ((read = in.read(chunk)) != -1) {
                if (buffer.size() + read > MAX_AUDIO_BYTES) {
                    throw new IOException("audio larger than " + MAX_AUDIO_BYTES + " bytes");
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
        return "KipitzSpeechAdapter[" + config.endpoint("audio/speech") + "]";
    }
}
