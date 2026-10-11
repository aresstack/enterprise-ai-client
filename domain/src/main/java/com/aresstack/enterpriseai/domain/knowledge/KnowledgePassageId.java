package com.aresstack.enterpriseai.domain.knowledge;

/**
 * Stabile Adresse einer aufbereiteten Passage im Index (askai arch: Capture + Position + Segmentierungsversion +
 * Embedding-Fingerabdruck). Form: {@code <resourceId>#p<position>@<segmentation>-<embedding>}, z. B.
 * {@code confluence:47110815#p3@9f2c41ab-5e07d1c3}. {@code position} zählt die Passagen der Ressource ab 0;
 * {@code segmentation} und {@code embedding} sind Kurzformen (8 Hex-Zeichen) der Fingerabdrücke von Zerlegung und
 * Embedding-Modell. Ändert sich eines davon, ändert sich die ID: ein Finding zeigt nie still auf eine andere Passage.
 */
public final class KnowledgePassageId {

    private static final String POSITION = "#p";
    private static final String VERSION = "@";
    private static final String SEPARATOR = "-";

    private final KnowledgeResourceId resourceId;
    private final int position;
    private final String segmentation;
    private final String embedding;
    private final String value;

    private KnowledgePassageId(KnowledgeResourceId resourceId, int position, String segmentation, String embedding) {
        this.resourceId = resourceId;
        this.position = position;
        this.segmentation = segmentation;
        this.embedding = embedding;
        this.value = resourceId.value() + POSITION + position + VERSION + segmentation + SEPARATOR + embedding;
    }

    public static KnowledgePassageId of(KnowledgeResourceId resourceId, int position, String segmentation,
                                        String embedding) {
        if (resourceId == null) {
            throw new IllegalArgumentException("Resource-ID fehlt");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position darf nicht negativ sein: " + position);
        }
        if (!isTag(segmentation) || !isTag(embedding)) {
            throw new IllegalArgumentException("Versionskennungen müssen [a-z0-9] sein: " + segmentation + ", "
                    + embedding);
        }
        return new KnowledgePassageId(resourceId, position, segmentation, embedding);
    }

    /** {@code true}, wenn {@code value} die Form einer Passage-ID hat (sonst ist es eine Resource-ID). */
    public static boolean looksLike(String value) {
        try {
            parse(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static KnowledgePassageId parse(String value) {
        int at = value == null ? -1 : value.lastIndexOf(VERSION);
        int hash = at <= 0 ? -1 : value.lastIndexOf(POSITION, at);
        if (hash <= 0) {
            throw new IllegalArgumentException("Ungültige Passage-ID: " + KnowledgeSourceId.quote(value));
        }
        String position = value.substring(hash + POSITION.length(), at);
        String versions = value.substring(at + VERSION.length());
        int separator = versions.indexOf(SEPARATOR);
        if (position.isEmpty() || position.length() > 9 || !position.matches("[0-9]+") || separator <= 0) {
            throw new IllegalArgumentException("Ungültige Passage-ID: " + KnowledgeSourceId.quote(value));
        }
        return of(KnowledgeResourceId.of(value.substring(0, hash)), Integer.parseInt(position),
                versions.substring(0, separator), versions.substring(separator + 1));
    }

    /** Kurzform (8 Hex-Zeichen aus SHA-256) eines Fingerabdrucks für die Versionsteile der ID. */
    public static String versionTag(String fingerprint) {
        if (fingerprint == null) {
            throw new IllegalArgumentException("fingerprint fehlt");
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", digest[i] & 0xff));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt", e);
        }
    }

    private static boolean isTag(String tag) {
        return tag != null && !tag.isEmpty() && tag.length() <= 32 && tag.matches("[a-z0-9]+");
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    public int position() {
        return position;
    }

    public String segmentation() {
        return segmentation;
    }

    public String embedding() {
        return embedding;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof KnowledgePassageId
                && value.equals(((KnowledgePassageId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
