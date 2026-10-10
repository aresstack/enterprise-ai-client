package com.aresstack.enterpriseai.domain.localruntime;

/**
 * Eine lokale Stimme für die Sprachausgabe, die der Client installieren kann (kuratierte Liste aus der
 * Konfiguration), mit ihrem Installationsstand im Modellverzeichnis des Sidecars. Die Kennung ist zugleich der
 * Ordnername unter dem Modellverzeichnis und der Modellname, unter dem der Sidecar die Stimme meldet.
 */
public final class LocalVoiceOffer {

    private final String id;
    private final String displayName;
    private final String language;
    private final boolean installed;

    public LocalVoiceOffer(String id, String displayName, String language, boolean installed) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id.trim();
        this.displayName = displayName == null || displayName.trim().isEmpty() ? this.id : displayName.trim();
        this.language = language == null ? "" : language.trim();
        this.installed = installed;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Sprache der Stimme (z. B. {@code de_DE}); leer, wenn nicht angegeben. */
    public String language() {
        return language;
    }

    public boolean isInstalled() {
        return installed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LocalVoiceOffer)) {
            return false;
        }
        LocalVoiceOffer that = (LocalVoiceOffer) other;
        return id.equals(that.id) && displayName.equals(that.displayName) && language.equals(that.language)
                && installed == that.installed;
    }

    @Override
    public int hashCode() {
        return (id.hashCode() * 31 + displayName.hashCode()) * 31 + (installed ? 1 : 0);
    }

    @Override
    public String toString() {
        return "LocalVoiceOffer[" + id + (installed ? ", installiert" : "") + "]";
    }
}
