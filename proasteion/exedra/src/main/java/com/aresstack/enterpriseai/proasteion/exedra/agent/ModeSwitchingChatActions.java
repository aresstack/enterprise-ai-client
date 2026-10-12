package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatDraft;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatQuote;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;

import java.nio.file.Path;
import java.util.List;

/**
 * Ein Chat, zwei Fähigkeiten (askai arch: die Modus-Pille schaltet nur Verhalten und Erscheinung desselben
 * Chats um): im Modus „Chat“ antwortet der normale Chat, im Modus „Agent“ der Agent — über dasselbe Model,
 * denselben Verlauf und denselben Composer. Denkaufwand gilt für beide.
 */
public final class ModeSwitchingChatActions implements ChatShellActions {

    private final ChatShellActions chat;
    private final AgentServiceBinding agent;
    private final ShellModeModel modes;
    private final ChatShellModel model;

    public ModeSwitchingChatActions(ChatShellActions chat, AgentServiceBinding agent, ShellModeModel modes,
                                    ChatShellModel model) {
        if (chat == null || agent == null || modes == null || model == null) {
            throw new IllegalArgumentException("chat, agent, modes and model must not be null");
        }
        this.chat = chat;
        this.agent = agent;
        this.modes = modes;
        this.model = model;
    }

    private boolean agentMode() {
        return modes.getMode() == ShellMode.AGENT;
    }

    @Override
    public void sendRequested(String text, boolean ragEnabled) {
        if (agentMode()) {
            agent.sendRequested(text, ragEnabled);
        } else {
            chat.sendRequested(text, ragEnabled);
        }
    }

    @Override
    public void sendRequested(String text, boolean ragEnabled, List<Path> attachments) {
        if (agentMode()) {
            agent.sendRequested(text, ragEnabled, attachments);
        } else {
            chat.sendRequested(text, ragEnabled, attachments);
        }
    }

    /**
     * Seiten aus dem Lesebereich: im Chat als Bookmarks des Chats, im Agent-Modus als Zeilen „Seite: Titel
     * (Bookmark)“ an der Nachricht; der Agent liest sie selbst mit {@code knowledge_read}.
     */
    @Override
    public void sendRequested(String text, boolean ragEnabled, List<Path> attachments, List<ChatPage> pages) {
        if (!agentMode()) {
            chat.sendRequested(text, ragEnabled, attachments, pages);
            return;
        }
        StringBuilder withPages = new StringBuilder(text);
        for (ChatPage page : pages) {
            withPages.append("\nSeite: ").append(page.title()).append(" (").append(page.bookmark()).append(')');
        }
        agent.sendRequested(withPages.toString(), ragEnabled, attachments);
    }

    /**
     * Der Entwurf des Composers: im Chat unverändert an den Chat; im Agent-Modus gehen Seiten wie oben als Zeilen an
     * die Nachricht und Verweise als Zeilen „Bezug: Titel (Kennung)“, die der Agent mit seinen Werkzeugen auflöst;
     * Zitate gehen als Hinweis vor den Auftrag ({@link AgentServiceBinding#sendQuoted}).
     */
    @Override
    public void sendRequested(ChatDraft draft) {
        if (!agentMode()) {
            chat.sendRequested(draft);
            return;
        }
        StringBuilder text = new StringBuilder(draft.text());
        for (ChatPage page : draft.pages()) {
            text.append("\nSeite: ").append(page.title()).append(" (").append(page.bookmark()).append(')');
        }
        for (ChatResource reference : draft.references()) {
            text.append("\nBezug: ").append(reference.title()).append(" (").append(reference.id()).append(')');
        }
        if (!draft.quotes().isEmpty()) {
            // Zitate: sichtbar bleibt der eingegebene Text mit den Zitat-Chips, der Agent bekommt den Hinweis davor.
            agent.sendQuoted(draft.text(), text.toString(), draft.attachments(), draft.quotes());
            return;
        }
        agent.sendRequested(text.toString(), draft.ragEnabled(), draft.attachments());
    }

    /** Die Ressourcen gehören dem Chat (derselbe Verlauf in beiden Modi). */
    @Override
    public List<ChatResource> chatResources() {
        return chat.chatResources();
    }

    /** Anschlussfragen zu einem Zitat stellt in beiden Modi der Chat (eine Nebenanfrage an das Chat-Modell). */
    @Override
    public void quoteFollowUpsRequested(ChatQuote quote, java.util.function.Consumer<List<String>> suggestions) {
        chat.quoteFollowUpsRequested(quote, suggestions);
    }

    @Override
    public boolean supportsAttachments() {
        return chat.supportsAttachments() || agent.supportsAttachments();
    }

    @Override
    public void reasoningChanged(String effort) {
        chat.reasoningChanged(effort);
        agent.reasoningChanged(effort);
    }

    @Override
    public void stopRequested() {
        // Es läuft höchstens eine Antwort; der jeweils andere Teil hat nichts abzubrechen.
        chat.stopRequested();
        agent.stopRequested();
    }
}
