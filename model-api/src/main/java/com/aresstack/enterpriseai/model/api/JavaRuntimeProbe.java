package com.aresstack.enterpriseai.model.api;

import com.aresstack.enterpriseai.domain.localruntime.JavaRuntimeInstallation;

import java.nio.file.Path;

/** Prüft eine einzelne Java-Installation durch Ausführen; blockiert. */
public interface JavaRuntimeProbe {

    /**
     * @param javaExecutable {@code java}/{@code java.exe} oder das Installationsverzeichnis (mit {@code bin/java})
     * @return die Installation oder {@code null}, wenn dort kein ausführbares Java liegt
     */
    JavaRuntimeInstallation inspect(Path javaExecutable);
}
