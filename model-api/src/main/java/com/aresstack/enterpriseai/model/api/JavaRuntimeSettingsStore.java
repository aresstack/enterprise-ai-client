package com.aresstack.enterpriseai.model.api;

import java.nio.file.Path;

/** Die gespeicherte Java-Laufzeit des lokalen Sidecars. */
public interface JavaRuntimeSettingsStore {

    /** @return die gespeicherte Wahl oder {@code null}, wenn keine gespeichert ist */
    Path load();

    /** Speichert die Wahl dauerhaft; Fehler beim Schreiben werden protokolliert, nicht geworfen. */
    void save(Path javaExecutable);
}
