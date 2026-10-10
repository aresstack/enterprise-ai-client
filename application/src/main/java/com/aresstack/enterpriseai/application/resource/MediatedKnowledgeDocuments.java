package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.application.knowledge.KnowledgeDocumentReader;
import com.aresstack.enterpriseai.application.resource.policy.ActorIdentity;
import com.aresstack.enterpriseai.application.resource.policy.ActorType;
import com.aresstack.enterpriseai.application.resource.policy.ResourceAccessRequest;
import com.aresstack.enterpriseai.application.resource.policy.ResourceOperation;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.resource.BookmarkUri;
import com.aresstack.enterpriseai.domain.resource.KnowledgeBookmarks;
import com.aresstack.enterpriseai.resource.api.AcquisitionFailure;
import com.aresstack.enterpriseai.resource.api.BronzeContent;
import com.aresstack.enterpriseai.resource.api.KnowledgeBronze;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liest Wissensdokumente über den vermittelten Zugriff aus corenth: Use Case → {@link MediatedResourceService}
 * (Tamias entscheidet, Chalcotheca hält die Bronze-Kopie) → {@code AcquisitionPort} → Holkas-Connector der Quelle.
 * Der Quellen-Port wird hier nicht direkt gefragt.
 *
 * <p>Merkt sich je Quelle, welche Adressen sie geliefert hat; {@link #withdraw} zieht deren Archiveinträge zurück,
 * wenn die Quelle entfernt wird (als {@link ActorType#SERVICE}).
 */
public final class MediatedKnowledgeDocuments implements KnowledgeDocumentReader {

    private final MediatedResourceService access;
    private final ActorIdentity reader;
    private final ActorIdentity service;
    private final Map<KnowledgeSourceId, Set<BookmarkUri>> served =
            new ConcurrentHashMap<KnowledgeSourceId, Set<BookmarkUri>>();

    /**
     * @param reader wer liest (Mensch in der Oberfläche oder Agent)
     */
    public MediatedKnowledgeDocuments(MediatedResourceService access, ActorIdentity reader) {
        if (access == null || reader == null) {
            throw new IllegalArgumentException("access and reader must not be null");
        }
        this.access = access;
        this.reader = reader;
        this.service = new ActorIdentity("enterprise-ai-client", ActorType.SERVICE);
    }

    @Override
    public KnowledgeDocument read(KnowledgeSourceId owner, KnowledgeSourcePort source, KnowledgeResourceId id)
            throws KnowledgeSourceException {
        BookmarkUri uri;
        try {
            uri = KnowledgeBookmarks.of(id);
        } catch (IllegalArgumentException e) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED,
                    "keine Ressourcenadresse für " + id);
        }
        MediatedResult<BronzeContent> result = access.readContent(
                new ResourceAccessRequest(reader, uri, ResourceOperation.READ_CONTENT, "Wissensdokument laden"));
        if (result.isDenied()) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.ACCESS_DENIED,
                    "Zugriff abgelehnt: " + result.decision().reasonCode());
        }
        if (!result.isSuccess()) {
            throw new KnowledgeSourceException(kindOf(result.errorMessage()),
                    "Laden über die Ressourcenschicht fehlgeschlagen: " + id);
        }
        KnowledgeDocument document;
        try {
            document = KnowledgeBronze.decode(result.value().content());
        } catch (IOException | RuntimeException e) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.INVALID_RESPONSE,
                    "Bronze-Kopie nicht lesbar: " + id);
        }
        if (owner != null) {
            Set<BookmarkUri> uris = served.get(owner);
            if (uris == null) {
                Set<BookmarkUri> fresh = ConcurrentHashMap.<BookmarkUri>newKeySet();
                uris = served.putIfAbsent(owner, fresh);
                if (uris == null) {
                    uris = fresh;
                }
            }
            uris.add(uri);
        }
        return document;
    }

    /** Zieht die Archiveinträge zurück, die diese Quelle geliefert hat; die Anzahl der zurückgezogenen Adressen. */
    public int withdraw(KnowledgeSourceId owner) {
        Set<BookmarkUri> uris = owner == null ? null : served.remove(owner);
        if (uris == null) {
            return 0;
        }
        int withdrawn = 0;
        for (BookmarkUri uri : new ArrayList<BookmarkUri>(uris)) {
            if (access.deleteEntry(new ResourceAccessRequest(service, uri, ResourceOperation.DELETE_ARCHIVE_ENTRY,
                    "Quelle entfernt")).isSuccess()) {
                withdrawn++;
            }
        }
        return withdrawn;
    }

    private static KnowledgeSourceException.Kind kindOf(String message) {
        String kind = AcquisitionFailure.kindIn(message);
        for (KnowledgeSourceException.Kind candidate : KnowledgeSourceException.Kind.values()) {
            if (candidate.name().equals(kind)) {
                return candidate;
            }
        }
        return KnowledgeSourceException.Kind.UNAVAILABLE;
    }

    /** Die Quellen, deren Adressen gerade im Archiv gemerkt sind (für Tests und Log). */
    public List<KnowledgeSourceId> owners() {
        return new ArrayList<KnowledgeSourceId>(served.keySet());
    }
}
