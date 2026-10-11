package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogAdapter;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Verständliche Hinweise beim Start, die keine Fehler sind: KeePass deaktiviert (Secrets fehlen), Warnungen
 * des Konfigurations-Loaders. Reiner Text ohne Werte aus der Datei; die Anzeige übernimmt {@code Main}.
 */
public final class StartupNotices {

    private StartupNotices() {
    }

    public static List<String> of(AppConfig config) {
        List<String> notices = new ArrayList<String>();
        if (!config.keePass().enabled()) {
            StringBuilder sb = new StringBuilder("KeePassRPC ist deaktiviert. Ohne KeePass fehlen die Secrets: ");
            sb.append("der API-Key für Chat und Embeddings");
            boolean credentials = false;
            for (SourceDefinition source : config.sources()) {
                if (source.settings().has("credentialRef")) {
                    credentials = true;
                    break;
                }
            }
            if (credentials) {
                sb.append(" sowie die Zugangsdaten konfigurierter Wissensquellen");
            }
            sb.append(". Anfragen scheitern mit einem Authentifizierungsfehler, bis security.keepass.enabled=true "
                    + "gesetzt ist und KeePass mit dem KeePassRPC-Plugin läuft.");
            notices.add(sb.toString());
        }
        for (ModelCategory category : new ModelCategory[] {ModelCategory.CHAT, ModelCategory.EMBEDDING}) {
            ModelReference selected = config.models().selections().get(category);
            if (selected == null || KipitzModelCatalogAdapter.CATALOG_ID.equals(selected.catalogId())) {
                continue;
            }
            if (!java.nio.file.Files.isDirectory(config.models().localModelRoot().resolve(
                    selected.modelId()))) {
                notices.add(category.displayName() + ": das lokale Modell " + selected.modelId() + " ist nicht "
                        + "installiert (Einstellungen → Modelle → Lokale Modelle).");
            }
        }
        notices.addAll(config.warnings());
        return notices;
    }
}
