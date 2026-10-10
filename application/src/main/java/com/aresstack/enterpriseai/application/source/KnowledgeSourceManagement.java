package com.aresstack.enterpriseai.application.source;

import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceDefinitionStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Use Case „Wissensquellen verwalten“: der Dialog „+ Quelle“ bekommt die Quelltypen aller registrierten
 * {@link KnowledgeSourceProvider}, prüft und speichert Entwürfe darüber und entfernt Quellen. Nach corenth sind die
 * Indexdaten einer Quelle abgeleitete Sichten: wer eine Quelle entfernt (oder unter neuer ID speichert), zieht ihre
 * Einträge aus dem Wissensindex (und über {@link #withWithdrawal} aus dem Ressourcenarchiv) zurück, damit RAG und Agent sie nicht mehr finden. Das gehört hierher, nicht in die
 * Oberfläche.
 *
 * <p>Ohne {@link SourceDefinitionStore} (keine Konfigurationsdatei) lassen sich Quellen nur lesen und öffnen.
 */
public final class KnowledgeSourceManagement {

    private static final Pattern SOURCE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    static final String ID_RULE = "ID: nur Buchstaben, Ziffern, Punkt, Unterstrich und Strich, beginnend mit "
            + "Buchstabe oder Ziffer";

    private final Map<String, KnowledgeSourceProvider> providers;
    private final SourceDefinitionStore store;
    private final KnowledgeIndexPort index;
    private final List<SourceDataWithdrawal> withdrawals;

    /**
     * @param providers ein Provider je Quelltyp, in der Reihenfolge des Dialogs
     * @param store     Ablage der Quellen; {@code null}, wenn keine Datei angeschlossen ist
     * @param index     Wissensindex, aus dem entfernte Quellen zurückgezogen werden; {@code null}: keiner
     */
    public KnowledgeSourceManagement(List<KnowledgeSourceProvider> providers, SourceDefinitionStore store,
                                     KnowledgeIndexPort index) {
        this(providers, store, index, Collections.<SourceDataWithdrawal>emptyList());
    }

    private KnowledgeSourceManagement(List<KnowledgeSourceProvider> providers, SourceDefinitionStore store,
                                      KnowledgeIndexPort index, List<SourceDataWithdrawal> withdrawals) {
        if (providers == null) {
            throw new IllegalArgumentException("providers must not be null");
        }
        Map<String, KnowledgeSourceProvider> byType = new LinkedHashMap<String, KnowledgeSourceProvider>();
        for (KnowledgeSourceProvider provider : providers) {
            if (provider == null) {
                throw new IllegalArgumentException("provider must not be null");
            }
            if (byType.put(provider.type().id(), provider) != null) {
                throw new IllegalArgumentException("Quelltyp doppelt registriert: " + provider.type().id());
            }
        }
        this.providers = Collections.unmodifiableMap(byType);
        this.store = store;
        this.index = index;
        this.withdrawals = Collections.unmodifiableList(new ArrayList<SourceDataWithdrawal>(withdrawals));
    }

    /** Dieselbe Verwaltung über einer (anderen) Ablage. */
    public KnowledgeSourceManagement withStore(SourceDefinitionStore value) {
        return new KnowledgeSourceManagement(new ArrayList<KnowledgeSourceProvider>(providers.values()), value, index,
                withdrawals);
    }

    /** Dieselbe Verwaltung, die beim Entfernen zusätzlich {@code value} zurückzieht (z. B. das Ressourcenarchiv). */
    public KnowledgeSourceManagement withWithdrawal(SourceDataWithdrawal value) {
        if (value == null) {
            throw new IllegalArgumentException("withdrawal must not be null");
        }
        List<SourceDataWithdrawal> all = new ArrayList<SourceDataWithdrawal>(withdrawals);
        all.add(value);
        return new KnowledgeSourceManagement(new ArrayList<KnowledgeSourceProvider>(providers.values()), store, index,
                all);
    }

    /** Die Quelltypen in der Reihenfolge der Registrierung. */
    public List<KnowledgeSourceType> types() {
        List<KnowledgeSourceType> types = new ArrayList<KnowledgeSourceType>();
        for (KnowledgeSourceProvider provider : providers.values()) {
            types.add(provider.type());
        }
        return Collections.unmodifiableList(types);
    }

    /** Der Typ zur ID oder {@code null}, wenn kein Adapter ihn anbietet. */
    public KnowledgeSourceType type(String typeId) {
        KnowledgeSourceProvider provider = providers.get(typeId);
        return provider == null ? null : provider.type();
    }

    public boolean canEdit() {
        return store != null;
    }

    /** Die gespeicherten Quellen (leer ohne Ablage). */
    public List<SourceDefinition> definitions() throws IOException {
        return store == null ? Collections.<SourceDefinition>emptyList() : store.definitions();
    }

    /**
     * Ein neuer Entwurf des Typs mit freier ID und den Vorgaben der Felder.
     *
     * @param taken IDs, die schon vergeben sind (auch angebundene ohne Datei)
     */
    public SourceDefinition draft(String typeId, Set<String> taken) {
        KnowledgeSourceType type = type(typeId);
        if (type == null) {
            throw new IllegalArgumentException("unbekannter Quelltyp: " + typeId);
        }
        Set<String> used = new HashSet<String>(taken == null ? Collections.<String>emptySet() : taken);
        try {
            for (SourceDefinition definition : definitions()) {
                used.add(definition.id());
            }
        } catch (IOException e) {
            // ohne lesbare Datei bleibt es bei den übergebenen IDs; Speichern meldet das Problem
        }
        String candidate = type.idPrefix();
        int n = 2;
        while (used.contains(candidate)) {
            candidate = type.idPrefix() + n++;
        }
        return new SourceDefinition(candidate, type.id(), true, type.defaults());
    }

    /**
     * Probleme des Entwurfs mit Feldnamen, nie Werten; leer, wenn er sich speichern lässt.
     *
     * @param originalId die bisherige ID beim Bearbeiten, {@code null} beim Hinzufügen
     */
    public List<String> validate(SourceDefinition draft, String originalId) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        List<String> problems = new ArrayList<String>();
        if (draft.id().isEmpty()) {
            problems.add("ID: fehlt (Kurzname der Quelle)");
        } else if (!SOURCE_ID.matcher(draft.id()).matches()) {
            problems.add(ID_RULE);
        } else if (store != null && !draft.id().equals(originalId)) {
            try {
                for (SourceDefinition other : store.definitions()) {
                    if (other.id().equals(draft.id())) {
                        problems.add("ID: eine Quelle „" + draft.id() + "“ gibt es schon");
                        break;
                    }
                }
            } catch (IOException e) {
                problems.add("Konfigurationsdatei nicht lesbar (" + e.getClass().getSimpleName() + ")");
            }
        }
        KnowledgeSourceProvider provider = providers.get(draft.typeId());
        if (provider == null) {
            problems.add("Typ: unbekannter Quelltyp; verfügbar sind " + providers.keySet());
        } else {
            problems.addAll(provider.validate(draft.settings()));
        }
        return problems;
    }

    /**
     * Speichert den Entwurf (neu oder ersetzt, auch unter neuer ID). Wechselt die ID, werden die Indexdaten der
     * alten ID zurückgezogen.
     *
     * @throws IllegalArgumentException wenn {@link #validate} Probleme meldet
     */
    public void save(SourceDefinition draft, String originalId) throws IOException {
        requireStore();
        List<String> problems = validate(draft, originalId);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Entwurf hat Probleme: " + problems);
        }
        store.save(draft, originalId);
        if (originalId != null && !originalId.equals(draft.id())) {
            withdraw(originalId);
        }
    }

    /**
     * Entfernt die Quelle aus der Konfiguration und zieht ihre abgeleiteten Daten (Wissensindex) zurück.
     */
    public void remove(String id) throws IOException {
        requireStore();
        store.remove(id);
        withdraw(id);
    }

    /** Schreibt nur das Häkchen. */
    public void setEnabled(String id, boolean enabled) throws IOException {
        requireStore();
        store.setEnabled(id, enabled);
    }

    /** Problemlos gespeicherte Quelle als Registrierung (Port und Crawl-Umfang); ohne Netzwerkzugriff. */
    public KnowledgeSourceRegistration open(SourceDefinition definition) {
        KnowledgeSourceProvider provider = providers.get(definition.typeId());
        if (provider == null) {
            throw new IllegalArgumentException("unbekannter Quelltyp: " + definition.typeId());
        }
        KnowledgeSourceId sourceId = KnowledgeSourceId.of(definition.id());
        return new KnowledgeSourceRegistration(provider.open(sourceId, definition.settings()),
                provider.scope(definition.settings()));
    }

    /** Die gespeicherte Quelle mit dieser ID oder {@code null}. */
    public SourceDefinition find(String id) throws IOException {
        for (SourceDefinition definition : definitions()) {
            if (definition.id().equals(id)) {
                return definition;
            }
        }
        return null;
    }

    private void withdraw(String id) {
        if (!SOURCE_ID.matcher(id).matches()) {
            return;
        }
        KnowledgeSourceId sourceId = KnowledgeSourceId.of(id);
        if (index != null) {
            index.removeSource(sourceId);
        }
        for (SourceDataWithdrawal withdrawal : withdrawals) {
            withdrawal.withdraw(sourceId);
        }
    }

    private void requireStore() {
        if (store == null) {
            throw new IllegalStateException("keine Konfigurationsdatei angeschlossen");
        }
    }
}
