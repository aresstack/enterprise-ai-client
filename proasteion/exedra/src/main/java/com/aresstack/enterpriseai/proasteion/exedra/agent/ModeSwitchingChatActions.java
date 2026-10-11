package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
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
