package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

/** Ein OpenNLP-Modell des Vorlesens mit Installationsstand (askai arch „Modelle → NLP“). */
public final class NlpModelOffer {

    private final String id;
    private final String name;
    private final boolean installed;
    private final boolean inArchive;

    public NlpModelOffer(String id, String name, boolean installed) {
        this(id, name, installed, false);
    }

    /**
     * @param inArchive ob das Modell im Offline-Paket neben dem Jar liegt (Installation ohne Download)
     */
    public NlpModelOffer(String id, String name, boolean installed, boolean inArchive) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        this.id = id;
        this.name = name == null || name.trim().isEmpty() ? id : name;
        this.installed = installed;
        this.inArchive = inArchive;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean isInstalled() {
        return installed;
    }

    /** Ob die Installation aus dem Offline-Paket käme statt per Download. */
    public boolean isInArchive() {
        return inArchive;
    }
}
