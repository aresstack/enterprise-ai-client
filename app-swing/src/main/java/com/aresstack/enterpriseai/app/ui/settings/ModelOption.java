package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.modelcatalog.ModelDescriptor;

/**
 * Ein Eintrag einer Modell-Auswahlliste: der Wert, wie er gespeichert wird ({@code [<katalog>:]<modell>}, leer =
 * keine Auswahl), und die Anzeige. Ohne Deskriptor ist es entweder „keine Auswahl“ oder eine gespeicherte Auswahl,
 * die der Katalog gerade nicht kennt (Quelle nicht erreichbar); sie bleibt erhalten.
 */
final class ModelOption {

    private final String stored;
    private final String label;
    private final ModelDescriptor descriptor;
    private final boolean available;

    private ModelOption(String stored, String label, ModelDescriptor descriptor) {
        this(stored, label, descriptor, descriptor != null);
    }

    private ModelOption(String stored, String label, ModelDescriptor descriptor, boolean available) {
        this.stored = stored;
        this.label = label;
        this.descriptor = descriptor;
        this.available = available;
    }

    static ModelOption none(String label) {
        return new ModelOption("", label, null);
    }

    static ModelOption of(String stored, ModelDescriptor descriptor) {
        StringBuilder label = new StringBuilder(descriptor.displayName());
        if (!descriptor.displayName().equals(descriptor.modelId())) {
            label.append("  (").append(descriptor.modelId()).append(')');
        }
        label.append("  · ").append(descriptor.catalogName());
        if (descriptor.toolCalling()) {
            label.append(" · Tool-Calling");
        }
        return new ModelOption(stored, label.toString(), descriptor);
    }

    static ModelOption missing(String stored) {
        return new ModelOption(stored, stored + "  (nicht im Katalog)", null);
    }

    /** Ein lokal installiertes Modell, das der Katalog (noch) nicht meldet, etwa eine Stimme ohne laufenden Sidecar. */
    static ModelOption installed(String stored, String label) {
        return new ModelOption(stored, label + "  · Lokal", null, true);
    }

    /** Die Auswahl liegt im anderen Reiter; der Eintrag hält sie, bis hier etwas gewählt wird. */
    static ModelOption elsewhere(String stored, String label) {
        return new ModelOption(stored, label, null, true);
    }

    String stored() {
        return stored;
    }

    ModelDescriptor descriptor() {
        return descriptor;
    }

    /** Ein wählbares Modell (aus dem Katalog oder lokal installiert), keine Platzhalterzeile. */
    boolean available() {
        return available;
    }

    @Override
    public String toString() {
        return label;
    }
}
