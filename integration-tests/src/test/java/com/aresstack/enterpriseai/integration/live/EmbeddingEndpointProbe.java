package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.embedding.openai.BearerTokenSource;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Roh-Probe für {@code POST /embeddings} in der Live-Verifikation (Stufen 2 bis 4): sendet einen Body mit genau
 * den Headern des produktiven Adapters ({@code Content-Type: application/json; charset=utf-8},
 * {@code Authorization: Bearer …}, kein {@code Accept}) und <em>beschreibt</em> die Antwort, statt sie wie
 * {@code OpenAiCompatibleEmbeddingAdapter} zu validieren. So lassen sich die tatsächliche Dimension, die Form von
 * {@code data[]} und das Verhalten bei Array-Eingabe erfassen, bevor eine Dimension konfiguriert ist.
 *
 * <p>Nur Testcode; der Adapter bleibt die einzige produktive Implementierung. Ein {@link Report} enthält weder URL,
 * Token, Eingabetexte noch Antworttext: Fehlerantworten werden nur als Status und maschinenlesbarer Fehlercode
 * genannt, Vektoren nur als Zahlen.
 */
public final class EmbeddingEndpointProbe {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final long MAX_RESPONSE_BYTES = 32L * 1024 * 1024;
    private static final int MAX_ERROR_BODY_BYTES = 64 * 1024;
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    /** Wie {@code data[].embedding} in der Antwort vorliegt. */
    public enum EmbeddingShape {
        /** Float-Array, die vom Adapter unterstützte Form. */
        FLOAT_ARRAY,
        /** String, z. B. base64; der Adapter lehnt das ab. */
        STRING,
        /** Kein oder {@code null}-Feld {@code embedding}. */
        MISSING,
        /** Etwas anderes oder gemischt. */
        OTHER,
        /** Kein einziger {@code data}-Eintrag. */
        NONE
    }

    /** Beschreibung einer Antwort; alle Felder sind Beobachtungen, nichts davon ist ein Secret oder ein Hostname. */
    public static final class Report {

        private final int status;
        private final long bodyBytes;
        private final boolean jsonObject;
        private final String errorCode;
        private final String objectField;
        private final boolean modelEchoed;
        private final int dataCount;
        private final boolean indexEverywhere;
        private final boolean indexAscending;
        private final EmbeddingShape shape;
        private final List<Integer> dimensions;
        private final boolean allFinite;
        private final List<float[]> vectors;
        private final boolean usagePresent;
        private final int promptTokens;
        private final int totalTokens;

        Report(int status, long bodyBytes, boolean jsonObject, String errorCode, String objectField,
               boolean modelEchoed, int dataCount, boolean indexEverywhere, boolean indexAscending,
               EmbeddingShape shape, List<Integer> dimensions, boolean allFinite, List<float[]> vectors,
               boolean usagePresent, int promptTokens, int totalTokens) {
            this.status = status;
            this.bodyBytes = bodyBytes;
            this.jsonObject = jsonObject;
            this.errorCode = errorCode;
            this.objectField = objectField;
            this.modelEchoed = modelEchoed;
            this.dataCount = dataCount;
            this.indexEverywhere = indexEverywhere;
            this.indexAscending = indexAscending;
            this.shape = shape;
            this.dimensions = Collections.unmodifiableList(new ArrayList<Integer>(dimensions));
            this.allFinite = allFinite;
            this.vectors = Collections.unmodifiableList(new ArrayList<float[]>(vectors));
            this.usagePresent = usagePresent;
            this.promptTokens = promptTokens;
            this.totalTokens = totalTokens;
        }

        public int status() {
            return status;
        }

        public boolean isOk() {
            return status == 200;
        }

        /** Maschinenlesbarer Fehlercode aus {@code error.code}/{@code error.type}, sonst {@code null}. */
        public String errorCode() {
            return errorCode;
        }

        /** @return {@code data.length} oder -1, wenn die Antwort kein {@code data}-Array hat */
        public int dataCount() {
            return dataCount;
        }

        public EmbeddingShape shape() {
            return shape;
        }

        /** Dimension je {@code data}-Eintrag (nur bei {@link EmbeddingShape#FLOAT_ARRAY} gefüllt). */
        public List<Integer> dimensions() {
            return dimensions;
        }

