package com.aresstack.enterpriseai.application.localruntime;

import com.aresstack.enterpriseai.domain.localruntime.JavaRuntimeInstallation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ergebnis einer Suche: alle gefundenen Installationen in Anzeigereihenfolge und die automatische Wahl. */
public final class JavaRuntimeOverview {

    private final List<JavaRuntimeInstallation> installations;
    private final JavaRuntimeInstallation recommended;

    public JavaRuntimeOverview(List<JavaRuntimeInstallation> installations, JavaRuntimeInstallation recommended) {
        this.installations = installations == null ? Collections.<JavaRuntimeInstallation>emptyList()
                : Collections.unmodifiableList(new ArrayList<JavaRuntimeInstallation>(installations));
        this.recommended = recommended;
    }

    public static JavaRuntimeOverview empty() {
        return new JavaRuntimeOverview(null, null);
    }

    /** Alle gefundenen Installationen, kompatible zuerst; nie {@code null}. */
    public List<JavaRuntimeInstallation> installations() {
        return installations;
    }

    /** Die automatische Wahl oder {@code null}, wenn kein Java 21+ gefunden wurde. */
    public JavaRuntimeInstallation recommended() {
        return recommended;
    }
}
