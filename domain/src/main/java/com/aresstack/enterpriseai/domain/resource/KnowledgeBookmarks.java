package com.aresstack.enterpriseai.domain.resource;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Übersetzt zwischen der Ressourcen-ID der Wissensbasis ({@code wiki:intranet/Hauptseite}) und der virtuellen
 * Ressourcen-Identität aus corenth ({@code wiki://intranet/Hauptseite}). Beide benennen dieselbe Ressource; die
 * {@link BookmarkUri} ist die Adresse, unter der Tamias, Chalcotheca und Holkas sie kennen.
 *
 * <p>Bei Standardschemata ({@code file}, {@code http} ...) werden Zeichen, die in einer URI nicht stehen dürfen
 * (Leerzeichen im Dateinamen), kodiert; der Rückweg dekodiert sie wieder.
 */
public final class KnowledgeBookmarks {

    private KnowledgeBookmarks() {
    }

    public static BookmarkUri of(KnowledgeResourceId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        ResourceScheme scheme = ResourceScheme.of(id.scheme());
        String path = id.schemeSpecificPart();
        if (BookmarkUri.isStandard(scheme)) {
            try {
                return BookmarkUri.parse(new URI(scheme.name(), "//" + path, null).toASCIIString());
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("keine gültige Ressourcen-ID für " + scheme.name() + ": " + id);
            }
        }
        return BookmarkUri.of(scheme, path);
    }

    public static KnowledgeResourceId toResourceId(BookmarkUri uri) {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        String path = uri.schemeSpecificPart();
        if (path.startsWith("//")) {
            path = path.substring(2);
        }
        return KnowledgeResourceId.of(uri.scheme().name(), path);
    }
}