        /** @return die gemeinsame Dimension aller Einträge oder -1, wenn es keine oder verschiedene gibt */
        public int dimension() {
            if (dimensions.isEmpty()) {
                return -1;
            }
            int first = dimensions.get(0);
            for (int dimension : dimensions) {
                if (dimension != first) {
                    return -1;
                }
            }
            return first;
        }

        public boolean allFinite() {
            return allFinite;
        }

        /** Die Vektoren in Listenreihenfolge (nur bei {@link EmbeddingShape#FLOAT_ARRAY}); Kopien. */
        public List<float[]> vectors() {
            List<float[]> copies = new ArrayList<float[]>(vectors.size());
            for (float[] vector : vectors) {
                copies.add(vector.clone());
            }
            return copies;
        }

        public boolean indexEverywhere() {
            return indexEverywhere;
        }

        public boolean indexAscending() {
            return indexAscending;
        }

        public boolean usagePresent() {
            return usagePresent;
        }

        public boolean modelEchoed() {
            return modelEchoed;
        }

        /** @return {@code true}, wenn der Adapter diese Antwort für {@code inputCount} Eingaben annehmen würde */
        public boolean usableByAdapter(int inputCount) {
            return isOk() && dataCount == inputCount && shape == EmbeddingShape.FLOAT_ARRAY && allFinite
                    && dimension() > 0 && (!indexEverywhere || indexAscending);
        }

        /** Lesbare Zusammenfassung für die Konsole: nur Status, Codes, Anzahlen und Zahlen. */
        public String describe() {
            StringBuilder text = new StringBuilder("HTTP ").append(status);
            if (!isOk()) {
                text.append(errorCode == null ? ", kein maschinenlesbarer Fehlercode" : ", Fehlercode " + errorCode)
                        .append(jsonObject ? " (JSON-Fehlerobjekt" : " (kein JSON-Objekt").append(", ")
                        .append(bodyBytes).append(" Bytes)");
                return text.toString();
            }
            text.append(", object=").append(objectField == null ? "fehlt" : objectField);
            text.append(", model wie angefragt: ").append(modelEchoed ? "ja" : "nein oder fehlt");
            if (dataCount < 0) {
                return text.append(", kein data-Array").toString();
            }
            text.append(", data: ").append(dataCount).append(dataCount == 1 ? " Eintrag" : " Einträge");
            if (dataCount > 0) {
                text.append(", index ").append(indexEverywhere ? (indexAscending ? "vorhanden und aufsteigend"
                        : "vorhanden, aber nicht aufsteigend") : "fehlt");
                text.append(", embedding als ").append(shapeName());
                if (shape == EmbeddingShape.FLOAT_ARRAY) {
                    text.append(", Dimension ").append(dimension() > 0 ? String.valueOf(dimension())
                            : "uneinheitlich " + dimensions);
                    text.append(allFinite ? ", alle Werte endlich" : ", NICHT alle Werte endlich");
                    text.append(", L2-Norm ").append(norms());
                }
            }
            text.append(", usage ").append(usagePresent
                    ? "vorhanden (prompt_tokens=" + promptTokens + ", total_tokens=" + totalTokens + ")" : "fehlt");
            return text.toString();
        }

        private String shapeName() {
            switch (shape) {
                case FLOAT_ARRAY:
                    return "Float-Array";
                case STRING:
                    return "String (base64?)";
                case MISSING:
                    return "fehlend";
                case NONE:
                    return "nicht vorhanden";
                default:
                    return "anderer Typ";
            }
        }

        private String norms() {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < vectors.size(); i++) {
                if (i > 0) {
                    text.append('/');
                }
                text.append(String.format(Locale.ROOT, "%.3f", norm(vectors.get(i))));
            }
            return text.toString();
        }

