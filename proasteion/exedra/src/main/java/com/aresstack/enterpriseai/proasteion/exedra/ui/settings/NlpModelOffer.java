package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

/** Ein OpenNLP-Modell des Vorlesens mit Installationsstand (askai arch „Modelle → NLP“). */
public final class NlpModelOffer {

    private final String id;
    private final String name;
    private final boolean installed;

    public NlpModelOffer(String id, String name, boolean installed) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        this.id = id;
        this.name = name == null || name.trim().isEmpty() ? id : name;
        this.installed = installed;
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
}
