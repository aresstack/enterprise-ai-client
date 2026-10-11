package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;

/**
 * „Zum aktuellen Chat hinzufügen“ im Lesebereich: aus der kanonischen Resource-ID des Tabs wird über
 * {@link KnowledgeBookmarks} das Bookmark ({@code confluence://cf.intern/confluence/page/123}); nur Titel und
 * Bookmark kommen als Seiten-Chip in den Composer, nie der Seitentext. Danach zeigt die Schale den Chat.
 * Nur auf dem UI-Thread verwenden.
 */
public final class ReaderChatPages {

    private final ChatComposerPanel composer;
    private final ChatWorkspacePanel workspace;

    public ReaderChatPages(ChatComposerPanel composer, ChatWorkspacePanel workspace) {
        if (composer == null || workspace == null) {
            throw new IllegalArgumentException("composer and workspace must not be null");
        }
        this.composer = composer;
        this.workspace = workspace;
    }

    /** Ob der Tab eine Ressource zeigt, die ein Bookmark hat (Quellen- und Bereichsübersichten haben keins). */
    public static boolean canAdd(ReaderLink link) {
        return pageOf(link) != null;
    }

    /** Seite aus dem Tab: Titel und Bookmark, oder {@code null} ohne gültige Resource-ID. */
    public static ChatPage pageOf(ReaderLink link) {
        if (link == null || link.isSource() || link.resourceId().isEmpty()) {
            return null;
        }
        try {
            return new ChatPage(link.title(),
                    KnowledgeBookmarks.of(KnowledgeResourceId.of(link.resourceId())).toString());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** Legt die Seite des Tabs in den Composer und zeigt den Chat; {@code false} ohne Bookmark. */
    public boolean add(ReaderLink link) {
        ChatPage page = pageOf(link);
        if (page == null) {
            return false;
        }
        composer.addPage(page);
        workspace.showChatArea();
        return true;
    }
}
