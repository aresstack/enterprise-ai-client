package com.aresstack.enterpriseai.domain.modelcatalog;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Die eine Modellauswahl je {@link ModelCategory}, unabhängig davon, ob das Modell aus einem entfernten oder
 * einem lokalen Katalog kommt. Unveränderlich; eine fehlende Kategorie heißt „nichts gewählt“.
 */
public final class ModelSelections {

    private final Map<ModelCategory, ModelReference> selections;

    private ModelSelections(Map<ModelCategory, ModelReference> selections) {
        EnumMap<ModelCategory, ModelReference> copy = new EnumMap<ModelCategory, ModelReference>(ModelCategory.class);
        copy.putAll(selections);
        this.selections = Collections.unmodifiableMap(copy);
    }

    public static ModelSelections none() {
        return new ModelSelections(Collections.<ModelCategory, ModelReference>emptyMap());
    }

    /** @return die Auswahl der Kategorie oder {@code null} */
    public ModelReference get(ModelCategory category) {
        return selections.get(category);
    }

    /** @param reference {@code null} entfernt die Auswahl */
    public ModelSelections with(ModelCategory category, ModelReference reference) {
        EnumMap<ModelCategory, ModelReference> copy = new EnumMap<ModelCategory, ModelReference>(ModelCategory.class);
        copy.putAll(selections);
        if (reference == null) {
            copy.remove(category);
        } else {
            copy.put(category, reference);
        }
        return new ModelSelections(copy);
    }

    public Map<ModelCategory, ModelReference> asMap() {
        return selections;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModelSelections && selections.equals(((ModelSelections) other).selections);
    }

    @Override
    public int hashCode() {
        return selections.hashCode();
    }

    @Override
    public String toString() {
        return "ModelSelections" + selections;
    }
}
