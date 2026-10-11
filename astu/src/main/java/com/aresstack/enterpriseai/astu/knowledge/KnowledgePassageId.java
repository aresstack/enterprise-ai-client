package com.aresstack.enterpriseai.astu.knowledge;

/**
 * Stabile Adresse einer aufbereiteten Passage im Index, wie askai arch: unveränderliche Capture + Position +
 * Segmentierungsversion + Embedding-Fingerabdruck. Form:
 * {@code <resourceId>@<capture>#p<position>@<segmentation>-<embedding>}, z. B.
 * {@code confluence:47110815@3fa9c2d1e0b4#p3@9f2c41ab-5e07d1c3}.
 * <ul>
 *   <li>{@code resourceId}: die Ressource, stabil über Inhaltsänderungen (ihr Format bestimmt der Connector);</li>
 *   <li>{@code capture}: die gelesene Fassung, die ersten {@value #CAPTURE_LENGTH} Hex-Zeichen des
 *       {@code contentHash} ihres Textes (askai {@code SourceCapture.captureId}); ändert sich der Inhalt, ändert sich
 *       die Capture und damit jede Passage-ID;</li>
 *   <li>{@code position} zählt die Passagen dieser Fassung ab 0;</li>
 *   <li>{@code segmentation} und {@code embedding} sind Kurzformen (8 Hex-Zeichen) der Fingerabdrücke von Zerlegung
 *       und Embedding-Modell.</li>
 * </ul>
 * Ändert sich eines davon, ändert sich die ID: ein Beleg zeigt nie still auf eine andere Passage oder neuen Text.
 */
public final class KnowledgePassageId {

    /** Länge der Capture-Kennung (Präfix des SHA-256-Hex-Hashes des Textes). */
    public static final int CAPTURE_LENGTH = 12;

    private static final String CAPTURE = "@";
    private static final String POSITION = "#p";
    private static final String VERSION = "@";
    private static final String SEPARATOR = "-";

    private final KnowledgeResourceId resourceId;
    private final String capture;
    private final int position;
    private final String segmentation;
    private final String embedding;
    private final String value;

    private KnowledgePassageId(KnowledgeResourceId resourceId, String capture, int position, String segmentation,
                               String embedding) {
        this.resourceId = resourceId;
        this.capture = capture;
        this.position = position;
        this.segmentation = segmentation;
        this.embedding = embedding;
        this.value = resourceId.value() + CAPTURE + capture + POSITION + position + VERSION + segmentation
                + SEPARATOR + embedding;
    }

    /**
     * @param capture die Capture der gelesenen Fassung, siehe {@link #captureOf(String)}
     */
    public static KnowledgePassageId of(KnowledgeResourceId resourceId, String capture, int position,
                                        String segmentation, String embedding) {
        if (resourceId == null) {
            throw new IllegalArgumentException("Resource-ID fehlt");
        }
        if (!isCapture(capture)) {
            throw new IllegalArgumentException("Capture muss " + CAPTURE_LENGTH + " Hex-Zeichen haben: " + capture);
        }
        if (position < 0) {
            throw new IllegalArgumentException("position darf nicht negativ sein: " + position);
        }
        if (!isTag(segmentation) || !isTag(embedding)) {
            throw new IllegalArgumentException("Versionskennungen müssen [a-z0-9] sein: " + segmentation + ", "
                    + embedding);
        }
        return new KnowledgePassageId(resourceId, capture, position, segmentation, embedding);
    }

    /**
     * Die Capture einer gelesenen Fassung aus dem {@code contentHash} ihres Textes (SHA-256, hex).
     *
     * @throws IllegalArgumentException wenn der Hash fehlt oder kein Hex ist (Fassung ohne Hash ist keine Capture)
     */
    public static String captureOf(String contentHash) {
        String capture = contentHash == null || contentHash.length() < CAPTURE_LENGTH ? ""
                : contentHash.substring(0, CAPTURE_LENGTH).toLowerCase(java.util.Locale.ROOT);
        if (!isCapture(capture)) {
            throw new IllegalArgumentException("contentHash fehlt oder ist kein SHA-256-Hex");
        }
        return capture;
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
        int captureAt = hash <= 0 ? -1 : value.lastIndexOf(CAPTURE, hash);
        if (captureAt <= 0) {
            throw new IllegalArgumentException("Ungültige Passage-ID: " + KnowledgeSourceId.quote(value));
        }
        String position = value.substring(hash + POSITION.length(), at);
        String versions = value.substring(at + VERSION.length());
        int separator = versions.indexOf(SEPARATOR);
        if (position.isEmpty() || position.length() > 9 || !position.matches("[0-9]+") || separator <= 0) {
            throw new IllegalArgumentException("Ungültige Passage-ID: " + KnowledgeSourceId.quote(value));
        }
        return of(KnowledgeResourceId.of(value.substring(0, captureAt)),
                value.substring(captureAt + CAPTURE.length(), hash), Integer.parseInt(position),
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

    private static boolean isCapture(String capture) {
        return capture != null && capture.length() == CAPTURE_LENGTH && capture.matches("[0-9a-f]+");
    }

    private static boolean isTag(String tag) {
        return tag != null && !tag.isEmpty() && tag.length() <= 32 && tag.matches("[a-z0-9]+");
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    /** Die Capture, also die gelesene Fassung der Ressource, zu der die Passage gehört. */
    public String capture() {
        return capture;
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
