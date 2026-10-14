package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.KnowledgeReaderPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * „Zum aktuellen Chat hinzufügen“ im Lesebereich: aus der kanonischen Resource-ID des Tabs wird über
 * {@link KnowledgeBookmarks} das Bookmark ({@code confluence://cf.intern/confluence/page/123}); nur Titel und
 * Bookmark kommen als Seiten-Chip in den Composer, nie der Seitentext. Danach zeigt die Schale den Chat. Der Eintrag
 * steht im Kontextmenü jedes Reiters, der eine Ressource zeigt, zusammen mit „Neuen Chat mit Dokument starten“. Nur
 * auf dem UI-Thread verwenden.
 */
public final class ReaderChatPages implements KnowledgeReaderPanel.TabMenuContributor {

    static final String MENU_ADD_TO_CHAT = "Zum aktuellen Chat hinzufügen";
    static final String MENU_NEW_CHAT = "Neuen Chat mit Dokument starten";

    private final ChatComposerPanel composer;
    private final ChatWorkspacePanel workspace;

    public ReaderChatPages(ChatComposerPanel composer, ChatWorkspacePanel workspace) {
        if (composer == null || workspace == null) {
            throw new IllegalArgumentException("composer and workspace must not be null");
        }
        this.composer = composer;
        this.workspace = workspace;
    }

    @Override
    public void contribute(JPopupMenu menu, final ReaderLink link) {
        if (!canAdd(link)) {
            return;
        }
        JMenuItem item = new JMenuItem(MENU_ADD_TO_CHAT);
        item.addActionListener(event -> add(link));
        menu.add(item);
        JMenuItem fresh = new JMenuItem(MENU_NEW_CHAT);
        fresh.addActionListener(event -> startNewChatWith(link));
        menu.add(fresh);
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

    /**
     * „Neuen Chat mit Dokument starten“ (#113): start a new chat through the usual path, then put the page of the tab
     * into its composer exactly like {@link #add}; send nothing. Touch nothing when no new chat can be started.
     *
     * @return {@code false} without a bookmark or without a new chat
     */
    public boolean startNewChatWith(ReaderLink link) {
        if (pageOf(link) == null || !workspace.startNewChat()) {
            return false;
        }
        return add(link);
    }
}
