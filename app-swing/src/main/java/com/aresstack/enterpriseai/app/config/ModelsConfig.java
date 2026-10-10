package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelSelections;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogAdapter;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarConfig;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarModelCatalogAdapter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Modellverwaltung: die eine Auswahl je {@link ModelCategory} und der optionale lokale Java-21-Sidecar. Chat und
 * Embeddings stehen weiter unter {@code chat.model} und {@code embedding.model}, alle anderen Kategorien unter
 * {@code model.<kategorie>}; Werte sind {@code [<katalog>:]<modell>}, ohne Katalog gilt die Enterprise-API.
 */
public final class ModelsConfig {

    /** Präfix der Kategorien ohne eigenen Abschnitt ({@code model.rerank}, {@code model.tts}, ...). */
    public static final String KEY_PREFIX = "model.";

    private final ModelSelections selections;
    private final LocalSidecarConfig localSidecar;

    ModelsConfig(ModelSelections selections, LocalSidecarConfig localSidecar) {
        this.selections = selections == null ? ModelSelections.none() : selections;
        this.localSidecar = localSidecar;
    }

    /** Die bekannten Kataloge (Präfixe einer Auswahl). */
    public static List<String> catalogIds() {
        return Collections.unmodifiableList(Arrays.asList(KipitzModelCatalogAdapter.CATALOG_ID,
                LocalSidecarModelCatalogAdapter.CATALOG_ID));
    }

    /** Der Katalog einer Auswahl ohne Präfix: die Enterprise-API. */
    public static String defaultCatalogId() {
        return KipitzModelCatalogAdapter.CATALOG_ID;
    }

    /** Liest {@code [<katalog>:]<modell>}; {@code null} bei leerem Text. */
    public static ModelReference parse(String text) {
        return ModelReference.parse(text, catalogIds(), defaultCatalogId());
    }

    /** Der Konfigurationsschlüssel einer Kategorie. */
    public static String keyOf(ModelCategory category) {
        switch (category) {
            case CHAT:
                return "chat.model";
            case EMBEDDING:
                return "embedding.model";
            default:
                return KEY_PREFIX + category.key();
        }
    }

    public ModelSelections selections() {
        return selections;
    }

    /** Der lokale Sidecar oder {@code null}, wenn kein Java 21 konfiguriert ist (lokale Modelle fehlen dann). */
    public LocalSidecarConfig localSidecar() {
        return localSidecar;
    }

    @Override
    public String toString() {
        return "ModelsConfig[" + selections + ", local=" + localSidecar + "]";
    }
}
