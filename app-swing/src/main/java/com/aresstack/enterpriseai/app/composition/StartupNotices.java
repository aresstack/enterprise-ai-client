package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.SourceConfig;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogAdapter;

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
            for (SourceConfig source : config.sources()) {
                if (source.credentialRef() != null) {
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
            if (selected != null && !KipitzModelCatalogAdapter.CATALOG_ID.equals(selected.catalogId())) {
                notices.add(category.displayName() + ": gewählt ist ein lokales Modell. Chat und Embeddings laufen "
                        + "bisher nur über die Enterprise-API; bitte unter Einstellungen → Modelle ein KIPITZ-Modell "
                        + "wählen.");
            }
        }
        notices.addAll(config.warnings());
        return notices;
    }
}
