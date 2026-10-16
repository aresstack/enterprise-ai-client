package com.aresstack.enterpriseai.searchrun.api;

/**
 * One resource in a {@link KnowledgeFunnel}, identified like a candidate: by its source and its resource id. Two
 * stages that see the same resource count it once.
 */
public final class FunnelResource {

    private final String sourceId;
    private final String resourceId;

    public FunnelResource(String sourceId, String resourceId) {
        if (sourceId == null || sourceId.trim().isEmpty() || resourceId == null || resourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("source id and resource id must not be empty");
        }
        this.sourceId = sourceId.trim();
        this.resourceId = resourceId.trim();
    }

    public String sourceId() {
        return sourceId;
    }

    public String resourceId() {
        return resourceId;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof FunnelResource)) {
            return false;
        }
        FunnelResource that = (FunnelResource) other;
        return sourceId.equals(that.sourceId) && resourceId.equals(that.resourceId);
    }

    @Override
    public int hashCode() {
        return 31 * sourceId.hashCode() + resourceId.hashCode();
    }

    @Override
    public String toString() {
        return sourceId + "/" + resourceId;
    }
}
