package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Der Lebenszyklus einer Unterhaltung über Chat, Agent und Research-Projekt hinweg (askai arch
 * {@code AgentSessionCoordinator}: eine Agentensitzung und ein Research-Projekt je Chat, Kennung = Chat-Kennung).
 * Es gilt immer genau ein Chat; Agentensitzung und Research-Projekt gehören zu ihm:
 *
 * <ul>
 *   <li><b>Start</b>: das Research-Projekt des offenen Chats wird gebunden; Projekte ohne gespeicherten Chat
 *       (Reste, frühere Ablage mit {@code current-project}) werden verworfen.</li>
 *   <li><b>Neuer Chat</b>: neue Unterhaltung, die Agentensitzung endet, ein neues (leeres) Research-Projekt.</li>
 *   <li><b>Chat öffnen</b>: die Agentensitzung endet, das Research-Projekt dieses Chats wird geladen (Phase,
 *       Konzept, Quellen, Gliederung, Dokument). Wie in askai bekommt der Agent den bisherigen Chatverlauf nicht
 *       eingespielt: er setzt auf dem gespeicherten Projektstand auf, der Verlauf steht im Transkript.</li>
 *   <li><b>Chat löschen</b>: ist es der offene, wechselt die Oberfläche vorher in einen neuen Chat (neue Sitzung,
 *       neues Projekt); gelöscht werden Chat und Research-Projekt wie in askai erst beim Beenden
 *       ({@link #purgeDeleted}), bis dahin holt ↩ beide zurück.</li>
 * </ul>
 *
 * <p>Gewechselt wird nur, solange weder eine Antwort noch ein Agentenauftrag läuft. Nur auf dem UI-Thread
 * verwenden, ausgenommen {@link #purgeDeleted} beim Beenden.
 */
public final class ConversationLifecycle {

    /** Die Chat-Seite (Verlauf, Unterhaltung am ChatService, gespeicherte Chats). */
    public interface Chats {
        /** Die Kennung des offenen Chats. */
        String currentChatId();

        /** Alle gespeicherten Chats. */
        List<String> savedChatIds();

        /** Beginnt eine neue, leere Unterhaltung; {@code false}, solange eine Antwort läuft. */
        boolean startNewConversation();

        /** Öffnet einen gespeicherten Chat; {@code false}, wenn das gerade nicht geht. */
        boolean open(String chatId);

        /** Löscht einen gespeicherten Chat samt Anhängen endgültig. */
        void delete(String chatId);
    }

    /** Die Agentensitzung (ACP-Session samt Prozess) des offenen Chats. */
    public interface AgentSessions {
        boolean isBusy();

        /** Beendet die Sitzung; der nächste Auftrag startet frisch und ohne den alten Kontext. */
        void endSession();
    }

    private final Chats chats;
    private final AgentSessions agent;
    private final ResearchAgentHost research;
    private final Set<String> pendingDeletes = new LinkedHashSet<String>();

    /**
     * @param agent    die Agentensitzung, {@code null} ohne Agent-Modus
     * @param research das Research-Projekt je Chat, {@code null} ohne Recherche-Agent
     */
    public ConversationLifecycle(Chats chats, AgentSessions agent, ResearchAgentHost research) {
        if (chats == null) {
            throw new IllegalArgumentException("chats must not be null");
        }
        this.chats = chats;
        this.agent = agent;
        this.research = research;
    }

    /** Bindet das Research-Projekt an den offenen Chat und räumt Projekte ohne gespeicherten Chat ab. */
    public void start() {
        if (research == null) {
            return;
        }
        research.openChat(chats.currentChatId());
        research.retainProjectsOf(chats.savedChatIds());
    }

    /** „+ Neuer Chat“ (in beiden Modi): {@code false}, solange Chat oder Agent noch arbeiten. */
    public boolean newChat() {
        if (agentBusy() || !chats.startNewConversation()) {
            return false;
        }
        followChat();
        return true;
    }

    /** Öffnet einen gespeicherten Chat samt seinem Research-Projekt in einer frischen Agentensitzung. */
    public boolean openChat(String chatId) {
        if (chatId == null || agentBusy()) {
            return false;
        }
        if (chatId.equals(chats.currentChatId())) {
            return true;
        }
        if (!chats.open(chatId)) {
            return false;
        }
        followChat();
        return true;
    }

    /**
     * Merkt einen Chat zum Löschen vor; der offene Chat wird vorher verlassen. {@code false}, wenn er sich gerade
     * nicht verlassen lässt.
     */
    public boolean deleteChat(String chatId) {
        if (chatId == null) {
            return false;
        }
        if (chatId.equals(chats.currentChatId()) && !newChat()) {
            return false;
        }
        synchronized (pendingDeletes) {
            pendingDeletes.add(chatId);
        }
        return true;
    }

    /** ↩: der vorgemerkte Chat bleibt samt Research-Projekt. */
    public void restoreChat(String chatId) {
        synchronized (pendingDeletes) {
            pendingDeletes.remove(chatId);
        }
    }

    /** Die zum Löschen vorgemerkten Chats. */
    public List<String> pendingDeletes() {
        synchronized (pendingDeletes) {
            return Collections.unmodifiableList(new ArrayList<String>(pendingDeletes));
        }
    }

    /** Beim Beenden (beliebiger Thread): vorgemerkte Chats samt Anhängen und Research-Projekt endgültig löschen. */
    public void purgeDeleted() {
        List<String> doomed;
        synchronized (pendingDeletes) {
            doomed = new ArrayList<String>(pendingDeletes);
            pendingDeletes.clear();
        }
        for (String chatId : doomed) {
            chats.delete(chatId);
            if (research != null) {
                research.deleteChatProject(chatId);
            }
        }
    }

    /** Agentensitzung beenden, Research-Projekt des jetzt offenen Chats binden. */
    private void followChat() {
        if (agent != null) {
            try {
                agent.endSession(); // der Agent behielte sonst den Kontext des vorigen Chats
            } catch (IllegalStateException running) {
                // ausgeschlossen: gewechselt wird nur ohne laufenden Auftrag (agentBusy)
            }
        }
        if (research != null) {
            research.openChat(chats.currentChatId());
        }
    }

    private boolean agentBusy() {
        return agent != null && agent.isBusy();
    }
}