        @Override
        public String toString() {
            return "Report[" + describe() + "]";
        }
    }

    private final URI endpoint;
    private final BearerTokenSource tokenSource;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final java.net.Proxy proxy;

    /**
     * @param configuration liefert Endpunkt, Timeouts und Proxy wie beim Adapter; die konfigurierte Dimension wird
     *                      von der Probe nicht geprüft (sie darf ein Platzhalter sein)
     */
    public EmbeddingEndpointProbe(OpenAiCompatibleEmbeddingConfiguration configuration, BearerTokenSource tokenSource) {
        if (configuration == null || tokenSource == null) {
            throw new IllegalArgumentException("configuration and tokenSource must not be null");
        }
        this.endpoint = configuration.endpoint();
        this.tokenSource = tokenSource;
        this.connectTimeoutMillis = configuration.connectTimeoutMillis();
        this.readTimeoutMillis = configuration.readTimeoutMillis();
        this.proxy = configuration.proxy();
    }

    /** {@code {"model": …, "input": "<text>"}} – die Form, die der Adapter im Standardmodus sendet. */
    public Report single(String model, String text) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("input", text);
        return send(body);
    }

    /** Wie {@link #single(String, String)}, zusätzlich mit {@code encoding_format} (UNVERIFIED laut Nachtrag). */
    public Report single(String model, String text, String encodingFormat) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("input", text);
        body.addProperty("encoding_format", encodingFormat);
        return send(body);
    }

    /** {@code {"model": …, "input": ["a", "b", …]}} – die Form des Modus {@code ARRAY_UNVERIFIED}. */
    public Report array(String model, List<String> texts) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray input = new JsonArray();
        for (String text : texts) {
            input.add(text);
        }
        body.add("input", input);
        return send(body);
    }

    private Report send(JsonObject body) throws IOException {
        String requestedModel = body.get("model").getAsString();
        char[] token = tokenSource.bearerToken();
        try {
            requireHeaderSafe(token);
            return post(body.toString().getBytes(UTF_8), token, requestedModel);
        } finally {
            if (token != null) {
                Arrays.fill(token, '\0');
            }
        }
    }

    private Report post(byte[] body, char[] token, String requestedModel) throws IOException {
        URLConnection raw = proxy == null ? endpoint.toURL().openConnection() : endpoint.toURL().openConnection(proxy);
        if (!(raw instanceof HttpURLConnection)) {
            throw new IOException("not an HTTP endpoint");
        }
        HttpURLConnection connection = (HttpURLConnection) raw;
        try {
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestMethod("POST");
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Content-Type", CONTENT_TYPE);
            if (token != null && token.length > 0) {
                connection.setRequestProperty("Authorization", "Bearer " + new String(token));
            }
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream out = connection.getOutputStream();
            try {
                out.write(body);
            } finally {
                out.close();
            }
            int status = connection.getResponseCode();
            if (status >= 400) {
                return describeError(status, readAtMost(connection.getErrorStream(), MAX_ERROR_BODY_BYTES));
            }
            return describe(status, readAll(connection.getInputStream(), MAX_RESPONSE_BYTES), requestedModel);
        } finally {
            connection.disconnect();
        }
    }

    static Report describeError(int status, String body) {
        JsonObject root = parseObject(body);
        String code = null;
        if (root != null) {
            JsonElement error = root.get("error");
            if (error != null && error.isJsonObject()) {
                code = token(error.getAsJsonObject().get("code"));
                if (code == null) {
                    code = token(error.getAsJsonObject().get("type"));
                }
            } else {
                code = token(error);
            }
        }
        return new Report(status, body.getBytes(UTF_8).length, root != null, code, null, false, -1, false, false,
                EmbeddingShape.NONE, Collections.<Integer>emptyList(), false, Collections.<float[]>emptyList(),
                false, -1, -1);
    }

    static Report describe(int status, String body, String requestedModel) {
        long bytes = body.getBytes(UTF_8).length;
        JsonObject root = parseObject(body);
        if (root == null) {
            return new Report(status, bytes, false, null, null, false, -1, false, false, EmbeddingShape.NONE,
                    Collections.<Integer>emptyList(), false, Collections.<float[]>emptyList(), false, -1, -1);
        }
        String objectField = root.has("object") && root.get("object").isJsonPrimitive()
                ? root.get("object").getAsString() : null;
        boolean modelEchoed = root.has("model") && root.get("model").isJsonPrimitive()
                && requestedModel.equals(root.get("model").getAsString());
        JsonElement dataElement = root.get("data");
        int dataCount = dataElement != null && dataElement.isJsonArray() ? dataElement.getAsJsonArray().size() : -1;
        boolean indexEverywhere = dataCount > 0;
        boolean indexAscending = dataCount > 0;
        EmbeddingShape shape = EmbeddingShape.NONE;
        List<Integer> dimensions = new ArrayList<Integer>();
        List<float[]> vectors = new ArrayList<float[]>();
        boolean allFinite = true;
        for (int position = 0; position < Math.max(dataCount, 0); position++) {
            JsonElement entryElement = dataElement.getAsJsonArray().get(position);
            if (!entryElement.isJsonObject()) {
                shape = EmbeddingShape.OTHER;
                indexEverywhere = false;
                continue;
            }
            JsonObject entry = entryElement.getAsJsonObject();
            JsonElement index = entry.get("index");
            if (index == null || index.isJsonNull() || !index.isJsonPrimitive() || !index.getAsJsonPrimitive().isNumber()) {
                indexEverywhere = false;
            } else if (index.getAsDouble() != position) {
                indexAscending = false;
            }
            EmbeddingShape entryShape = shapeOf(entry.get("embedding"));
            shape = position == 0 ? entryShape : (shape == entryShape ? shape : EmbeddingShape.OTHER);
            if (entryShape == EmbeddingShape.FLOAT_ARRAY) {
                JsonArray array = entry.get("embedding").getAsJsonArray();
                float[] vector = new float[array.size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = array.get(i).getAsFloat();
                    if (Float.isNaN(vector[i]) || Float.isInfinite(vector[i])) {
                        allFinite = false;
                    }
                }
                dimensions.add(vector.length);
                vectors.add(vector);
            }
        }
        if (dataCount > 0 && !indexEverywhere) {
            indexAscending = false;
        }
        boolean usagePresent = root.has("usage") && root.get("usage").isJsonObject();
        int promptTokens = usagePresent ? integer(root.getAsJsonObject("usage").get("prompt_tokens")) : -1;
        int totalTokens = usagePresent ? integer(root.getAsJsonObject("usage").get("total_tokens")) : -1;
        return new Report(status, bytes, true, null, objectField, modelEchoed, dataCount, indexEverywhere,
                indexAscending, shape, dimensions, allFinite, vectors, usagePresent, promptTokens, totalTokens);
    }

    private static EmbeddingShape shapeOf(JsonElement embedding) {
        if (embedding == null || embedding.isJsonNull()) {
            return EmbeddingShape.MISSING;
        }
        if (embedding.isJsonArray()) {
            for (JsonElement component : embedding.getAsJsonArray()) {
                if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()) {
                    return EmbeddingShape.OTHER;
                }
            }
            return EmbeddingShape.FLOAT_ARRAY;
        }
        if (embedding.isJsonPrimitive() && embedding.getAsJsonPrimitive().isString()) {
            return EmbeddingShape.STRING;
        }
        return EmbeddingShape.OTHER;
    }

    private static int integer(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt() : -1;
    }

    private static String token(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return ERROR_CODE.matcher(value).matches() ? value : null;
    }

    private static JsonObject parseObject(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException | IllegalStateException ex) {
            return null;
        }
    }

    /** Wie im Adapter: ein Token mit unzulässigen Zeichen darf nie in einer Exception samt Header landen. */
    private static void requireHeaderSafe(char[] token) {
        if (token == null) {
            return;
        }
        for (char c : token) {
            if (c < 0x21 || c > 0x7E) {
                throw new IllegalArgumentException("bearer token contains characters that are not allowed in an HTTP header");
            }
        }
    }

    private static String readAtMost(InputStream in, int limit) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            int read;
            while (buffer.size() < limit && (read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, Math.min(read, limit - buffer.size()));
            }
        } finally {
            in.close();
        }
        return new String(buffer.toByteArray(), UTF_8);
    }

    private static String readAll(InputStream in, long limit) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            int read;
            while ((read = in.read(chunk)) != -1) {
                if (buffer.size() + (long) read > limit) {
                    throw new IOException("embedding response exceeds " + limit + " bytes");
                }
                buffer.write(chunk, 0, read);
            }
        } finally {
            in.close();
        }
        return new String(buffer.toByteArray(), UTF_8);
    }

    /** Cosinus-Ähnlichkeit zweier Vektoren gleicher Länge; 0 bei Nullvektoren. */
    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("vectors differ in length: " + a.length + " vs " + b.length);
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / Math.sqrt(normA * normB);
    }

    static double norm(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += (double) v * v;
        }
        return Math.sqrt(sum);
    }
}
