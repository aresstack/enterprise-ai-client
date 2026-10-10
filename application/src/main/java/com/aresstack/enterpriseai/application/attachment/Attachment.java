package com.aresstack.enterpriseai.application.attachment;

/** Ein abgelegter Anhang: Kennung ({@code att-…}), ursprünglicher Dateiname und Größe. */
public final class Attachment {

    private final String id;
    private final String fileName;
    private final long sizeBytes;

    public Attachment(String id, String fileName, long sizeBytes) {
        if (id == null || id.isEmpty() || fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("id and fileName must not be empty");
        }
        this.id = id;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
    }

    public String id() {
        return id;
    }

    public String fileName() {
        return fileName;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    @Override
    public String toString() {
        return "Attachment[" + id + ", " + fileName + ", " + sizeBytes + " B]";
    }
}
