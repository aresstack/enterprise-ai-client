package com.aresstack.enterpriseai.model.api;

import com.aresstack.enterpriseai.domain.localruntime.JavaRuntimeInstallation;

import java.util.List;

/**
 * Sucht installierte Java-Laufzeiten und prüft jede durch Ausführen. Blockiert (Dateisystem, Prozessstarts) und
 * wird deshalb nie auf dem Swing-EDT gerufen.
 */
public interface JavaRuntimeDiscovery {

    /** @return alle gefundenen, ausführbaren Installationen (auch zu alte), nie {@code null} */
    List<JavaRuntimeInstallation> discover();
}
