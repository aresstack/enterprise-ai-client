package com.aresstack.enterpriseai.astu.localruntime;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;

/**
 * Ein lokales Modell, das der Client installieren kann (Stimme für die Sprachausgabe, Embedding- oder Chat-Modell;
 * kuratiert oder aus der Hub-Suche), mit Kategorie und Installationsstand im Modellverzeichnis. Die Kennung ist zugleich der
 * Ordnername unter dem Modellverzeichnis und der Modellname, unter dem die Stimme gewählt wird.
 */
public final class LocalVoiceOffer {

    private final String id;
    private final String displayName;
    private final String language;
    private final boolean installed;
    private final ModelCategory category;
    private final boolean inOfflinePackage;

    /** Eine Stimme (Kategorie TTS). */
    public LocalVoiceOffer(String id, String displayName, String language, boolean installed) {
        this(id, displayName, language, installed, ModelCategory.TTS);
    }

    public LocalVoiceOffer(String id, String displayName, String language, boolean installed,
                           ModelCategory category) {
        this(id, displayName, language, installed, category, false);
    }

    /** @param inOfflinePackage ob die Installation aus dem Offline-Paket käme statt per Download */
    public LocalVoiceOffer(String id, String displayName, String language, boolean installed,
                           ModelCategory category, boolean inOfflinePackage) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id.trim();
        this.displayName = displayName == null || displayName.trim().isEmpty() ? this.id : displayName.trim();
        this.language = language == null ? "" : language.trim();
        this.installed = installed;
        this.category = category == null ? ModelCategory.TTS : category;
        this.inOfflinePackage = inOfflinePackage;
    }

    /** Dasselbe Angebot mit dem Vermerk, ob es im Offline-Paket liegt. */
    public LocalVoiceOffer withOfflinePackage(boolean inPackage) {
        return inPackage == inOfflinePackage ? this
                : new LocalVoiceOffer(id, displayName, language, installed, category, inPackage);
    }

    /** Wofür das Modell gewählt werden kann (TTS, EMBEDDING, CHAT). */
    public ModelCategory category() {
        return category;
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

    /** Ob die Installation aus dem Offline-Paket neben dem Jar käme (ohne Download). */
    public boolean isInOfflinePackage() {
        return inOfflinePackage;
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
                && installed == that.installed && category == that.category
                && inOfflinePackage == that.inOfflinePackage;
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
