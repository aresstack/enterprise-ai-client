package com.aresstack.enterpriseai.application.history;

/**
 * Gespeicherte Metadaten eines Anhangs einer Nutzernachricht (aus askai-java8 arch, dort nur für Bilder). Die
 * Bytes liegen im {@link com.aresstack.enterpriseai.application.attachment.AttachmentStore} der Unterhaltung unter
 * {@link #getId()}; {@code null} heißt: die Ablage ist gescheitert, nur der Name ist bekannt.
 */
public final class AttachmentRecord {

    private String id;
    private String fileName;
    private long size;

    /** Für die Deserialisierung. */
    public AttachmentRecord() {
    }

    public AttachmentRecord(String id, String fileName, long size) {
        this.id = id;
        this.fileName = fileName;
        this.size = size;
    }

    public String getId() {
        return id;
    }

    public String getFileName() {
        return fileName != null ? fileName : "";
    }

    public long getSize() {
        return size;
    }
}
