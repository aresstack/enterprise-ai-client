package com.aresstack.enterpriseai.application.localruntime;

import com.aresstack.enterpriseai.domain.localruntime.JavaRuntimeInstallation;
import com.aresstack.enterpriseai.domain.localruntime.JavaRuntimeSelector;
import com.aresstack.enterpriseai.model.api.JavaRuntimeDiscovery;
import com.aresstack.enterpriseai.model.api.JavaRuntimeProbe;
import com.aresstack.enterpriseai.model.api.JavaRuntimeSettingsStore;

import java.nio.file.Path;
import java.util.List;

/**
 * Wählt die Java-Laufzeit des lokalen Sidecars. Beim Start wird eine gespeicherte Wahl nur geprüft; erst wenn
 * keine gespeichert oder sie ungültig ist (verschwunden, zu alt), läuft die vollständige Suche, die automatische
 * Wahl wird gespeichert. „Neu suchen“ in den Einstellungen sucht immer vollständig. Alle Methoden blockieren.
 */
public final class JavaRuntimeSelectionService {

    private final JavaRuntimeDiscovery discovery;
    private final JavaRuntimeProbe probe;
    private final JavaRuntimeSettingsStore store;
    private final JavaRuntimeSelector selector = new JavaRuntimeSelector();

    public JavaRuntimeSelectionService(JavaRuntimeDiscovery discovery, JavaRuntimeProbe probe,
                                       JavaRuntimeSettingsStore store) {
        if (discovery == null || probe == null || store == null) {
            throw new IllegalArgumentException("discovery, probe and store must not be null");
        }
        this.discovery = discovery;
        this.probe = probe;
        this.store = store;
    }

    /**
     * Ablauf beim Start: gespeicherte gültige Wahl verwenden, sonst suchen, automatisch wählen und speichern.
     *
     * @return die zu verwendende Installation oder {@code null}, wenn kein Java 21+ gefunden wurde (der Sidecar
     *         bleibt aus, eine vorhandene Einstellung wird dann nicht überschrieben)
     */
    public JavaRuntimeInstallation resolveAtStartup() {
        Path stored = store.load();
        if (stored != null) {
            JavaRuntimeInstallation current = probe.inspect(stored);
            if (current != null && current.isCompatible()) {
                if (!current.executable().equals(stored)) {
                    store.save(current.executable());
                }
                return current;
            }
        }
        JavaRuntimeInstallation selected = selector.selectDefault(discovery.discover());
        if (selected != null) {
            store.save(selected.executable());
        }
        return selected;
    }

    /** Vollständige Suche für das Dropdown („Neu suchen“); speichert nichts. */
    public JavaRuntimeOverview discover() {
        List<JavaRuntimeInstallation> sorted = selector.sortForDisplay(discovery.discover());
        return new JavaRuntimeOverview(sorted, selector.selectDefault(sorted));
    }
}
