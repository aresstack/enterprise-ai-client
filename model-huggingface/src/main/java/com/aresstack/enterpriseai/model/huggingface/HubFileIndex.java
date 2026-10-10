package com.aresstack.enterpriseai.model.huggingface;

import com.aresstack.huggingface.hub.auth.HuggingFaceTokenProvider;
import com.aresstack.huggingface.hub.http.HubHttpClient;
import com.aresstack.huggingface.hub.http.HubHttpRequest;
import com.aresstack.huggingface.hub.http.HubHttpResponse;
import com.aresstack.huggingface.hub.http.UrlConnectionHubHttpClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ProxySelector;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Größe und SHA-256 je Datei aus den Hub-Metadaten ({@code GET /api/models/<repo>/tree/<revision>/<ordner>}):
 * Git-LFS-Dateien tragen {@code lfs.oid} (SHA-256 des Inhalts) und {@code lfs.size}, kleine Git-Dateien nur
 * {@code size}. Der {@code ETag} der Download-Antwort taugt dafür nicht: nach der Weiterleitung zum CDN (Xet-Speicher)
 * ist er ein anderer Hash als der SHA-256 der Datei.
 */
final class HubFileIndex {

    /** Erwartete Größe und, nur bei LFS-Dateien, SHA-256. */
    static final class Entry {
        final long size;
        final String sha256;

        Entry(long size, String sha256) {
            this.size = size;
            this.sha256 = sha256;
        }
    }

    private final HubHttpClient http;
    private final Map<String, Map<String, Entry>> directories = new HashMap<String, Map<String, Entry>>();

    HubFileIndex(String endpoint, ProxySelector proxies) {
        this(new UrlConnectionHubHttpClient(endpoint, new HuggingFaceTokenProvider.Anonymous(), null, proxies));
    }

    HubFileIndex(HubHttpClient http) {
        this.http = http;
    }

    /** Metadaten einer Datei; {@code null}, wenn der Hub sie im Ordner nicht kennt. */
    Entry entry(String repository, String revision, String path) throws IOException {
        int slash = path.lastIndexOf('/');
        String directory = slash < 0 ? "" : path.substring(0, slash);
        String key = repository + "@" + revision + ":" + directory;
        Map<String, Entry> files = directories.get(key);
        if (files == null) {
            files = list(repository, revision, directory);
            directories.put(key, files);
        }
        return files.get(path);
    }

    private Map<String, Entry> list(String repository, String revision, String directory) throws IOException {
        String request = "/api/models/" + repository + "/tree/" + encode(revision)
                + (directory.isEmpty() ? "" : "/" + encodePath(directory));
        HubHttpResponse response = http.execute(HubHttpRequest.get(request));
        if (response.getStatusCode() != 200) {
            throw new IOException("Hub-Metadaten für " + repository + "/" + directory + " nicht lesbar (HTTP "
                    + response.getStatusCode() + ")");
        }
        try {
            return parse(response.getBodyAsUtf8());
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new IOException("Hub-Metadaten für " + repository + "/" + directory + " unlesbar: "
                    + e.getMessage(), e);
        }
    }

    static Map<String, Entry> parse(String json) {
        Map<String, Entry> files = new HashMap<String, Entry>();
        JsonArray entries = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("path") || !entry.has("size") || !"file".equals(string(entry, "type"))) {
                continue;
            }
            long size = entry.get("size").getAsLong();
            String sha256 = null;
            if (entry.has("lfs") && entry.get("lfs").isJsonObject()) {
                JsonObject lfs = entry.getAsJsonObject("lfs");
                String oid = string(lfs, "oid");
                if (oid != null && oid.toLowerCase(Locale.ROOT).matches("[0-9a-f]{64}")) {
                    sha256 = oid.toLowerCase(Locale.ROOT);
                }
                if (lfs.has("size")) {
                    size = lfs.get("size").getAsLong();
                }
            }
            files.put(entry.get("path").getAsString(), new Entry(size, sha256));
        }
        return files;
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String encodePath(String path) {
        StringBuilder encoded = new StringBuilder();
        for (String segment : path.split("/")) {
            if (encoded.length() > 0) {
                encoded.append('/');
            }
            encoded.append(encode(segment));
        }
        return encoded.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
