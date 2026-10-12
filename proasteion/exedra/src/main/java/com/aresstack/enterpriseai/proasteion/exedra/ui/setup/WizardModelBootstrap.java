package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelDescriptor;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bereitet die Modelle des Assistenten vor: fragt den Modellkatalog des KI-Dienstes ab (gleich nach dem erfolgreichen
 * Verbindungstest im Hintergrund, nicht erst beim Betreten von „Modelle“). Which model gets selected decides
 * {@link ModelPreselection}.
 */
final class WizardModelBootstrap {

    /** Das Ergebnis einer Abfrage, auf dem EDT. */
    static final class Models {
        private final ModelCatalogSnapshot snapshot;
        private final List<String> chats;
        private final List<String> embeddings;
        private final List<String> speech;

        Models(ModelCatalogSnapshot snapshot, List<String> chats, List<String> embeddings, List<String> speech) {
            this.snapshot = snapshot;
            this.chats = Collections.unmodifiableList(new ArrayList<String>(chats));
            this.embeddings = Collections.unmodifiableList(new ArrayList<String>(embeddings));
            this.speech = Collections.unmodifiableList(new ArrayList<String>(speech));
        }

        ModelCatalogSnapshot snapshot() {
            return snapshot;
        }

        List<String> chats() {
            return chats;
        }

        List<String> embeddings() {
            return embeddings;
        }

        /** The remote speech output (TTS) models for {@code model.tts}. */
        List<String> speech() {
            return speech;
        }
    }

    private final SettingsDialogActions settings;

    WizardModelBootstrap(SettingsDialogActions settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        this.settings = settings;
    }

    /** Fragt die Modelle des Entwurfs ab; {@code done} kommt auf dem EDT. */
    void prepare(SettingsForm form, final Consumer<Models> done) {
        settings.refreshModels(form, snapshot -> done.accept(new Models(snapshot,
                remoteModels(snapshot, ModelCategory.CHAT), remoteModels(snapshot, ModelCategory.EMBEDDING),
                remoteModels(snapshot, ModelCategory.TTS))));
    }

    private List<String> remoteModels(ModelCatalogSnapshot snapshot, ModelCategory category) {
        List<String> ids = new ArrayList<String>();
        if (snapshot == null) {
            return ids;
        }
        for (ModelDescriptor descriptor : snapshot.modelsFor(category)) {
            ModelReference reference = new ModelReference(descriptor.catalogId(), descriptor.modelId());
            if (!settings.isLocal(reference)) {
                String stored = settings.storedModel(reference);
                if (!ids.contains(stored)) {
                    ids.add(stored);
                }
            }
        }
        return ids;
    }
}
