package com.aresstack.enterpriseai.astu.acropolis.source;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceDefinitionStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Use Case „Wissensquellen verwalten“: der Dialog „+ Quelle“ bekommt die Quelltypen aller registrierten
 * {@link KnowledgeSourceProvider}, prüft und speichert Entwürfe darüber und entfernt Quellen. Die einzige
 * Identität einer Quelle ist ihre kanonische Adresse ({@link SourceAddress}, {@code confluence://cf.intern/confluence}):
 * nie vom Benutzer vergeben, sondern aus Connector-Typ und kanonischem Endpoint abgeleitet ({@link #addressOf}); eine
 * zweite Quelle mit derselben Adresse wird abgelehnt. Nach corenth sind die
 * Indexdaten einer Quelle abgeleitete Sichten: wer eine Quelle entfernt (oder auf einen anderen Endpoint umstellt), zieht ihre
 * Einträge aus dem Wissensindex (und über {@link #withWithdrawal} aus dem Ressourcenarchiv) zurück, damit RAG und Agent sie nicht mehr finden. Das gehört hierher, nicht in die
 * Oberfläche.
 *
 * <p>Ohne {@link SourceDefinitionStore} (keine Konfigurationsdatei) lassen sich Quellen nur lesen und öffnen.
 */
public final class KnowledgeSourceManagement {

    static final String ADDRESS_RULE = "Adresse: fehlt (unter „Erweitert“ angeben oder aus dem KeePass-Eintrag "
            + "übernehmen); aus ihr entsteht die Identität der Quelle";
    static final String DUPLICATE = "Diese Wissensquelle ist bereits vorhanden.";

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
     * Ein neuer Entwurf des Typs mit den Vorgaben der Felder. Seine Adresse bekommt er erst beim Speichern: aus Typ
     * und kanonischem Endpoint ({@link #addressOf}), nie vom Benutzer.
     *
     * @param taken ohne Bedeutung (früher: schon vergebene IDs)
     */
    public SourceDefinition draft(String typeId, Set<String> taken) {
        KnowledgeSourceType type = type(typeId);
        if (type == null) {
            throw new IllegalArgumentException("unbekannter Quelltyp: " + typeId);
        }
        return new SourceDefinition("", type.id(), true, type.defaults());
    }

    /**
     * Die kanonische Adresse der Quelle ({@link SourceAddress}): deterministisch aus Connector-Typ und kanonischem
     * Endpoint ({@link KnowledgeSourceProvider#locator}, {@link KnowledgeSourceType#addressFor}). Titel und
     * KeePass-Eintrag gehören nie dazu. Liefern die Einstellungen keinen Endpoint (Adresse nur im KeePass-Eintrag),
     * bleibt es bei der gespeicherten Adresse.
     *
     * @return die Adresse oder {@code ""}, wenn weder Endpoint noch gespeicherte Adresse vorliegen
     */
    public String addressOf(SourceDefinition definition) {
        KnowledgeSourceProvider provider = providers.get(definition.typeId());
        String derived = provider == null ? "" : provider.type().addressFor(provider.locator(definition.settings()));
        return derived.isEmpty() ? definition.address() : derived;
    }

    /** Das Pfadpräfix des Typs zur Anzeige ({@code confluence:}); leer bei unbekanntem Typ. */
    public String pathPrefix(String typeId) {
        KnowledgeSourceType type = type(typeId);
        return type == null ? "" : type.pathPrefix();
    }

    /**
     * Probleme des Entwurfs mit Feldnamen, nie Werten; leer, wenn er sich speichern lässt.
     *
     * @param originalAddress die bisherige Adresse beim Bearbeiten, {@code null} beim Hinzufügen
     */
    public List<String> validate(SourceDefinition draft, String originalAddress) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        List<String> problems = new ArrayList<String>();
        KnowledgeSourceProvider provider = providers.get(draft.typeId());
        if (provider == null) {
            problems.add("Typ: unbekannter Quelltyp; verfügbar sind " + providers.keySet());
            return problems;
        }
        problems.addAll(provider.validate(draft.settings()));
        if (!problems.isEmpty()) {
            return problems;
        }
        String address = addressOf(draft);
        if (address.isEmpty()) {
            problems.add(ADDRESS_RULE);
        } else if (store != null && !address.equals(originalAddress)) {
            try {
                for (SourceDefinition other : store.definitions()) {
                    if (other.address().equals(address)) {
                        problems.add(DUPLICATE);
                        break;
                    }
                }
            } catch (IOException e) {
                problems.add("Konfigurationsdatei nicht lesbar (" + e.getClass().getSimpleName() + ")");
            }
        }
        return problems;
    }

    /**
     * Speichert den Entwurf unter seiner abgeleiteten Adresse (neu oder an der Stelle von {@code originalAddress}).
     * Ändert sich die Adresse, weil der Endpoint ein anderer ist, werden die Indexdaten der alten zurückgezogen.
     *
     * @throws IllegalArgumentException wenn {@link #validate} Probleme meldet
     */
    public void save(SourceDefinition draft, String originalAddress) throws IOException {
        requireStore();
        List<String> problems = validate(draft, originalAddress);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Entwurf hat Probleme: " + problems);
        }
        String address = addressOf(draft);
        store.save(draft.withAddress(address), originalAddress);
        if (originalAddress != null && !originalAddress.equals(address)) {
            withdraw(originalAddress);
        }
    }

    /**
     * Gleicht die gespeicherten Adressen beim Start mit Typ und Endpoint ab: von Hand eingetragene Quellen ohne
     * Adresse bekommen ihre, geänderte Endpoints ihre neue (Indexdaten der alten werden zurückgezogen, nicht
     * überführt). Quellen ohne Endpoint und Dubletten bleiben unverändert.
     *
     * @return alte → neue Adresse der geänderten Quellen (alt leer, wenn keine gespeichert war)
     */
    public Map<String, String> adoptAddresses() throws IOException {
        Map<String, String> changed = new LinkedHashMap<String, String>();
        if (store == null) {
            return changed;
        }
        List<SourceDefinition> all = store.definitions();
        Set<String> used = new HashSet<String>();
        for (SourceDefinition definition : all) {
            used.add(definition.address());
        }
        for (SourceDefinition definition : all) {
            KnowledgeSourceProvider provider = providers.get(definition.typeId());
            if (provider == null || !provider.validate(definition.settings()).isEmpty()) {
                continue;
            }
            String address = provider.type().addressFor(provider.locator(definition.settings()));
            if (address.isEmpty() || address.equals(definition.address()) || used.contains(address)) {
                continue;
            }
            store.save(definition.withAddress(address), definition.address());
            used.add(address);
            withdraw(definition.address());
            changed.put(definition.address(), address);
        }
        return changed;
    }

    /**
     * Entfernt die Quelle aus der Konfiguration und zieht ihre abgeleiteten Daten (Wissensindex) zurück.
     */
    public void remove(String address) throws IOException {
        requireStore();
        // erst die abgeleiteten Daten: scheitert das, bleibt die Quelle in der Datei und lässt sich erneut entfernen
        withdraw(address);
        store.remove(address);
    }

    /** Schreibt nur das Häkchen. */
    public void setEnabled(String address, boolean enabled) throws IOException {
        requireStore();
        store.setEnabled(address, enabled);
    }

    /** Problemlos gespeicherte Quelle als Registrierung (Port und Crawl-Umfang); ohne Netzwerkzugriff. */
    public KnowledgeSourceRegistration open(SourceDefinition definition) {
        KnowledgeSourceProvider provider = providers.get(definition.typeId());
        if (provider == null) {
            throw new IllegalArgumentException("unbekannter Quelltyp: " + definition.typeId());
        }
        String address = addressOf(definition);
        if (address.isEmpty()) {
            throw new IllegalArgumentException(ADDRESS_RULE);
        }
        return new KnowledgeSourceRegistration(provider.open(SourceAddress.of(address), definition.settings()),
                provider.scope(definition.settings()));
    }

    /**
     * "Verbindung testen" for a draft, generic for every source type: opens the source through its provider and asks
     * it for at most one resource of the configured scope. This touches the network and reads credentials through the
     * adapter, so callers run it off the UI thread. Nothing is stored or indexed.
     */
    public SourceConnectionCheck checkConnection(SourceDefinition draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        KnowledgeSourceProvider provider = providers.get(draft.typeId());
        if (provider == null) {
            return SourceConnectionCheck.failure("Unbekannter Quelltyp.");
        }
        List<String> problems = provider.validate(draft.settings());
        if (!problems.isEmpty()) {
            return SourceConnectionCheck.failure("Einstellungen unvollständig: " + String.join("; ", problems));
        }
        String address = addressOf(draft);
        if (address.isEmpty()) {
            return SourceConnectionCheck.failure(ADDRESS_RULE);
        }
        try {
            KnowledgeSourcePort port = provider.open(SourceAddress.of(address), draft.settings());
            SourceScope scope = provider.scope(draft.settings());
            SourceScope probe = SourceScope.builder().startPoints(scope.startPoints()).maxDepth(scope.maxDepth())
                    .maxResources(1).build().withExcludedPartitions(scope.excludedPartitions());
            List<KnowledgeResource> found = port.discover(probe);
            return SourceConnectionCheck.success(found.isEmpty()
                    ? "Verbindung erfolgreich, aber im gewählten Umfang wurde nichts gefunden."
                    : "Verbindung erfolgreich, Inhalte gefunden.");
        } catch (KnowledgeSourceException e) {
            return SourceConnectionCheck.failure(label(e.kind()) + detail(e.getMessage()));
        } catch (RuntimeException e) {
            return SourceConnectionCheck.failure("Verbindung fehlgeschlagen (" + e.getClass().getSimpleName() + ")"
                    + detail(e.getMessage()));
        }
    }

    private static String label(KnowledgeSourceException.Kind kind) {
        switch (kind) {
            case ACCESS_DENIED:
                return "Anmeldung fehlgeschlagen oder Zugriff verweigert";
            case NOT_FOUND:
                return "Nicht gefunden";
            case UNAVAILABLE:
                return "Quelle nicht erreichbar";
            case INVALID_RESPONSE:
                return "Unerwartete Antwort der Quelle";
            default:
                return "Verbindung fehlgeschlagen";
        }
    }

    private static String detail(String message) {
        return message == null || message.trim().isEmpty() ? "." : ": " + message.trim();
    }

    /** Die gespeicherte Quelle mit dieser Adresse oder {@code null}. */
    public SourceDefinition find(String address) throws IOException {
        for (SourceDefinition definition : definitions()) {
            if (definition.address().equals(address)) {
                return definition;
            }
        }
        return null;
    }

    private void withdraw(String address) {
        if (address == null || address.isEmpty()) {
            return;
        }
        SourceAddress sourceId = SourceAddress.of(address);
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
