package com.aresstack.enterpriseai.proasteion.exedra.ui.memory;

/** One memory row of the management view; {@code version} binds a delete to what the row shows. */
public final class MemoryEntry {

    private final String id;
    private final long version;
    private final String content;
    private final String created;

    /**
     * @param id      visible id such as {@code M-0042}
     * @param created date shown next to the id
     */
    public MemoryEntry(String id, long version, String content, String created) {
        if (id == null || content == null) {
            throw new IllegalArgumentException("id and content must not be null");
        }
        this.id = id;
        this.version = version;
        this.content = content;
        this.created = created == null ? "" : created;
    }

    public String id() {
        return id;
    }

    public long version() {
        return version;
    }

    public String content() {
        return content;
    }

    public String created() {
        return created;
    }

    @Override
    public String toString() {
        return id + "  " + created + "  " + content.replace('\n', ' ');
    }
}
