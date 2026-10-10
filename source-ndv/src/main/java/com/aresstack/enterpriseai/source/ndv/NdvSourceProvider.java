package com.aresstack.enterpriseai.source.ndv;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceSettingField;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSettingsReader;

import java.util.List;

/**
 * Quelltyp „Natural (NDV)“ ({@code source.<id>.type=ndv}, Ressourcenschema {@code ndv}): Natural-Quellen aus
 * Bibliotheken eines Natural Development Servers. Schlüssel: {@code host}, {@code port} (Standard 2700),
 * {@code credentialRef} (KeePass-Eintrag mit Benutzer und Passwort, Pflicht), {@code startPoints} (Bibliotheken,
 * optional mit Filter {@code LIB/PREFIX*}), {@code maxResources}.
 */
public final class NdvSourceProvider implements KnowledgeSourceProvider {

    public static final String TYPE_ID = "ndv";

    private static final KnowledgeSourceType TYPE = KnowledgeSourceType.builder(TYPE_ID, NdvKnowledgeSource.SCHEME,
                    "Natural (NDV)")
            .description("Natural-Quellen (Programme, Subprogramme, Copycodes ...) aus Bibliotheken eines NDV-Servers")
            .idPrefix("natural")
            .summaryKey("host")
            .field(SourceSettingField.text("host", "Host", "Name oder Adresse des NDV-Servers").required())
            .field(SourceSettingField.of("port", SourceSettingField.Kind.NUMBER, "Port", "Standard 2700")
                    .withDefault("2700"))
            .field(SourceSettingField.of("credentialRef", SourceSettingField.Kind.SECRET_REF,
                    "KeePass-Eintrag", "Titel des KeePass-Eintrags mit Benutzer und Passwort").required())
            .field(SourceSettingField.text("startPoints", "Bibliotheken",
                    "Kommagetrennt, optional mit Filter: MYLIB oder MYLIB/CUST*").required())
            .field(SourceSettingField.of("maxResources", SourceSettingField.Kind.NUMBER,
                    "Höchstzahl Objekte (optional)", "Obergrenze je Lauf; leer = 5000"))
            .build();

    private static final int DEFAULT_PORT = 2700;
    private static final int DEFAULT_MAX_RESOURCES = 5000;

    private final SecretProvider secrets;

    /** @param secrets Security-Port für Benutzer und Passwort beim Verbindungsaufbau */
    public NdvSourceProvider(SecretProvider secrets) {
        if (secrets == null) {
            throw new IllegalArgumentException("secrets must not be null");
        }
        this.secrets = secrets;
    }

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
        host(reader);
        reader.integer("port", DEFAULT_PORT, 1, 65535);
        credentialRef(reader);
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
        String host = host(reader);
        int port = reader.integer("port", DEFAULT_PORT, 1, 65535);
        SecretRef credentialRef = credentialRef(reader);
        if (reader.hasProblems() || host == null || credentialRef == null) {
            throw new IllegalArgumentException("NDV-Quelle " + sourceId + " ist fehlerhaft: " + reader.problems());
        }
        return new NdvKnowledgeSource(sourceId, host, port, credentialRef, secrets, (h, p, user, password) -> {
            NdvClient client = new NdvClient();
            try {
                client.connect(h, p, user, password);
            } catch (NdvException e) {
                throw new NdvKnowledgeSource.NdvLoginException(e.getMessage(), e);
            }
            return client;
        });
    }

    private static String host(SourceSettingsReader r) {
        String host = r.required("host");
        if (host != null && (host.contains("://") || host.contains("/") || host.contains("@") || host.contains(" "))) {
            r.problem("host", "nur Name oder Adresse, ohne Schema, Pfad und Zugangsdaten");
            return null;
        }
        return host;
    }

    private static SecretRef credentialRef(SourceSettingsReader r) {
        SecretRef ref = r.secretRef("credentialRef");
        if (ref == null && r.text("credentialRef", null) == null) {
            r.problem("credentialRef", "fehlt (Pflichtangabe: NDV braucht Benutzer und Passwort)");
        }
        return ref;
    }

    private static SourceScope scope(SourceSettingsReader r) {
        return r.scope(null, 0, DEFAULT_MAX_RESOURCES);
    }
}
