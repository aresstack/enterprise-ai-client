package com.aresstack.enterpriseai.source.outlook;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceSettingField;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSettingsReader;

import java.nio.file.Path;
import java.util.List;

/**
 * Quelltyp „Outlook (PST/OST)“ ({@code source.<id>.type=outlook}, Ressourcenschema {@code mail}): lokale
 * Postfachdateien. Schlüssel: {@code mailbox} (Postfachdatei oder Ordner mit {@code .pst}/{@code .ost}),
 * {@code startPoints} (Ordner im Postfach, Standard {@code .} = alles), {@code maxDepth} (Standard 15),
 * {@code maxResources} (Standard 5000).
 */
public final class OutlookSourceProvider implements KnowledgeSourceProvider {

    public static final String TYPE_ID = "outlook";

    private static final KnowledgeSourceType TYPE = KnowledgeSourceType.builder(TYPE_ID, OutlookKnowledgeSource.SCHEME,
                    "Outlook (PST/OST)")
            .description("E-Mails aus lokalen Outlook-Postfachdateien (.pst, .ost); jede Nachricht ist ein Dokument")
            .idPrefix("mail")
            .summaryKey("mailbox")
            .field(SourceSettingField.of("mailbox", SourceSettingField.Kind.DIRECTORY, "Postfach",
                    "Ordner mit .pst/.ost-Dateien oder eine einzelne Postfachdatei").required())
            .field(SourceSettingField.text("startPoints", "Ordner im Postfach",
                    "Kommagetrennt, z. B. /Posteingang (. = alles)").withDefault("."))
            .field(SourceSettingField.of("maxDepth", SourceSettingField.Kind.NUMBER, "Tiefe",
                    "Wie viele Ordnerebenen durchsucht werden").withDefault("15"))
            .field(SourceSettingField.of("maxResources", SourceSettingField.Kind.NUMBER,
                    "Höchstzahl Nachrichten (optional)", "Obergrenze je Lauf; leer = 5000"))
            .build();

    /** Der Typ ohne Laufzeitumgebung (Beschreibung, Felder). */
    public static KnowledgeSourceType sourceType() {
        return TYPE;
    }

    @Override
    public KnowledgeSourceType type() {
        return TYPE;
    }

    @Override
    public List<String> validate(SourceSettings settings) {
        SourceSettingsReader reader = new SourceSettingsReader(TYPE, settings);
        mailbox(reader);
        scope(reader);
        return reader.problems();
    }

    @Override
    public SourceScope scope(SourceSettings settings) {
        return scope(new SourceSettingsReader(TYPE, settings));
    }

    @Override
    public KnowledgeSourcePort open(KnowledgeSourceId sourceId, SourceSettings settings) {
        SourceSettingsReader reader = new SourceSettingsReader(TYPE, settings);
        Path mailbox = mailbox(reader);
        if (reader.hasProblems() || mailbox == null) {
            throw new IllegalArgumentException("Outlook-Quelle " + sourceId + " ist fehlerhaft: " + reader.problems());
        }
        return new OutlookKnowledgeSource(sourceId, mailbox);
    }

    private static Path mailbox(SourceSettingsReader r) {
        Path mailbox = r.path("mailbox", null);
        if (mailbox == null && r.text("mailbox", null) == null) {
            r.problem("mailbox", "fehlt (Pflichtangabe: Postfachdatei oder Ordner)");
        }
        return mailbox;
    }

    private static SourceScope scope(SourceSettingsReader r) {
        return r.scope(".", 15, 5000);
    }
}
