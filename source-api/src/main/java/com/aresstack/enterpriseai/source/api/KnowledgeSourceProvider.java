package com.aresstack.enterpriseai.source.api;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceSettings;

import java.util.List;

/**
 * Der abstrakte Quellen-Port, über den ein Adapter seinen Quelltyp anbietet: Beschreibung samt Eingabefeldern,
 * Prüfung der Einstellungen und das Öffnen der konfigurierten Quelle. Die Composition Root registriert je Adapter
 * genau einen Provider; der Use Case {@code application.source.KnowledgeSourceManagement} und der Dialog
 * „+ Quelle“ kennen nur diesen Port und verzweigen nie nach dem Typ. Neue Quellen (SharePoint, Mail, FTP ...)
 * kommen als weiterer Provider hinzu, ohne Kern oder Oberfläche zu ändern.
 *
 * <p>Die Schlüssel in {@link SourceSettings} gehören dem Adapter; serialisiert werden sie unverändert als
 * {@code source.<id>.<schlüssel>}. Prüfen und Öffnen berühren nie das Netz und lesen nie Secrets.
 */
public interface KnowledgeSourceProvider {

    /** Typ, Anzeigename und Eingabefelder. */
    KnowledgeSourceType type();

    /**
     * Probleme der Einstellungen als „Feld: Erwartung“, nie mit Werten; leer, wenn sie sich öffnen lassen.
     */
    List<String> validate(SourceSettings settings);

    /** Crawl-Umfang der Indexierung; nur für Einstellungen ohne {@link #validate Probleme}. */
    SourceScope scope(SourceSettings settings);

    /**
     * Baut die Quelle (ohne Netzwerkzugriff).
     *
     * @throws IllegalArgumentException wenn {@link #validate} Probleme meldet
     */
    KnowledgeSourcePort open(KnowledgeSourceId sourceId, SourceSettings settings);
}
