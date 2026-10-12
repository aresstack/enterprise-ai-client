package com.aresstack.enterpriseai.proasteion.application.resources;

import java.net.URI;
import java.util.Locale;

/**
 * Eine installierbare lokale Ressource, wie sie eine zentrale Liste beschreibt (etwa {@code nlp-models.properties}):
 * Kennung, Download-Adresse, Dateiname und die genaue Größe und SHA-256, gegen die jede Quelle geprüft wird.
 * Weiß nichts davon, wofür die Datei gebraucht wird.
 */
public final class ResourceDescriptor {

    private final String id;
    private final URI url;
    private final String file;
    private final String sha256;
    private final long size;

    public ResourceDescriptor(String id, URI url, String file, String sha256, long size) {
        if (id == null || id.trim().isEmpty() || file == null || file.trim().isEmpty() || sha256 == null) {
            throw new IllegalArgumentException("id, file and sha256 must not be empty");
        }
        this.id = id.trim();
        this.url = url;
        this.file = file.trim();
        this.sha256 = sha256.trim().toLowerCase(Locale.ROOT);
        this.size = size;
    }

    public String id() {
        return id;
    }

    /** Die Download-Adresse oder {@code null}, wenn die Ressource nur aus dem lokalen Bundle kommt. */
    public URI url() {
        return url;
    }

    public String file() {
        return file;
    }

    public String sha256() {
        return sha256;
    }

    /** Die genaue Größe in Bytes, {@code -1} = unbekannt (dann zählt nur SHA-256). */
    public long size() {
        return size;
    }
}
