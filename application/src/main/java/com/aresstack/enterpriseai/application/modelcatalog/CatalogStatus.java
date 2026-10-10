package com.aresstack.enterpriseai.application.modelcatalog;

/** Stand einer Modellquelle nach der letzten Abfrage: erreichbar oder nicht, mit kurzer Meldung (nie Secrets). */
public final class CatalogStatus {

    private final String catalogId;
    private final String displayName;
    private final boolean available;
    private final String message;

    public CatalogStatus(String catalogId, String displayName, boolean available, String message) {
        if (catalogId == null || catalogId.isEmpty()) {
            throw new IllegalArgumentException("catalogId must not be empty");
        }
        this.catalogId = catalogId;
        this.displayName = displayName == null || displayName.isEmpty() ? catalogId : displayName;
        this.available = available;
        this.message = message == null ? "" : message;
    }

    public String catalogId() {
        return catalogId;
    }

    public String displayName() {
        return displayName;
    }

    public boolean available() {
        return available;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return "CatalogStatus[" + catalogId + ", available=" + available + ", " + message + "]";
    }
}
