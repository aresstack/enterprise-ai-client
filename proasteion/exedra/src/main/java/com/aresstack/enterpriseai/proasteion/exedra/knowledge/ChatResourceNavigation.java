package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.resource.BookmarkUri;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResourceReader;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;

import java.util.function.Function;

/**
 * Die Ressourcenübersicht im Composer am Lesebereich: „Im Lesebereich öffnen“ geht über denselben Eingang wie die
 * Quellen unter einer Antwort ({@link ChatSourceNavigation.Reading}, also {@link KnowledgeReadingController}). Grundlage
 * ist allein die stabile Kennung der Ressource: eine Seite trägt ihr Bookmark ({@code confluence://...}), das über
 * {@link KnowledgeBookmarks} zur Resource-ID wird, eine verwendete Quelle trägt die Resource-ID selbst. Dateien des
 * Chats haben keinen Leseweg; sie bleiben nur anhängbar.
 */
public final class ChatResourceNavigation implements ChatResourceReader {

    private final ChatSourceNavigation.Reading reading;
    private final Function<String, String> sourceIdOf;

    /**
     * @param reading    der Lesebereich
     * @param sourceIdOf Wissensquelle zu einer Resource-ID (z. B. über den Index); leer oder {@code null}: unbekannt
     */
    public ChatResourceNavigation(ChatSourceNavigation.Reading reading, Function<String, String> sourceIdOf) {
        if (reading == null || sourceIdOf == null) {
            throw new IllegalArgumentException("reading and sourceIdOf must not be null");
        }
        this.reading = reading;
        this.sourceIdOf = sourceIdOf;
    }

    /** Return the resource id behind a page or source resource, {@code null} for files and invalid ids. */
    static KnowledgeResourceId resourceIdOf(ChatResource resource) {
        if (resource == null) {
            return null;
        }
        try {
            switch (resource.kind()) {
                case PAGE:
                    return KnowledgeBookmarks.toResourceId(BookmarkUri.parse(resource.id()));
                case SOURCE:
                    return KnowledgeResourceId.of(resource.id());
                default:
                    return null;
            }
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    @Override
    public boolean canRead(ChatResource resource) {
        return resourceIdOf(resource) != null;
    }

    @Override
    public void read(ChatResource resource) {
        KnowledgeResourceId id = resourceIdOf(resource);
        if (id == null) {
            return;
        }
        String sourceId = sourceIdOf.apply(id.value());
        if (sourceId == null || sourceId.trim().isEmpty()) {
            return;
        }
        String location = resource.kind() == ChatResource.Kind.SOURCE ? resource.detail() : "";
        reading.showKnowledgeArea();
        reading.open(new ReaderLink(sourceId, id.value(), resource.title(), "", location), false);
    }
}
